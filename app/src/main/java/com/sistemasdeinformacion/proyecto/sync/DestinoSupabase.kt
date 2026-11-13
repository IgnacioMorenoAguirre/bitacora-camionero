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
import com.sistemasdeinformacion.proyecto.data.remote.BUCKET_ADJUNTOS
import com.sistemasdeinformacion.proyecto.data.remote.EventoDto
import com.sistemasdeinformacion.proyecto.data.remote.GastoDto
import com.sistemasdeinformacion.proyecto.data.remote.NovedadDto
import com.sistemasdeinformacion.proyecto.data.remote.RevisionDto
import com.sistemasdeinformacion.proyecto.data.remote.ViajeDto
import com.sistemasdeinformacion.proyecto.data.remote.aDto
import com.sistemasdeinformacion.proyecto.data.remote.aSubida
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import java.io.File
import java.time.OffsetDateTime

/**
 * Supabase como destino de la sincronización. Los errores de red de supabase-kt llegan como
 * HttpRequestException (una IOException), que el Sincronizador traduce a "sin conexión".
 */
class DestinoSupabase(private val supabase: SupabaseClient) : DestinoRemoto {

    /*
     * JSON propio en vez del serializador por defecto del cliente (Json.Default):
     *  - encodeDefaults: cada fila lleva todas sus columnas. Sin esto se omiten los valores por defecto
     *    (un comentario borrado no llegaría) y PostgREST exige las mismas llaves en todo el lote;
     *  - ignoreUnknownKeys: el servidor devuelve columnas que la app no usa (creado_en, validado_por).
     */
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun subirViaje(v: ViajeEntity): ResultadoFila = try {
        supabase.from(Tablas.VIAJES).upsert(JsonArray(listOf(json.encodeToJsonElement(v.aSubida())))) { onConflict = "id" }
        ResultadoFila.OK
    } catch (e: RestException) {
        // El trigger proteger_campos lanza 'VIAJE_APROBADO' (P0001) si el administrador ya lo aprobó.
        if (e.error.contains(VIAJE_APROBADO) || e.message?.contains(VIAJE_APROBADO) == true) {
            ResultadoFila.RECHAZADO_APROBADO
        } else {
            throw e
        }
    }

    override suspend fun subirLote(tabla: String, items: List<Sincronizable>) {
        if (items.isEmpty()) return
        val filas = when (tabla) {
            Tablas.REVISIONES -> json.encodeToJsonElement(items.map { (it as RevisionEntity).aDto() })
            Tablas.EVENTOS -> json.encodeToJsonElement(items.map { (it as EventoEntity).aDto() })
            Tablas.GASTOS -> json.encodeToJsonElement(items.map { (it as GastoEntity).aDto() })
            Tablas.NOVEDADES -> json.encodeToJsonElement(items.map { (it as NovedadEntity).aDto() })
            Tablas.ADJUNTOS -> json.encodeToJsonElement(items.map { (it as AdjuntoEntity).aDto() })
            else -> error("Tabla sin subida por lote: $tabla")
        }
        supabase.from(tabla).upsert(filas.jsonArray) { onConflict = "id" }
    }

    override suspend fun subirArchivo(a: AdjuntoEntity): String {
        val ruta = rutaRemotaDe(a)
        val archivo = File(a.rutaLocal)
        // Un archivo que falta no es falta de señal (FileNotFoundException es una IOException).
        val bytes = withContext(Dispatchers.IO) { if (archivo.isFile) archivo.readBytes() else null }
            ?: throw ArchivoNoDisponibleException(a.rutaLocal)
        supabase.storage.from(BUCKET_ADJUNTOS).upload(ruta, bytes) {
            upsert = true // reintentar sube el mismo archivo a la misma ruta
            contentType = if (a.tipo == TipoAdjunto.FIRMA) ContentType.Image.PNG else ContentType.Image.JPEG
        }
        return ruta
    }

    override suspend fun bajarDesde(marca: String?, choferId: String): CambiosRemotos {
        // Se vuelve a pedir un margen antes de la marca: now() de PostgreSQL es la hora de inicio de la
        // transacción, así que una fila puede quedar visible después de otra con marca mayor. Aplicar
        // dos veces la misma fila no cambia nada.
        val desde = marca?.let { OffsetDateTime.parse(it).minusMinutes(MARGEN_MINUTOS).toString() }
        // Las políticas RLS ya limitan los registros a los viajes del chofer.
        return CambiosRemotos(
            viajes = bajar<ViajeDto>(Tablas.VIAJES, desde, choferId),
            revisiones = bajar<RevisionDto>(Tablas.REVISIONES, desde),
            eventos = bajar<EventoDto>(Tablas.EVENTOS, desde),
            gastos = bajar<GastoDto>(Tablas.GASTOS, desde),
            novedades = bajar<NovedadDto>(Tablas.NOVEDADES, desde),
            adjuntos = bajar<AdjuntoDto>(Tablas.ADJUNTOS, desde),
        )
    }

    /**
     * Pide por páginas ordenadas por id (no cambia al actualizarse una fila, así ninguna se salta).
     * La página es menor que el máximo de filas por respuesta de Supabase (1000 por defecto).
     */
    private suspend inline fun <reified T> bajar(tabla: String, desde: String?, choferId: String? = null): List<T> {
        val filas = mutableListOf<T>()
        var inicio = 0L
        while (true) {
            val pagina = supabase.from(tabla).select {
                filter {
                    if (choferId != null) eq("chofer_id", choferId)
                    if (desde != null) gt("actualizado_en", desde)
                }
                order("id", Order.ASCENDING)
                range(inicio, inicio + PAGINA - 1)
            }.data.let { json.decodeFromString<List<T>>(it) }
            filas += pagina
            if (pagina.size < PAGINA) return filas
            inicio += PAGINA
        }
    }

    private companion object {
        const val VIAJE_APROBADO = "VIAJE_APROBADO"
        const val PAGINA = 500L
        const val MARGEN_MINUTOS = 2L
    }
}
