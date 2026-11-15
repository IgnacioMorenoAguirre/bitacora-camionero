package com.sistemasdeinformacion.proyecto.feature.inicio

import com.sistemasdeinformacion.proyecto.data.local.GastoEntity
import com.sistemasdeinformacion.proyecto.data.model.TipoGasto
import com.sistemasdeinformacion.proyecto.data.repo.ViajesFalso
import com.sistemasdeinformacion.proyecto.feature.viajes.HORA
import com.sistemasdeinformacion.proyecto.feature.viajes.viajeFinalizado
import com.sistemasdeinformacion.proyecto.sync.ProgramadorSyncVacio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class InicioViewModelTest {

    private val zona = ZoneId.of("America/Santiago")
    private fun instante(dia: Int, mes: Int, hora: Int) = ZonedDateTime.of(2026, mes, dia, hora, 0, 0, 0, zona).toInstant().toEpochMilli()

    @After fun despues() { Dispatchers.resetMain() }

    /** Viaje que salió el 31 de octubre a las 22:00 y cargó combustible pasada la medianoche. */
    private fun viajeDeFinDeMes(): ViajesFalso {
        val base = viajeFinalizado()
        val salida = instante(31, 10, 22)
        val viaje = base.viaje!!.copy(fechaSalida = salida, fechaLlegada = salida + 8 * HORA)
        val gasto = GastoEntity("g1", "v1", TipoGasto.COMBUSTIBLE, 210_000, 180.0, salida + 3 * HORA, null, modificadoLocal = 0, syncPendiente = false)
        return ViajesFalso(base.copy(viaje = viaje, gastos = listOf(gasto)))
    }

    @Test fun gastosDelMes_cuentanSoloLosDeViajesDelMes() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val ahora = instante(1, 11, 10)
        val vm = InicioViewModel("Juan", viajeDeFinDeMes(), ProgramadorSyncVacio, reloj = { ahora }, zona = zona)

        val e = vm.estado.first { !it.cargando }

        // El viaje es de octubre: sus km no cuentan en noviembre, y su combustible tampoco.
        assertEquals(0, e.viajesMes)
        assertEquals(0, e.kmMes)
        assertEquals(0L, e.gastosMes)
    }

    @Test fun cambioDeMes_conLaAppAbierta_reiniciaLosTotales() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        var ahora = instante(31, 10, 23)
        val vm = InicioViewModel("Juan", viajeDeFinDeMes(), ProgramadorSyncVacio, reloj = { ahora }, zona = zona)
        backgroundScope.launch { vm.estado.collect {} }
        runCurrent()

        assertEquals(1, vm.estado.value.viajesMes)
        assertEquals(210_000L, vm.estado.value.gastosMes)

        ahora = instante(1, 11, 0) + 60_000
        advanceTimeBy(31_000)

        assertEquals(0, vm.estado.value.viajesMes)
        assertEquals(0L, vm.estado.value.gastosMes)
    }
}
