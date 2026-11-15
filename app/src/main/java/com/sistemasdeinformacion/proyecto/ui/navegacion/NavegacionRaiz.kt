package com.sistemasdeinformacion.proyecto.ui.navegacion

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Signpost
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.sistemasdeinformacion.proyecto.data.model.Rol
import com.sistemasdeinformacion.proyecto.data.prefs.Perfil
import com.sistemasdeinformacion.proyecto.data.repo.EstadoSesion
import com.sistemasdeinformacion.proyecto.feature.admin.AdminCamionesScreen
import com.sistemasdeinformacion.proyecto.feature.admin.AdminDetalleScreen
import com.sistemasdeinformacion.proyecto.feature.admin.AdminReportesScreen
import com.sistemasdeinformacion.proyecto.feature.admin.AdminViajesScreen
import com.sistemasdeinformacion.proyecto.feature.auth.EnlaceRecuperacion
import com.sistemasdeinformacion.proyecto.feature.auth.LoginScreen
import com.sistemasdeinformacion.proyecto.feature.auth.NuevaContrasenaScreen
import com.sistemasdeinformacion.proyecto.feature.auth.RecuperacionScreen
import com.sistemasdeinformacion.proyecto.feature.auth.RegistroScreen
import com.sistemasdeinformacion.proyecto.feature.entrega.EntregaScreen
import com.sistemasdeinformacion.proyecto.feature.inicio.InicioScreen
import com.sistemasdeinformacion.proyecto.feature.perfil.PerfilScreen
import com.sistemasdeinformacion.proyecto.feature.ruta.HojaDeRutaScreen
import com.sistemasdeinformacion.proyecto.feature.viajes.CorregirViajeScreen
import com.sistemasdeinformacion.proyecto.feature.viajes.MisViajesScreen
import com.sistemasdeinformacion.proyecto.feature.viajes.NuevoViajeScreen
import com.sistemasdeinformacion.proyecto.feature.viajes.ResumenScreen
import com.sistemasdeinformacion.proyecto.ui.theme.Bitacora

private data class Pestana(val destino: NavKey, val texto: String, val icono: ImageVector)

private val pestanasChofer = listOf(
    Pestana(Inicio, "Inicio", Icons.Outlined.Signpost),
    Pestana(MisViajes, "Mis viajes", Icons.Outlined.Route),
    Pestana(MiPerfil, "Perfil", Icons.Outlined.Badge),
)

private val pestanasAdmin = listOf(
    Pestana(AdminViajes, "Viajes", Icons.Outlined.Route),
    Pestana(AdminReportes, "Reportes", Icons.Outlined.BarChart),
    Pestana(AdminCamiones, "Camiones", Icons.Outlined.LocalShipping),
    Pestana(MiPerfil, "Perfil", Icons.Outlined.Badge),
)

/**
 * Raíz según la sesión: sin sesión, pila [Login]; chofer, [Inicio] con su barra; administrador,
 * [AdminViajes] con la suya. Cada caso tiene su propia pila (con key), así al cerrar sesión o al
 * entrar otra persona no queda nada de la anterior en el "atrás".
 */
@Composable
fun NavegacionRaiz() {
    val container = LocalContainer.current
    val sesion by remember { container.auth.estado }.collectAsStateWithLifecycle(EstadoSesion.Cargando)

    when (val s = sesion) {
        // Lee preferencias locales: dura un instante, sin parpadeo de la pantalla de login.
        EstadoSesion.Cargando -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        EstadoSesion.SinSesion -> key("sin-sesion") { PilaSinSesion() }
        is EstadoSesion.Activa -> key(s.perfil.id, s.perfil.rol) { PilaConSesion(s.perfil) }
    }
}

@Composable
private fun PilaSinSesion() {
    val container = LocalContainer.current
    val enlace by container.enlaceRecuperacion.collectAsStateWithLifecycle()
    val pila = rememberNavBackStack(Login)
    val nav = remember(pila) { Navegador(pila) }

    // El enlace del correo de recuperación abre la pantalla de contraseña nueva (o avisa que venció).
    LaunchedEffect(enlace) {
        when (enlace) {
            EnlaceRecuperacion.VALIDO -> {
                if (pila.lastOrNull() != NuevaContrasena) nav.ir(NuevaContrasena)
                container.enlaceRecuperacion.value = EnlaceRecuperacion.NINGUNO
            }
            EnlaceRecuperacion.VENCIDO -> if (pila.lastOrNull() != Recuperacion) nav.ir(Recuperacion)
            EnlaceRecuperacion.NINGUNO -> Unit
        }
    }

    Marco(actual = null, pestanas = emptyList(), onPestana = {}) {
        Pantallas(pila, nav, entryProvider {
            entry<Login> { LoginScreen(nav) }
            entry<Registro> { RegistroScreen(nav) }
            entry<Recuperacion> { RecuperacionScreen(nav) }
            entry<NuevaContrasena> { NuevaContrasenaScreen(nav) }
        })
    }
}

