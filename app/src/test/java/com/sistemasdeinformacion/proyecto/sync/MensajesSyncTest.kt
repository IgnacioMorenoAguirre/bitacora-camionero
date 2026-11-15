package com.sistemasdeinformacion.proyecto.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MensajesSyncTest {

    private val porDefecto = "No se pudo enviar. Se reintentará solo."
    private val sinPermiso = "Sin permiso para guardar este dato."

    @Test fun estados401y403_sinPermiso() {
        assertEquals(sinPermiso, mensajeSyncParaChofer("Forbidden", 403))
        assertEquals(sinPermiso, mensajeSyncParaChofer("Unauthorized", 401))
    }

    @Test fun rls_sinPermiso() {
        assertEquals(sinPermiso, mensajeSyncParaChofer("new row violates row-level security policy for table \"gastos\"", null))
        assertEquals(sinPermiso, mensajeSyncParaChofer("permission denied", null))
    }

    @Test fun sesionVencida() {
        assertEquals("La sesión venció. Cierra sesión y vuelve a entrar.", mensajeSyncParaChofer("JWT expired", 401))
    }

    @Test fun viajeAprobado() {
        assertEquals(
            "El administrador ya aprobó este viaje. No se puede cambiar.",
            mensajeSyncParaChofer("VIAJE_APROBADO", 400),
        )
    }

    @Test fun datoRechazadoPorElServidor() {
        assertEquals(
            "El servidor no aceptó un dato. Avisa al administrador.",
            mensajeSyncParaChofer("new row for relation \"gastos\" violates check constraint \"gastos_monto_check\"", 400),
        )
    }

    @Test fun otrosErrores_mensajeGenericoSinTextoCrudo() {
        assertEquals(porDefecto, mensajeSyncParaChofer("Internal Server Error", 500))
        assertEquals(porDefecto, mensajeSyncParaChofer(null, null))
        assertEquals(porDefecto, mensajeSyncParaChofer("IllegalStateException", null))
        assertFalse(mensajeSyncParaChofer("some english gibberish", 502).contains("gibberish"))
    }
}
