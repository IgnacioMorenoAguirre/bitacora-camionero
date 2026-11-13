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

    /** El servidor rechazó algo; lo demás se envió y la bajada se aplicó igual. */
    data class Falla(val mensaje: String, val rechazados: List<String> = emptyList()) : ResultadoSync
}

/**
 * Sube lo pendiente y baja lo que cambió en el servidor (spec, sección 5):
 *  - upsert por id, así reintentar nunca duplica;
 *  - orden viajes → revisiones → eventos → gastos → novedades → adjuntos (llaves foráneas);
 *  - cada fila se marca como enviada apenas el servidor la acepta, con la condición de que no haya
 *    cambiado mientras viajaba (si falla a mitad, lo ya enviado no se repite);
 *  - una fila que el servidor rechaza (RLS, CHECK, sesión vencida…) queda pendiente sin frenar a las
 *    demás ni la bajada: así igual llegan las aprobaciones del administrador;
 *  - la bajada no pisa filas que en el teléfono siguen pendientes.
 * Sin red (IOException) se corta todo y se informa SinConexion.
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
            ResultadoSync.Falla(mensajeCorto(e))
        }
    }

    private suspend fun ejecutar(chofer: String): ResultadoSync {
        val lote = local.pendientes()
        var enviados = 0
        val errores = mutableListOf<String>()
        val rechazados = mutableListOf<String>()
        val noSubidos = mutableSetOf<String>() // viajes rechazados o con error: sus registros esperan

        // Viajes de a uno: si uno ya fue aprobado o falla, no frena a los demás.
        for (v in lote.viajes) {
            val resultado = aislado(errores) { remoto.subirViaje(v) }
            when (resultado) {
                ResultadoFila.OK -> { local.marcarEnviado(v); enviados++ }
                ResultadoFila.RECHAZADO_APROBADO -> { rechazados += v.id; noSubidos += v.id }
                null -> noSubidos += v.id
            }
        }

        // Los registros de un viaje rechazado no se suben: se descartan junto con él.
        enviados += subir(Tablas.REVISIONES, lote.revisiones.filter { it.viajeId !in noSubidos }, errores)
        enviados += subir(Tablas.EVENTOS, lote.eventos.filter { it.viajeId !in noSubidos }, errores)
        enviados += subir(Tablas.GASTOS, lote.gastos.filter { it.viajeId !in noSubidos }, errores)
        enviados += subir(Tablas.NOVEDADES, lote.novedades.filter { it.viajeId !in noSubidos }, errores)

        // Adjuntos: primero el archivo y su ruta queda guardada en el teléfono; después la fila.
        // Si el archivo local desapareció, el adjunto queda pendiente sin frenar lo demás.
        for (a in lote.adjuntos.filter { it.viajeId !in noSubidos }) {
            aislado(errores) {
                val fila = if (a.rutaRemota == null && !a.eliminado) {
                    val ruta = try {
                        remoto.subirArchivo(a)
                    } catch (_: ArchivoNoDisponibleException) {
                        return@aislado
                    }
                    local.guardarRutaRemota(a.id, ruta)
                    a.copy(rutaRemota = ruta)
                } else {
                    a
                }
                remoto.subirLote(Tablas.ADJUNTOS, listOf(fila))
                local.marcarEnviado(a)
                enviados++
            }
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

        return if (errores.isEmpty()) {
            ResultadoSync.Ok(enviados, cambios.total, rechazados)
        } else {
            ResultadoSync.Falla(errores.first(), rechazados)
        }
    }

    /**
     * Sube la tabla en un lote; si el servidor rechaza el lote, prueba fila por fila para que solo
     * queden pendientes las que no acepta.
     */
    private suspend fun subir(tabla: String, items: List<Sincronizable>, errores: MutableList<String>): Int {
        if (items.isEmpty()) return 0
        if (aislado(errores) { remoto.subirLote(tabla, items) } != null) {
            items.forEach { local.marcarEnviado(it) }
            return items.size
        }
        var enviados = 0
        for (item in items) {
            if (aislado(null) { remoto.subirLote(tabla, listOf(item)) } != null) {
                local.marcarEnviado(item)
                enviados++
            }
        }
        return enviados
    }

    /**
     * Ejecuta [bloque]; si el servidor lo rechaza devuelve null y anota el error. La falta de red
     * (IOException) no se aísla: corta la sincronización completa.
     */
    private inline fun <T> aislado(errores: MutableList<String>?, bloque: () -> T): T? = try {
        bloque()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        throw e
    } catch (e: Exception) {
        errores?.add(mensajeCorto(e))
        null
    }

    /**
     * Solo la primera línea: los errores de supabase-kt agregan la URL y los encabezados de la
     * petición (con el token), que no deben llegar a la pantalla.
     */
    private fun mensajeCorto(e: Exception): String =
        e.message?.lineSequence()?.first()?.trim()?.ifBlank { null } ?: e::class.java.simpleName
}
