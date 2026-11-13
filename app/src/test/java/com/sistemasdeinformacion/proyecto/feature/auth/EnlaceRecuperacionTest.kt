package com.sistemasdeinformacion.proyecto.feature.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnlaceRecuperacionTest {

    @Test fun enlaceConToken_traeSesion() {
        assertTrue(enlaceTraeSesion("access_token=abc&expires_in=3600&refresh_token=r&token_type=bearer&type=recovery", null))
    }

    @Test fun enlaceVencido_noTraeSesion() {
        assertFalse(enlaceTraeSesion("error=access_denied&error_code=otp_expired&error_description=Email+link+is+invalid", null))
    }

    @Test fun enlaceVacio_noTraeSesion() {
        assertFalse(enlaceTraeSesion(null, null))
        assertFalse(enlaceTraeSesion("", ""))
    }

    @Test fun errorEnLaConsulta_noTraeSesion() {
        assertFalse(enlaceTraeSesion("access_token=abc", "error=server_error"))
    }
}
