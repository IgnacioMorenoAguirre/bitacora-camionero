package com.sistemasdeinformacion.proyecto.data.repo

import android.util.Log
import com.sistemasdeinformacion.proyecto.data.model.EstadoValidacion
import com.sistemasdeinformacion.proyecto.data.model.EstadoViaje
import com.sistemasdeinformacion.proyecto.data.remote.AdjuntoDto
import com.sistemasdeinformacion.proyecto.data.remote.BUCKET_ADJUNTOS
import com.sistemasdeinformacion.proyecto.data.remote.CamionDto
import com.sistemasdeinformacion.proyecto.data.remote.EventoDto
import com.sistemasdeinformacion.proyecto.data.remote.GastoDto
import com.sistemasdeinformacion.proyecto.data.remote.NovedadDto
import com.sistemasdeinformacion.proyecto.data.remote.PerfilDto
import com.sistemasdeinformacion.proyecto.data.remote.ResumenViajeDto
import com.sistemasdeinformacion.proyecto.data.remote.RevisionDto
import com.sistemasdeinformacion.proyecto.data.remote.ViajeDto
import com.sistemasdeinformacion.proyecto.data.remote.aEntidad
import com.sistemasdeinformacion.proyecto.data.remote.aIso
import com.sistemasdeinformacion.proyecto.data.remote.desdeIso
import com.sistemasdeinformacion.proyecto.domain.CalculosViaje
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import kotlin.time.Duration.Companion.hours

/** La lista del administrador trae como máximo los viajes más recientes hasta este número. */
const val LIMITE_LISTA_ADMIN = 200

/** Filtros de la lista del administrador; null = sin filtrar. [desde] y [hasta] miran la fecha de salida. */
data class FiltroViajes(
    val choferId: String? = null,
    val estado: EstadoViaje? = null,
    val validacion: EstadoValidacion? = null,
    val desde: Long? = null,
    val hasta: Long? = null,
)

/** Foto o firma con su URL firmada (válida 1 h); null si el archivo aún no se sube o no se pudo firmar. */
data class AdjuntoRemoto(val adjunto: AdjuntoDto, val url: String?)

/** Bitácora completa de un viaje tal como está en el servidor. */
data class DetalleViajeRemoto(
    val viaje: ViajeDto,
    val chofer: String?,
    val camion: CamionDto?,
    val revision: RevisionDto?,
    val eventos: List<EventoDto>,
    val gastos: List<GastoDto>,
    val novedades: List<NovedadDto>,
    val adjuntos: List<AdjuntoRemoto>,
    /** true si en algún punto del viaje el chofer superó el límite de manejo continuo. */
    val excedioHoras: Boolean,
) {
    /** Las mismas entidades que usa el chofer, para reutilizar la línea de tiempo (construirHitos). */
    fun comoDetalleLocal() = DetalleViaje(
        viaje = viaje.aEntidad(),
        revision = revision?.aEntidad(),
        eventos = eventos.map { it.aEntidad() },
        gastos = gastos.map { it.aEntidad() },
        novedades = novedades.map { it.aEntidad() },
        adjuntos = adjuntos.map { it.adjunto.aEntidad() },
        camion = camion?.aEntidad(),
    )

    companion object {
        /** Arma el detalle y calcula [excedioHoras]; [ahora] es el fin para un viaje que sigue en curso. */
        fun armar(
            viaje: ViajeDto, chofer: String?, camion: CamionDto?, revision: RevisionDto?, eventos: List<EventoDto>,
            gastos: List<GastoDto>, novedades: List<NovedadDto>, adjuntos: List<AdjuntoRemoto>, ahora: Long,
        ): DetalleViajeRemoto {
            val vigentes = eventos.filter { !it.eliminado }
            val excedio = excedioManejo(viaje.estado, viaje.fechaSalida, viaje.fechaLlegada, vigentes, ahora)
            return DetalleViajeRemoto(
                viaje, chofer, camion, revision?.takeIf { !it.eliminado }, vigentes,
                gastos.filter { !it.eliminado }, novedades.filter { !it.eliminado }, adjuntos.filter { !it.adjunto.eliminado }, excedio,
            )
        }
    }
}

/**
 * Fin del conteo de manejo: la llegada; si sigue en curso, ahora; si se canceló sin llegada, el último
 * evento (así un viaje cancelado no sigue sumando horas para siempre).
 */
