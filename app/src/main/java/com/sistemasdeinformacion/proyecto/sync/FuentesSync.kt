package com.sistemasdeinformacion.proyecto.sync

import com.sistemasdeinformacion.proyecto.data.local.AdjuntoEntity
import com.sistemasdeinformacion.proyecto.data.local.EventoEntity
import com.sistemasdeinformacion.proyecto.data.local.GastoEntity
import com.sistemasdeinformacion.proyecto.data.local.NovedadEntity
import com.sistemasdeinformacion.proyecto.data.local.RevisionEntity
import com.sistemasdeinformacion.proyecto.data.local.Sincronizable
import com.sistemasdeinformacion.proyecto.data.local.ViajeEntity
import com.sistemasdeinformacion.proyecto.data.model.TipoAdjunto
import com.sistemasdeinformacion.proyecto.data.remote.AdjuntoDto
import com.sistemasdeinformacion.proyecto.data.remote.EventoDto
import com.sistemasdeinformacion.proyecto.data.remote.GastoDto
import com.sistemasdeinformacion.proyecto.data.remote.NovedadDto
import com.sistemasdeinformacion.proyecto.data.remote.RevisionDto
import com.sistemasdeinformacion.proyecto.data.remote.ViajeDto
import java.time.OffsetDateTime

/*
 * Las dos puntas de la sincronización. El Sincronizador solo conoce estas interfaces, así su
 * algoritmo se prueba en la JVM con versiones en memoria (sin Room ni Supabase).
 */

/** Nombres de las tablas en PostgreSQL, en el orden en que se suben (por las llaves foráneas). */
object Tablas {
    const val VIAJES = "viajes"
    const val REVISIONES = "revisiones"
    const val EVENTOS = "eventos"
    const val GASTOS = "gastos"
    const val NOVEDADES = "novedades"
    const val ADJUNTOS = "adjuntos"
}

/** Todo lo que el teléfono tiene sin enviar. */
data class LotePendiente(
    val viajes: List<ViajeEntity> = emptyList(),
    val revisiones: List<RevisionEntity> = emptyList(),
    val eventos: List<EventoEntity> = emptyList(),
    val gastos: List<GastoEntity> = emptyList(),
    val novedades: List<NovedadEntity> = emptyList(),
    val adjuntos: List<AdjuntoEntity> = emptyList(),
) {
    val total: Int get() = viajes.size + revisiones.size + eventos.size + gastos.size + novedades.size + adjuntos.size
}

/**
 * Filas que cambiaron en el servidor desde la última bajada. [marca] es el mayor `actualizado_en`
 * recibido (lo fija el servidor); si no llegó nada queda null y se conserva la marca anterior.
 */
data class CambiosRemotos(
    val viajes: List<ViajeDto> = emptyList(),
    val revisiones: List<RevisionDto> = emptyList(),
    val eventos: List<EventoDto> = emptyList(),
    val gastos: List<GastoDto> = emptyList(),
    val novedades: List<NovedadDto> = emptyList(),
    val adjuntos: List<AdjuntoDto> = emptyList(),
    val marca: String? = marcaMaxima(
        viajes.map { it.actualizadoEn } + revisiones.map { it.actualizadoEn } + eventos.map { it.actualizadoEn } +
            gastos.map { it.actualizadoEn } + novedades.map { it.actualizadoEn } + adjuntos.map { it.actualizadoEn }
    ),
) {
    val total: Int get() = viajes.size + revisiones.size + eventos.size + gastos.size + novedades.size + adjuntos.size
}

/**
 * La marca más reciente. Se compara como instante (no como texto) porque PostgreSQL recorta los
 * ceros finales de los microsegundos; se devuelve el texto original para no perder precisión.
 */
fun marcaMaxima(marcas: List<String?>): String? =
    marcas.filterNotNull().maxByOrNull { OffsetDateTime.parse(it).toInstant() }

/** Ruta del archivo dentro del bucket: la primera carpeta es el viaje (así lo exige la política de Storage). */
fun rutaRemotaDe(a: AdjuntoEntity): String =
    "${a.viajeId}/${a.id}.${if (a.tipo == TipoAdjunto.FIRMA) "png" else "jpg"}"

interface OrigenLocal {
    suspend fun pendientes(): LotePendiente

    /** Limpia syncPendiente solo si la fila no cambió desde que se leyó (mismo modificadoLocal). */
    suspend fun marcarEnviado(item: Sincronizable)

    /** Guarda lo que bajó del servidor, salvo las filas que en el teléfono siguen pendientes. */
    suspend fun aplicarRemotos(c: CambiosRemotos)

    /** Borra el viaje local con todos sus registros (se reemplaza por la versión del servidor). */
    suspend fun descartarViaje(viajeId: String)
}

enum class ResultadoFila { OK, RECHAZADO_APROBADO }

/** El archivo local del adjunto ya no existe: no se puede subir, pero no debe frenar lo demás. */
class ArchivoNoDisponibleException(ruta: String) : Exception("No se encontró el archivo $ruta")

interface DestinoRemoto {
    /** Upsert de un viaje; RECHAZADO_APROBADO si el administrador ya lo aprobó. */
    suspend fun subirViaje(v: ViajeEntity): ResultadoFila

    /** Upsert por id (onConflict = "id") de filas de una misma [tabla]. */
    suspend fun subirLote(tabla: String, items: List<Sincronizable>)

    /** Sube el archivo del adjunto al bucket y devuelve su ruta remota ([ArchivoNoDisponibleException] si falta). */
    suspend fun subirArchivo(a: AdjuntoEntity): String

    /** Filas del chofer con actualizado_en mayor que [marca] (todas si es null). */
    suspend fun bajarDesde(marca: String?, choferId: String): CambiosRemotos
}
