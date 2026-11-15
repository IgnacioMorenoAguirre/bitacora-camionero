package com.sistemasdeinformacion.proyecto.feature.viajes

import com.sistemasdeinformacion.proyecto.data.model.EstadoValidacion
import com.sistemasdeinformacion.proyecto.data.model.TipoGasto
import com.sistemasdeinformacion.proyecto.data.repo.ViajesFalso
import com.sistemasdeinformacion.proyecto.domain.CalculosViaje
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResumenViewModelTest {

    @Before fun antes() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun despues() { Dispatchers.resetMain() }

    @Test fun resumen_muestraValoresDeCalculosViaje() = runTest {
        val d = viajeFinalizado()
        val vm = ResumenViewModel("v1", ViajesFalso(d))

        val e = vm.estado.filterNotNull().first()

        assertEquals(CalculosViaje.resumen(d.viaje!!, d.gastos, 3.0), e.resumen)
        assertEquals(600, e.resumen.kmRecorridos)
        assertEquals(8 * HORA, e.resumen.duracionMs)
        assertEquals(680_000L, e.resumen.ganancia)
        assertEquals(mapOf(TipoGasto.COMBUSTIBLE to 210_000L, TipoGasto.PEAJE to 10_000L), e.resumen.gastoPorTipo)
        assertEquals(200.0, e.resumen.litrosEstimados!!, 0.01)
        assertEquals(180.0, e.resumen.litrosReales, 0.01)
    }

    @Test fun observado_muestraComentarioYPermiteCorregir() = runTest {
        val vm = ResumenViewModel("v1", ViajesFalso(viajeFinalizado(EstadoValidacion.OBSERVADO, "Falta el peaje de Angostura.")))

        val e = vm.estado.filterNotNull().first()

        assertTrue(e.puedeCorregir)
        assertEquals("Falta el peaje de Angostura.", e.comentario)
    }

    @Test fun aprobado_esSoloLectura() = runTest {
        val vm = ResumenViewModel("v1", ViajesFalso(viajeFinalizado(EstadoValidacion.APROBADO)))

        val e = vm.estado.filterNotNull().first()

        assertFalse(e.puedeCorregir)
    }
}
