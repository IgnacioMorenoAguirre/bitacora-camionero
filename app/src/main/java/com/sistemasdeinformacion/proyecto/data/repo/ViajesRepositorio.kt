package com.sistemasdeinformacion.proyecto.data.repo

import androidx.room.withTransaction
import com.sistemasdeinformacion.proyecto.core.Ids
import com.sistemasdeinformacion.proyecto.core.Reloj
import com.sistemasdeinformacion.proyecto.data.local.AdjuntoEntity
import com.sistemasdeinformacion.proyecto.data.local.AppDatabase
import com.sistemasdeinformacion.proyecto.data.local.CamionEntity
import com.sistemasdeinformacion.proyecto.data.local.EventoEntity
import com.sistemasdeinformacion.proyecto.data.local.GastoEntity
import com.sistemasdeinformacion.proyecto.data.local.NovedadEntity
import com.sistemasdeinformacion.proyecto.data.local.RevisionEntity
import com.sistemasdeinformacion.proyecto.data.local.ViajeEntity
import com.sistemasdeinformacion.proyecto.data.model.EstadoValidacion
import com.sistemasdeinformacion.proyecto.data.model.EstadoViaje
import com.sistemasdeinformacion.proyecto.data.model.Gravedad
import com.sistemasdeinformacion.proyecto.data.model.TipoEvento
import com.sistemasdeinformacion.proyecto.data.model.TipoGasto
import com.sistemasdeinformacion.proyecto.data.model.TipoNovedad
import com.sistemasdeinformacion.proyecto.domain.Validaciones
import com.sistemasdeinformacion.proyecto.sync.ProgramadorSync
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

data class DatosNuevoViaje(
    val camionId: String?,
    val cliente: String,
    val origen: String,
    val destino: String,
    val ruta: String?,
    val tipoCarga: String,
    val pesoKg: Double,
    val refrigeracion: Boolean,
    val precio: Long,
    val kmInicial: Int,
    val observaciones: String?,
)

data class DatosRevision(
    val neumaticos: Boolean,
    val luces: Boolean,
    val frenos: Boolean,
    val niveles: Boolean,
    val documentos: Boolean,
    val comentario: String?,
)

data class DetalleViaje(
    val viaje: ViajeEntity?,
    val revision: RevisionEntity?,
    val eventos: List<EventoEntity>,
    val gastos: List<GastoEntity>,
    val novedades: List<NovedadEntity>,
    val adjuntos: List<AdjuntoEntity>,
    val camion: CamionEntity?,
)

class ViajeEnCursoException(val viajeId: String) : IllegalStateException("Ya tienes un viaje en curso.")
class ViajeSoloLecturaException : IllegalStateException("Este viaje ya fue aprobado y no se puede modificar.")
class KmIncoherenteException(val kmInicial: Int) : IllegalArgumentException("El km final no puede ser menor que el inicial.")

/** Todo lo del chofer pasa por aquí: lee y escribe solo en Room, y avisa al sincronizador. */
interface ViajesRepositorio {
    fun viajes(): Flow<List<ViajeEntity>>
    fun enCurso(): Flow<ViajeEntity?>
    fun detalle(id: String): Flow<DetalleViaje>
    fun eventosDelChofer(): Flow<List<EventoEntity>>
    fun gastosDelPeriodo(desde: Long, hasta: Long): Flow<List<GastoEntity>>
    fun camiones(): Flow<List<CamionEntity>>
    suspend fun crearViaje(datos: DatosNuevoViaje, revision: DatosRevision): Result<String>
    suspend fun registrarEvento(viajeId: String, tipo: TipoEvento, km: Int?, nota: String?): Result<String>
    suspend fun cerrarDescanso(eventoId: String): Result<Unit>
    suspend fun registrarGasto(viajeId: String, tipo: TipoGasto, monto: Long, litros: Double?, descripcion: String?): Result<String>
    suspend fun registrarNovedad(viajeId: String, tipo: TipoNovedad, gravedad: Gravedad, descripcion: String): Result<String>
    suspend fun finalizar(viajeId: String, kmFinal: Int, receptor: String?): Result<Unit>
}

