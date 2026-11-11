package com.sistemasdeinformacion.proyecto.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Estado visible de la sincronización (lo muestra el IndicadorSync). */
data class EstadoSync(
    val pendientes: Int = 0,
    val enviando: Boolean = false,
    val conectado: Boolean = false,
    val ultimoError: String? = null,
    val viajesRestaurados: List<String> = emptyList(),
)

/**
 * Programa la sincronización. La implementación con WorkManager llega en la Task 6;
 * mientras tanto [ProgramadorSyncLocal] solo informa los pendientes.
 */
interface ProgramadorSync {
    fun alGuardar()
    val estado: Flow<EstadoSync>
}

class ProgramadorSyncLocal(private val pendientes: Flow<Int>, private val conectado: Flow<Boolean>) : ProgramadorSync {
    override fun alGuardar() = Unit
    override val estado: Flow<EstadoSync> =
        kotlinx.coroutines.flow.combine(pendientes, conectado) { p, c -> EstadoSync(pendientes = p, conectado = c) }
}

object ProgramadorSyncVacio : ProgramadorSync {
    override fun alGuardar() = Unit
    override val estado: Flow<EstadoSync> = flowOf(EstadoSync())
}
