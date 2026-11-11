package com.sistemasdeinformacion.proyecto.data.prefs

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.sistemasdeinformacion.proyecto.data.model.Rol
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

data class Perfil(val id: String, val nombre: String, val email: String, val rol: Rol)

private val Context.dataStore by preferencesDataStore("preferencias")

/** Preferencias locales. El perfil en caché y la marca de bajada se agregan en las Tasks 5 y 8. */
class Preferencias(private val context: Context) {
    private val claveTemaClaro = booleanPreferencesKey("tema_claro")

    val temaClaro: Flow<Boolean> = context.dataStore.data.map { it[claveTemaClaro] ?: false }

    suspend fun guardarTemaClaro(valor: Boolean) {
        context.dataStore.edit { it[claveTemaClaro] = valor }
    }
}

/** true cuando hay una red con internet validado. */
fun observarConexion(context: Context): Flow<Boolean> = callbackFlow {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    fun hayInternet(): Boolean = cm.getNetworkCapabilities(cm.activeNetwork)
        ?.let { it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } == true
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { trySend(hayInternet()) }
        override fun onLost(network: Network) { trySend(hayInternet()) }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { trySend(hayInternet()) }
    }
    trySend(hayInternet())
    cm.registerNetworkCallback(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), callback)
    awaitClose { cm.unregisterNetworkCallback(callback) }
}.distinctUntilChanged()
