package com.sistemasdeinformacion.proyecto.data.remote

import com.sistemasdeinformacion.proyecto.data.local.AdjuntoEntity
import com.sistemasdeinformacion.proyecto.data.local.CamionEntity
import com.sistemasdeinformacion.proyecto.data.local.EventoEntity
import com.sistemasdeinformacion.proyecto.data.local.GastoEntity
import com.sistemasdeinformacion.proyecto.data.local.NovedadEntity
import com.sistemasdeinformacion.proyecto.data.local.RevisionEntity
import com.sistemasdeinformacion.proyecto.data.local.ViajeEntity
import com.sistemasdeinformacion.proyecto.data.model.EstadoValidacion
import com.sistemasdeinformacion.proyecto.data.model.EstadoViaje
import com.sistemasdeinformacion.proyecto.data.model.Gravedad
import com.sistemasdeinformacion.proyecto.data.model.TipoAdjunto
import com.sistemasdeinformacion.proyecto.data.model.TipoEvento
import com.sistemasdeinformacion.proyecto.data.model.TipoGasto
import com.sistemasdeinformacion.proyecto.data.model.TipoNovedad
import com.sistemasdeinformacion.proyecto.data.model.desdeValor
import java.time.Instant
import java.time.OffsetDateTime

/*
 * Conversión entre filas locales (Room) y remotas (Supabase).
 * Lo que baja del servidor llega ya sincronizado: syncPendiente = false y modificadoLocal = 0.
 */

fun Long.aIso(): String = Instant.ofEpochMilli(this).toString()
fun String.desdeIso(): Long = OffsetDateTime.parse(this).toInstant().toEpochMilli()

// ---------- viajes ----------
fun ViajeEntity.aSubida() = ViajeSubidaDto(
    id = id, choferId = choferId, camionId = camionId, cliente = cliente, origen = origen, destino = destino,
    ruta = ruta, tipoCarga = tipoCarga, pesoKg = pesoKg, requiereRefrigeracion = requiereRefrigeracion,
    precio = precio, fechaSalida = fechaSalida.aIso(), fechaLlegada = fechaLlegada?.aIso(), kmInicial = kmInicial,
    kmFinal = kmFinal, estado = estado.valor, observaciones = observaciones, eliminado = eliminado,
)

/** Cómo se vería en el servidor recién subido (útil para pruebas). */
fun ViajeSubidaDto.comoDtoBajada() = ViajeDto(
    id = id, choferId = choferId, camionId = camionId, cliente = cliente, origen = origen, destino = destino,
    ruta = ruta, tipoCarga = tipoCarga, pesoKg = pesoKg, requiereRefrigeracion = requiereRefrigeracion,
    precio = precio, fechaSalida = fechaSalida, fechaLlegada = fechaLlegada, kmInicial = kmInicial, kmFinal = kmFinal,
    estado = estado, observaciones = observaciones, eliminado = eliminado,
)

fun ViajeDto.aEntidad() = ViajeEntity(
    id = id, choferId = choferId, camionId = camionId, cliente = cliente, origen = origen, destino = destino,
    ruta = ruta, tipoCarga = tipoCarga, pesoKg = pesoKg, requiereRefrigeracion = requiereRefrigeracion,
    precio = precio, fechaSalida = fechaSalida.desdeIso(), fechaLlegada = fechaLlegada?.desdeIso(),
    kmInicial = kmInicial, kmFinal = kmFinal, estado = desdeValor(estado, EstadoViaje.EN_CURSO),
    validacion = desdeValor(validacion, EstadoValidacion.PENDIENTE), comentarioValidacion = comentarioValidacion,
    observaciones = observaciones, eliminado = eliminado, modificadoLocal = 0, syncPendiente = false,
)

// ---------- revisiones ----------
fun RevisionEntity.aDto() = RevisionDto(id, viajeId, neumaticos, luces, frenos, niveles, documentos, comentario, eliminado)
fun RevisionDto.aEntidad() = RevisionEntity(id, viajeId, neumaticos, luces, frenos, niveles, documentos, comentario, eliminado, 0, false)

// ---------- eventos ----------
fun EventoEntity.aDto() = EventoDto(
    id = id, viajeId = viajeId, tipo = tipo.valor, fechaHora = fechaHora.aIso(), fechaFin = fechaFin?.aIso(),
    latitud = latitud, longitud = longitud, direccion = direccion, km = km, nota = nota, eliminado = eliminado,
)

fun EventoDto.aEntidad() = EventoEntity(
    id = id, viajeId = viajeId, tipo = desdeValor(tipo, TipoEvento.PARADA), fechaHora = fechaHora.desdeIso(),
    fechaFin = fechaFin?.desdeIso(), latitud = latitud, longitud = longitud, direccion = direccion, km = km,
    nota = nota, eliminado = eliminado, modificadoLocal = 0, syncPendiente = false,
)

// ---------- gastos ----------
fun GastoEntity.aDto() = GastoDto(id, viajeId, tipo.valor, monto, litros, fechaHora.aIso(), descripcion, eliminado)
fun GastoDto.aEntidad() = GastoEntity(
    id, viajeId, desdeValor(tipo, TipoGasto.OTRO), monto, litros, fechaHora.desdeIso(), descripcion, eliminado, 0, false,
)

// ---------- novedades ----------
fun NovedadEntity.aDto() = NovedadDto(id, viajeId, tipo.valor, gravedad.valor, descripcion, fechaHora.aIso(), latitud, longitud, eliminado)
fun NovedadDto.aEntidad() = NovedadEntity(
    id, viajeId, desdeValor(tipo, TipoNovedad.OTRO), desdeValor(gravedad, Gravedad.BAJA), descripcion,
    fechaHora.desdeIso(), latitud, longitud, eliminado, 0, false,
)

// ---------- adjuntos ----------
fun AdjuntoEntity.aDto() = AdjuntoDto(id, viajeId, tipo.valor, rutaRemota, nombreReceptor, fechaHora.aIso(), eliminado)

/** El archivo local solo existe en el teléfono que tomó la foto; al bajar se conserva si ya estaba. */
fun AdjuntoDto.aEntidad(rutaLocal: String = "") = AdjuntoEntity(
    id, viajeId, desdeValor(tipo, TipoAdjunto.FOTO), rutaLocal, rutaRemota, nombreReceptor, fechaHora.desdeIso(), eliminado, 0, false,
)

// ---------- camiones ----------
fun CamionDto.aEntidad() = CamionEntity(id ?: patente, patente, marca, modelo, rendimientoKmL)
