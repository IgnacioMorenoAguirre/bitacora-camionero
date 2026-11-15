package com.sistemasdeinformacion.proyecto.feature.admin

import com.sistemasdeinformacion.proyecto.data.remote.ResumenViajeDto
import com.sistemasdeinformacion.proyecto.data.repo.ErrorAdmin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class AdminReportesViewModelTest {

    private val zona = ZoneId.of("America/Santiago")
    private val ahora = OffsetDateTime.parse("2026-11-14T15:00:00-03:00").toInstant().toEpochMilli()
    private val conexion = MutableStateFlow(true)

    @Before fun antes() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun despues() { Dispatchers.resetMain() }

    @Test fun abre_enElMesActual_yConsultaSuRango() = runTest {
        val repo = ReportesFalso(listOf(fila("a", "2026-11-05T12:00:00Z", km = 500)))
        val vm = AdminReportesViewModel(repo, conexion, { ahora }, zona)

        val e = vm.estado.value
        assertEquals(YearMonth.of(2026, 11), e.mes)
        assertEquals(1, e.reporte?.totales?.viajes)
        assertEquals(500, e.reporte?.totales?.km)
        val (desde, hasta) = repo.consultas.single()
        assertEquals(OffsetDateTime.parse("2026-11-01T00:00:00-03:00").toInstant().toEpochMilli(), desde)
        assertEquals(OffsetDateTime.parse("2026-12-01T00:00:00-03:00").toInstant().toEpochMilli(), hasta)
    }

    @Test fun mesAnterior_recarga_yNoAvanzaMasAllaDelActual() = runTest {
        val repo = ReportesFalso(emptyList())
        val vm = AdminReportesViewModel(repo, conexion, { ahora }, zona)

        assertFalse(vm.estado.value.puedeAvanzar)
        vm.mesSiguiente()
        assertEquals(YearMonth.of(2026, 11), vm.estado.value.mes)

        vm.mesAnterior()
        assertEquals(YearMonth.of(2026, 10), vm.estado.value.mes)
        assertTrue(vm.estado.value.puedeAvanzar)
        assertEquals(2, repo.consultas.size)
    }

    @Test fun sinConexion_muestraEstadoSinRed() = runTest {
        conexion.value = false
        val vm = AdminReportesViewModel(ReportesFalso(emptyList()), conexion, { ahora }, zona)

        assertTrue(vm.estado.value.sinConexion)
        assertNull(vm.estado.value.reporte)
    }

    @Test fun errorDelServidor_muestraMensaje() = runTest {
        val repo = ReportesFalso(emptyList()).apply { falla = ErrorAdmin.SinPermiso }
        val vm = AdminReportesViewModel(repo, conexion, { ahora }, zona)

        assertEquals("Tu cuenta no tiene permiso de administrador.", vm.estado.value.error)
    }

    private fun fila(id: String, salida: String, km: Int) = ResumenViajeDto(
        id = id, choferId = "c1", chofer = "Juan Pérez", patente = "KFZT21", cliente = "Agrícola Sur",
        origen = "Santiago", destino = "Temuco", fechaSalida = salida, estado = "finalizado",
        validacion = "pendiente", precio = 800_000, kmRecorridos = km, gastoTotal = 200_000, margen = 600_000,
    )
}

/** Solo responde a la consulta de los reportes; el resto es el de [AdminFalso]. */
private class ReportesFalso(private val filas: List<ResumenViajeDto>) : AdminFalso() {
    val consultas = mutableListOf<Pair<Long, Long>>()
    var falla: Exception? = null

    override suspend fun finalizadosEntre(desde: Long, hasta: Long): List<ResumenViajeDto> {
        consultas += desde to hasta
        falla?.let { throw it }
        return filas
    }
}
