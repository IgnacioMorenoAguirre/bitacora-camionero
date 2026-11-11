package com.sistemasdeinformacion.proyecto.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sistemasdeinformacion.proyecto.data.model.EstadoViaje
import com.sistemasdeinformacion.proyecto.data.model.TipoEvento
import com.sistemasdeinformacion.proyecto.data.model.TipoGasto
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
class DaosTest {
    private lateinit var db: AppDatabase

    @Before fun abrir() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
    }

    @After fun cerrar() = db.close()

    private fun viaje(id: String = "v1", estado: EstadoViaje = EstadoViaje.EN_CURSO, mod: Long = 100, pend: Boolean = true) = ViajeEntity(
        id = id, choferId = "c1", camionId = null, cliente = "Cliente", origen = "Santiago", destino = "Concepción",
        ruta = null, tipoCarga = "Fruta", pesoKg = 1000.0, requiereRefrigeracion = false, precio = 100,
        fechaSalida = 1, fechaLlegada = null, kmInicial = 0, kmFinal = null, estado = estado,
        observaciones = null, modificadoLocal = mod, syncPendiente = pend,
    )

    private fun evento(id: String = "e1") = EventoEntity(id, "v1", TipoEvento.PARADA, 2, null, null, null, null, 10, null, false, 1, true)
    private fun gasto(id: String) = GastoEntity(id, "v1", TipoGasto.PEAJE, 4100, null, 3, null, false, 1, true)

    @Test fun upsert_noBorraHijos() = runTest {
        db.viajeDao().guardar(viaje())
        db.registroDao().guardar(evento())
        db.viajeDao().guardar(viaje().copy(cliente = "Otro"))
        assertEquals(1, db.registroDao().eventos("v1").size)
    }

    @Test fun marcarEnviado_soloSiNoCambio() = runTest {
        db.viajeDao().guardar(viaje(mod = 100))
        db.syncDao().marcarViajeEnviado("v1", 99)
        assertTrue(db.viajeDao().obtener("v1")!!.syncPendiente)
        db.syncDao().marcarViajeEnviado("v1", 100)
        assertFalse(db.viajeDao().obtener("v1")!!.syncPendiente)
    }

    @Test fun contarPendientes_sumaTablas() = runTest {
        db.viajeDao().guardar(viaje())
        db.registroDao().guardar(gasto("g1"))
        db.registroDao().guardar(gasto("g2"))
        assertEquals(3, db.syncDao().contarPendientes().first())
    }

    @Test fun observarEnCurso_devuelveSoloEnCurso() = runTest {
        db.viajeDao().guardar(viaje("v1", EstadoViaje.FINALIZADO))
        assertNull(db.viajeDao().observarEnCurso("c1").first())
        db.viajeDao().guardar(viaje("v2", EstadoViaje.EN_CURSO))
        assertEquals("v2", db.viajeDao().observarEnCurso("c1").first()?.id)
    }
}
