package com.sistemasdeinformacion.proyecto.data.prefs

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sistemasdeinformacion.proyecto.data.model.Rol
import com.sistemasdeinformacion.proyecto.data.model.desdeValor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class Perfil(val id: String, val nombre: String, val email: String, val rol: Rol)

private val Context.dataStore by preferencesDataStore("preferencias")

/** Preferencias locales: tema, perfil en caché (para abrir sin señal) y marca de la última bajada. */
class Preferencias(private val context: Context) {
    private val claveTemaClaro = booleanPreferencesKey("tema_claro")
    private val clavePerfilId = stringPreferencesKey("perfil_id")
    private val clavePerfilNombre = stringPreferencesKey("perfil_nombre")
    private val clavePerfilEmail = stringPreferencesKey("perfil_email")
    private val clavePerfilRol = stringPreferencesKey("perfil_rol")
    private val claveMarcaBajada = stringPreferencesKey("marca_bajada")
    private val claveRestaurados = stringPreferencesKey("viajes_restaurados")

    val temaClaro: Flow<Boolean> = context.dataStore.data.map { it[claveTemaClaro] ?: false }

    suspend fun guardarTemaClaro(valor: Boolean) {
        context.dataStore.edit { it[claveTemaClaro] = valor }
    }

    val perfil: Flow<Perfil?> = context.dataStore.data.map { p ->
        val id = p[clavePerfilId] ?: return@map null
        Perfil(id, p[clavePerfilNombre].orEmpty(), p[clavePerfilEmail].orEmpty(), desdeValor(p[clavePerfilRol], Rol.CHOFER))
    }

    suspend fun guardarPerfil(perfil: Perfil?) {
        context.dataStore.edit {
            if (perfil == null) {
                it.remove(clavePerfilId); it.remove(clavePerfilNombre); it.remove(clavePerfilEmail); it.remove(clavePerfilRol)
            } else {
                it[clavePerfilId] = perfil.id
                it[clavePerfilNombre] = perfil.nombre
                it[clavePerfilEmail] = perfil.email
                it[clavePerfilRol] = perfil.rol.valor
            }
        }
    }

    suspend fun marcaBajada(): String? = context.dataStore.data.first()[claveMarcaBajada]

    suspend fun guardarMarcaBajada(iso: String) {
        context.dataStore.edit { it[claveMarcaBajada] = iso }
    }

    /** Viajes que el servidor rechazó por estar aprobados; el Inicio avisa una vez y los limpia. */
    val viajesRestaurados: Flow<List<String>> =
        context.dataStore.data.map { p -> p[claveRestaurados]?.split(',')?.filter { it.isNotBlank() } ?: emptyList() }

    suspend fun agregarRestaurados(ids: List<String>) {
        if (ids.isEmpty()) return
        context.dataStore.edit { p ->
            val actuales = p[claveRestaurados]?.split(',')?.filter { it.isNotBlank() } ?: emptyList()
            p[claveRestaurados] = (actuales + ids).distinct().joinToString(",")
        }
    }

    suspend fun limpiarRestaurados() {
        context.dataStore.edit { it.remove(claveRestaurados) }
    }

    /** Al cerrar sesión: borra perfil y marcas, conserva el tema elegido. */
    suspend fun limpiar() {
        context.dataStore.edit { p ->
            val tema = p[claveTemaClaro]
            p.clear()
            if (tema != null) p[claveTemaClaro] = tema
        }
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
