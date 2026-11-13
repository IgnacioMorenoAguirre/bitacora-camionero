package com.sistemasdeinformacion.proyecto.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Filas de PostgreSQL (Supabase) con nombres snake_case. Las fechas viajan como texto ISO-8601.
 * actualizado_en lo fija siempre un trigger del servidor: lo que mande la app se ignora.
 */

@Serializable
data class PerfilDto(
    val id: String,
    @SerialName("nombre_completo") val nombreCompleto: String,
    val rut: String? = null,
    val telefono: String? = null,
    val rol: String,
    val activo: Boolean = true,
)

@Serializable
data class CamionDto(
    val id: String? = null,
    val patente: String,
    val marca: String,
    val modelo: String,
    val anio: Int? = null,
    @SerialName("rendimiento_km_l") val rendimientoKmL: Double,
    val activo: Boolean = true,
)

/** Lo que sube el chofer: sin campos de validación (esos los maneja el administrador). */
@Serializable
data class ViajeSubidaDto(
    val id: String,
    @SerialName("chofer_id") val choferId: String,
    @SerialName("camion_id") val camionId: String?,
    val cliente: String,
    val origen: String,
    val destino: String,
    val ruta: String?,
    @SerialName("tipo_carga") val tipoCarga: String,
    @SerialName("peso_kg") val pesoKg: Double,
    @SerialName("requiere_refrigeracion") val requiereRefrigeracion: Boolean,
    val precio: Long,
    @SerialName("fecha_salida") val fechaSalida: String,
    @SerialName("fecha_llegada") val fechaLlegada: String?,
    @SerialName("km_inicial") val kmInicial: Int?,
    @SerialName("km_final") val kmFinal: Int?,
    val estado: String,
    val observaciones: String?,
    val eliminado: Boolean,
)

/** Lo que baja desde el servidor (incluye validación y la marca de actualización). */
@Serializable
data class ViajeDto(
    val id: String,
    @SerialName("chofer_id") val choferId: String,
    @SerialName("camion_id") val camionId: String? = null,
    val cliente: String,
    val origen: String,
    val destino: String,
    val ruta: String? = null,
    @SerialName("tipo_carga") val tipoCarga: String,
    @SerialName("peso_kg") val pesoKg: Double,
    @SerialName("requiere_refrigeracion") val requiereRefrigeracion: Boolean,
    val precio: Long,
    @SerialName("fecha_salida") val fechaSalida: String,
    @SerialName("fecha_llegada") val fechaLlegada: String? = null,
    @SerialName("km_inicial") val kmInicial: Int? = null,
    @SerialName("km_final") val kmFinal: Int? = null,
    val estado: String,
    val validacion: String = "pendiente",
    @SerialName("comentario_validacion") val comentarioValidacion: String? = null,
    val observaciones: String? = null,
    val eliminado: Boolean = false,
    @SerialName("actualizado_en") val actualizadoEn: String? = null,
)

@Serializable
data class RevisionDto(
    val id: String,
    @SerialName("viaje_id") val viajeId: String,
    val neumaticos: Boolean,
    val luces: Boolean,
    val frenos: Boolean,
    val niveles: Boolean,
    val documentos: Boolean,
    val comentario: String? = null,
    val eliminado: Boolean = false,
    @SerialName("actualizado_en") val actualizadoEn: String? = null,
)

@Serializable
data class EventoDto(
    val id: String,
    @SerialName("viaje_id") val viajeId: String,
    val tipo: String,
    @SerialName("fecha_hora") val fechaHora: String,
    @SerialName("fecha_fin") val fechaFin: String? = null,
    val latitud: Double? = null,
    val longitud: Double? = null,
    val direccion: String? = null,
    val km: Int? = null,
    val nota: String? = null,
    val eliminado: Boolean = false,
    @SerialName("actualizado_en") val actualizadoEn: String? = null,
)

@Serializable
data class GastoDto(
    val id: String,
    @SerialName("viaje_id") val viajeId: String,
    val tipo: String,
    val monto: Long,
    val litros: Double? = null,
    @SerialName("fecha_hora") val fechaHora: String,
    val descripcion: String? = null,
    val eliminado: Boolean = false,
    @SerialName("actualizado_en") val actualizadoEn: String? = null,
)

@Serializable
data class NovedadDto(
    val id: String,
    @SerialName("viaje_id") val viajeId: String,
    val tipo: String,
    val gravedad: String,
    val descripcion: String,
    @SerialName("fecha_hora") val fechaHora: String,
    val latitud: Double? = null,
    val longitud: Double? = null,
    val eliminado: Boolean = false,
    @SerialName("actualizado_en") val actualizadoEn: String? = null,
)

@Serializable
data class AdjuntoDto(
    val id: String,
    @SerialName("viaje_id") val viajeId: String,
    val tipo: String,
    @SerialName("ruta_remota") val rutaRemota: String? = null,
    @SerialName("nombre_receptor") val nombreReceptor: String? = null,
    @SerialName("fecha_hora") val fechaHora: String,
    val eliminado: Boolean = false,
    @SerialName("actualizado_en") val actualizadoEn: String? = null,
)

/** Fila de la vista resumen_viajes (reportes del administrador). */
@Serializable
data class ResumenViajeDto(
    val id: String,
    @SerialName("chofer_id") val choferId: String,
    val chofer: String,
    val patente: String? = null,
    val cliente: String,
    val origen: String,
    val destino: String,
    @SerialName("fecha_salida") val fechaSalida: String,
    @SerialName("fecha_llegada") val fechaLlegada: String? = null,
    val estado: String,
    val validacion: String,
    val precio: Long,
    @SerialName("km_recorridos") val kmRecorridos: Int? = null,
    @SerialName("gasto_combustible") val gastoCombustible: Long = 0,
    @SerialName("gasto_peajes") val gastoPeajes: Long = 0,
    @SerialName("gasto_total") val gastoTotal: Long = 0,
    val litros: Double = 0.0,
    val margen: Long = 0,
)
