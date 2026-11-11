package com.sistemasdeinformacion.proyecto.domain

import com.sistemasdeinformacion.proyecto.data.local.EventoEntity
import com.sistemasdeinformacion.proyecto.data.local.GastoEntity
import com.sistemasdeinformacion.proyecto.data.local.ViajeEntity
import com.sistemasdeinformacion.proyecto.data.model.AlertaHoras
import com.sistemasdeinformacion.proyecto.data.model.EstadoViaje
import com.sistemasdeinformacion.proyecto.data.model.TipoEvento
import com.sistemasdeinformacion.proyecto.data.model.TipoGasto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculosViajeTest {

    private val h = 3_600_000L
    private val min = 60_000L
    private val seis = 6 * h // 06:00 del "día 0"

    private fun viaje(kmIni: Int? = 1000, kmFin: Int? = 1500, llegada: Long? = seis + 8 * h) = ViajeEntity(
        id = "v1", choferId = "c1", camionId = "k1", cliente = "Agrícola Sur", origen = "Santiago",
        destino = "Concepción", ruta = "5 SUR", tipoCarga = "Fruta", pesoKg = 18500.0,
        requiereRefrigeracion = true, precio = 900_000, fechaSalida = seis, fechaLlegada = llegada,
        kmInicial = kmIni, kmFinal = kmFin, estado = EstadoViaje.FINALIZADO, observaciones = null,
        modificadoLocal = 0, syncPendiente = false,
    )

    private fun gasto(id: String, tipo: TipoGasto, monto: Long, litros: Double? = null, eliminado: Boolean = false) =
        GastoEntity(id, "v1", tipo, monto, litros, seis, null, eliminado, 0, false)

    private fun descanso(desde: Long, hasta: Long?) =
        EventoEntity("e$desde", "v1", TipoEvento.DESCANSO, desde, hasta, null, null, null, null, null, false, 0, false)

    private val gastos = listOf(
        gasto("g1", TipoGasto.COMBUSTIBLE, 210_000, 180.0),
        gasto("g2", TipoGasto.PEAJE, 4_100),
    )

    @Test fun resumen_calculaGananciaYLitros() {
        val r = CalculosViaje.resumen(viaje(), gastos, 3.0)
        assertEquals(500, r.kmRecorridos)
        assertEquals(214_100L, r.gastoTotal)
        assertEquals(685_900L, r.ganancia)
        assertEquals(180.0, r.litrosReales, 0.01)
        assertEquals(166.67, r.litrosEstimados!!, 0.01)
        assertEquals(194_444L, r.pesosEstimados)
        assertEquals(8 * h, r.duracionMs)
        assertEquals(210_000L, r.gastoPorTipo[TipoGasto.COMBUSTIBLE])
    }

    @Test fun resumen_ignoraGastosEliminados() {
        val r = CalculosViaje.resumen(viaje(), gastos + gasto("g3", TipoGasto.OTRO, 50_000, eliminado = true), 3.0)
        assertEquals(214_100L, r.gastoTotal)
    }

    @Test fun resumen_sinKmFinal_noEstima() {
        val r = CalculosViaje.resumen(viaje(kmFin = null, llegada = null), gastos, 3.0)
        assertNull(r.kmRecorridos)
        assertNull(r.litrosEstimados)
        assertNull(r.duracionMs)
    }

    @Test fun manejoContinuo_sinDescansos_cuentaDesdeInicio() =
        assertEquals(5 * h + 20 * min, CalculosViaje.manejoContinuoMs(seis, emptyList(), seis + 5 * h + 20 * min))

    @Test fun manejoContinuo_descansoLargoReinicia() {
        val ev = listOf(descanso(seis + 3 * h, seis + 3 * h + 40 * min))
        assertEquals(1 * h + 40 * min, CalculosViaje.manejoContinuoMs(seis, ev, seis + 5 * h + 20 * min))
    }

    @Test fun manejoContinuo_paradaCortaNoReinicia() {
        val ev = listOf(descanso(seis + 3 * h, seis + 3 * h + 10 * min))
        assertEquals(5 * h + 20 * min, CalculosViaje.manejoContinuoMs(seis, ev, seis + 5 * h + 20 * min))
    }

    @Test fun manejoContinuo_descansoEnCurso_esCero() {
        val ev = listOf(descanso(seis + 3 * h, null))
        assertEquals(0L, CalculosViaje.manejoContinuoMs(seis, ev, seis + 4 * h))
    }

    @Test fun alerta_umbrales() {
        assertEquals(AlertaHoras.NORMAL, CalculosViaje.alerta(4 * h + 29 * min))
        assertEquals(AlertaHoras.PROXIMA, CalculosViaje.alerta(4 * h + 30 * min))
        assertEquals(AlertaHoras.EXCEDIDA, CalculosViaje.alerta(5 * h))
    }
}
