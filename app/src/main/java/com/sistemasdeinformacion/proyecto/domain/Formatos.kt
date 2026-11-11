package com.sistemasdeinformacion.proyecto.domain

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** Formatos de Chile: punto de miles, montos en pesos sin decimales. */
object Formatos {

    private val localeCl = Locale.forLanguageTag("es-CL")
    private val miles = DecimalFormat("#,##0", DecimalFormatSymbols(localeCl).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    })
    private val decimal1 = DecimalFormat("#,##0.#", DecimalFormatSymbols(localeCl).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    })
    private val fmtHora = DateTimeFormatter.ofPattern("HH:mm", localeCl)
    private val fmtFecha = DateTimeFormatter.ofPattern("d MMM", localeCl)
    private val fmtFechaLarga = DateTimeFormatter.ofPattern("d 'de' MMMM, HH:mm", localeCl)

    fun numero(n: Long): String = miles.format(n)

    fun pesos(monto: Long): String = (if (monto < 0) "-" else "") + "$" + miles.format(abs(monto))

    fun km(km: Int): String = "${miles.format(km.toLong())} km"

    fun litros(l: Double): String = "${decimal1.format(l)} L"

    fun duracion(ms: Long): String {
        val totalMin = ms / 60_000
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h == 0L -> "$m min"
            m == 0L -> "$h h"
            else -> "$h h $m min"
        }
    }

    fun hora(epochMs: Long, zona: ZoneId = ZoneId.systemDefault()): String =
        fmtHora.format(Instant.ofEpochMilli(epochMs).atZone(zona))

    fun fechaCorta(epochMs: Long, zona: ZoneId = ZoneId.systemDefault()): String =
        fmtFecha.format(Instant.ofEpochMilli(epochMs).atZone(zona)).replace(".", "")

    fun fechaLarga(epochMs: Long, zona: ZoneId = ZoneId.systemDefault()): String =
        fmtFechaLarga.format(Instant.ofEpochMilli(epochMs).atZone(zona))
}
