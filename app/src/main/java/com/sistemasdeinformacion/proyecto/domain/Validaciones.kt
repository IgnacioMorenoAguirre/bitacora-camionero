package com.sistemasdeinformacion.proyecto.domain

/** Validaciones y lectura de números escritos "a la chilena" (punto de miles, coma decimal). */
object Validaciones {

    private val regexEmail = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val regexPatente = Regex("^[A-Z]{4}\\d{2}$|^[A-Z]{2}\\d{4}$")

    fun email(s: String): Boolean = regexEmail.matches(s.trim())

    /** Patentes chilenas: formato nuevo BBBB12 o antiguo AB1234 (acepta guiones, espacios y minúsculas). */
    fun patente(s: String): Boolean = regexPatente.matches(normalizarPatente(s))

    fun normalizarPatente(s: String): String = s.uppercase().filter { it.isLetterOrDigit() }

    /** RUT chileno con dígito verificador (módulo 11). Acepta puntos y guion. */
    fun rut(s: String): Boolean {
        val limpio = s.uppercase().filter { it.isDigit() || it == 'K' }
        if (limpio.length < 2) return false
        val cuerpo = limpio.dropLast(1)
        val dv = limpio.last()
        if (!cuerpo.all { it.isDigit() }) return false
        var suma = 0
        var factor = 2
        for (c in cuerpo.reversed()) {
            suma += (c - '0') * factor
            factor = if (factor == 7) 2 else factor + 1
        }
        val esperado = when (val r = 11 - suma % 11) {
            11 -> '0'
            10 -> 'K'
            else -> '0' + r
        }
        return dv == esperado
    }

    /** "18.500" → 18500; " 214 " → 214; texto o vacío → null. */
    fun parsearEntero(s: String): Int? {
        val t = s.trim().replace(".", "").replace(" ", "")
        if (t.isEmpty() || !t.all { it.isDigit() }) return null
        return t.toIntOrNull()
    }

    private val regexDecimalConComa = Regex("^(\\d+|\\d{1,3}(\\.\\d{3})+)(,\\d+)?$")
    private val regexMiles = Regex("^\\d{1,3}(\\.\\d{3})+$")
    private val regexDecimalConPunto = Regex("^\\d+\\.\\d{1,2}$")

    /**
     * "3,5" → 3.5; "1.234,5" → 1234.5; "180" → 180.0; "1.250" → 1250.0.
     * Muchos teclados numéricos solo tienen punto, así que "45.5" (uno o dos decimales) es 45.5.
     * Lo ambiguo ("1.234.5", "12.3456") se rechaza en vez de adivinar.
     */
    fun parsearDecimal(s: String): Double? {
        val t = s.trim().replace(" ", "")
        val normal = when {
            t.contains(',') -> t.takeIf { regexDecimalConComa.matches(it) }?.replace(".", "")?.replace(',', '.')
            regexDecimalConPunto.matches(t) -> t
            regexMiles.matches(t) -> t.replace(".", "")
            t.isNotEmpty() && t.all { it.isDigit() } -> t
            else -> null
        }
        return normal?.toDoubleOrNull()?.takeIf { it >= 0 }
    }

    fun kmCoherentes(inicial: Int?, final: Int?): Boolean =
        inicial == null || final == null || final >= inicial

    /** Mínimo 8 caracteres, con al menos una letra y un número. */
    fun contrasenaValida(s: String): Boolean =
        s.length >= 8 && s.any { it.isLetter() } && s.any { it.isDigit() }
}
