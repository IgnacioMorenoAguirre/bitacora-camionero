package com.sistemasdeinformacion.proyecto.feature.entrega

import androidx.lifecycle.SavedStateHandle
import com.sistemasdeinformacion.proyecto.data.local.ArchivosAdjuntos
import com.sistemasdeinformacion.proyecto.data.local.EventoEntity
import com.sistemasdeinformacion.proyecto.data.local.ViajeEntity
import com.sistemasdeinformacion.proyecto.data.model.EstadoViaje
import com.sistemasdeinformacion.proyecto.data.model.TipoAdjunto
import com.sistemasdeinformacion.proyecto.data.model.TipoEvento
import com.sistemasdeinformacion.proyecto.data.repo.DetalleViaje
import com.sistemasdeinformacion.proyecto.data.repo.Finalizacion
import com.sistemasdeinformacion.proyecto.data.repo.KmIncoherenteException
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class EntregaViewModelTest {

    @get:Rule val carpeta = TemporaryFolder()
    private val despachador = UnconfinedTestDispatcher()
    private lateinit var archivos: ArchivosAdjuntos

    @Before fun antes() {
        Dispatchers.setMain(despachador)
        archivos = ArchivosAdjuntos(carpeta.root)
    }
    @After fun despues() { Dispatchers.resetMain() }

    @Test fun kmFinalMenor_muestraError() = runTest {
        val repo = ViajesFalso(detalle())
        val vm = vm(repo)

        vm.km = "900"
        vm.finalizar {}

        assertEquals("El km final no puede ser menor que el inicial (1.000 km).", vm.errorKm)
        assertTrue(repo.finalizaciones.isEmpty())
    }

    @Test fun kmFinalMenor_segunElRepositorio_muestraElMismoError() = runTest {
        val repo = ViajesFalso(detalle()).apply { falla = KmIncoherenteException(1000) }
        val vm = vm(repo)

        vm.km = "1.500"
        vm.finalizar {}

        assertEquals("El km final no puede ser menor que el inicial (1.000 km).", vm.errorKm)
        assertFalse(vm.guardando)
    }

    @Test fun finalizar_sinAdjunto_permitido() = runTest {
        val repo = ViajesFalso(detalle())
        val vm = vm(repo)
        var terminado = false

        vm.km = "1.500"
        vm.receptor = "Pedro Soto"
        vm.finalizar { terminado = true }

        assertEquals(listOf(Finalizacion("v1", 1500, "Pedro Soto", null)), repo.finalizaciones)
        assertTrue(terminado)
    }

    @Test fun finalizar_conFoto_enviaElArchivo() = runTest {
        val repo = ViajesFalso(detalle())
        val vm = vm(repo)

        val foto = vm.archivoParaFoto()
        foto.writeBytes(byteArrayOf(1, 2, 3))
        vm.fotoTomada(true)
        vm.km = "1500"
        vm.receptor = "Pedro Soto"
        vm.finalizar {}

        val adjunto = repo.finalizaciones.single().adjunto!!
        assertEquals(TipoAdjunto.FOTO, adjunto.tipo)
        assertEquals(foto, adjunto.archivo)
        assertEquals("Pedro Soto", adjunto.nombreReceptor)
        // Queda en la carpeta del viaje: filesDir/adjuntos/{viajeId}/
        assertEquals(java.io.File(carpeta.root, "adjuntos/v1"), foto.parentFile)
    }

    @Test fun fotoCancelada_borraElTemporal() = runTest {
        val vm = vm(ViajesFalso(detalle()))

        val foto = vm.archivoParaFoto()
        vm.fotoTomada(false)

        assertFalse(foto.exists())
        assertNull(vm.adjunto)
    }

    @Test fun usarFirma_reemplazaLaFoto() = runTest {
        val vm = vm(ViajesFalso(detalle()))
        val foto = vm.archivoParaFoto()
        foto.writeBytes(byteArrayOf(1))
        vm.fotoTomada(true)

        vm.usarFirma { it.write(byteArrayOf(9, 9)) }

        val firma = vm.adjunto!!
        assertEquals(TipoAdjunto.FIRMA, firma.tipo)
        assertTrue(firma.archivo.name.endsWith(".png"))
        assertEquals(2L, firma.archivo.length())
        assertFalse(foto.exists())
    }

    private fun vm(repo: ViajesFalso) = EntregaViewModel("v1", repo, archivos, SavedStateHandle(), despachador)

    private fun detalle(): DetalleViaje {
        val viaje = ViajeEntity(
            id = "v1", choferId = "c1", camionId = null, cliente = "Agrícola Sur", origen = "Santiago", destino = "Temuco",
            ruta = null, tipoCarga = "Fruta", pesoKg = 12_000.0, requiereRefrigeracion = false, precio = 850_000,
            fechaSalida = 0, fechaLlegada = null, kmInicial = 1000, kmFinal = null, estado = EstadoViaje.EN_CURSO,
            observaciones = null, modificadoLocal = 0, syncPendiente = false,
        )
        val inicio = EventoEntity(
            id = "e0", viajeId = "v1", tipo = TipoEvento.INICIO, fechaHora = 0, fechaFin = null, latitud = null,
            longitud = null, direccion = null, km = 1000, nota = null, modificadoLocal = 0, syncPendiente = false,
        )
        return DetalleViaje(viaje, null, listOf(inicio), emptyList(), emptyList(), emptyList(), null)
    }
}
