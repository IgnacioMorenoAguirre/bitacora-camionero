package com.sistemasdeinformacion.proyecto.feature.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Flujo PKCE: el enlace trae ?code=... y nunca tokens. */
class EnlaceRecuperacionTest {

    @Test fun enlaceConCodigo_traeSesion() {
        assertTrue(enlaceTraeSesion(null, "code=8f2c1e"))
    }

    @Test fun enlaceVencido_noTraeSesion() {
        assertFalse(enlaceTraeSesion(null, "error=access_denied&error_code=otp_expired&error_description=Email+link+is+invalid"))
        assertFalse(enlaceTraeSesion("error=access_denied&error_code=otp_expired", null))
    }

    @Test fun enlaceVacio_noTraeSesion() {
        assertFalse(enlaceTraeSesion(null, null))
        assertFalse(enlaceTraeSesion("", ""))
    }

    @Test fun tokensEnElFragmento_noSeAceptan() {
        // Flujo implícito: el token viajaría en el enlace; la app solo acepta el código PKCE.
        assertFalse(enlaceTraeSesion("access_token=abc&refresh_token=r&type=recovery", null))
    }

    @Test fun codigoConError_noTraeSesion() {
        assertFalse(enlaceTraeSesion("error=server_error", "code=8f2c1e"))
    }
}
