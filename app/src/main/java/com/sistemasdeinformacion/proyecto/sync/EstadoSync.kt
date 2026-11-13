package com.sistemasdeinformacion.proyecto.sync

/**
 * Estado visible de la sincronización (lo muestra el IndicadorSync).
 * [viajesRestaurados]: viajes rechazados por estar aprobados; el Inicio avisa y los limpia.
 */
data class EstadoSync(
    val pendientes: Int = 0,
    val enviando: Boolean = false,
    val conectado: Boolean = false,
    val ultimoError: String? = null,
    val viajesRestaurados: List<String> = emptyList(),
)
