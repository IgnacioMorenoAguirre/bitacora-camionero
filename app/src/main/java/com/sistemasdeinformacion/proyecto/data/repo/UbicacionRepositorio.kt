package com.sistemasdeinformacion.proyecto.data.repo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

data class Ubicacion(val lat: Double, val lon: Double, val direccion: String?)

/** Ubicación por evento (nunca seguimiento continuo). Devuelve null sin permiso o sin fijación: nunca bloquea al chofer. */
interface UbicacionRepositorio {
    suspend fun actual(timeoutMs: Long = 3_000): Ubicacion?
}

class UbicacionRepositorioFused(private val context: Context) : UbicacionRepositorio {

    private val cliente = LocationServices.getFusedLocationProviderClient(context)

    fun tienePermiso(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // se verifica con tienePermiso()
    override suspend fun actual(timeoutMs: Long): Ubicacion? {
        if (!tienePermiso()) return null
        // Primero la última ubicación si es reciente (instantáneo); si no, una lectura nueva con tiempo límite.
        val reciente = runCatching { cliente.lastLocation.await() }.getOrNull()
            ?.takeIf { System.currentTimeMillis() - it.time < 2 * 60_000 }
        val loc = reciente ?: withTimeoutOrNull(timeoutMs) {
            runCatching {
                val token = CancellationTokenSource()
                cliente.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token).await()
            }.getOrNull()
        } ?: runCatching { cliente.lastLocation.await() }.getOrNull() ?: return null
        // Sin internet la dirección no se puede obtener: se guarda solo el punto y se completa al sincronizar.
        val dir = if (hayInternet()) direccion(loc.latitude, loc.longitude) else null
        return Ubicacion(loc.latitude, loc.longitude, dir)
    }

    /** Dirección legible; sin internet suele fallar y se deja en null (se completa al sincronizar). */
    suspend fun direccion(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        val geocoder = Geocoder(context, Locale.forLanguageTag("es-CL"))
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                withTimeoutOrNull(2_500) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(lista: MutableList<android.location.Address>) { cont.resume(lista.firstOrNull()?.let(::formatear)) }
                            override fun onError(errorMessage: String?) { cont.resume(null) }
                        })
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()?.let(::formatear)
            }
        }.getOrNull()
    }

    private fun hayInternet(): Boolean {
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
        return cm.getNetworkCapabilities(cm.activeNetwork)
            ?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }

    private fun formatear(a: android.location.Address): String =
        listOfNotNull(a.thoroughfare, a.locality ?: a.subAdminArea).joinToString(", ").ifBlank { a.getAddressLine(0) ?: "" }
}
