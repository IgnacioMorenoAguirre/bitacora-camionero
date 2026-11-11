package com.sistemasdeinformacion.proyecto.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sistemasdeinformacion.proyecto.data.model.EstadoValidacion
import com.sistemasdeinformacion.proyecto.data.model.EstadoViaje
import com.sistemasdeinformacion.proyecto.data.model.Gravedad
import com.sistemasdeinformacion.proyecto.data.model.TipoEvento
import com.sistemasdeinformacion.proyecto.data.model.TipoGasto
import com.sistemasdeinformacion.proyecto.data.model.TipoNovedad

/*
 * Tablas locales (Room). Cada registro lleva:
 *  - syncPendiente: true mientras no se haya confirmado su envío al servidor.
 *  - modificadoLocal: instante de la última edición en el teléfono; permite saber si el
 *    registro cambió mientras se estaba enviando (y no marcarlo como sincronizado por error).
 * Las fechas se guardan como epoch en milisegundos.
 */

@Entity(tableName = "viajes", indices = [Index("choferId"), Index("syncPendiente")])
data class ViajeEntity(
    @PrimaryKey val id: String,
    val choferId: String,
    val camionId: String?,
    val cliente: String,
    val origen: String,
    val destino: String,
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
    val modificadoLocal: Long,
    val syncPendiente: Boolean,
)

val ViajeEntity.kmRecorridos: Int?
    get() = if (kmInicial != null && kmFinal != null) kmFinal - kmInicial else null

@Entity(
    tableName = "eventos",
    foreignKeys = [ForeignKey(entity = ViajeEntity::class, parentColumns = ["id"], childColumns = ["viajeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("viajeId"), Index("syncPendiente")],
)
data class EventoEntity(
    @PrimaryKey val id: String,
    val viajeId: String,
    val tipo: TipoEvento,
    val fechaHora: Long,
    val latitud: Double?,
    val longitud: Double?,
    val direccion: String?,
    val km: Int?,
    val nota: String?,
    val eliminado: Boolean = false,
    val modificadoLocal: Long,
    val syncPendiente: Boolean,
)

@Entity(
    tableName = "gastos",
    foreignKeys = [ForeignKey(entity = ViajeEntity::class, parentColumns = ["id"], childColumns = ["viajeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("viajeId"), Index("syncPendiente")],
)
data class GastoEntity(
    @PrimaryKey val id: String,
    val viajeId: String,
    val tipo: TipoGasto,
    val monto: Long,
    val litros: Double?,
    val fechaHora: Long,
    val descripcion: String?,
    val eliminado: Boolean = false,
    val modificadoLocal: Long,
    val syncPendiente: Boolean,
)

@Entity(
    tableName = "novedades",
    foreignKeys = [ForeignKey(entity = ViajeEntity::class, parentColumns = ["id"], childColumns = ["viajeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("viajeId"), Index("syncPendiente")],
)
data class NovedadEntity(
    @PrimaryKey val id: String,
    val viajeId: String,
    val tipo: TipoNovedad,
    val gravedad: Gravedad,
    val descripcion: String,
    val fechaHora: Long,
    val latitud: Double?,
    val longitud: Double?,
    val eliminado: Boolean = false,
    val modificadoLocal: Long,
    val syncPendiente: Boolean,
)

/** Copia local (solo lectura para el chofer) de los camiones activos. */
@Entity(tableName = "camiones")
data class CamionEntity(
    @PrimaryKey val id: String,
    val patente: String,
    val marca: String,
    val modelo: String,
    val rendimientoKmL: Double,
)