internal fun excedioManejo(estado: String, salidaIso: String, llegadaIso: String?, eventos: List<EventoDto>, ahora: Long): Boolean {
    val inicio = salidaIso.desdeIso()
    val entidades = eventos.map { it.aEntidad() }
    val fin = llegadaIso?.desdeIso()
        ?: if (estado == EstadoViaje.EN_CURSO.valor) ahora
        else entidades.maxOfOrNull { it.fechaFin ?: it.fechaHora } ?: inicio
    return CalculosViaje.excedioManejoContinuo(inicio, fin, entidades)
}

/** Errores que la pantalla muestra tal cual (el mensaje ya dice qué pasó y qué hacer). */
sealed class ErrorAdmin(mensaje: String) : Exception(mensaje) {
    data object SinConexion : ErrorAdmin("Sin conexión. Revisa tu señal y vuelve a intentar.")
    data object SinServidor : ErrorAdmin("Falta configurar el servidor (Supabase) en local.properties.")
    data object SoloFinalizados : ErrorAdmin("Solo se aprueban viajes finalizados.")
    data object ViajeNoEncontrado : ErrorAdmin("No se encontró el viaje. Puede que el chofer lo haya eliminado.")
    data object PatenteRepetida : ErrorAdmin("Ya existe un camión con esa patente.")
    data object PatenteInvalida : ErrorAdmin("Revisa la patente: usa el formato BBBB12 o AB1234.")
    data object SinPermiso : ErrorAdmin("Tu cuenta no tiene permiso de administrador.")
    data object Desconocido : ErrorAdmin("No se pudo completar. Vuelve a intentar.")
}

/** Pantallas del administrador: todo se lee y escribe directo en Supabase (necesitan internet). */
interface AdminRepositorio {
    /** Consulta resumen_viajes y vuelve a emitir con cada cambio de Realtime en viajes (canal admin-viajes). */
    fun viajesEnVivo(filtro: FiltroViajes): Flow<List<ResumenViajeDto>>
    /** Ids de los viajes de la lista en que el chofer superó el límite de manejo continuo. */
    suspend fun excesosDeHoras(viajes: List<ResumenViajeDto>): Set<String>
    suspend fun choferes(): List<PerfilDto>
    /** Viajes finalizados con salida en [desde, hasta) (epoch ms), sin el tope de la lista: para los reportes. */
    suspend fun finalizadosEntre(desde: Long, hasta: Long): List<ResumenViajeDto>
    suspend fun detalle(viajeId: String): DetalleViajeRemoto
    suspend fun validar(viajeId: String, aprobado: Boolean, comentario: String?): Result<Unit>
    suspend fun urlAdjunto(ruta: String): String
    /** Todos los camiones (también los inactivos); vuelve a emitir después de cada guardarCamion. */
    fun camiones(): Flow<List<CamionDto>>
    suspend fun guardarCamion(c: CamionDto): Result<Unit>
}

/** Sin Supabase configurado no hay sesión de administrador; igual se responde con un error claro. */
object AdminRepositorioSinServidor : AdminRepositorio {
    override fun viajesEnVivo(filtro: FiltroViajes): Flow<List<ResumenViajeDto>> = flow { throw ErrorAdmin.SinServidor }
    override suspend fun excesosDeHoras(viajes: List<ResumenViajeDto>): Set<String> = emptySet()
    override suspend fun choferes(): List<PerfilDto> = emptyList()
    override suspend fun finalizadosEntre(desde: Long, hasta: Long): List<ResumenViajeDto> = throw ErrorAdmin.SinServidor
    override suspend fun detalle(viajeId: String): DetalleViajeRemoto = throw ErrorAdmin.SinServidor
    override suspend fun validar(viajeId: String, aprobado: Boolean, comentario: String?) = Result.failure<Unit>(ErrorAdmin.SinServidor)
    override suspend fun urlAdjunto(ruta: String): String = throw ErrorAdmin.SinServidor
    override fun camiones(): Flow<List<CamionDto>> = flow { throw ErrorAdmin.SinServidor }
    override suspend fun guardarCamion(c: CamionDto) = Result.failure<Unit>(ErrorAdmin.SinServidor)
}

