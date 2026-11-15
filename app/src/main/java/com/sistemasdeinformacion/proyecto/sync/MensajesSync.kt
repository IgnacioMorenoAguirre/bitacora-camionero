package com.sistemasdeinformacion.proyecto.sync

import io.github.jan.supabase.exceptions.RestException

private const val SIN_PERMISO = "Sin permiso para guardar este dato."
private const val SESION_VENCIDA = "La sesión venció. Cierra sesión y vuelve a entrar."
private const val VIAJE_APROBADO = "El administrador ya aprobó este viaje. No se puede cambiar."
private const val DATO_RECHAZADO = "El servidor no aceptó un dato. Avisa al administrador."
private const val POR_DEFECTO = "No se pudo enviar. Se reintentará solo."

/**
 * Mensaje corto en español para el chofer a partir del error del servidor (que viene en inglés y con
 * detalles técnicos). [texto] es la primera línea del error y [estado] el código HTTP, si lo hay.
 */
internal fun mensajeSyncParaChofer(texto: String?, estado: Int?): String {
    val t = texto.orEmpty().lowercase()
    return when {
        "viaje_aprobado" in t -> VIAJE_APROBADO
        "jwt" in t || ("token" in t && "expired" in t) -> SESION_VENCIDA
        estado == 401 || estado == 403 || "row-level security" in t || "permission denied" in t -> SIN_PERMISO
        "violates" in t && "constraint" in t || estado == 400 || estado == 409 || estado == 422 -> DATO_RECHAZADO
        else -> POR_DEFECTO
    }
}

internal fun Exception.mensajeSyncParaChofer(): String {
    // Solo la primera línea: supabase-kt agrega la URL y los encabezados de la petición.
    val primera = message?.lineSequence()?.first()?.trim()
    val texto = if (this is RestException) listOfNotNull(error, primera).joinToString(" ") else primera
    return mensajeSyncParaChofer(texto, (this as? RestException)?.statusCode)
}
