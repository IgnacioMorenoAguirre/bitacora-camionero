package com.sistemasdeinformacion.proyecto.feature.admin

import com.sistemasdeinformacion.proyecto.data.remote.ViajeDto
import com.sistemasdeinformacion.proyecto.data.repo.DetalleViajeRemoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminCamionesViewModelTest {

    private val repo = AdminFalso(
        DetalleViajeRemoto.armar(
            viaje = ViajeDto(
                id = "v1", choferId = "c1", cliente = "-", origen = "-", destino = "-", tipoCarga = "-", pesoKg = 1.0,
                requiereRefrigeracion = false, precio = 0, fechaSalida = "2026-11-10T10:00:00Z", estado = "en_curso",
            ),
            chofer = null, camion = null, revision = null, eventos = emptyList(), gastos = emptyList(),
            novedades = emptyList(), adjuntos = emptyList(), ahora = 0,
        ),
    )

    @Before fun antes() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun despues() { Dispatchers.resetMain() }

    @Test fun guardar_patenteInvalida_noLlamaAlServidor() = runTest {
        val vm = AdminCamionesViewModel(repo, MutableStateFlow(true))
        vm.nuevo()
        vm.cambiarPatente("ABC12")
        vm.cambiarMarca("Volvo")
        vm.cambiarModelo("FH 460")
        vm.cambiarRendimiento("3,2")

        vm.guardar()

        assertEquals("Escribe una patente válida, como BBBB12 o AB1234.", vm.estado.value.formulario?.errores?.get("patente"))
        assertTrue(repo.guardados.isEmpty())
    }

    @Test fun guardar_datosValidos_normalizaPatenteYCierra() = runTest {
        val vm = AdminCamionesViewModel(repo, MutableStateFlow(true))
        vm.nuevo()
        vm.cambiarPatente("hj-kl 21")
        vm.cambiarMarca("Volvo")
        vm.cambiarModelo("FH 460")
        vm.cambiarAnio("2019")
        vm.cambiarRendimiento("3,2")

        vm.guardar()

        val c = repo.guardados.single()
        assertEquals("HJKL21", c.patente)
        assertEquals(3.2, c.rendimientoKmL, 0.0001)
        assertEquals(2019, c.anio)
        assertNull(vm.estado.value.formulario)
    }
}
