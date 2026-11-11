package com.sistemasdeinformacion.proyecto.data.repo

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sistemasdeinformacion.proyecto.data.local.AppDatabase
import com.sistemasdeinformacion.proyecto.data.model.EstadoViaje
import com.sistemasdeinformacion.proyecto.data.model.TipoEvento
import com.sistemasdeinformacion.proyecto.data.model.TipoGasto
import com.sistemasdeinformacion.proyecto.sync.ProgramadorSyncVacio
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ViajesRepositorioTest {
    private lateinit var db: AppDatabase
    private var ahora = 1_000_000L
    private var ubicacion: Ubicacion? = Ubicacion(-33.45, -70.66, "Santiago")
    private lateinit var repo: ViajesRepositorioRoom

    private val datos = DatosNuevoViaje(
        camionId = null, cliente = "Agrícola Sur", origen = "Santiago", destino = "Concepción", ruta = "5 SUR",
        tipoCarga = "Fruta", pesoKg = 18500.0, refrigeracion = true, precio = 900_000, kmInicial = 1000, observaciones = null,
    )
    private val revision = DatosRevision(true, true, true, true, false, "Falta revisión técnica al día")

    @Before fun abrir() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        repo = ViajesRepositorioRoom(
            db = db,
            choferId = { "c1" },
            reloj = { ahora },
            ubicaciones = object : UbicacionRepositorio { override suspend fun actual(timeoutMs: Long) = ubicacion },
            programador = ProgramadorSyncVacio,
        )
    }

    @After fun cerrar() = db.close()

    @Test fun crearViaje_creaRevisionYEventoInicio() = runTest {
        val id = repo.crearViaje(datos, revision).getOrThrow()
        val d = repo.detalle(id).first()
        assertEquals(EstadoViaje.EN_CURSO, d.viaje!!.estado)
        assertNotNull(d.revision)
        assertEquals(TipoEvento.INICIO, d.eventos.single().tipo)
        assertEquals(1000, d.eventos.single().km)
        assertTrue(d.viaje!!.syncPendiente)
    }

    @Test fun crearViaje_conViajeEnCurso_noCreaOtro() = runTest {
        val primero = repo.crearViaje(datos, revision).getOrThrow()
        val r = repo.crearViaje(datos.copy(cliente = "Otro"), revision)
        val error = r.exceptionOrNull() as ViajeEnCursoException
        assertEquals(primero, error.viajeId)
        assertEquals(1, repo.viajes().first().size)
    }

    @Test fun registrarParada_sinUbicacion_guardaSinCoordenadas() = runTest {
        val id = repo.crearViaje(datos, revision).getOrThrow()
        ubicacion = null
        ahora += 60_000
        repo.registrarEvento(id, TipoEvento.PARADA, km = 1100, nota = null).getOrThrow()
        val parada = repo.detalle(id).first().eventos.last()
        assertEquals(TipoEvento.PARADA, parada.tipo)
        assertNull(parada.latitud)
        assertNull(parada.longitud)
    }

    @Test fun descanso_abiertoYCerrado() = runTest {
        val id = repo.crearViaje(datos, revision).getOrThrow()
        ahora += 3_600_000
        val d = repo.registrarEvento(id, TipoEvento.DESCANSO, null, null).getOrThrow()
        ahora += 40 * 60_000
        repo.cerrarDescanso(d).getOrThrow()
        val ev = repo.detalle(id).first().eventos.first { it.id == d }
        assertEquals(ahora, ev.fechaFin)
    }

    @Test fun finalizar_kmMenor_falla() = runTest {
        val id = repo.crearViaje(datos, revision).getOrThrow()
        assertTrue(repo.finalizar(id, 900, null).isFailure)
        repo.finalizar(id, 1500, "Pedro Soto").getOrThrow()
        val d = repo.detalle(id).first()
        assertEquals(EstadoViaje.FINALIZADO, d.viaje!!.estado)
        assertEquals(listOf(TipoEvento.INICIO, TipoEvento.ENTREGA, TipoEvento.FIN), d.eventos.map { it.tipo })
    }

    @Test fun registrarGasto_combustible() = runTest {
        val id = repo.crearViaje(datos, revision).getOrThrow()
        repo.registrarGasto(id, TipoGasto.COMBUSTIBLE, 210_000, 180.0, null).getOrThrow()
        assertEquals(180.0, repo.detalle(id).first().gastos.single().litros!!, 0.01)
    }
}
