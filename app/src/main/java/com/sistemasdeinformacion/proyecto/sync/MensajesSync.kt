package com.sistemasdeinformacion.proyecto.sync

import io.github.jan.supabase.exceptions.RestException

private const val SIN_PERMISO = "Sin permiso para guardar este dato."
private const val SESION_VENCIDA = "La sesión venció. Cierra sesión y vuelve a entrar."
private const val VIAJE_APROBADO = "El administrador ya aprobó este viaje. No se puede cambiar."
private const val DATO_RECHAZADO = "El servidor no aceptó un dato. Avisa al administrador."
private const val POR_DEFECTO = "No se pudo enviar. Se reintentará solo."

/**
 * Qué tipo de rechazo mandó el servidor. Solo [PASAJERO] (servidor caído, 5xx, demasiadas
 * peticiones o un error sin código) merece reintentar pronto; los demás no cambian por insistir.
 */
internal enum class TipoErrorSync { VIAJE_APROBADO, SESION, PERMISO, DATO, PASAJERO }

/** [texto] es la primera línea del error y [estado] el código HTTP, si lo hay. */
internal fun tipoErrorSync(texto: String?, estado: Int?): TipoErrorSync {
    val t = texto.orEmpty().lowercase()
    return when {
        "viaje_aprobado" in t -> TipoErrorSync.VIAJE_APROBADO
        "jwt" in t || ("token" in t && "expired" in t) -> TipoErrorSync.SESION
        estado == 401 || estado == 403 || "row-level security" in t || "permission denied" in t -> TipoErrorSync.PERMISO
        estado == 408 || estado == 429 -> TipoErrorSync.PASAJERO
        "violates" in t && "constraint" in t || (estado != null && estado in 400..499) -> TipoErrorSync.DATO
        else -> TipoErrorSync.PASAJERO
    }
}

internal fun errorSyncPasajero(texto: String?, estado: Int?): Boolean = tipoErrorSync(texto, estado) == TipoErrorSync.PASAJERO

/**
 * Mensaje corto en español para el chofer a partir del error del servidor (que viene en inglés y con
 * detalles técnicos).
 */
internal fun mensajeSyncParaChofer(texto: String?, estado: Int?): String = when (tipoErrorSync(texto, estado)) {
    TipoErrorSync.VIAJE_APROBADO -> VIAJE_APROBADO
    TipoErrorSync.SESION -> SESION_VENCIDA
    TipoErrorSync.PERMISO -> SIN_PERMISO
    TipoErrorSync.DATO -> DATO_RECHAZADO
    TipoErrorSync.PASAJERO -> POR_DEFECTO
}

/** Solo la primera línea del error: supabase-kt agrega la URL y los encabezados de la petición. */
private fun Exception.textoYEstado(): Pair<String?, Int?> {
    val primera = message?.lineSequence()?.first()?.trim()
    val texto = if (this is RestException) listOfNotNull(error, primera).joinToString(" ") else primera
    return texto to (this as? RestException)?.statusCode
}

internal fun Exception.tipoErrorSync(): TipoErrorSync = textoYEstado().let { (t, e) -> tipoErrorSync(t, e) }

internal fun Exception.mensajeSyncParaChofer(): String = textoYEstado().let { (t, e) -> mensajeSyncParaChofer(t, e) }
