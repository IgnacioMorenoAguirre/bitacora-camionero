package com.sistemasdeinformacion.proyecto.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import com.sistemasdeinformacion.proyecto.BitacoraApp
import kotlinx.coroutines.flow.first
import java.time.Duration

/**
 * Ejecuta la sincronización en segundo plano. Sin conexión o con falla pide reintento: WorkManager
 * espera con backoff exponencial y, por la restricción de red, corre apenas vuelve la señal.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as BitacoraApp).container
        val sincronizador = container.sincronizador ?: return Result.success()

        // Si el chofer guardó algo mientras se enviaba, se da otra vuelta en vez de esperar al periódico.
        repeat(MAX_VUELTAS) {
            when (val r = sincronizador.sincronizar()) {
                is ResultadoSync.Ok -> {
                    container.errorSync.value = null
                    container.preferencias.agregarRestaurados(r.rechazados)
                    if (r.enviados == 0 || container.db.syncDao().contarPendientes().first() == 0) return Result.success()
                }
                ResultadoSync.SinConexion -> return Result.retry()
                is ResultadoSync.Falla -> {
                    // Lo demás ya se envió y la bajada se aplicó; solo lo rechazado espera el reintento.
                    container.errorSync.value = r.mensaje
                    container.preferencias.agregarRestaurados(r.rechazados)
                    return Result.retry()
                }
            }
        }
        return Result.success()
    }

    companion object {
        const val TRABAJO_INMEDIATO = "sync-inmediato"
        const val TRABAJO_PERIODICO = "sync-periodico"
        private const val MAX_VUELTAS = 3
        private val BACKOFF_INICIAL = Duration.ofSeconds(30)

        private val conRed = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun solicitudInmediata(): OneTimeWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(conRed)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_INICIAL)
            .build()

        fun solicitudPeriodica(): PeriodicWorkRequest = PeriodicWorkRequestBuilder<SyncWorker>(Duration.ofMinutes(15))
            .setConstraints(conRed)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_INICIAL)
            .build()
    }
}
