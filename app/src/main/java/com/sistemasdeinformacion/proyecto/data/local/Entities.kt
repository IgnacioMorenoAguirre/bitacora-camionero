package com.sistemasdeinformacion.proyecto.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sistemasdeinformacion.proyecto.data.model.EstadoValidacion
import com.sistemasdeinformacion.proyecto.data.model.EstadoViaje
import com.sistemasdeinformacion.proyecto.data.model.Gravedad
import com.sistemasdeinformacion.proyecto.data.model.TipoAdjunto
import com.sistemasdeinformacion.proyecto.data.model.TipoEvento
import com.sistemasdeinformacion.proyecto.data.model.TipoGasto
import com.sistemasdeinformacion.proyecto.data.model.TipoNovedad

/*
 * Tablas locales (Room). Cada registro sincronizable lleva:
 *  - syncPendiente: true mientras el servidor no haya confirmado el envío.
 *  - modificadoLocal: instante de la última edición en el teléfono; si cambia mientras se
 *    envía, el registro no se marca como enviado (sigue pendiente para el próximo intento).
 * Las fechas se guardan como epoch en milisegundos.
 */

interface Sincronizable {
    val id: String
    val modificadoLocal: Long
    val syncPendiente: Boolean
}

@Entity(tableName = "viajes", indices = [Index("choferId"), Index("syncPendiente")])
data class ViajeEntity(
    @PrimaryKey override val id: String,
    val choferId: String,
    val camionId: String?,
    val cliente: String,
    val origen: String,
    val destino: String,
    val ruta: String?,
    val tipoCarga: String,
    val pesoKg: Double,
    val requiereRefrigeracion: Boolean,
    val precio: Long,
    val fechaSalida: Long,
    val fechaLlegada: Long?,
    val kmInicial: Int?,
    val kmFinal: Int?,
    val estado: EstadoViaje,
    val validacion: EstadoValidacion = EstadoValidacion.PENDIENTE,
    val comentarioValidacion: String? = null,
    val observaciones: String?,
    val eliminado: Boolean = false,
    override val modificadoLocal: Long,
    override val syncPendiente: Boolean,
) : Sincronizable

val ViajeEntity.kmRecorridos: Int?
    get() = if (kmInicial != null && kmFinal != null) kmFinal - kmInicial else null

private const val FK_VIAJE = "viajeId"

@Entity(
    tableName = "revisiones",
    foreignKeys = [ForeignKey(entity = ViajeEntity::class, parentColumns = ["id"], childColumns = [FK_VIAJE], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = [FK_VIAJE], unique = true), Index("syncPendiente")],
)
data class RevisionEntity(
    @PrimaryKey override val id: String,
    val viajeId: String,
    val neumaticos: Boolean,
    val luces: Boolean,
    val frenos: Boolean,
    val niveles: Boolean,
    val documentos: Boolean,
    val comentario: String?,
    val eliminado: Boolean = false,
    override val modificadoLocal: Long,
    override val syncPendiente: Boolean,
) : Sincronizable

val RevisionEntity.todoOk: Boolean
    get() = neumaticos && luces && frenos && niveles && documentos

@Entity(
    tableName = "eventos",
    foreignKeys = [ForeignKey(entity = ViajeEntity::class, parentColumns = ["id"], childColumns = [FK_VIAJE], onDelete = ForeignKey.CASCADE)],
    indices = [Index(FK_VIAJE), Index("syncPendiente")],
)
data class EventoEntity(
    @PrimaryKey override val id: String,
    val viajeId: String,
    val tipo: TipoEvento,
    val fechaHora: Long,
    val fechaFin: Long? = null,
    val latitud: Double?,
    val longitud: Double?,
    val direccion: String?,
    val km: Int?,
    val nota: String?,
    val eliminado: Boolean = false,
    override val modificadoLocal: Long,
    override val syncPendiente: Boolean,
) : Sincronizable

@Entity(
    tableName = "gastos",
    foreignKeys = [ForeignKey(entity = ViajeEntity::class, parentColumns = ["id"], childColumns = [FK_VIAJE], onDelete = ForeignKey.CASCADE)],
    indices = [Index(FK_VIAJE), Index("syncPendiente")],
)
data class GastoEntity(
    @PrimaryKey override val id: String,
    val viajeId: String,
    val tipo: TipoGasto,
    val monto: Long,
    val litros: Double?,
    val fechaHora: Long,
    val descripcion: String?,
    val eliminado: Boolean = false,
    override val modificadoLocal: Long,
    override val syncPendiente: Boolean,
) : Sincronizable

@Entity(
    tableName = "novedades",
    foreignKeys = [ForeignKey(entity = ViajeEntity::class, parentColumns = ["id"], childColumns = [FK_VIAJE], onDelete = ForeignKey.CASCADE)],
    indices = [Index(FK_VIAJE), Index("syncPendiente")],
)
data class NovedadEntity(
    @PrimaryKey override val id: String,
    val viajeId: String,
    val tipo: TipoNovedad,
    val gravedad: Gravedad,
    val descripcion: String,
    val fechaHora: Long,
    val latitud: Double?,
    val longitud: Double?,
    val eliminado: Boolean = false,
    override val modificadoLocal: Long,
    override val syncPendiente: Boolean,
) : Sincronizable

@Entity(
    tableName = "adjuntos",
    foreignKeys = [ForeignKey(entity = ViajeEntity::class, parentColumns = ["id"], childColumns = [FK_VIAJE], onDelete = ForeignKey.CASCADE)],
    indices = [Index(FK_VIAJE), Index("syncPendiente")],
)
data class AdjuntoEntity(
    @PrimaryKey override val id: String,
    val viajeId: String,
    val tipo: TipoAdjunto,
    val rutaLocal: String,
    val rutaRemota: String?,
    val nombreReceptor: String?,
    val fechaHora: Long,
    val eliminado: Boolean = false,
    override val modificadoLocal: Long,
    override val syncPendiente: Boolean,
) : Sincronizable

/** Copia local (solo lectura para el chofer) de los camiones activos. */
@Entity(tableName = "camiones")
data class CamionEntity(
    @PrimaryKey val id: String,
    val patente: String,
    val marca: String,
    val modelo: String,
    val rendimientoKmL: Double,
)
