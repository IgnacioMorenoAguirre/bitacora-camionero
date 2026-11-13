package com.sistemasdeinformacion.proyecto

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.sistemasdeinformacion.proyecto.data.remote.ESQUEMA_ENLACE
import com.sistemasdeinformacion.proyecto.data.remote.HOST_RECUPERAR
import com.sistemasdeinformacion.proyecto.data.remote.supabaseConfigurado
import com.sistemasdeinformacion.proyecto.data.repo.EstadoSesion
import com.sistemasdeinformacion.proyecto.feature.auth.EnlaceRecuperacion
import com.sistemasdeinformacion.proyecto.feature.auth.enlaceTraeSesion
import com.sistemasdeinformacion.proyecto.ui.navegacion.LocalContainer
import com.sistemasdeinformacion.proyecto.ui.navegacion.NavegacionRaiz
import com.sistemasdeinformacion.proyecto.ui.theme.TemaBitacora
import io.github.jan.supabase.auth.handleDeeplinks
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container get() = (application as BitacoraApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val container = container
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
        // Al rotar la pantalla el intent es el mismo: el enlace ya se procesó.
        if (savedInstanceState == null) manejarEnlace(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        manejarEnlace(intent)
    }

    /**
     * Enlace bitacora://recuperar del correo de recuperación. Trae la sesión de recuperación en el
     * fragmento (#access_token=…&type=recovery); supabase-kt la importa y la navegación abre la
     * pantalla de contraseña nueva. Con una sesión activa en el teléfono se ignora: importar la de
     * otra cuenta mezclaría sus datos con los de quien está usando la app.
     */
    private fun manejarEnlace(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != ESQUEMA_ENLACE || uri.host != HOST_RECUPERAR || !supabaseConfigurado) return
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        lifecycleScope.launch {
            val sesion = container.auth.estado.first { it !is EstadoSesion.Cargando }
            if (sesion is EstadoSesion.Activa) {
                Toast.makeText(this@MainActivity, "Ya hay una sesión iniciada. Ciérrala para cambiar la contraseña.", Toast.LENGTH_LONG).show()
                return@launch
            }
            if (!enlaceTraeSesion(uri.encodedFragment, uri.encodedQuery)) {
                container.enlaceRecuperacion.value = EnlaceRecuperacion.VENCIDO
                return@launch
            }
            container.supabase.handleDeeplinks(
                intent,
                onSessionSuccess = { container.enlaceRecuperacion.value = EnlaceRecuperacion.VALIDO },
                onError = { container.enlaceRecuperacion.value = EnlaceRecuperacion.VENCIDO },
            )
        }
    }
}
