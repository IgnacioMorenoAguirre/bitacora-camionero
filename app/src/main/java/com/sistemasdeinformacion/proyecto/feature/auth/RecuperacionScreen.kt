package com.sistemasdeinformacion.proyecto.feature.auth

import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sistemasdeinformacion.proyecto.ui.componentes.Aviso
import com.sistemasdeinformacion.proyecto.ui.componentes.BotonPrincipal
import com.sistemasdeinformacion.proyecto.ui.componentes.BotonSecundario
import com.sistemasdeinformacion.proyecto.ui.componentes.CampoTexto
import com.sistemasdeinformacion.proyecto.ui.navegacion.LocalContainer
import com.sistemasdeinformacion.proyecto.ui.navegacion.Navegador
import com.sistemasdeinformacion.proyecto.ui.theme.Bitacora


/** Paso 1: pide el correo y manda el enlace. El enlace abre la app en [NuevaContrasenaScreen]. */
@Composable
fun RecuperacionScreen(nav: Navegador) {
    val container = LocalContainer.current
    val vm = viewModel { RecuperacionViewModel(container.auth) }
    val e by vm.estado.collectAsStateWithLifecycle()
    val enlace by container.enlaceRecuperacion.collectAsStateWithLifecycle()
    val c = Bitacora.colores

    // El aviso de enlace vencido se muestra una vez, mientras esta pantalla está abierta.
    DisposableEffect(Unit) { onDispose { container.enlaceRecuperacion.value = EnlaceRecuperacion.NINGUNO } }

    PantallaAuth(
        titulo = "Recuperar contraseña",
        bajada = "Te mandamos un enlace a tu correo para elegir una contraseña nueva.",
        onVolver = { nav.volver() },
    ) {
        if (e.enviado) {
            Aviso("Te enviamos un enlace a tu correo. Ábrelo en este teléfono.", Bitacora.colores.exito, icono = Icons.Outlined.MarkEmailRead)
            Spacer(Modifier.height(4.dp))
            BotonSecundario("Volver a iniciar sesión", { nav.volver() })
        } else {
            if (enlace == EnlaceRecuperacion.VENCIDO) Aviso("El enlace venció o ya se usó. Pide uno nuevo.", c.acento)
            CampoTexto(
                valor = e.email, onCambio = vm::cambiarEmail, etiqueta = "Correo", error = e.errorEmail,
                teclado = KeyboardType.Email, mayusculas = KeyboardCapitalization.None,
                imeAction = ImeAction.Done, onListo = vm::enviar,
            )
            e.error?.let { Aviso(it, c.peligro) }
            Spacer(Modifier.height(4.dp))
            BotonPrincipal("Enviar enlace", vm::enviar, cargando = e.cargando)
        }
    }
}

/** Paso 2: abierta por el enlace bitacora://recuperar, con la sesión de recuperación ya importada. */
@Composable
fun NuevaContrasenaScreen(nav: Navegador) {
    val container = LocalContainer.current
    val contexto = LocalContext.current
    val vm = viewModel { NuevaContrasenaViewModel(container.auth) }
    val e by vm.estado.collectAsStateWithLifecycle()
    val guardar = {
        // Al terminar, la sesión queda activa y la raíz cambia sola al Inicio.
        vm.guardar { Toast.makeText(contexto, "Contraseña cambiada.", Toast.LENGTH_SHORT).show() }
    }

    PantallaAuth(
        titulo = "Nueva contraseña",
        bajada = "Elige la contraseña con la que vas a entrar desde ahora.",
        onVolver = { nav.volver() },
    ) {
        CampoTexto(valor = e.nueva, onCambio = vm::cambiarNueva, etiqueta = "Contraseña nueva", error = e.errorNueva, contrasena = true)
        CampoTexto(
            valor = e.confirmacion, onCambio = vm::cambiarConfirmacion, etiqueta = "Repite la contraseña",
            error = e.errorConfirmacion, contrasena = true, imeAction = ImeAction.Done, onListo = guardar,
        )
        e.error?.let { Aviso(it, Bitacora.colores.peligro) }
        Spacer(Modifier.height(4.dp))
        BotonPrincipal("Cambiar contraseña", guardar, cargando = e.cargando)
    }
}
