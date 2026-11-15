package com.sistemasdeinformacion.proyecto.feature.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sistemasdeinformacion.proyecto.data.remote.CamionDto
import com.sistemasdeinformacion.proyecto.data.repo.AdminRepositorio
import com.sistemasdeinformacion.proyecto.data.repo.ErrorAdmin
import com.sistemasdeinformacion.proyecto.domain.Validaciones
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/** Lista de camiones según la red y el servidor. */
sealed interface ListaCamiones {
    data object Cargando : ListaCamiones
    data object SinConexion : ListaCamiones
    data class Error(val mensaje: String) : ListaCamiones
    data class Lista(val camiones: List<CamionDto>) : ListaCamiones
}

/** Formulario de alta o edición; [id] null = camión nuevo. */
data class FormCamion(
    val id: String? = null,
    val patente: String = "",
    val marca: String = "",
    val modelo: String = "",
    val anio: String = "",
    val rendimiento: String = "",
    val activo: Boolean = true,
    val errores: Map<String, String> = emptyMap(),
    val error: String? = null,
    val guardando: Boolean = false,
)

data class EstadoAdminCamiones(
    val lista: ListaCamiones = ListaCamiones.Cargando,
    val formulario: FormCamion? = null,
    val mensaje: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class AdminCamionesViewModel(private val repo: AdminRepositorio, conexion: Flow<Boolean>) : ViewModel() {

    private val intento = MutableStateFlow(0)
    private val formulario = MutableStateFlow<FormCamion?>(null)
    private val mensaje = MutableStateFlow<String?>(null)

    private val lista: Flow<ListaCamiones> = combine(conexion, intento) { hay, _ -> hay }.flatMapLatest { hay ->
        if (!hay) flowOf(ListaCamiones.SinConexion)
        else repo.camiones()
            .map<List<CamionDto>, ListaCamiones> { ListaCamiones.Lista(it) }
            .catch { e ->
                emit(if (e is ErrorAdmin.SinConexion) ListaCamiones.SinConexion else ListaCamiones.Error((e as? ErrorAdmin)?.message ?: ErrorAdmin.Desconocido.message!!))
            }
    }

    // Eagerly: la lista es una consulta corta (sin Realtime) y así el estado está al día aunque nadie lo observe.
    val estado: StateFlow<EstadoAdminCamiones> = combine(lista, formulario, mensaje) { l, f, m -> EstadoAdminCamiones(l, f, m) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, EstadoAdminCamiones())

    fun reintentar() = intento.update { it + 1 }

    fun nuevo() { formulario.value = FormCamion() }

    fun editar(c: CamionDto) {
        formulario.value = FormCamion(
            id = c.id, patente = c.patente, marca = c.marca, modelo = c.modelo, anio = c.anio?.toString().orEmpty(),
            rendimiento = String.format(Locale.forLanguageTag("es-CL"), "%.2f", c.rendimientoKmL).trimEnd('0').trimEnd(','),
            activo = c.activo,
        )
    }

    fun cerrarFormulario() { formulario.value = null }
    fun mensajeVisto() { mensaje.value = null }

    private fun cambiar(campo: String, f: (FormCamion) -> FormCamion) =
        formulario.update { it?.let { actual -> f(actual).copy(errores = actual.errores - campo, error = null) } }

    fun cambiarPatente(v: String) = cambiar("patente") { it.copy(patente = v) }
    fun cambiarMarca(v: String) = cambiar("marca") { it.copy(marca = v) }
    fun cambiarModelo(v: String) = cambiar("modelo") { it.copy(modelo = v) }
    fun cambiarAnio(v: String) = cambiar("anio") { it.copy(anio = v) }
    fun cambiarRendimiento(v: String) = cambiar("rendimiento") { it.copy(rendimiento = v) }
    fun cambiarActivo(v: Boolean) = cambiar("activo") { it.copy(activo = v) }

    fun guardar() {
        val f = formulario.value ?: return
        if (f.guardando) return
        val anio = if (f.anio.isBlank()) null else Validaciones.parsearEntero(f.anio)
        val rendimiento = Validaciones.parsearDecimal(f.rendimiento)
        val errores = buildMap {
            if (!Validaciones.patente(f.patente)) put("patente", "Escribe una patente válida, como BBBB12 o AB1234.")
            if (f.marca.isBlank()) put("marca", "Escribe la marca.")
            if (f.modelo.isBlank()) put("modelo", "Escribe el modelo.")
            if (f.anio.isNotBlank() && (anio == null || anio !in 1980..2100)) put("anio", "Escribe un año entre 1980 y 2100, o déjalo vacío.")
            if (rendimiento == null || rendimiento <= 0.0 || rendimiento >= 1000.0) put("rendimiento", "Escribe el rendimiento en km/L, por ejemplo 3,2.")
        }
        if (errores.isNotEmpty()) {
            formulario.value = f.copy(errores = errores)
            return
        }
        val camion = CamionDto(
            id = f.id, patente = Validaciones.normalizarPatente(f.patente), marca = f.marca.trim(), modelo = f.modelo.trim(),
            anio = anio, rendimientoKmL = rendimiento!!, activo = f.activo,
        )
        formulario.value = f.copy(guardando = true, error = null)
        viewModelScope.launch {
            val error = repo.guardarCamion(camion).exceptionOrNull()
            if (error == null) {
                formulario.value = null
                mensaje.value = if (f.id == null) "Camión agregado." else "Camión guardado."
            } else {
                formulario.update {
                    it?.copy(guardando = false, error = (error as? ErrorAdmin)?.message ?: "No se pudo guardar. Vuelve a intentar.")
                }
            }
        }
    }
}
