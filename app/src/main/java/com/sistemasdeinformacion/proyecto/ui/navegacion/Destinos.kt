package com.sistemasdeinformacion.proyecto.ui.navegacion

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavKey
import com.sistemasdeinformacion.proyecto.di.AppContainer
import kotlinx.serialization.Serializable

@Serializable data object Inicio : NavKey
@Serializable data object MisViajes : NavKey
@Serializable data object NuevoViaje : NavKey
@Serializable data class HojaDeRuta(val viajeId: String) : NavKey
@Serializable data class Entrega(val viajeId: String) : NavKey
@Serializable data class Resumen(val viajeId: String) : NavKey
@Serializable data object MiPerfil : NavKey

/** Destinos de la barra inferior del chofer. */
val destinosPrincipales: List<NavKey> = listOf(Inicio, MisViajes, MiPerfil)

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer no disponible") }

/** Acciones de navegación que reciben las pantallas (así no dependen de la pila). */
class Navegador(private val pila: MutableList<NavKey>) {
    fun ir(destino: NavKey) { pila.add(destino) }
    fun volver() { if (pila.size > 1) pila.removeAt(pila.lastIndex) }
    fun reemplazar(destino: NavKey) {
        if (pila.size > 1) pila.removeAt(pila.lastIndex)
        pila.add(destino)
    }
    fun raiz(destino: NavKey) {
        pila.clear()
        pila.add(destino)
    }
    /** Deja solo el Inicio debajo del destino (p. ej. al cerrar un viaje). */
    fun raizCon(destino: NavKey) {
        pila.clear()
        pila.add(Inicio)
        pila.add(destino)
    }
}
