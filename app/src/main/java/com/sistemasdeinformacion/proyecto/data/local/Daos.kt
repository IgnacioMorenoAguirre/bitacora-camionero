package com.sistemasdeinformacion.proyecto.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.sistemasdeinformacion.proyecto.data.model.EstadoViaje
import kotlinx.coroutines.flow.Flow

@Dao
interface ViajeDao {
    @Query("SELECT * FROM viajes WHERE choferId = :choferId AND eliminado = 0 ORDER BY fechaSalida DESC")
    fun observarDelChofer(choferId: String): Flow<List<ViajeEntity>>

    @Query("SELECT * FROM viajes WHERE choferId = :choferId AND estado = :enCurso AND eliminado = 0 ORDER BY fechaSalida DESC LIMIT 1")
    fun observarEnCurso(choferId: String, enCurso: EstadoViaje = EstadoViaje.EN_CURSO): Flow<ViajeEntity?>

    @Query("SELECT * FROM viajes WHERE choferId = :choferId AND estado = :enCurso AND eliminado = 0 LIMIT 1")
    suspend fun obtenerEnCurso(choferId: String, enCurso: EstadoViaje = EstadoViaje.EN_CURSO): ViajeEntity?

    @Query("SELECT * FROM viajes WHERE id = :id")
    fun observar(id: String): Flow<ViajeEntity?>

    @Query("SELECT * FROM viajes WHERE id = :id")
    suspend fun obtener(id: String): ViajeEntity?

    @Upsert
    suspend fun guardar(v: ViajeEntity)
}

@Dao
interface RegistroDao {
    @Query("SELECT * FROM eventos WHERE viajeId = :viajeId AND eliminado = 0 ORDER BY fechaHora")
    fun observarEventos(viajeId: String): Flow<List<EventoEntity>>

    @Query("SELECT * FROM eventos WHERE viajeId = :viajeId AND eliminado = 0 ORDER BY fechaHora")
    suspend fun eventos(viajeId: String): List<EventoEntity>

    @Query("SELECT * FROM gastos WHERE viajeId = :viajeId AND eliminado = 0 ORDER BY fechaHora")
    fun observarGastos(viajeId: String): Flow<List<GastoEntity>>

    @Query("SELECT * FROM novedades WHERE viajeId = :viajeId AND eliminado = 0 ORDER BY fechaHora")
    fun observarNovedades(viajeId: String): Flow<List<NovedadEntity>>

    @Query("SELECT * FROM adjuntos WHERE viajeId = :viajeId AND eliminado = 0 ORDER BY fechaHora")
    fun observarAdjuntos(viajeId: String): Flow<List<AdjuntoEntity>>

    @Query("SELECT * FROM revisiones WHERE viajeId = :viajeId LIMIT 1")
    fun observarRevision(viajeId: String): Flow<RevisionEntity?>

    @Query("SELECT * FROM eventos WHERE id = :id")
    suspend fun evento(id: String): EventoEntity?

    @Query(
        "SELECT e.* FROM eventos e JOIN viajes v ON v.id = e.viajeId " +
            "WHERE v.choferId = :choferId AND e.eliminado = 0 ORDER BY e.fechaHora"
    )
    fun observarEventosDelChofer(choferId: String): Flow<List<EventoEntity>>

    @Query(
        "SELECT g.* FROM gastos g JOIN viajes v ON v.id = g.viajeId " +
            "WHERE v.choferId = :choferId AND g.eliminado = 0 AND g.fechaHora BETWEEN :desde AND :hasta"
    )
    fun observarGastosDelPeriodo(choferId: String, desde: Long, hasta: Long): Flow<List<GastoEntity>>

    @Upsert suspend fun guardar(e: EventoEntity)
    @Upsert suspend fun guardar(g: GastoEntity)
    @Upsert suspend fun guardar(n: NovedadEntity)
    @Upsert suspend fun guardar(r: RevisionEntity)
    @Upsert suspend fun guardar(a: AdjuntoEntity)
}

@Dao
interface CamionDao {
    @Query("SELECT * FROM camiones ORDER BY patente")
    fun observarTodos(): Flow<List<CamionEntity>>

    @Query("SELECT * FROM camiones WHERE id = :id")
    suspend fun obtener(id: String): CamionEntity?

    @Query("DELETE FROM camiones")
    suspend fun borrarTodos()

    @Upsert
    suspend fun guardar(camiones: List<CamionEntity>)

    @Transaction
    suspend fun reemplazar(camiones: List<CamionEntity>) {
        borrarTodos()
        guardar(camiones)
    }
}

/** Consultas de la cola de sincronización. */
@Dao
interface SyncDao {
    @Query("SELECT * FROM viajes WHERE syncPendiente = 1") suspend fun viajesPendientes(): List<ViajeEntity>
    @Query("SELECT * FROM revisiones WHERE syncPendiente = 1") suspend fun revisionesPendientes(): List<RevisionEntity>
    @Query("SELECT * FROM eventos WHERE syncPendiente = 1") suspend fun eventosPendientes(): List<EventoEntity>
    @Query("SELECT * FROM gastos WHERE syncPendiente = 1") suspend fun gastosPendientes(): List<GastoEntity>
    @Query("SELECT * FROM novedades WHERE syncPendiente = 1") suspend fun novedadesPendientes(): List<NovedadEntity>
    @Query("SELECT * FROM adjuntos WHERE syncPendiente = 1") suspend fun adjuntosPendientes(): List<AdjuntoEntity>

    // Solo se marca como enviado si no cambió desde que empezó el envío.
    @Query("UPDATE viajes SET syncPendiente = 0 WHERE id = :id AND modificadoLocal = :modificadoLocal")
    suspend fun marcarViajeEnviado(id: String, modificadoLocal: Long)
    @Query("UPDATE revisiones SET syncPendiente = 0 WHERE id = :id AND modificadoLocal = :modificadoLocal")
    suspend fun marcarRevisionEnviada(id: String, modificadoLocal: Long)
    @Query("UPDATE eventos SET syncPendiente = 0 WHERE id = :id AND modificadoLocal = :modificadoLocal")
    suspend fun marcarEventoEnviado(id: String, modificadoLocal: Long)
    @Query("UPDATE gastos SET syncPendiente = 0 WHERE id = :id AND modificadoLocal = :modificadoLocal")
    suspend fun marcarGastoEnviado(id: String, modificadoLocal: Long)
    @Query("UPDATE novedades SET syncPendiente = 0 WHERE id = :id AND modificadoLocal = :modificadoLocal")
    suspend fun marcarNovedadEnviada(id: String, modificadoLocal: Long)
    @Query("UPDATE adjuntos SET syncPendiente = 0 WHERE id = :id AND modificadoLocal = :modificadoLocal")
    suspend fun marcarAdjuntoEnviado(id: String, modificadoLocal: Long)

    @Query(
        "SELECT (SELECT COUNT(*) FROM viajes WHERE syncPendiente = 1) + " +
            "(SELECT COUNT(*) FROM revisiones WHERE syncPendiente = 1) + " +
            "(SELECT COUNT(*) FROM eventos WHERE syncPendiente = 1) + " +
            "(SELECT COUNT(*) FROM gastos WHERE syncPendiente = 1) + " +
            "(SELECT COUNT(*) FROM novedades WHERE syncPendiente = 1) + " +
            "(SELECT COUNT(*) FROM adjuntos WHERE syncPendiente = 1)"
    )
    fun contarPendientes(): Flow<Int>
}
