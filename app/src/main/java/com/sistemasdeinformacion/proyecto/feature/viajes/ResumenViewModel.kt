package com.sistemasdeinformacion.proyecto.feature.viajes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sistemasdeinformacion.proyecto.data.local.AdjuntoEntity
import com.sistemasdeinformacion.proyecto.data.local.ViajeEntity
import com.sistemasdeinformacion.proyecto.data.model.EstadoValidacion
import com.sistemasdeinformacion.proyecto.data.model.EstadoViaje
import com.sistemasdeinformacion.proyecto.data.repo.ViajesRepositorio
import com.sistemasdeinformacion.proyecto.domain.CalculosViaje
import com.sistemasdeinformacion.proyecto.domain.ResumenViaje
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class EstadoResumen(
    val viaje: ViajeEntity,
    val resumen: ResumenViaje,
    val adjuntos: List<AdjuntoEntity>,
) {
    val validacion: EstadoValidacion get() = viaje.validacion
    /** Comentario del administrador (se conserva también después de corregir, hasta que lo apruebe). */
    val comentario: String? get() = viaje.comentarioValidacion?.trim()?.ifBlank { null }
    /** Solo un viaje observado se corrige; uno aprobado queda de solo lectura. */
    val puedeCorregir: Boolean get() = viaje.estado == EstadoViaje.FINALIZADO && validacion == EstadoValidacion.OBSERVADO
}

/** Resumen del viaje terminado: lo que calcula [CalculosViaje.resumen] más el estado de validación. */
class ResumenViewModel(viajeId: String, repo: ViajesRepositorio) : ViewModel() {
    val estado: StateFlow<EstadoResumen?> = repo.detalle(viajeId).map { d ->
        d.viaje?.let { v ->
            EstadoResumen(v, CalculosViaje.resumen(v, d.gastos, d.camion?.rendimientoKmL), d.adjuntos.filter { !it.eliminado })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
