package com.sistemasdeinformacion.proyecto.feature.viajes

import com.sistemasdeinformacion.proyecto.data.local.CamionEntity
import com.sistemasdeinformacion.proyecto.data.local.GastoEntity
import com.sistemasdeinformacion.proyecto.data.local.ViajeEntity
import com.sistemasdeinformacion.proyecto.data.model.EstadoValidacion
import com.sistemasdeinformacion.proyecto.data.model.EstadoViaje
import com.sistemasdeinformacion.proyecto.data.model.TipoGasto
import com.sistemasdeinformacion.proyecto.data.repo.DetalleViaje

internal const val HORA = 60 * 60_000L

/** Viaje finalizado de Santiago a Temuco: 600 km en 8 h, con combustible y un peaje. */
internal fun viajeFinalizado(validacion: EstadoValidacion = EstadoValidacion.PENDIENTE, comentario: String? = null): DetalleViaje {
    val viaje = ViajeEntity(
        id = "v1", choferId = "c1", camionId = "k1", cliente = "Agrícola Sur", origen = "Santiago", destino = "Temuco",
        ruta = "5 SUR", tipoCarga = "Fruta", pesoKg = 12_000.0, requiereRefrigeracion = true, precio = 900_000,
        fechaSalida = 0, fechaLlegada = 8 * HORA, kmInicial = 1000, kmFinal = 1600, estado = EstadoViaje.FINALIZADO,
        validacion = validacion, comentarioValidacion = comentario, observaciones = "Entrega por portón 2",
        modificadoLocal = 0, syncPendiente = false,
    )
    val gastos = listOf(
        GastoEntity("g1", "v1", TipoGasto.COMBUSTIBLE, 210_000, 180.0, HORA, null, modificadoLocal = 0, syncPendiente = false),
        GastoEntity("g2", "v1", TipoGasto.PEAJE, 10_000, null, 2 * HORA, null, modificadoLocal = 0, syncPendiente = false),
    )
    val camion = CamionEntity("k1", "KFZT21", "Volvo", "FH 460", 3.0)
    return DetalleViaje(viaje, null, emptyList(), gastos, emptyList(), emptyList(), camion)
}