@OptIn(ExperimentalCoroutinesApi::class)
class ViajesRepositorioRoom(
    private val db: AppDatabase,
    private val choferId: suspend () -> String,
    private val reloj: Reloj,
    private val ubicaciones: UbicacionRepositorio,
    private val programador: ProgramadorSync,
) : ViajesRepositorio {

    private val viajeDao = db.viajeDao()
    private val registroDao = db.registroDao()

    private fun <T> delChofer(bloque: (String) -> Flow<T>): Flow<T> = flow { emit(choferId()) }.flatMapLatest(bloque)

    override fun viajes() = delChofer { viajeDao.observarDelChofer(it) }
    override fun enCurso() = delChofer { viajeDao.observarEnCurso(it) }
    override fun eventosDelChofer() = delChofer { registroDao.observarEventosDelChofer(it) }
    override fun gastosDelPeriodo(desde: Long, hasta: Long) = delChofer { registroDao.observarGastosDelPeriodo(it, desde, hasta) }
    override fun camiones() = db.camionDao().observarTodos()

    override fun detalle(id: String): Flow<DetalleViaje> {
        val viaje = viajeDao.observar(id)
        val camion: Flow<CamionEntity?> = viaje.flatMapLatest { v ->
            val cid = v?.camionId
            if (cid == null) flowOf<CamionEntity?>(null) else flow<CamionEntity?> { emit(db.camionDao().obtener(cid)) }
        }
        return combine(
            viaje, registroDao.observarRevision(id), registroDao.observarEventos(id), registroDao.observarGastos(id),
            combine(registroDao.observarNovedades(id), registroDao.observarAdjuntos(id), camion) { n, a, c -> Triple(n, a, c) },
        ) { v, r, e, g, (n, a, c) -> DetalleViaje(v, r, e, g, n, a, c) }
    }

    override suspend fun crearViaje(datos: DatosNuevoViaje, revision: DatosRevision): Result<String> = runCatching {
        val chofer = choferId()
        viajeDao.obtenerEnCurso(chofer)?.let { throw ViajeEnCursoException(it.id) }
        val ubic = ubicaciones.actual()
        val ahora = reloj.ahora()
        val id = Ids.nuevo()
        db.withTransaction {
            viajeDao.guardar(
                ViajeEntity(
                    id = id, choferId = chofer, camionId = datos.camionId, cliente = datos.cliente.trim(),
                    origen = datos.origen.trim(), destino = datos.destino.trim(), ruta = datos.ruta?.trim()?.ifBlank { null },
                    tipoCarga = datos.tipoCarga.trim(), pesoKg = datos.pesoKg, requiereRefrigeracion = datos.refrigeracion,
                    precio = datos.precio, fechaSalida = ahora, fechaLlegada = null, kmInicial = datos.kmInicial, kmFinal = null,
                    estado = EstadoViaje.EN_CURSO, observaciones = datos.observaciones?.trim()?.ifBlank { null },
                    modificadoLocal = ahora, syncPendiente = true,
                )
            )
            registroDao.guardar(
                RevisionEntity(
                    id = Ids.nuevo(), viajeId = id, neumaticos = revision.neumaticos, luces = revision.luces,
                    frenos = revision.frenos, niveles = revision.niveles, documentos = revision.documentos,
                    comentario = revision.comentario?.trim()?.ifBlank { null }, modificadoLocal = ahora, syncPendiente = true,
                )
            )
            registroDao.guardar(evento(id, TipoEvento.INICIO, ahora, datos.kmInicial, null, ubic))
        }
        programador.alGuardar()
        id
    }

    override suspend fun registrarEvento(viajeId: String, tipo: TipoEvento, km: Int?, nota: String?): Result<String> = runCatching {
        exigirEditable(viajeId)
        val ubic = ubicaciones.actual()
        val e = evento(viajeId, tipo, reloj.ahora(), km, nota, ubic)
        registroDao.guardar(e)
        programador.alGuardar()
        e.id
    }

    override suspend fun cerrarDescanso(eventoId: String): Result<Unit> = runCatching {
        val e = registroDao.evento(eventoId) ?: error("No se encontró el descanso.")
        val ahora = reloj.ahora()
        registroDao.guardar(e.copy(fechaFin = ahora, modificadoLocal = ahora, syncPendiente = true))
        programador.alGuardar()
    }

    override suspend fun registrarGasto(viajeId: String, tipo: TipoGasto, monto: Long, litros: Double?, descripcion: String?): Result<String> = runCatching {
        exigirEditable(viajeId)
        val ahora = reloj.ahora()
        val g = GastoEntity(Ids.nuevo(), viajeId, tipo, monto, litros, ahora, descripcion?.trim()?.ifBlank { null }, false, ahora, true)
        registroDao.guardar(g)
        programador.alGuardar()
        g.id
    }

    override suspend fun registrarNovedad(viajeId: String, tipo: TipoNovedad, gravedad: Gravedad, descripcion: String): Result<String> = runCatching {
        exigirEditable(viajeId)
        val ubic = ubicaciones.actual()
        val ahora = reloj.ahora()
        val n = NovedadEntity(Ids.nuevo(), viajeId, tipo, gravedad, descripcion.trim(), ahora, ubic?.lat, ubic?.lon, false, ahora, true)
        registroDao.guardar(n)
        programador.alGuardar()
        n.id
    }

    override suspend fun finalizar(viajeId: String, kmFinal: Int, receptor: String?): Result<Unit> = runCatching {
        val v = exigirEditable(viajeId)
        if (!Validaciones.kmCoherentes(v.kmInicial, kmFinal)) throw KmIncoherenteException(v.kmInicial ?: 0)
        val ubic = ubicaciones.actual()
        val ahora = reloj.ahora()
        db.withTransaction {
            val nota = receptor?.trim()?.ifBlank { null }?.let { "Recibió: $it" }
            registroDao.guardar(evento(viajeId, TipoEvento.ENTREGA, ahora, kmFinal, nota, ubic))
            registroDao.guardar(evento(viajeId, TipoEvento.FIN, ahora + 1, kmFinal, null, ubic))
            viajeDao.guardar(
                v.copy(
                    kmFinal = kmFinal, fechaLlegada = ahora, estado = EstadoViaje.FINALIZADO,
                    validacion = if (v.validacion == EstadoValidacion.OBSERVADO) EstadoValidacion.PENDIENTE else v.validacion,
                    modificadoLocal = ahora, syncPendiente = true,
                )
            )
        }
        programador.alGuardar()
    }

    private suspend fun exigirEditable(viajeId: String): ViajeEntity {
        val v = viajeDao.obtener(viajeId) ?: error("No se encontró el viaje.")
        if (v.validacion == EstadoValidacion.APROBADO) throw ViajeSoloLecturaException()
        return v
    }

    private fun evento(viajeId: String, tipo: TipoEvento, hora: Long, km: Int?, nota: String?, u: Ubicacion?) = EventoEntity(
        id = Ids.nuevo(), viajeId = viajeId, tipo = tipo, fechaHora = hora, fechaFin = null,
        latitud = u?.lat, longitud = u?.lon, direccion = u?.direccion, km = km, nota = nota?.trim()?.ifBlank { null },
        modificadoLocal = hora, syncPendiente = true,
    )
}
