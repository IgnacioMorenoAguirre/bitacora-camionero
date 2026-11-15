package com.sistemasdeinformacion.proyecto.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RegistroTest {

    @Test fun resumenSeguro_noIncluyeElMensajeNiLosEncabezados() {
        val e = IllegalStateException("Fallo\nURL: https://x.supabase.co/rest/v1/viajes\nHeaders: [Authorization=Bearer eyJabc]")

        val resumen = e.resumenSeguro()

        assertEquals("IllegalStateException", resumen)
        assertFalse(resumen.contains("Bearer"))
    }

    @Test fun resumenSeguro_incluyeLaCausaSinSuMensaje() {
        val e = RuntimeException("envoltorio con token", java.io.IOException("Bearer eyJabc"))

        assertEquals("RuntimeException (causa: IOException)", e.resumenSeguro())
    }
}
