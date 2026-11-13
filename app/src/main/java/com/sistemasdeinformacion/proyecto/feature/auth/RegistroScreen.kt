package com.sistemasdeinformacion.proyecto.feature.auth

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sistemasdeinformacion.proyecto.ui.componentes.Aviso
import com.sistemasdeinformacion.proyecto.ui.componentes.BotonPrincipal
import com.sistemasdeinformacion.proyecto.ui.componentes.CampoTexto
import com.sistemasdeinformacion.proyecto.ui.navegacion.LocalContainer
import com.sistemasdeinformacion.proyecto.ui.navegacion.Navegador
import com.sistemasdeinformacion.proyecto.ui.theme.Bitacora

@Composable
fun RegistroScreen(nav: Navegador) {
    val container = LocalContainer.current
    val vm = viewModel { RegistroViewModel(container.auth) }
    val e by vm.estado.collectAsStateWithLifecycle()

    PantallaAuth(
        titulo = "Crear cuenta",
        bajada = "Cuenta de chofer. Necesitas conexión solo para este paso.",
        onVolver = { nav.volver() },
    ) {
        CampoTexto(
            valor = e.nombre, onCambio = vm::cambiarNombre, etiqueta = "Nombre completo", error = e.errores["nombre"],
            mayusculas = KeyboardCapitalization.Words,
        )
        CampoTexto(
            valor = e.email, onCambio = vm::cambiarEmail, etiqueta = "Correo", error = e.errores["email"],
            teclado = KeyboardType.Email, mayusculas = KeyboardCapitalization.None,
        )
        CampoTexto(
            valor = e.contrasena, onCambio = vm::cambiarContrasena, etiqueta = "Contraseña", error = e.errores["contrasena"],
            contrasena = true,
        )
        CampoTexto(
            valor = e.confirmacion, onCambio = vm::cambiarConfirmacion, etiqueta = "Repite la contraseña",
            error = e.errores["confirmacion"], contrasena = true, imeAction = ImeAction.Done, onListo = vm::crear,
        )
        Text("Mínimo 8 caracteres, con letras y números.", style = MaterialTheme.typography.bodyMedium, color = Bitacora.colores.textoSuave)
        e.error?.let { Aviso(it, Bitacora.colores.peligro) }
        Spacer(Modifier.height(4.dp))
        BotonPrincipal("Crear cuenta", vm::crear, cargando = e.cargando)
    }
}
