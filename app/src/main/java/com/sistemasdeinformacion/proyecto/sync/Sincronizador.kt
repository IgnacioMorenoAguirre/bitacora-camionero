package com.sistemasdeinformacion.proyecto.sync

import com.sistemasdeinformacion.proyecto.data.local.AdjuntoEntity
import com.sistemasdeinformacion.proyecto.data.local.EventoEntity
import com.sistemasdeinformacion.proyecto.data.local.GastoEntity
import com.sistemasdeinformacion.proyecto.data.local.NovedadEntity
import com.sistemasdeinformacion.proyecto.data.local.RevisionEntity
import com.sistemasdeinformacion.proyecto.data.local.Sincronizable
import com.sistemasdeinformacion.proyecto.data.local.ViajeEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

sealed interface ResultadoSync {
    /** [rechazados]: viajes que el servidor no aceptó por estar aprobados (se restauró su versión). */
    data class Ok(val enviados: Int, val recibidos: Int, val rechazados: List<String>) : ResultadoSync
    data object SinConexion : ResultadoSync

    /**
     * El servidor rechazó algo; lo demás se envió y la bajada se aplicó igual. [reintentar] es true si
     * algún error parece pasajero (5xx, sin código): conviene reintentar pronto. Un rechazo permanente
     * (permisos, dato inválido) no cambia por insistir: espera al próximo guardado o al periódico.
     */
    data class Falla(
        val mensaje: String,
        val rechazados: List<String> = emptyList(),
        val reintentar: Boolean = false,
    ) : ResultadoSync
}

