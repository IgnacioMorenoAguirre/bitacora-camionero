package com.sistemasdeinformacion.proyecto.feature.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sistemasdeinformacion.proyecto.data.model.EstadoValidacion
import com.sistemasdeinformacion.proyecto.data.model.EstadoViaje
import com.sistemasdeinformacion.proyecto.data.repo.AdminRepositorio
import com.sistemasdeinformacion.proyecto.data.repo.DetalleViajeRemoto
import com.sistemasdeinformacion.proyecto.data.repo.ErrorAdmin
import com.sistemasdeinformacion.proyecto.feature.ruta.construirHitos
import com.sistemasdeinformacion.proyecto.ui.componentes.Hito
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EstadoAdminDetalle(
    val cargando: Boolean = true,
    val sinConexion: Boolean = false,
    val error: String? = null,
    val detalle: DetalleViajeRemoto? = null,
    val hitos: List<Hito> = emptyList(),
    val comentario: String = "",
    val errorComentario: String? = null,
    val guardando: Boolean = false,
    val errorValidacion: String? = null,
    val mensaje: String? = null,
) {
    private val finalizado: Boolean get() = detalle?.viaje?.estado == EstadoViaje.FINALIZADO.valor
    val validacion: EstadoValidacion?
        get() = detalle?.viaje?.validacion?.let { v -> EstadoValidacion.entries.firstOrNull { it.valor == v } ?: EstadoValidacion.PENDIENTE }
    /**
     * Comentario y botones de revisión: solo para un viaje finalizado que espera revisión. Uno aprobado ya
     * no se toca; uno observado vuelve a "pendiente" cuando el chofer lo corrige (trigger del servidor).
     */
    val muestraRevision: Boolean get() = finalizado && validacion == EstadoValidacion.PENDIENTE
    val puedeAprobar: Boolean get() = muestraRevision && !guardando
    val puedeObservar: Boolean get() = muestraRevision && !guardando
    /** Por qué no se puede revisar ahora (lo explica la pantalla en lugar de los botones). */
    val motivoSinValidar: String?
        get() = when {
            detalle == null -> null
            !finalizado -> MSJ_SOLO_FINALIZADOS
            validacion == EstadoValidacion.OBSERVADO -> MSJ_ESPERANDO_CORRECCION
            else -> null
        }
    val excedioHoras: Boolean get() = detalle?.excedioHoras == true
}

const val MSJ_SOLO_FINALIZADOS = "Solo se aprueban viajes finalizados."
const val MSJ_ESPERANDO_CORRECCION = "Esperando la corrección del chofer."
const val MSJ_FALTA_COMENTARIO = "Escribe qué debe corregir el chofer."

/** Detalle de un viaje para el administrador: lo baja del servidor y permite aprobarlo u observarlo. */
class AdminDetalleViewModel(
    private val viajeId: String,
    private val repo: AdminRepositorio,
    private val conexion: Flow<Boolean>,
    private val ahora: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoAdminDetalle())
    val estado: StateFlow<EstadoAdminDetalle> = _estado.asStateFlow()
    private var carga: Job? = null

    init {
        // Sin red se muestra el aviso; al volver, se recarga solo.
        viewModelScope.launch {
            conexion.collect { hay ->
                if (!hay) _estado.update { it.copy(sinConexion = true) }
                else if (_estado.value.sinConexion || _estado.value.detalle == null) cargar()
            }
        }
    }

    fun reintentar() = cargar()

    private fun cargar() {
        if (carga?.isActive == true) return
        _estado.update { it.copy(cargando = true, sinConexion = false, error = null) }
        carga = viewModelScope.launch {
            try {
                val d = repo.detalle(viajeId)
                _estado.update {
                    it.copy(cargando = false, detalle = d, hitos = construirHitos(d.comoDetalleLocal(), ahora()))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update {
                    if (e is ErrorAdmin.SinConexion) it.copy(cargando = false, sinConexion = true)
                    else it.copy(cargando = false, error = (e as? ErrorAdmin)?.message ?: "No se pudo cargar el viaje. Vuelve a intentar.")
                }
            }
        }
    }

    fun cambiarComentario(v: String) = _estado.update { it.copy(comentario = v, errorComentario = null, errorValidacion = null) }

    fun aprobar() {
        if (!_estado.value.puedeAprobar) return
        validar(aprobado = true, comentario = null)
    }

    fun observar() {
        val e = _estado.value
        if (!e.puedeObservar) return
        val comentario = e.comentario.trim()
        if (comentario.isEmpty()) {
            _estado.update { it.copy(errorComentario = MSJ_FALTA_COMENTARIO) }
            return
        }
        validar(aprobado = false, comentario = comentario)
    }

    fun mensajeVisto() = _estado.update { it.copy(mensaje = null) }

    private fun validar(aprobado: Boolean, comentario: String?) {
        _estado.update { it.copy(guardando = true, errorValidacion = null, mensaje = null) }
        viewModelScope.launch {
            val error = repo.validar(viajeId, aprobado, comentario).exceptionOrNull()
            if (error == null) {
                _estado.update {
                    it.copy(
                        guardando = false,
                        comentario = "",
                        mensaje = if (aprobado) "Bitácora aprobada." else "Observación enviada al chofer.",
                        // Se refleja al instante; la recarga trae lo que quedó en el servidor.
                        detalle = it.detalle?.let { d ->
                            d.copy(viaje = d.viaje.copy(
                                validacion = if (aprobado) EstadoValidacion.APROBADO.valor else EstadoValidacion.OBSERVADO.valor,
                                comentarioValidacion = comentario,
                            ))
                        },
                    )
                }
                cargar()
            } else {
                _estado.update {
                    it.copy(guardando = false, errorValidacion = (error as? ErrorAdmin)?.message ?: "No se pudo guardar. Vuelve a intentar.")
                }
            }
        }
    }
}
