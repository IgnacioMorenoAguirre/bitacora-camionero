package com.sistemasdeinformacion.proyecto.sync

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sistemasdeinformacion.proyecto.data.local.AdjuntoEntity
import com.sistemasdeinformacion.proyecto.data.local.AppDatabase
import com.sistemasdeinformacion.proyecto.data.local.GastoEntity
import com.sistemasdeinformacion.proyecto.data.local.ViajeEntity
import com.sistemasdeinformacion.proyecto.data.model.EstadoViaje
import com.sistemasdeinformacion.proyecto.data.model.TipoAdjunto
import com.sistemasdeinformacion.proyecto.data.model.TipoGasto
import com.sistemasdeinformacion.proyecto.data.remote.aDto
import com.sistemasdeinformacion.proyecto.data.remote.aSubida
import com.sistemasdeinformacion.proyecto.data.remote.comoDtoBajada
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrigenLocalRoomTest {
    private lateinit var db: AppDatabase
    private lateinit var origen: OrigenLocalRoom

    @Before fun abrir() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        origen = OrigenLocalRoom(db.syncDao())
    }

    @After fun cerrar() = db.close()

    private fun viaje(id: String, cliente: String = "Local", pend: Boolean = true) = ViajeEntity(
        id = id, choferId = "c1", camionId = null, cliente = cliente, origen = "Santiago", destino = "Concepción",
        ruta = null, tipoCarga = "Fruta", pesoKg = 1000.0, requiereRefrigeracion = false, precio = 100,
        fechaSalida = 1_000, fechaLlegada = null, kmInicial = 0, kmFinal = null, estado = EstadoViaje.EN_CURSO,
        observaciones = null, modificadoLocal = 100, syncPendiente = pend,
    )

    private fun gasto(id: String, viajeId: String, mod: Long = 1) =
        GastoEntity(id, viajeId, TipoGasto.PEAJE, 4100, null, 3_000, null, false, mod, true)

    private fun adjunto(id: String, viajeId: String) =
        AdjuntoEntity(id, viajeId, TipoAdjunto.FOTO, "/files/$id.jpg", null, null, 5_000, false, 1, false)

    @Test fun aplicarRemotos_noPisaPendientes_conservaRutaLocal_yOmiteHuerfanos() = runTest {
        db.viajeDao().guardar(viaje("v1", pend = true))
        db.viajeDao().guardar(viaje("v2", pend = false))
        db.registroDao().guardar(adjunto("a1", "v2"))

        origen.aplicarRemotos(
            CambiosRemotos(
                viajes = listOf(
                    viaje("v1", cliente = "Servidor").aSubida().comoDtoBajada(),
                    viaje("v2", cliente = "Servidor").aSubida().comoDtoBajada(),
                ),
                gastos = listOf(gasto("g9", "v404").aDto()),
                adjuntos = listOf(adjunto("a1", "v2").copy(rutaRemota = "v2/a1.jpg").aDto()),
            )
        )

        assertEquals("Local", db.viajeDao().obtener("v1")!!.cliente)
        assertTrue(db.viajeDao().obtener("v1")!!.syncPendiente)
        assertEquals("Servidor", db.viajeDao().obtener("v2")!!.cliente)
        val a1 = db.registroDao().observarAdjuntos("v2").first().single()
        assertEquals("/files/a1.jpg", a1.rutaLocal)
        assertEquals("v2/a1.jpg", a1.rutaRemota)
        assertNull(db.syncDao().gastoPendiente("g9"))
    }

    @Test fun descartarViaje_borraElViajeConSusRegistros() = runTest {
        db.viajeDao().guardar(viaje("v1"))
        db.registroDao().guardar(gasto("g1", "v1"))
        db.registroDao().guardar(adjunto("a1", "v1"))

        origen.descartarViaje("v1")

        assertNull(db.viajeDao().obtener("v1"))
        assertEquals(0, db.syncDao().contarPendientes().first())
        assertTrue(db.registroDao().observarAdjuntos("v1").first().isEmpty())
    }

    @Test fun guardarRutaRemota_noCambiaModificadoNiPendiente() = runTest {
        db.viajeDao().guardar(viaje("v1", pend = false))
        val leido = adjunto("a1", "v1").copy(syncPendiente = true)
        db.registroDao().guardar(leido)

        origen.guardarRutaRemota("a1", "v1/a1.jpg")
        val a1 = origen.pendientes().adjuntos.single()
        assertEquals("v1/a1.jpg", a1.rutaRemota)
        assertEquals(leido.modificadoLocal, a1.modificadoLocal)

        origen.marcarEnviado(leido)
        assertFalse(db.syncDao().adjuntoPendiente("a1")!!)
    }

    @Test fun marcarEnviado_soloSiNoCambioDuranteElEnvio() = runTest {
        db.viajeDao().guardar(viaje("v1", pend = false))
        val leido = gasto("g1", "v1", mod = 1)
        db.registroDao().guardar(leido)
        db.registroDao().guardar(leido.copy(monto = 9_000, modificadoLocal = 2))

        origen.marcarEnviado(leido)
        assertTrue(origen.pendientes().gastos.single().syncPendiente)

        origen.marcarEnviado(leido.copy(modificadoLocal = 2))
        assertFalse(db.syncDao().gastoPendiente("g1")!!)
    }
}
