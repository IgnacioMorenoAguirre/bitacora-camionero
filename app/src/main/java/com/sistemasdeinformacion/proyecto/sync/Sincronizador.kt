package com.sistemasdeinformacion.proyecto.sync

import com.sistemasdeinformacion.proyecto.data.local.Sincronizable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

sealed interface ResultadoSync {
    /** [rechazados]: viajes que el servidor no aceptó por estar aprobados (se restauró su versión). */
    data class Ok(val enviados: Int, val recibidos: Int, val rechazados: List<String>) : ResultadoSync
    data object SinConexion : ResultadoSync
    data class Falla(val mensaje: String) : ResultadoSync
}

/**
 * Sube lo pendiente y baja lo que cambió en el servidor (spec, sección 5):
 *  - upsert por id, así reintentar nunca duplica;
 *  - orden viajes → revisiones → eventos → gastos → novedades → adjuntos (llaves foráneas);
 *  - cada fila se marca como enviada apenas el servidor la acepta, con la condición de que no haya
 *    cambiado mientras viajaba (si falla a mitad, lo ya enviado no se repite);
 *  - la bajada no pisa filas que en el teléfono siguen pendientes.
 */
class Sincronizador(
    private val local: OrigenLocal,
    private val remoto: DestinoRemoto,
    private val marca: suspend () -> String?,
    private val guardarMarca: suspend (String) -> Unit,
    private val choferId: suspend () -> String?,
) {
    /** El trabajo inmediato y el periódico pueden coincidir: se sincroniza de a uno. */
    private val enCurso = Mutex()

    suspend fun sincronizar(): ResultadoSync = enCurso.withLock {
        val chofer = choferId() ?: return@withLock ResultadoSync.Ok(0, 0, emptyList())
        try {
            ejecutar(chofer)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            // Incluye HttpRequestException de supabase-kt: no hubo respuesta del servidor.
            ResultadoSync.SinConexion
        } catch (e: Exception) {
            // Solo la primera línea: los errores de supabase-kt agregan la URL y los encabezados de la
            // petición (con el token), que no deben llegar a la pantalla.
            val mensaje = e.message?.lineSequence()?.first()?.trim()
            ResultadoSync.Falla(mensaje?.ifBlank { null } ?: e::class.java.simpleName)
        }
    }

    private suspend fun ejecutar(chofer: String): ResultadoSync.Ok {
        val lote = local.pendientes()
        var enviados = 0
        val rechazados = mutableListOf<String>()

        // Viajes de a uno: si uno ya fue aprobado, el rechazo no frena a los demás.
        for (v in lote.viajes) {
            when (remoto.subirViaje(v)) {
                ResultadoFila.OK -> { local.marcarEnviado(v); enviados++ }
                ResultadoFila.RECHAZADO_APROBADO -> rechazados += v.id
            }
        }

        // Los registros de un viaje rechazado no se suben: se descartan junto con él.
        enviados += subir(Tablas.REVISIONES, lote.revisiones.filter { it.viajeId !in rechazados })
        enviados += subir(Tablas.EVENTOS, lote.eventos.filter { it.viajeId !in rechazados })
        enviados += subir(Tablas.GASTOS, lote.gastos.filter { it.viajeId !in rechazados })
        enviados += subir(Tablas.NOVEDADES, lote.novedades.filter { it.viajeId !in rechazados })

        // Adjuntos: primero el archivo; la fila se sube ya con su ruta remota. Si el archivo local
        // desapareció, el adjunto queda pendiente sin frenar el resto ni la bajada.
        for (a in lote.adjuntos.filter { it.viajeId !in rechazados }) {
            val fila = if (a.rutaRemota == null && !a.eliminado) {
                val ruta = try { remoto.subirArchivo(a) } catch (_: ArchivoNoDisponibleException) { continue }
                a.copy(rutaRemota = ruta)
            } else {
                a
            }
            remoto.subirLote(Tablas.ADJUNTOS, listOf(fila))
            local.marcarEnviado(a)
            enviados++
        }

        // Con rechazos se baja todo: la versión aprobada puede ser más antigua que la marca.
        // El descarte local ocurre recién con la bajada en la mano; si esta falla, el viaje sigue
        // pendiente y el próximo intento repite el rechazo y la restauración.
        val anterior = marca()
        val cambios = remoto.bajarDesde(if (rechazados.isEmpty()) anterior else null, chofer)
        rechazados.forEach { local.descartarViaje(it) }
        local.aplicarRemotos(cambios)
        // La marca solo avanza (el destino puede devolver filas algo anteriores a la marca pedida).
        cambios.marca?.takeIf { marcaMaxima(listOf(anterior, it)) != anterior }?.let { guardarMarca(it) }

        return ResultadoSync.Ok(enviados, cambios.total, rechazados)
    }

    private suspend fun subir(tabla: String, items: List<Sincronizable>): Int {
        if (items.isEmpty()) return 0
        remoto.subirLote(tabla, items)
        items.forEach { local.marcarEnviado(it) }
        return items.size
    }
}
