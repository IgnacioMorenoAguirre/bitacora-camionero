package com.sistemasdeinformacion.proyecto.feature.ruta

import com.sistemasdeinformacion.proyecto.data.local.todoOk
import com.sistemasdeinformacion.proyecto.data.model.EstadoViaje
import com.sistemasdeinformacion.proyecto.data.model.Gravedad
import com.sistemasdeinformacion.proyecto.data.model.TipoEvento
import com.sistemasdeinformacion.proyecto.data.model.TipoGasto
import com.sistemasdeinformacion.proyecto.data.repo.DetalleViaje
import com.sistemasdeinformacion.proyecto.domain.Formatos
import com.sistemasdeinformacion.proyecto.ui.componentes.Hito

/** Mezcla eventos, gastos y novedades en una sola línea de tiempo ordenada por hora. */
fun construirHitos(d: DetalleViaje, ahora: Long, hora: (Long) -> String = { Formatos.hora(it) }): List<Hito> {
    val v = d.viaje ?: return emptyList()
    val eventosOrdenados = d.eventos.sortedBy { it.fechaHora }
    // Los mojones muestran km recorridos desde la salida (0, 142, 214…), no el odómetro completo.
    val base = v.kmInicial
    fun relativo(km: Int?): Int? = if (km == null || base == null) km else (km - base).coerceAtLeast(0)
    fun kmEn(t: Long): Int? = relativo(eventosOrdenados.lastOrNull { it.fechaHora <= t && it.km != null }?.km)

    data class Item(val t: Long, val hito: Hito)
    val items = mutableListOf<Item>()

    eventosOrdenados.forEach { e ->
        val (titulo, detalle) = when (e.tipo) {
            TipoEvento.INICIO -> "Inicio en ${v.origen}" to when {
                d.revision == null -> null
                d.revision.todoOk -> "Revisión del camión completa"
                else -> "Revisión con observaciones" + (d.revision.comentario?.let { ": $it" } ?: "")
            }
            TipoEvento.DESCANSO -> "Descanso" to (e.fechaFin?.let { "Duró ${Formatos.duracion(it - e.fechaHora)}" } ?: if (ahora - e.fechaHora < 60_000) "En curso, recién comenzado" else "En curso, desde hace ${Formatos.duracion(ahora - e.fechaHora)}")
            TipoEvento.ENTREGA -> "Entrega en ${v.destino}" to e.nota
            TipoEvento.FIN -> "Fin del viaje" to null
            else -> e.tipo.etiqueta to null
        }
        val extra = listOfNotNull(detalle, e.nota.takeIf { e.tipo != TipoEvento.ENTREGA }, e.direccion).joinToString(". ").ifBlank { null }
        items += Item(e.fechaHora, Hito("e-${e.id}", relativo(e.km), hora(e.fechaHora), titulo, extra))
    }
    d.gastos.forEach { g ->
        val detalle = buildList {
            if (g.tipo == TipoGasto.COMBUSTIBLE && g.litros != null) add(Formatos.litros(g.litros))
            add(Formatos.pesos(g.monto))
        }.joinToString(", ")
        val titulo = if (g.tipo == TipoGasto.PEAJE && !g.descripcion.isNullOrBlank()) "Peaje ${g.descripcion}" else g.tipo.etiqueta
        items += Item(g.fechaHora, Hito("g-${g.id}", kmEn(g.fechaHora), hora(g.fechaHora), titulo, detalle))
    }
    d.novedades.forEach { n ->
        items += Item(
            n.fechaHora,
            Hito("n-${n.id}", kmEn(n.fechaHora), hora(n.fechaHora), "${n.tipo.etiqueta}, gravedad ${n.gravedad.etiqueta.lowercase()}", n.descripcion, alerta = n.gravedad == Gravedad.ALTA),
        )
    }
    val ordenados = items.sortedBy { it.t }.map { it.hito }
    return if (v.estado == EstadoViaje.EN_CURSO && ordenados.isNotEmpty()) {
        ordenados.dropLast(1) + ordenados.last().copy(actual = true)
    } else ordenados
}
