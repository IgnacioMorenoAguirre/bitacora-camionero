package com.sistemasdeinformacion.proyecto.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatosTest {
    @Test fun pesos() = assertEquals("$412.300", Formatos.pesos(412300))
    @Test fun pesosCero() = assertEquals("$0", Formatos.pesos(0))
    @Test fun pesosNegativo() = assertEquals("-$4.100", Formatos.pesos(-4100))
    @Test fun km() = assertEquals("3.240 km", Formatos.km(3240))
    @Test fun duracion() = assertEquals("5 h 20 min", Formatos.duracion(5 * 3_600_000L + 20 * 60_000L))
    @Test fun duracionMenorAUnaHora() = assertEquals("45 min", Formatos.duracion(45 * 60_000L))
    @Test fun duracionHorasExactas() = assertEquals("2 h", Formatos.duracion(2 * 3_600_000L))
}
