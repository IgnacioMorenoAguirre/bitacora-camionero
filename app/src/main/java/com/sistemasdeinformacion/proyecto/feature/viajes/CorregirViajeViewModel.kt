package com.sistemasdeinformacion.proyecto.feature.viajes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sistemasdeinformacion.proyecto.data.local.ViajeEntity
import com.sistemasdeinformacion.proyecto.data.model.EstadoValidacion
import com.sistemasdeinformacion.proyecto.data.repo.DatosEdicionViaje
import com.sistemasdeinformacion.proyecto.data.repo.ViajeSoloLecturaException
import com.sistemasdeinformacion.proyecto.data.repo.ViajesRepositorio
import com.sistemasdeinformacion.proyecto.domain.Formatos
import com.sistemasdeinformacion.proyecto.domain.Validaciones
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

const val MSJ_VIAJE_APROBADO = "Este viaje ya fue aprobado y no se puede modificar."

/** Corrección de los datos de un viaje observado por el administrador. */
class CorregirViajeViewModel(private val viajeId: String, private val repo: ViajesRepositorio) : ViewModel() {
    var cliente by mutableStateOf("")
    var origen by mutableStateOf("")
    var destino by mutableStateOf("")
    var carga by mutableStateOf("")
    var peso by mutableStateOf("")
    var precio by mutableStateOf("")
    var observaciones by mutableStateOf("")

    var comentarioAdmin by mutableStateOf<String?>(null)
        private set
    var listo by mutableStateOf(false)
        private set
    private var aprobado = false
    var errores by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var errorGeneral by mutableStateOf<String?>(null)
        private set
    var guardando by mutableStateOf(false)
        private set

    init {
        // Se rellena una sola vez: lo que el chofer escribe no se pisa si la base local cambia.
        viewModelScope.launch {
            val v = repo.detalle(viajeId).map { it.viaje }.filterNotNull().first()
            rellenar(v)
        }
    }

    private fun rellenar(v: ViajeEntity) {
        cliente = v.cliente
        origen = v.origen
        destino = v.destino
        carga = v.tipoCarga
        peso = formatoPeso(v.pesoKg)
        precio = Formatos.numero(v.precio)
        observaciones = v.observaciones.orEmpty()
        comentarioAdmin = v.comentarioValidacion?.trim()?.ifBlank { null }
        aprobado = v.validacion == EstadoValidacion.APROBADO
        listo = true
    }

    fun limpiar(clave: String) {
        if (clave in errores) errores = errores - clave
        errorGeneral = null
    }

    fun guardar(alGuardar: () -> Unit) {
        if (!listo || guardando) return
        if (aprobado) { errorGeneral = MSJ_VIAJE_APROBADO; return }
        val e = mutableMapOf<String, String>()
        if (cliente.isBlank()) e["cliente"] = "Escribe el cliente."
        if (origen.isBlank()) e["origen"] = "Escribe el origen."
        if (destino.isBlank()) e["destino"] = "Escribe el destino."
        if (carga.isBlank()) e["carga"] = "Escribe el tipo de carga."
        val pesoKg = Validaciones.parsearDecimal(peso)
        if (pesoKg == null || pesoKg <= 0.0) e["peso"] = "Escribe el peso en kg, por ejemplo 18.500."
        val precioNum = Validaciones.parsearEntero(precio)
        if (precioNum == null) e["precio"] = "Escribe el precio del flete, por ejemplo 900.000."
        errores = e
        if (e.isNotEmpty()) return

        guardando = true
        viewModelScope.launch {
            repo.editarViaje(
                viajeId,
                DatosEdicionViaje(
                    cliente = cliente.trim(), origen = origen.trim(), destino = destino.trim(), tipoCarga = carga.trim(),
                    pesoKg = pesoKg!!, precio = precioNum!!.toLong(), observaciones = observaciones.trim().ifBlank { null },
                ),
            ).onSuccess { alGuardar() }.onFailure {
                guardando = false
                errorGeneral = if (it is ViajeSoloLecturaException) MSJ_VIAJE_APROBADO else "No se pudo guardar la corrección. Vuelve a intentar."
            }
        }
    }

    private fun formatoPeso(kg: Double): String =
        if (kg % 1.0 == 0.0) Formatos.numero(kg.toLong()) else kg.toString().replace('.', ',')
}
