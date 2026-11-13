package com.sistemasdeinformacion.proyecto.sync

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** Programa la sincronización y expone su estado para la interfaz. */
interface ProgramadorSync {
    /** Se llama después de cada guardado local. */
    fun alGuardar()
    val estado: Flow<EstadoSync>
}

/**
 * Programador con WorkManager: los envíos sobreviven al cierre de la app y esperan a que haya red.
 *  - [alGuardar] encola un trabajo único con política KEEP: si ya hay uno esperando la red, ese
 *    mismo envía lo nuevo (lee los pendientes al correr). Lo que se guarde mientras corre lo toma
 *    la vuelta extra del [SyncWorker] o el trabajo periódico.
 *  - [periodico] sincroniza cada 15 minutos (mínimo de Android), así llegan las aprobaciones del
 *    administrador aunque el chofer no guarde nada.
 *  - Al volver la red se reemplaza el trabajo inmediato (REPLACE): si estaba esperando un reintento
 *    con backoff, corre al tiro en vez de esperar a que venza la espera (spec 5, punto 10).
 */
class ProgramadorSyncWorkManager(
    context: Context,
    pendientes: Flow<Int>,
    conectado: Flow<Boolean>,
    ultimoError: Flow<String?>,
    viajesRestaurados: Flow<List<String>>,
    alcance: CoroutineScope,
) : ProgramadorSync {

    private val workManager = WorkManager.getInstance(context)

    init {
        alcance.launch { volvioLaRed(conectado).collect { sincronizarAhora() } }
    }

    override fun alGuardar() {
        workManager.enqueueUniqueWork(SyncWorker.TRABAJO_INMEDIATO, ExistingWorkPolicy.KEEP, SyncWorker.solicitudInmediata())
    }

    private fun sincronizarAhora() {
        workManager.enqueueUniqueWork(SyncWorker.TRABAJO_INMEDIATO, ExistingWorkPolicy.REPLACE, SyncWorker.solicitudInmediata())
    }

    fun periodico() {
        workManager.enqueueUniquePeriodicWork(
            SyncWorker.TRABAJO_PERIODICO, ExistingPeriodicWorkPolicy.KEEP, SyncWorker.solicitudPeriodica(),
        )
    }

    private val enviando: Flow<Boolean> = combine(
        workManager.getWorkInfosForUniqueWorkFlow(SyncWorker.TRABAJO_INMEDIATO),
        workManager.getWorkInfosForUniqueWorkFlow(SyncWorker.TRABAJO_PERIODICO),
    ) { a, b -> (a + b).any { it.state == WorkInfo.State.RUNNING } }

    override val estado: Flow<EstadoSync> =
        combine(pendientes, conectado, enviando, ultimoError, viajesRestaurados) { p, c, e, error, restaurados ->
            EstadoSync(pendientes = p, enviando = e, conectado = c, ultimoError = error, viajesRestaurados = restaurados)
        }
}

/** Emite cada vez que la conexión pasa de "sin red" a "con red" (no al empezar ya conectado). */
fun volvioLaRed(conectado: Flow<Boolean>): Flow<Unit> = flow {
    var antes: Boolean? = null
    conectado.collect { ahora ->
        if (antes == false && ahora) emit(Unit)
        antes = ahora
    }
}

/** Sin Supabase configurado: solo informa pendientes y conexión (los datos quedan en el teléfono). */
class ProgramadorSyncLocal(private val pendientes: Flow<Int>, private val conectado: Flow<Boolean>) : ProgramadorSync {
    override fun alGuardar() = Unit
    override val estado: Flow<EstadoSync> = combine(pendientes, conectado) { p, c -> EstadoSync(pendientes = p, conectado = c) }
}

object ProgramadorSyncVacio : ProgramadorSync {
    override fun alGuardar() = Unit
    override val estado: Flow<EstadoSync> = flowOf(EstadoSync())
}