class AdminRepositorioSupabase(
    private val supabase: SupabaseClient,
    alcance: CoroutineScope,
    private val reloj: () -> Long = System::currentTimeMillis,
) : AdminRepositorio {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Un único canal de Realtime compartido: cambiar el filtro no abre otro. Emite con cada cambio en
     * viajes y cada vez que el canal queda suscrito (también al reconectarse, por si algo cambió sin señal).
     * Si Realtime no conecta, la lista igual se carga una vez; solo deja de actualizarse sola.
     */
    private val cambiosViajes: Flow<Unit> = channelFlow {
        val canal = supabase.channel(CANAL_VIAJES)
        val cambios = canal.postgresChangeFlow<PostgresAction>(schema = "public") { table = "viajes" }
        launch { cambios.collect { send(Unit) } }
        launch { canal.status.filter { it == RealtimeChannel.Status.SUBSCRIBED }.collect { send(Unit) } }
        try {
            try {
                canal.subscribe()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(ETIQUETA, "No se pudo abrir Realtime; la lista no se actualizará sola", e)
            }
            awaitCancellation()
        } finally {
            withContext(NonCancellable) { runCatching { supabase.realtime.removeChannel(canal) } }
        }
    }.shareIn(alcance, SharingStarted.WhileSubscribed(5_000))

    private val refrescoCamiones = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun viajesEnVivo(filtro: FiltroViajes): Flow<List<ResumenViajeDto>> =
        merge(flowOf(Unit), cambiosViajes)
            .conflate()
            .map { traducir { consultarResumen(filtro) } }

    private suspend fun consultarResumen(f: FiltroViajes): List<ResumenViajeDto> =
        supabase.from("resumen_viajes").select {
            filter {
                f.choferId?.let { eq("chofer_id", it) }
                f.estado?.let { eq("estado", it.valor) }
                f.validacion?.let { eq("validacion", it.valor) }
                f.desde?.let { gte("fecha_salida", it.aIso()) }
                f.hasta?.let { lte("fecha_salida", it.aIso()) }
            }
            order("fecha_salida", Order.DESCENDING)
            range(0, LIMITE_LISTA_ADMIN - 1L)
        }.data.let { json.decodeFromString<List<ResumenViajeDto>>(it) }

    override suspend fun excesosDeHoras(viajes: List<ResumenViajeDto>): Set<String> = traducir {
        if (viajes.isEmpty()) return@traducir emptySet()
        // Solo los descansos reinician el conteo; con ellos y las horas de salida/llegada basta.
        val descansos = viajes.map { it.id }.chunked(LOTE_IDS).flatMap { ids ->
            paginar<EventoDto>("eventos") {
                isIn("viaje_id", ids)
                eq("tipo", "descanso")
                eq("eliminado", false)
            }
        }.groupBy { it.viajeId }
        val ahora = reloj()
        viajes.filter { v ->
            excedioManejo(v.estado, v.fechaSalida, v.fechaLlegada, descansos[v.id].orEmpty(), ahora)
        }.map { it.id }.toSet()
    }

    override suspend fun choferes(): List<PerfilDto> = traducir {
        supabase.from("perfiles").select {
            filter { eq("rol", "chofer") }
            order("nombre_completo", Order.ASCENDING)
        }.data.let { json.decodeFromString<List<PerfilDto>>(it) }
    }

    override suspend fun finalizadosEntre(desde: Long, hasta: Long): List<ResumenViajeDto> = traducir {
        paginar("resumen_viajes") {
            eq("estado", EstadoViaje.FINALIZADO.valor)
            gte("fecha_salida", desde.aIso())
            lt("fecha_salida", hasta.aIso())
        }
    }

    override suspend fun detalle(viajeId: String): DetalleViajeRemoto = traducir {
        val viaje = supabase.from("viajes").select { filter { eq("id", viajeId) } }
            .data.let { json.decodeFromString<List<ViajeDto>>(it) }
            .firstOrNull()?.takeIf { !it.eliminado } ?: throw ErrorAdmin.ViajeNoEncontrado
        val chofer = supabase.from("perfiles").select { filter { eq("id", viaje.choferId) } }
            .data.let { json.decodeFromString<List<PerfilDto>>(it) }.firstOrNull()?.nombreCompleto
        val camion = viaje.camionId?.let { cid ->
            supabase.from("camiones").select { filter { eq("id", cid) } }
                .data.let { json.decodeFromString<List<CamionDto>>(it) }.firstOrNull()
        }
        val adjuntos = delViaje<AdjuntoDto>("adjuntos", viajeId).filter { !it.eliminado }.map { a ->
            AdjuntoRemoto(a, a.rutaRemota?.let { ruta -> firmarOrNull(ruta) })
        }
        DetalleViajeRemoto.armar(
            viaje = viaje, chofer = chofer, camion = camion,
            revision = delViaje<RevisionDto>("revisiones", viajeId).firstOrNull(),
            eventos = delViaje("eventos", viajeId),
            gastos = delViaje("gastos", viajeId),
            novedades = delViaje("novedades", viajeId),
            adjuntos = adjuntos,
            ahora = reloj(),
        )
    }

    /** Una foto que no se pudo firmar no impide revisar el resto de la bitácora. */
    private suspend fun firmarOrNull(ruta: String): String? = try {
        urlAdjunto(ruta)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(ETIQUETA, "No se pudo firmar $ruta", e)
        null
    }

    override suspend fun validar(viajeId: String, aprobado: Boolean, comentario: String?): Result<Unit> = resultado {
        val cambios = buildJsonObject {
            put("validacion", if (aprobado) EstadoValidacion.APROBADO.valor else EstadoValidacion.OBSERVADO.valor)
            // Al aprobar se borra el comentario de una observación anterior.
            if (aprobado || comentario.isNullOrBlank()) put("comentario_validacion", JsonNull) else put("comentario_validacion", comentario.trim())
        }
        val filas = supabase.from("viajes").update(cambios) {
            select()
            filter { eq("id", viajeId) }
        }.data.let { json.decodeFromString<List<JsonObject>>(it) }
        // Si la RLS no deja tocar la fila, PostgREST responde 200 sin filas.
        if (filas.isEmpty()) throw ErrorAdmin.SinPermiso
    }

    override suspend fun urlAdjunto(ruta: String): String = traducir {
        supabase.storage.from(BUCKET_ADJUNTOS).createSignedUrl(ruta, 1.hours)
    }

    override fun camiones(): Flow<List<CamionDto>> =
        refrescoCamiones.onStart { emit(Unit) }.map {
            traducir {
                supabase.from("camiones").select { order("patente", Order.ASCENDING) }
                    .data.let { json.decodeFromString<List<CamionDto>>(it) }
            }
        }

    override suspend fun guardarCamion(c: CamionDto): Result<Unit> = resultado {
        val fila = buildJsonObject {
            put("patente", c.patente)
            put("marca", c.marca.trim())
            put("modelo", c.modelo.trim())
            if (c.anio != null) put("anio", c.anio) else put("anio", JsonNull)
            put("rendimiento_km_l", c.rendimientoKmL)
            put("activo", c.activo)
        }
        if (c.id == null) {
            supabase.from("camiones").insert(fila)
        } else {
            val filas = supabase.from("camiones").update(fila) {
                select()
                filter { eq("id", c.id) }
            }.data.let { json.decodeFromString<List<JsonObject>>(it) }
            if (filas.isEmpty()) throw ErrorAdmin.SinPermiso
        }
        refrescoCamiones.tryEmit(Unit)
    }

    private suspend inline fun <reified T> delViaje(tabla: String, viajeId: String): List<T> =
        paginar(tabla) { eq("viaje_id", viajeId) }

    /** Por páginas ordenadas por id, bajo el máximo de filas por respuesta de Supabase. */
    private suspend inline fun <reified T> paginar(
        tabla: String,
        crossinline filtros: PostgrestFilterBuilder.() -> Unit,
    ): List<T> {
        val filas = mutableListOf<T>()
        var inicio = 0L
        while (true) {
            val pagina = supabase.from(tabla).select {
                filter { filtros() }
                order("id", Order.ASCENDING)
                range(inicio, inicio + PAGINA - 1)
            }.data.let { json.decodeFromString<List<T>>(it) }
            filas += pagina
            if (pagina.size < PAGINA) return filas
            inicio += PAGINA
        }
    }

    private suspend fun resultado(bloque: suspend () -> Unit): Result<Unit> = try {
        traducir { bloque() }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: ErrorAdmin) {
        Result.failure(e)
    }

    /** Traduce los errores de supabase-kt a [ErrorAdmin]. Sin señal llega HttpRequestException (IOException). */
    private suspend fun <T> traducir(bloque: suspend () -> T): T = try {
        bloque()
    } catch (e: CancellationException) {
        throw e
    } catch (e: ErrorAdmin) {
        throw e
    } catch (e: RestException) {
        val codigo = (e as? PostgrestRestException)?.code
        val texto = "${e.error} ${e.message}"
        throw when {
            texto.contains("SOLO_FINALIZADOS") -> ErrorAdmin.SoloFinalizados
            codigo == "23505" -> ErrorAdmin.PatenteRepetida
            codigo == "23514" && texto.contains("patente") -> ErrorAdmin.PatenteInvalida
            codigo == "42501" || e.statusCode == 401 || e.statusCode == 403 -> ErrorAdmin.SinPermiso
            else -> { Log.w(ETIQUETA, "Error del servidor", e); ErrorAdmin.Desconocido }
        }
    } catch (_: IOException) {
        throw ErrorAdmin.SinConexion
    } catch (e: Exception) {
        Log.w(ETIQUETA, "Error inesperado", e)
        throw ErrorAdmin.Desconocido
    }

    private companion object {
        const val ETIQUETA = "Bitacora"
        const val CANAL_VIAJES = "admin-viajes"
        const val LOTE_IDS = 50
        const val PAGINA = 500L
    }
}
