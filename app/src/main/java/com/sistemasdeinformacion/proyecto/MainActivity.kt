package com.sistemasdeinformacion.proyecto

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.sistemasdeinformacion.proyecto.ui.navegacion.LocalContainer
import com.sistemasdeinformacion.proyecto.ui.navegacion.NavegacionRaiz
import com.sistemasdeinformacion.proyecto.ui.theme.TemaBitacora

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val container = (application as BitacoraApp).container
        setContent {
            val claro by container.preferencias.temaClaro.collectAsStateWithLifecycle(false)
            LaunchedEffect(claro) {
                // Íconos de la barra de estado legibles según el tema elegido en la app.
                val estilo = if (claro) SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                else SystemBarStyle.dark(Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = estilo, navigationBarStyle = estilo)
            }
            CompositionLocalProvider(LocalContainer provides container) {
                TemaBitacora(claro = claro) {
                    NavegacionRaiz()
                }
            }
        }
    }
}
