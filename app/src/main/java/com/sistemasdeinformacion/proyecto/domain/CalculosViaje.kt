package com.sistemasdeinformacion.proyecto.domain

import com.sistemasdeinformacion.proyecto.data.local.EventoEntity
import com.sistemasdeinformacion.proyecto.data.local.GastoEntity
import com.sistemasdeinformacion.proyecto.data.local.ViajeEntity
import com.sistemasdeinformacion.proyecto.data.local.kmRecorridos
import com.sistemasdeinformacion.proyecto.data.model.AlertaHoras
import com.sistemasdeinformacion.proyecto.data.model.TipoEvento
import com.sistemasdeinformacion.proyecto.data.model.TipoGasto
import kotlin.math.roundToLong

data class ResumenViaje(
    val kmRecorridos: Int?,
    val duracionMs: Long?,
    val gastoTotal: Long,
    val gastoPorTipo: Map<TipoGasto, Long>,
    val ganancia: Long,
    val litrosReales: Double,
    val litrosEstimados: Double?,
    val pesosEstimados: Long?,
)

object CalculosViaje {

    /** Límite de manejo continuo (referencia: art. 25 bis del Código del Trabajo; verificar texto vigente). */
    const val LIMITE_MANEJO_MS: Long = 5 * 60 * 60_000L
    const val AVISO_PREVIO_MS: Long = 30 * 60_000L
    const val DESCANSO_MINIMO_MS: Long = 30 * 60_000L

    fun resumen(viaje: ViajeEntity, gastos: List<GastoEntity>, rendimientoKmL: Double?): ResumenViaje {
        val vigentes = gastos.filter { !it.eliminado }
        val porTipo = vigentes.groupBy { it.tipo }.mapValues { (_, g) -> g.sumOf { it.monto } }
        val total = vigentes.sumOf { it.monto }
        val combustible = vigentes.filter { it.tipo == TipoGasto.COMBUSTIBLE }
        val litrosReales = combustible.sumOf { it.litros ?: 0.0 }
        val km = viaje.kmRecorridos
        val litrosEstimados = if (km != null && rendimientoKmL != null && rendimientoKmL > 0) km / rendimientoKmL else null
        val precioLitro = if (litrosReales > 0) combustible.sumOf { it.monto } / litrosReales else null
        val pesosEstimados = if (litrosEstimados != null && precioLitro != null) (litrosEstimados * precioLitro).roundToLong() else null
        return ResumenViaje(
            kmRecorridos = km,
            duracionMs = viaje.fechaLlegada?.let { it - viaje.fechaSalida },
            gastoTotal = total,
            gastoPorTipo = porTipo,
            ganancia = viaje.precio - total,
            litrosReales = litrosReales,
            litrosEstimados = litrosEstimados,
            pesosEstimados = pesosEstimados,
        )
    }

    /**
     * Tiempo manejando sin un descanso válido. El conteo parte al iniciar el viaje y se reinicia al
     * terminar cada descanso de al menos [DESCANSO_MINIMO_MS]. Durante un descanso en curso es 0.
     */
    fun manejoContinuoMs(inicioViaje: Long, eventos: List<EventoEntity>, ahora: Long): Long {
        val descansos = eventos.filter { it.tipo == TipoEvento.DESCANSO && !it.eliminado }
        if (descansos.any { it.fechaFin == null }) return 0L
        val desde = descansos
            .filter { (it.fechaFin!! - it.fechaHora) >= DESCANSO_MINIMO_MS && it.fechaFin!! <= ahora }
            .maxOfOrNull { it.fechaFin!! }
            ?.coerceAtLeast(inicioViaje) ?: inicioViaje
        return (ahora - desde).coerceAtLeast(0L)
    }

    fun alerta(manejoContinuoMs: Long): AlertaHoras = when {
        manejoContinuoMs >= LIMITE_MANEJO_MS -> AlertaHoras.EXCEDIDA
        manejoContinuoMs >= LIMITE_MANEJO_MS - AVISO_PREVIO_MS -> AlertaHoras.PROXIMA
        else -> AlertaHoras.NORMAL
    }
}
