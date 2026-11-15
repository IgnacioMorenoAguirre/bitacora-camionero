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

/** Qué hace el worker después de una vuelta de sincronización. */
internal enum class PasoSync { OTRA_VUELTA, LISTO, REINTENTAR }

/**
 * - Ok con pendientes: el chofer guardó algo mientras corría (KEEP no encola otro trabajo), se da
 *   otra vuelta en vez de esperar al periódico, aunque esta vuelta no haya enviado nada.
 * - Sin red o con un error pasajero del servidor: reintento con backoff.
 * - Rechazo permanente (permisos, dato inválido): lo demás ya se envió, así que se termina bien.
 *   Insistir no lo arregla y el backoff (hasta 5 h) atrasaría lo que el chofer guarde después; lo
 *   rechazado se vuelve a probar con el próximo guardado o el periódico, y el aviso queda a la vista.
 */
internal fun siguientePaso(r: ResultadoSync, pendientes: Int): PasoSync = when (r) {
    is ResultadoSync.Ok -> if (pendientes > 0) PasoSync.OTRA_VUELTA else PasoSync.LISTO
    ResultadoSync.SinConexion -> PasoSync.REINTENTAR
    is ResultadoSync.Falla -> if (r.reintentar) PasoSync.REINTENTAR else PasoSync.LISTO
}

/**
 * Ejecuta la sincronización en segundo plano. Sin conexión o con un error pasajero pide reintento:
 * WorkManager espera con backoff exponencial y, por la restricción de red, corre apenas vuelve la señal.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as BitacoraApp).container
        val sincronizador = container.sincronizador ?: return Result.success()

        repeat(MAX_VUELTAS) {
            val r = sincronizador.sincronizar()
            when (r) {
                is ResultadoSync.Ok -> {
                    container.errorSync.value = null
                    container.preferencias.agregarRestaurados(r.rechazados)
                }
                ResultadoSync.SinConexion -> Unit
                is ResultadoSync.Falla -> {
                    container.errorSync.value = r.mensaje
                    container.preferencias.agregarRestaurados(r.rechazados)
                }
            }
            val pendientes = if (r is ResultadoSync.Ok) container.db.syncDao().contarPendientes().first() else 0
            when (siguientePaso(r, pendientes)) {
                PasoSync.OTRA_VUELTA -> Unit
                PasoSync.LISTO -> return Result.success()
                PasoSync.REINTENTAR -> return Result.retry()
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
