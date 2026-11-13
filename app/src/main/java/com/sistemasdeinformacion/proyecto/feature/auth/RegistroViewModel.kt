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

data class EstadoRegistro(
    val nombre: String = "",
    val email: String = "",
    val contrasena: String = "",
    val confirmacion: String = "",
    val errores: Map<String, String> = emptyMap(),
    val error: String? = null,
    val cargando: Boolean = false,
)

/** Crea la cuenta de chofer; el nombre viaja como metadata `nombre_completo` para el trigger de perfiles. */
class RegistroViewModel(private val repo: AuthRepositorio) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoRegistro())
    val estado: StateFlow<EstadoRegistro> = _estado.asStateFlow()

    fun cambiarNombre(v: String) = _estado.update { it.copy(nombre = v, errores = it.errores - "nombre", error = null) }
    fun cambiarEmail(v: String) = _estado.update { it.copy(email = v, errores = it.errores - "email", error = null) }
    fun cambiarContrasena(v: String) = _estado.update { it.copy(contrasena = v, errores = it.errores - "contrasena", error = null) }
    fun cambiarConfirmacion(v: String) = _estado.update { it.copy(confirmacion = v, errores = it.errores - "confirmacion", error = null) }

    fun crear() {
        val e = _estado.value
        if (e.cargando) return
        val errores = buildMap {
            if (e.nombre.trim().length < 3) put("nombre", "Escribe tu nombre completo.")
            if (!Validaciones.email(e.email)) put("email", MSJ_CORREO_INVALIDO)
            if (!Validaciones.contrasenaValida(e.contrasena)) put("contrasena", MSJ_CONTRASENA_INVALIDA)
            if (e.confirmacion != e.contrasena) put("confirmacion", MSJ_NO_COINCIDEN)
        }
        if (errores.isNotEmpty()) {
            _estado.update { it.copy(errores = errores) }
            return
        }
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            val error = repo.registrar(e.nombre, e.email, e.contrasena).exceptionOrNull()
            _estado.update {
                if (error == null) it.copy(cargando = false, contrasena = "", confirmacion = "")
                else it.copy(cargando = false, error = mensajeAuth(error, "Necesitas conexión para crear la cuenta."))
            }
        }
    }
}
