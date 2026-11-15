package com.sistemasdeinformacion.proyecto.domain

import com.sistemasdeinformacion.proyecto.data.remote.ResumenViajeDto
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class ReportesTest {

    private val santiago = ZoneId.of("America/Santiago")

    @Test fun mensual_cuentaSoloFinalizadosDelMes() {
        val filas = listOf(
            fila("a", salida = "2026-11-03T12:00:00Z", km = 500, gasto = 100_000, margen = 400_000),
            fila("b", salida = "2026-11-20T12:00:00Z", km = 300, gasto = 50_000, margen = 250_000),
            // En curso y cancelado no cuentan.
            fila("c", salida = "2026-11-10T12:00:00Z", estado = "en_curso", km = null),
            fila("d", salida = "2026-11-11T12:00:00Z", estado = "cancelado", km = 100),
            // Fuera del mes.
            fila("e", salida = "2026-10-31T12:00:00Z", km = 900),
            fila("f", salida = "2026-12-01T12:00:00Z", km = 900),
            // El mes se mide en hora de Chile (UTC-3 en noviembre): 03:30 UTC es 00:30 del 1 nov, entra.
            fila("g", salida = "2026-11-01T03:30:00Z", km = 10, gasto = 0, margen = 0),
            // 1 nov 02:00 UTC es 31 oct 23:00 en Santiago: queda fuera.
            fila("h", salida = "2026-11-01T02:00:00Z", km = 999),
        )

        val r = Reportes.mensual(filas, 2026, 11, santiago)

        assertEquals(3, r.totales.viajes)
        assertEquals(810, r.totales.km)
        assertEquals(150_000L, r.totales.gastos)
        assertEquals(650_000L, r.totales.ganancia)
    }

    @Test fun mensual_agrupaPorChoferYCamion() {
        val filas = listOf(
            fila("a", chofer = "Juan Pérez", choferId = "c1", patente = "KFZT21", km = 500, gasto = 100_000, margen = 400_000),
            fila("b", chofer = "Juan Pérez", choferId = "c1", patente = "HJRP54", km = 200, gasto = 20_000, margen = 80_000),
            fila("c", chofer = "Ana Soto", choferId = "c2", patente = "KFZT21", km = 700, gasto = 150_000, margen = 600_000),
            fila("d", chofer = "Ana Soto", choferId = "c2", patente = null, km = 100, gasto = 10_000, margen = 40_000),
        )

        val r = Reportes.mensual(filas, 2026, 11, santiago)

        // Ordenados de mayor a menor ganancia.
        assertEquals(
            listOf(
                FilaReporte("Ana Soto", viajes = 2, km = 800, gastos = 160_000, ganancia = 640_000),
                FilaReporte("Juan Pérez", viajes = 2, km = 700, gastos = 120_000, ganancia = 480_000),
            ),
            r.porChofer,
        )
        assertEquals(
            listOf(
                FilaReporte("KFZT21", viajes = 2, km = 1200, gastos = 250_000, ganancia = 1_000_000),
                FilaReporte("HJRP54", viajes = 1, km = 200, gastos = 20_000, ganancia = 80_000),
                FilaReporte("Sin camión", viajes = 1, km = 100, gastos = 10_000, ganancia = 40_000),
            ),
            r.porCamion,
        )
        assertEquals(FilaReporte("Total", viajes = 4, km = 1500, gastos = 280_000, ganancia = 1_120_000), r.totales)
    }

    @Test fun mensual_cuentaValidaciones() {
        val filas = listOf(
            fila("a", validacion = "aprobado"),
            fila("b", validacion = "aprobado"),
            fila("c", validacion = "pendiente"),
            fila("d", validacion = "observado"),
            // Un viaje en curso no cuenta como pendiente de revisión.
            fila("e", validacion = "pendiente", estado = "en_curso"),
        )

        val r = Reportes.mensual(filas, 2026, 11, santiago)

        assertEquals(2, r.aprobados)
        assertEquals(1, r.pendientes)
        assertEquals(1, r.observados)
    }

    private fun fila(
        id: String,
        salida: String = "2026-11-15T12:00:00Z",
        estado: String = "finalizado",
        validacion: String = "pendiente",
        chofer: String = "Juan Pérez",
        choferId: String = "c1",
        patente: String? = "KFZT21",
        km: Int? = 100,
        gasto: Long = 0,
        margen: Long = 0,
    ) = ResumenViajeDto(
        id = id, choferId = choferId, chofer = chofer, patente = patente, cliente = "Agrícola Sur",
        origen = "Santiago", destino = "Temuco", fechaSalida = salida, fechaLlegada = null,
        estado = estado, validacion = validacion, precio = gasto + margen, kmRecorridos = km,
        gastoTotal = gasto, margen = margen,
    )
}
