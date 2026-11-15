package com.sistemasdeinformacion.proyecto.feature.viajes

import com.sistemasdeinformacion.proyecto.data.model.EstadoValidacion
import com.sistemasdeinformacion.proyecto.data.repo.DatosEdicionViaje
import com.sistemasdeinformacion.proyecto.data.repo.ViajeSoloLecturaException
import com.sistemasdeinformacion.proyecto.data.repo.ViajesFalso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class CorregirViajeViewModelTest {

    @Before fun antes() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun despues() { Dispatchers.resetMain() }

    @Test fun abre_conLosDatosDelViaje() = runTest {
        val vm = CorregirViajeViewModel("v1", ViajesFalso(viajeFinalizado(EstadoValidacion.OBSERVADO, "Revisa el precio.")))

        assertTrue(vm.listo)
        assertEquals("Agrícola Sur", vm.cliente)
        assertEquals("Fruta", vm.carga)
        assertEquals("12.000", vm.peso)
        assertEquals("900.000", vm.precio)
        assertEquals("Entrega por portón 2", vm.observaciones)
        assertEquals("Revisa el precio.", vm.comentarioAdmin)
    }

    @Test fun guardar_enviaLosDatosCorregidos() = runTest {
        val repo = ViajesFalso(viajeFinalizado(EstadoValidacion.OBSERVADO))
        val vm = CorregirViajeViewModel("v1", repo)
        var guardado = false

        vm.precio = "950.000"
        vm.destino = "Temuco Norte"
        vm.guardar { guardado = true }

        val esperado = DatosEdicionViaje(
            cliente = "Agrícola Sur", origen = "Santiago", destino = "Temuco Norte", tipoCarga = "Fruta",
            pesoKg = 12_000.0, precio = 950_000, observaciones = "Entrega por portón 2",
        )
        assertEquals(listOf("v1" to esperado), repo.ediciones)
        assertTrue(guardado)
    }

    @Test fun clienteVacio_muestraError() = runTest {
        val repo = ViajesFalso(viajeFinalizado(EstadoValidacion.OBSERVADO))
        val vm = CorregirViajeViewModel("v1", repo)

        vm.cliente = "  "
        vm.guardar {}

        assertEquals("Escribe el cliente.", vm.errores["cliente"])
        assertTrue(repo.ediciones.isEmpty())
    }

    @Test fun viajeAprobado_noSeGuarda() = runTest {
        val repo = ViajesFalso(viajeFinalizado(EstadoValidacion.APROBADO)).apply { falla = ViajeSoloLecturaException() }
        val vm = CorregirViajeViewModel("v1", repo)
        var guardado = false

        vm.guardar { guardado = true }

        assertFalse(guardado)
        assertEquals("Este viaje ya fue aprobado y no se puede modificar.", vm.errorGeneral)
    }
}
