package com.sistemasdeinformacion.proyecto.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidacionesTest {
    @Test fun parsearEntero_aceptaPuntoDeMiles() = assertEquals(18500, Validaciones.parsearEntero("18.500"))
    @Test fun parsearEntero_aceptaEspacios() = assertEquals(214, Validaciones.parsearEntero(" 214 "))
    @Test fun parsearEntero_rechazaTexto() = assertNull(Validaciones.parsearEntero("abc"))
    @Test fun parsearEntero_rechazaVacio() = assertNull(Validaciones.parsearEntero(""))
    @Test fun parsearDecimal_aceptaComa() = assertEquals(3.5, Validaciones.parsearDecimal("3,5")!!, 0.0001)
    @Test fun parsearDecimal_aceptaMilesYComa() = assertEquals(1234.5, Validaciones.parsearDecimal("1.234,5")!!, 0.0001)
    @Test fun parsearDecimal_aceptaEntero() = assertEquals(180.0, Validaciones.parsearDecimal("180")!!, 0.0001)
    @Test fun kmCoherentes_rechazaFinalMenor() = assertFalse(Validaciones.kmCoherentes(1000, 900))
    @Test fun kmCoherentes_aceptaIgual() = assertTrue(Validaciones.kmCoherentes(1000, 1000))
    @Test fun kmCoherentes_aceptaNulos() = assertTrue(Validaciones.kmCoherentes(1000, null))

    @Test fun email() {
        assertTrue(Validaciones.email("chofer@empresa.cl"))
        assertFalse(Validaciones.email("chofer@"))
        assertFalse(Validaciones.email("sin arroba.cl"))
    }

    @Test fun patente_formatosChilenos() {
        assertTrue(Validaciones.patente("KFZT21"))
        assertTrue(Validaciones.patente("kfzt-21"))
        assertTrue(Validaciones.patente("AB1234"))
        assertFalse(Validaciones.patente("K1"))
    }

    @Test fun rut_digitoVerificador() {
        assertTrue(Validaciones.rut("11.111.111-1"))
        assertTrue(Validaciones.rut("12345678-5"))
        assertTrue(Validaciones.rut("10.000.013-K"))
        assertFalse(Validaciones.rut("12345678-9"))
    }

    @Test fun contrasena_minimo8ConLetraYNumero() {
        assertTrue(Validaciones.contrasenaValida("camion2026"))
        assertFalse(Validaciones.contrasenaValida("corta1"))
        assertFalse(Validaciones.contrasenaValida("solamenteletras"))
    }
}
