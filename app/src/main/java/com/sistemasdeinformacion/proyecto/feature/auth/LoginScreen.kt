package com.sistemasdeinformacion.proyecto.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sistemasdeinformacion.proyecto.data.remote.supabaseConfigurado
import com.sistemasdeinformacion.proyecto.ui.componentes.Aviso
import com.sistemasdeinformacion.proyecto.ui.componentes.BotonPrincipal
import com.sistemasdeinformacion.proyecto.ui.componentes.CampoTexto
import com.sistemasdeinformacion.proyecto.ui.navegacion.LocalContainer
import com.sistemasdeinformacion.proyecto.ui.navegacion.Navegador
import com.sistemasdeinformacion.proyecto.ui.navegacion.Recuperacion
import com.sistemasdeinformacion.proyecto.ui.navegacion.Registro
import com.sistemasdeinformacion.proyecto.ui.theme.Bitacora

@Composable
fun LoginScreen(nav: Navegador) {
    val container = LocalContainer.current
    val vm = viewModel { LoginViewModel(container.auth) }
    val e by vm.estado.collectAsStateWithLifecycle()

    PantallaAuth(
        titulo = "Iniciar sesión",
        bajada = "Entra con tu correo y contraseña. Después la app funciona sin señal.",
        etiqueta = "Bitácora del Camionero",
    ) {
        // Compilación sin llaves de Supabase: avisar antes de que alguien intente registrarse.
        if (!supabaseConfigurado) Aviso(MSJ_SIN_SERVIDOR, Bitacora.colores.peligro)
        CampoTexto(
            valor = e.email, onCambio = vm::cambiarEmail, etiqueta = "Correo", error = e.errorEmail,
            teclado = KeyboardType.Email, mayusculas = KeyboardCapitalization.None,
        )
        CampoTexto(
            valor = e.contrasena, onCambio = vm::cambiarContrasena, etiqueta = "Contraseña", error = e.errorContrasena,
            contrasena = true, imeAction = ImeAction.Done, onListo = vm::entrar,
        )
        e.error?.let { Aviso(it, Bitacora.colores.peligro) }
        Spacer(Modifier.height(4.dp))
        BotonPrincipal("Entrar", vm::entrar, cargando = e.cargando)
        EnlaceTexto("Olvidé mi contraseña") { nav.ir(Recuperacion) }
        EnlaceTexto("Crear cuenta de chofer") { nav.ir(Registro) }
    }
}

/** Esqueleto común de las pantallas sin sesión: título grande, una bajada corta y el formulario. */
@Composable
internal fun PantallaAuth(
    titulo: String,
    bajada: String,
    onVolver: (() -> Unit)? = null,
    etiqueta: String? = null,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    val c = Bitacora.colores
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
        if (onVolver != null) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver") }
                Text("Volver", style = MaterialTheme.typography.bodyLarge, color = c.textoSuave)
            }
        } else {
            Spacer(Modifier.height(48.dp))
        }
        Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (etiqueta != null) Text(etiqueta.uppercase(), style = MaterialTheme.typography.labelLarge, color = c.textoSuave)
            Text(titulo, style = MaterialTheme.typography.headlineLarge)
            Text(bajada, style = MaterialTheme.typography.bodyLarge, color = c.textoSuave)
            Spacer(Modifier.height(4.dp))
            contenido()
        }
    }
}

/** Acción menor en texto (el ámbar queda solo para la acción principal). */
@Composable
internal fun EnlaceTexto(texto: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(texto, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
    }
}
