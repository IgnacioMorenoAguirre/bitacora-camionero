package com.sistemasdeinformacion.proyecto.domain

import com.sistemasdeinformacion.proyecto.data.model.EstadoValidacion
import com.sistemasdeinformacion.proyecto.data.model.EstadoViaje
import com.sistemasdeinformacion.proyecto.data.remote.ResumenViajeDto
import com.sistemasdeinformacion.proyecto.data.remote.desdeIso
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Una fila del reporte: un chofer, un camión o el total del mes. [ganancia] = precio menos gastos. */
data class FilaReporte(val clave: String, val viajes: Int, val km: Int, val gastos: Long, val ganancia: Long)

data class Reporte(
    val porChofer: List<FilaReporte>,
    val porCamion: List<FilaReporte>,
    val totales: FilaReporte,
    val aprobados: Int,
    val pendientes: Int,
    val observados: Int,
)

const val SIN_CAMION = "Sin camión"

object Reportes {

    /**
     * Reporte de un mes: solo viajes finalizados cuya fecha de salida cae en [anio]/[mes] en la hora de [zona].
     * Las filas por chofer y por camión van de mayor a menor ganancia.
     */
    fun mensual(filas: List<ResumenViajeDto>, anio: Int, mes: Int, zona: ZoneId = ZoneId.systemDefault()): Reporte {
        val periodo = YearMonth.of(anio, mes)
        val delMes = filas.filter {
            it.estado == EstadoViaje.FINALIZADO.valor &&
                YearMonth.from(Instant.ofEpochMilli(it.fechaSalida.desdeIso()).atZone(zona)) == periodo
        }
        // Por id de chofer (dos choferes pueden llamarse igual); se muestra el nombre.
        val porChofer = delMes.groupBy { it.choferId }.values.map { sumar(it.first().chofer, it) }
        val porCamion = delMes.groupBy { it.patente }.map { (patente, viajes) -> sumar(patente ?: SIN_CAMION, viajes) }
        val orden = compareByDescending<FilaReporte> { it.ganancia }.thenBy { it.clave }
        return Reporte(
            porChofer = porChofer.sortedWith(orden),
            porCamion = porCamion.sortedWith(orden),
            totales = sumar("Total", delMes),
            aprobados = delMes.count { it.validacion == EstadoValidacion.APROBADO.valor },
            pendientes = delMes.count { it.validacion == EstadoValidacion.PENDIENTE.valor },
            observados = delMes.count { it.validacion == EstadoValidacion.OBSERVADO.valor },
        )
    }

    private fun sumar(clave: String, viajes: List<ResumenViajeDto>) = FilaReporte(
        clave = clave,
        viajes = viajes.size,
        km = viajes.sumOf { it.kmRecorridos ?: 0 },
        gastos = viajes.sumOf { it.gastoTotal },
        ganancia = viajes.sumOf { it.margen },
    )
}
