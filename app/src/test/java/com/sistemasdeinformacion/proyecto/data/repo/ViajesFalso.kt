package com.sistemasdeinformacion.proyecto.data.repo

import com.sistemasdeinformacion.proyecto.data.local.CamionEntity
import com.sistemasdeinformacion.proyecto.data.local.EventoEntity
import com.sistemasdeinformacion.proyecto.data.local.GastoEntity
import com.sistemasdeinformacion.proyecto.data.local.ViajeEntity
import com.sistemasdeinformacion.proyecto.data.model.EstadoViaje
import com.sistemasdeinformacion.proyecto.data.model.Gravedad
import com.sistemasdeinformacion.proyecto.data.model.TipoEvento
import com.sistemasdeinformacion.proyecto.data.model.TipoGasto
import com.sistemasdeinformacion.proyecto.data.model.TipoNovedad
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class Finalizacion(val viajeId: String, val kmFinal: Int, val receptor: String?, val adjunto: NuevoAdjunto?)

/** Repositorio del chofer en memoria: un solo viaje, y registra lo que se le pide guardar. */
class ViajesFalso(detalle: DetalleViaje) : ViajesRepositorio {
    val estado = MutableStateFlow(detalle)
    val finalizaciones = mutableListOf<Finalizacion>()
    val ediciones = mutableListOf<Pair<String, DatosEdicionViaje>>()
    var falla: Throwable? = null

    override fun viajes(): Flow<List<ViajeEntity>> = estado.map { listOfNotNull(it.viaje) }
    override fun enCurso(): Flow<ViajeEntity?> = estado.map { d -> d.viaje?.takeIf { it.estado == EstadoViaje.EN_CURSO } }
    override fun detalle(id: String): Flow<DetalleViaje> = estado
    override fun eventosDelChofer(): Flow<List<EventoEntity>> = estado.map { it.eventos }
    override fun gastosDelPeriodo(desde: Long, hasta: Long): Flow<List<GastoEntity>> = estado.map { it.gastos }
    override fun camiones(): Flow<List<CamionEntity>> = flowOf(emptyList())
    override suspend fun crearViaje(datos: DatosNuevoViaje, revision: DatosRevision): Result<String> = Result.success("v1")
    override suspend fun registrarEvento(viajeId: String, tipo: TipoEvento, km: Int?, nota: String?): Result<String> = Result.success("e1")
    override suspend fun cerrarDescanso(eventoId: String): Result<Unit> = Result.success(Unit)
    override suspend fun registrarGasto(viajeId: String, tipo: TipoGasto, monto: Long, litros: Double?, descripcion: String?) = Result.success("g1")
    override suspend fun registrarNovedad(viajeId: String, tipo: TipoNovedad, gravedad: Gravedad, descripcion: String) = Result.success("n1")

    override suspend fun finalizar(viajeId: String, kmFinal: Int, receptor: String?, adjunto: NuevoAdjunto?): Result<Unit> {
        falla?.let { return Result.failure(it) }
        finalizaciones += Finalizacion(viajeId, kmFinal, receptor, adjunto)
        return Result.success(Unit)
    }

    override suspend fun editarViaje(viajeId: String, datos: DatosEdicionViaje): Result<Unit> {
        falla?.let { return Result.failure(it) }
        ediciones += viajeId to datos
        return Result.success(Unit)
    }
}
