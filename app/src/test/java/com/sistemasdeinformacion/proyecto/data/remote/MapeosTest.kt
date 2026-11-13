package com.sistemasdeinformacion.proyecto.data.remote

import com.sistemasdeinformacion.proyecto.data.local.AdjuntoEntity
import com.sistemasdeinformacion.proyecto.data.local.EventoEntity
import com.sistemasdeinformacion.proyecto.data.local.GastoEntity
import com.sistemasdeinformacion.proyecto.data.local.ViajeEntity
import com.sistemasdeinformacion.proyecto.data.model.EstadoValidacion
import com.sistemasdeinformacion.proyecto.data.model.EstadoViaje
import com.sistemasdeinformacion.proyecto.data.model.TipoAdjunto
import com.sistemasdeinformacion.proyecto.data.model.TipoEvento
import com.sistemasdeinformacion.proyecto.data.model.TipoGasto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapeosTest {

    private fun viajeEjemplo() = ViajeEntity(
        id = "v1", choferId = "c1", camionId = "k1", cliente = "Agrícola Sur", origen = "Santiago",
        destino = "Concepción", ruta = "5 SUR", tipoCarga = "Fruta", pesoKg = 18500.0, requiereRefrigeracion = true,
        precio = 900_000, fechaSalida = 1_794_312_000_000, fechaLlegada = 1_794_340_800_000, kmInicial = 1000,
        kmFinal = 1500, estado = EstadoViaje.FINALIZADO, observaciones = "ok", modificadoLocal = 55, syncPendiente = true,
    )

    @Test fun iso_conOffsetYMicrosegundos() =
        assertEquals(1_794_312_000_123L, "2026-11-10T12:00:00.123456+00:00".desdeIso())

    @Test fun iso_conZ() = assertEquals(1_794_312_000_000L, "2026-11-10T12:00:00Z".desdeIso())

    @Test fun iso_idaYVuelta() = assertEquals(1_794_312_000_123L, 1_794_312_000_123L.aIso().desdeIso())

    @Test fun viaje_idaYVuelta() {
        val e = viajeEjemplo()
        assertEquals(e.copy(syncPendiente = false, modificadoLocal = 0), e.aSubida().comoDtoBajada().aEntidad())
    }

    @Test fun viajeBajado_traeValidacion() {
        val dto = viajeEjemplo().aSubida().comoDtoBajada().copy(validacion = "observado", comentarioValidacion = "Falta guía")
        val e = dto.aEntidad()
        assertEquals(EstadoValidacion.OBSERVADO, e.validacion)
        assertEquals("Falta guía", e.comentarioValidacion)
    }

    @Test fun subida_noIncluyeValidacion() {
        val json = Json.encodeToString(ViajeSubidaDto.serializer(), viajeEjemplo().aSubida())
        assertFalse("validacion" in json)
        assertTrue("\"chofer_id\":\"c1\"" in json)
        assertTrue("\"estado\":\"finalizado\"" in json)
    }

    @Test fun evento_idaYVuelta() {
        val e = EventoEntity("e1", "v1", TipoEvento.DESCANSO, 1_794_312_000_000, 1_794_314_400_000, -33.4, -70.6, null, 1214, "nota", false, 9, true)
        assertEquals(e.copy(modificadoLocal = 0, syncPendiente = false), e.aDto().aEntidad())
    }

    @Test fun gasto_idaYVuelta() {
        val g = GastoEntity("g1", "v1", TipoGasto.COMBUSTIBLE, 210_000, 180.0, 1_794_312_000_000, null, false, 9, true)
        assertEquals(g.copy(modificadoLocal = 0, syncPendiente = false), g.aDto().aEntidad())
    }

    @Test fun adjunto_bajadoConservaRutaLocalSiSeEntrega() {
        val a = AdjuntoEntity("a1", "v1", TipoAdjunto.FOTO, "/local/a1.jpg", "v1/a1.jpg", "Pedro", 1_794_312_000_000, false, 9, true)
        val bajado = a.aDto().aEntidad(rutaLocal = a.rutaLocal)
        assertEquals(a.copy(modificadoLocal = 0, syncPendiente = false), bajado)
    }
}
