package com.sistemasdeinformacion.proyecto.sync

import com.sistemasdeinformacion.proyecto.data.local.AdjuntoEntity
import com.sistemasdeinformacion.proyecto.data.local.EventoEntity
import com.sistemasdeinformacion.proyecto.data.local.GastoEntity
import com.sistemasdeinformacion.proyecto.data.local.NovedadEntity
import com.sistemasdeinformacion.proyecto.data.local.RevisionEntity
import com.sistemasdeinformacion.proyecto.data.local.Sincronizable
import com.sistemasdeinformacion.proyecto.data.local.SyncDao
import com.sistemasdeinformacion.proyecto.data.local.ViajeEntity
import com.sistemasdeinformacion.proyecto.data.remote.aEntidad

/** La base local (Room) como origen de la sincronización. */
class OrigenLocalRoom(private val dao: SyncDao) : OrigenLocal {

    override suspend fun pendientes() = LotePendiente(
        viajes = dao.viajesPendientes(),
        revisiones = dao.revisionesPendientes(),
        eventos = dao.eventosPendientes(),
        gastos = dao.gastosPendientes(),
        novedades = dao.novedadesPendientes(),
        adjuntos = dao.adjuntosPendientes(),
    )

    override suspend fun marcarEnviado(item: Sincronizable) = when (item) {
        is ViajeEntity -> dao.marcarViajeEnviado(item.id, item.modificadoLocal)
        is RevisionEntity -> dao.marcarRevisionEnviada(item.id, item.modificadoLocal)
        is EventoEntity -> dao.marcarEventoEnviado(item.id, item.modificadoLocal)
        is GastoEntity -> dao.marcarGastoEnviado(item.id, item.modificadoLocal)
        is NovedadEntity -> dao.marcarNovedadEnviada(item.id, item.modificadoLocal)
        is AdjuntoEntity -> dao.marcarAdjuntoEnviado(item.id, item.modificadoLocal)
        else -> error("Fila sin tabla de sincronización: ${item::class.simpleName}")
    }

    override suspend fun aplicarRemotos(c: CambiosRemotos) = dao.aplicarRemotos(
        viajes = c.viajes.map { it.aEntidad() },
        revisiones = c.revisiones.map { it.aEntidad() },
        eventos = c.eventos.map { it.aEntidad() },
        gastos = c.gastos.map { it.aEntidad() },
        novedades = c.novedades.map { it.aEntidad() },
        adjuntos = c.adjuntos.map { it.aEntidad() },
    )

    override suspend fun descartarViaje(viajeId: String) = dao.borrarViaje(viajeId)
}
