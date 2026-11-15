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

data class EstadoLogin(
    val email: String = "",
    val contrasena: String = "",
    val errorEmail: String? = null,
    val errorContrasena: String? = null,
    val error: String? = null,
    val cargando: Boolean = false,
)

/** Al entrar bien no hay que navegar: la raíz cambia sola cuando la sesión queda activa. */
class LoginViewModel(private val repo: AuthRepositorio) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoLogin())
    val estado: StateFlow<EstadoLogin> = _estado.asStateFlow()

    fun cambiarEmail(valor: String) = _estado.update { it.copy(email = valor, errorEmail = null, error = null) }

    fun cambiarContrasena(valor: String) = _estado.update { it.copy(contrasena = valor, errorContrasena = null, error = null) }

    fun entrar() {
        val e = _estado.value
        if (e.cargando) return
        val errorEmail = if (Validaciones.email(e.email)) null else MSJ_CORREO_INVALIDO
        val errorContrasena = if (e.contrasena.isEmpty()) "Escribe tu contraseña." else null
        if (errorEmail != null || errorContrasena != null) {
            _estado.update { it.copy(errorEmail = errorEmail, errorContrasena = errorContrasena) }
            return
        }
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            val error = repo.iniciarSesion(e.email, e.contrasena).exceptionOrNull()
            _estado.update {
                if (error == null) {
                    // La contraseña no queda en memoria si este ViewModel se reutiliza tras cerrar sesión.
                    it.copy(cargando = false, contrasena = "")
                } else {
                    it.copy(cargando = false, error = mensajeAuth(error, "Necesitas conexión para iniciar sesión la primera vez."))
                }
            }
        }
    }
}
