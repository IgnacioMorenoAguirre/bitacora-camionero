package com.sistemasdeinformacion.proyecto.sync

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

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
 */
class ProgramadorSyncWorkManager(
    context: Context,
    pendientes: Flow<Int>,
    conectado: Flow<Boolean>,
    ultimoError: Flow<String?>,
    viajesRestaurados: Flow<List<String>>,
) : ProgramadorSync {

    private val workManager = WorkManager.getInstance(context)

    override fun alGuardar() {
        workManager.enqueueUniqueWork(SyncWorker.TRABAJO_INMEDIATO, ExistingWorkPolicy.KEEP, SyncWorker.solicitudInmediata())
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

/** Sin Supabase configurado: solo informa pendientes y conexión (los datos quedan en el teléfono). */
class ProgramadorSyncLocal(private val pendientes: Flow<Int>, private val conectado: Flow<Boolean>) : ProgramadorSync {
    override fun alGuardar() = Unit
    override val estado: Flow<EstadoSync> = combine(pendientes, conectado) { p, c -> EstadoSync(pendientes = p, conectado = c) }
}

object ProgramadorSyncVacio : ProgramadorSync {
    override fun alGuardar() = Unit
    override val estado: Flow<EstadoSync> = flowOf(EstadoSync())
}
