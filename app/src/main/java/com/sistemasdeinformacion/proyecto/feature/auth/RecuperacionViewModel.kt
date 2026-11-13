package com.sistemasdeinformacion.proyecto.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sistemasdeinformacion.proyecto.data.repo.AuthRepositorio
import com.sistemasdeinformacion.proyecto.domain.Validaciones
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado del enlace bitacora://recuperar que abrió la app. */
enum class EnlaceRecuperacion { NINGUNO, VALIDO, VENCIDO }

/**
 * true si el enlace trae el código PKCE (?code=...) para canjear por la sesión de recuperación. Un
 * enlace vencido o usado llega con error=access_denied&error_code=otp_expired, y supabase-kt no avisa
 * eso por onError (solo lo registra), así que la app lo revisa antes.
 */
fun enlaceTraeSesion(fragmento: String?, consulta: String?): Boolean {
    fun claves(texto: String?) = texto.orEmpty().split('&').map { it.substringBefore('=') }.filter { it.isNotEmpty() }.toSet()
    val todas = claves(fragmento) + claves(consulta)
    return "code" in claves(consulta) && "error" !in todas && "error_code" !in todas
}

data class EstadoRecuperacion(
    val email: String = "",
    val errorEmail: String? = null,
    val error: String? = null,
    val cargando: Boolean = false,
    val enviado: Boolean = false,
)

/** Paso 1: pide el correo y manda el enlace bitacora://recuperar. */
class RecuperacionViewModel(private val repo: AuthRepositorio) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoRecuperacion())
    val estado: StateFlow<EstadoRecuperacion> = _estado.asStateFlow()

    fun cambiarEmail(v: String) = _estado.update { it.copy(email = v, errorEmail = null, error = null) }

    fun enviar() {
        val e = _estado.value
        if (e.cargando) return
        if (!Validaciones.email(e.email)) {
            _estado.update { it.copy(errorEmail = MSJ_CORREO_INVALIDO) }
            return
        }
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            val error = repo.enviarEnlaceRecuperacion(e.email).exceptionOrNull()
            _estado.update {
                it.copy(
                    cargando = false,
                    enviado = error == null,
                    error = error?.let { err -> mensajeAuth(err, "Necesitas conexión para enviar el enlace.") },
                )
            }
        }
    }
}

data class EstadoNuevaContrasena(
    val nueva: String = "",
    val confirmacion: String = "",
    val errorNueva: String? = null,
    val errorConfirmacion: String? = null,
    val error: String? = null,
    val cargando: Boolean = false,
)

/** Paso 2 (abierto desde el enlace del correo): fija la contraseña nueva con la sesión de recuperación. */
class NuevaContrasenaViewModel(private val repo: AuthRepositorio) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoNuevaContrasena())
    val estado: StateFlow<EstadoNuevaContrasena> = _estado.asStateFlow()

    fun cambiarNueva(v: String) = _estado.update { it.copy(nueva = v, errorNueva = null, error = null) }
    fun cambiarConfirmacion(v: String) = _estado.update { it.copy(confirmacion = v, errorConfirmacion = null, error = null) }

    /** [alTerminar] corre con la contraseña ya cambiada y el perfil guardado (la raíz entra sola a la app). */
    fun guardar(alTerminar: () -> Unit) {
        val e = _estado.value
        if (e.cargando) return
        val errorNueva = if (Validaciones.contrasenaValida(e.nueva)) null else MSJ_CONTRASENA_INVALIDA
        val errorConfirmacion = if (e.confirmacion == e.nueva) null else MSJ_NO_COINCIDEN
        if (errorNueva != null || errorConfirmacion != null) {
            _estado.update { it.copy(errorNueva = errorNueva, errorConfirmacion = errorConfirmacion) }
            return
        }
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            val error = repo.cambiarContrasena(e.nueva).exceptionOrNull()
            if (error == null) {
                _estado.value = EstadoNuevaContrasena()
                alTerminar()
            } else {
                _estado.update { it.copy(cargando = false, error = mensajeAuth(error, "Necesitas conexión para cambiar la contraseña.")) }
            }
        }
    }
}