@Composable
private fun PilaConSesion(perfil: Perfil) {
    val container = LocalContainer.current
    // Un aviso de enlace que no alcanzó a mostrarse no debe reaparecer tras un cierre de sesión.
    LaunchedEffect(Unit) { container.enlaceRecuperacion.value = EnlaceRecuperacion.NINGUNO }
    val esAdmin = perfil.rol == Rol.ADMIN
    val pila = rememberNavBackStack(if (esAdmin) AdminViajes else Inicio)
    val nav = remember(pila) { Navegador(pila) }

    Marco(
        actual = pila.lastOrNull(),
        pestanas = if (esAdmin) pestanasAdmin else pestanasChofer,
        onPestana = { destino -> if (pila.lastOrNull() != destino) nav.raiz(destino) },
    ) {
        Pantallas(pila, nav, entryProvider {
            entry<MiPerfil> { PerfilScreen(perfil) }
            if (esAdmin) {
                entry<AdminViajes> { AdminViajesScreen(nav) }
                entry<AdminDetalle> { AdminDetalleScreen(it.viajeId, nav) }
                entry<AdminReportes> { AdminReportesScreen() }
                entry<AdminCamiones> { AdminCamionesScreen() }
            } else {
                entry<Inicio> { InicioScreen(nav, perfil.nombre) }
                entry<MisViajes> { MisViajesScreen(nav) }
                entry<NuevoViaje> { NuevoViajeScreen(nav) }
                entry<HojaDeRuta> { HojaDeRutaScreen(it.viajeId, nav) }
                entry<Entrega> { EntregaScreen(it.viajeId, nav) }
                entry<Resumen> { ResumenScreen(it.viajeId, nav) }
                entry<CorregirViaje> { CorregirViajeScreen(it.viajeId, nav) }
            }
        })
    }
}

/** Scaffold común: la barra inferior aparece solo en los destinos de primer nivel. */
@Composable
private fun Marco(actual: NavKey?, pestanas: List<Pestana>, onPestana: (NavKey) -> Unit, contenido: @Composable () -> Unit) {
    val c = Bitacora.colores
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (pestanas.any { it.destino == actual }) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    pestanas.forEach { p ->
                        NavigationBarItem(
                            selected = actual == p.destino,
                            onClick = { onPestana(p.destino) },
                            icon = { Icon(p.icono, contentDescription = null) },
                            label = { Text(p.texto) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = c.sobreAcento,
                                indicatorColor = c.acento,
                                selectedTextColor = c.acento,
                                unselectedIconColor = c.textoSuave,
                                unselectedTextColor = c.textoSuave,
                            ),
                        )
                    }
                }
            }
        },
    ) { relleno ->
        // Las pantallas con teclado usan imePadding: sin consumir, el relleno de las barras se sumaría dos veces.
        Box(Modifier.padding(relleno).consumeWindowInsets(relleno)) { contenido() }
    }
}

@Composable
private fun Pantallas(pila: List<NavKey>, nav: Navegador, entradas: (NavKey) -> NavEntry<NavKey>) {
    NavDisplay(
        backStack = pila,
        onBack = { nav.volver() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        transitionSpec = {
            (slideInHorizontally(tween(260)) { it / 6 } + fadeIn(tween(260))) togetherWith fadeOut(tween(160))
        },
        popTransitionSpec = {
            fadeIn(tween(220)) togetherWith (slideOutHorizontally(tween(220)) { it / 6 } + fadeOut(tween(220)))
        },
        predictivePopTransitionSpec = {
            fadeIn(tween(220)) togetherWith (slideOutHorizontally(tween(220)) { it / 6 } + fadeOut(tween(220)))
        },
        entryProvider = entradas,
    )
}