/**
 * Sube lo pendiente y baja lo que cambió en el servidor (spec, sección 5):
 *  - upsert por id, así reintentar nunca duplica;
 *  - orden viajes → revisiones → eventos → gastos → novedades → adjuntos (llaves foráneas);
 *  - cada fila se marca como enviada apenas el servidor la acepta, con la condición de que no haya
 *    cambiado mientras viajaba (si falla a mitad, lo ya enviado no se repite);
 *  - una fila que el servidor rechaza (RLS, CHECK, sesión vencida…) queda pendiente sin frenar a las
 *    demás ni la bajada: así igual llegan las aprobaciones del administrador;
 *  - si el viaje o uno de sus registros choca con la aprobación del administrador (VIAJE_APROBADO),
 *    el viaje entero se reemplaza por la versión del servidor y se avisa al chofer;
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

    /** Corre [bloque] cuando no hay una sync a medias (p. ej. borrar los datos al cerrar sesión). */
    suspend fun sinSincronizar(bloque: suspend () -> Unit) = enCurso.withLock { bloque() }

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
            ResultadoSync.Falla(e.mensajeSyncParaChofer(), reintentar = e.tipoErrorSync() == TipoErrorSync.PASAJERO)
        }
    }

    private suspend fun ejecutar(chofer: String): ResultadoSync {
        val lote = local.pendientes()
        val fallas = Fallas()
        var enviados = 0
        val noSubidos = mutableSetOf<String>() // viajes rechazados o con error: sus registros esperan

        // Viajes de a uno: si uno ya fue aprobado o falla, no frena a los demás.
        for (v in lote.viajes) {
            var resultado: ResultadoFila? = null
            fallas.anotar(v.id, intentar { resultado = remoto.subirViaje(v) })
            when (resultado) {
                ResultadoFila.OK -> { local.marcarEnviado(v); enviados++ }
                ResultadoFila.RECHAZADO_APROBADO -> { fallas.rechazados += v.id; noSubidos += v.id }
                null -> noSubidos += v.id
            }
        }

        // Los registros de un viaje rechazado no se suben: se descartan junto con él.
        enviados += subir(Tablas.REVISIONES, lote.revisiones.filter { it.viajeId !in noSubidos }, fallas)
        enviados += subir(Tablas.EVENTOS, lote.eventos.filter { it.viajeId !in noSubidos }, fallas)
        enviados += subir(Tablas.GASTOS, lote.gastos.filter { it.viajeId !in noSubidos }, fallas)
        enviados += subir(Tablas.NOVEDADES, lote.novedades.filter { it.viajeId !in noSubidos }, fallas)

        // Adjuntos: primero el archivo y su ruta queda guardada en el teléfono; después la fila.
        // Si el archivo local desapareció ya no hay cómo recuperarlo: se sube la fila sin él (el
        // administrador ve que falta la imagen), así no queda pendiente para siempre.
        for (a in lote.adjuntos.filter { it.viajeId !in noSubidos }) {
            val error = intentar {
                val fila = if (a.rutaRemota == null && !a.eliminado) {
                    try {
                        val ruta = remoto.subirArchivo(a)
                        local.guardarRutaRemota(a.id, ruta)
                        a.copy(rutaRemota = ruta)
                    } catch (_: ArchivoNoDisponibleException) {
                        a
                    }
                } else {
                    a
                }
                remoto.subirLote(Tablas.ADJUNTOS, listOf(fila))
                local.marcarEnviado(a)
                enviados++
            }
            fallas.anotar(a.viajeId, error)
        }

        // Con rechazos se baja todo: la versión aprobada puede ser más antigua que la marca.
        // El descarte local ocurre recién con la bajada en la mano; si esta falla, el viaje sigue
        // pendiente y el próximo intento repite el rechazo y la restauración.
        val rechazados = fallas.rechazados.toList()
        val anterior = marca()
        val cambios = remoto.bajarDesde(if (rechazados.isEmpty()) anterior else null, chofer)
        rechazados.forEach { local.descartarViaje(it) }
        local.aplicarRemotos(cambios)
        // La marca solo avanza (el destino puede devolver filas algo anteriores a la marca pedida).
        cambios.marca?.takeIf { marcaMaxima(listOf(anterior, it)) != anterior }?.let { guardarMarca(it) }

        val primera = fallas.errores.firstOrNull()
            ?: return ResultadoSync.Ok(enviados, cambios.total, rechazados)
        return ResultadoSync.Falla(
            mensaje = primera.mensajeSyncParaChofer(),
            rechazados = rechazados,
            reintentar = fallas.errores.any { it.tipoErrorSync() == TipoErrorSync.PASAJERO },
        )
    }

    /**
     * Sube la tabla en un lote; si el servidor rechaza el lote, prueba fila por fila para que solo
     * queden pendientes las que no acepta.
     */
    private suspend fun subir(tabla: String, items: List<Sincronizable>, fallas: Fallas): Int {
        if (items.isEmpty()) return 0
        if (intentar { remoto.subirLote(tabla, items) } == null) {
            items.forEach { local.marcarEnviado(it) }
            return items.size
        }
        var enviados = 0
        for (item in items) {
            val error = intentar { remoto.subirLote(tabla, listOf(item)) }
            if (error == null) {
                local.marcarEnviado(item)
                enviados++
            } else {
                fallas.anotar(viajeDe(item), error)
            }
        }
        return enviados
    }

    /**
     * Ejecuta [bloque] y devuelve el error si el servidor lo rechazó (null si salió bien). La falta de
     * red (IOException) no se aísla: corta la sincronización completa.
     */
    private inline fun intentar(bloque: () -> Unit): Exception? = try {
        bloque()
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        throw e
    } catch (e: Exception) {
        e
    }

    /** Lo que el servidor rechazó en esta vuelta. */
    private class Fallas {
        val errores = mutableListOf<Exception>()

        /** Viajes que chocaron con la aprobación del administrador (por su propia fila o por un registro). */
        val rechazados = linkedSetOf<String>()

        fun anotar(viajeId: String?, error: Exception?) {
            if (error == null) return
            if (viajeId != null && error.tipoErrorSync() == TipoErrorSync.VIAJE_APROBADO) rechazados += viajeId else errores += error
        }
    }

    private fun viajeDe(item: Sincronizable): String? = when (item) {
        is ViajeEntity -> item.id
        is RevisionEntity -> item.viajeId
        is EventoEntity -> item.viajeId
        is GastoEntity -> item.viajeId
        is NovedadEntity -> item.viajeId
        is AdjuntoEntity -> item.viajeId
        else -> null
    }
}
