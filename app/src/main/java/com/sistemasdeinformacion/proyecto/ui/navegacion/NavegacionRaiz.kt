package com.sistemasdeinformacion.proyecto.ui.navegacion

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.sistemasdeinformacion.proyecto.feature.entrega.EntregaScreen
import com.sistemasdeinformacion.proyecto.feature.inicio.InicioScreen
import com.sistemasdeinformacion.proyecto.feature.perfil.PerfilScreen
import com.sistemasdeinformacion.proyecto.feature.ruta.HojaDeRutaScreen
import com.sistemasdeinformacion.proyecto.feature.viajes.MisViajesScreen
import com.sistemasdeinformacion.proyecto.feature.viajes.NuevoViajeScreen
import com.sistemasdeinformacion.proyecto.feature.viajes.ResumenScreen
import com.sistemasdeinformacion.proyecto.ui.theme.Bitacora

@Composable
fun NavegacionRaiz() {
    val pila = rememberNavBackStack(Inicio)
    val nav = remember(pila) { Navegador(pila) }
    val actual = pila.lastOrNull()
    val c = Bitacora.colores

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (actual in destinosPrincipales) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    listOf(
                        Triple(Inicio, "Inicio", Icons.Outlined.Signpost),
                        Triple(MisViajes, "Mis viajes", Icons.Outlined.Route),
                        Triple(MiPerfil, "Perfil", Icons.Outlined.Badge),
                    ).forEach { (destino, texto, icono) ->
                        NavigationBarItem(
                            selected = actual == destino,
                            onClick = { if (actual != destino) nav.raiz(destino) },
                            icon = { Icon(icono, contentDescription = null) },
                            label = { Text(texto) },
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
        Box(Modifier.padding(relleno)) {
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
                entryProvider = entryProvider<NavKey> {
                    entry<Inicio> { InicioScreen(nav) }
                    entry<MisViajes> { MisViajesScreen(nav) }
                    entry<MiPerfil> { PerfilScreen() }
                    entry<NuevoViaje> { NuevoViajeScreen(nav) }
                    entry<HojaDeRuta> { HojaDeRutaScreen(it.viajeId, nav) }
                    entry<Entrega> { EntregaScreen(it.viajeId, nav) }
                    entry<Resumen> { ResumenScreen(it.viajeId, nav) }
                },
            )
        }
    }
}
