package com.sistemasdeinformacion.proyecto.core

import io.github.jan.supabase.exceptions.RestException

/**
 * Resumen de un error para logcat. Nunca va el mensaje completo ni la excepción: en supabase-kt el
 * mensaje trae la URL y los encabezados de la petición, con el token de acceso incluido. De un
 * error del servidor queda el código HTTP y su texto corto (p. ej. "new row violates ...").
 */
fun Throwable.resumenSeguro(): String {
    val nombre = this::class.simpleName ?: "Error"
    val base = if (this is RestException) "$nombre $statusCode: $error" else nombre
    val causa = cause?.takeIf { it !== this }?.let { it::class.simpleName }
    return if (causa != null) "$base (causa: $causa)" else base
}
