package com.sistemasdeinformacion.proyecto.feature.ruta

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sistemasdeinformacion.proyecto.data.model.Gravedad
import com.sistemasdeinformacion.proyecto.data.model.TipoEvento
import com.sistemasdeinformacion.proyecto.data.model.TipoGasto
import com.sistemasdeinformacion.proyecto.data.model.TipoNovedad
import com.sistemasdeinformacion.proyecto.data.repo.ViajeSoloLecturaException
import com.sistemasdeinformacion.proyecto.domain.Validaciones
import com.sistemasdeinformacion.proyecto.ui.componentes.Aviso
import com.sistemasdeinformacion.proyecto.ui.componentes.BotonPrincipal
import com.sistemasdeinformacion.proyecto.ui.componentes.CampoTexto
import com.sistemasdeinformacion.proyecto.ui.componentes.SelectorOpciones
import com.sistemasdeinformacion.proyecto.ui.navegacion.LocalContainer
import com.sistemasdeinformacion.proyecto.ui.theme.Bitacora
import kotlinx.coroutines.launch

sealed interface HojaRegistro {
    data object Parada : HojaRegistro
    data class Gasto(val tipo: TipoGasto) : HojaRegistro
    data object Novedad : HojaRegistro
    data class TerminarDescanso(val eventoId: String) : HojaRegistro
}

/** Hojas inferiores para registrar en ruta. Todo se guarda en el teléfono al instante. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojasDeRegistro(hoja: HojaRegistro?, viajeId: String, kmActual: Int?, onCerrar: () -> Unit) {
    if (hoja == null) return
    val estadoHoja = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onCerrar,
        sheetState = estadoHoja,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            when (hoja) {
                HojaRegistro.Parada -> FormParada(viajeId, kmActual, onCerrar)
                is HojaRegistro.Gasto -> FormGasto(viajeId, hoja.tipo, onCerrar)
                HojaRegistro.Novedad -> FormNovedad(viajeId, onCerrar)
                is HojaRegistro.TerminarDescanso -> FormTerminarDescanso(hoja.eventoId, onCerrar)
            }
        }
    }
}

private fun mensaje(e: Throwable): String = when (e) {
    is ViajeSoloLecturaException -> e.message!!
    else -> "No se pudo guardar. Intenta de nuevo."
}

@Composable
private fun AvisoUbicacion() {
    val container = LocalContainer.current
    var concedido by remember { mutableStateOf(container.ubicaciones.tienePermiso()) }
    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        concedido = container.ubicaciones.tienePermiso()
    }
    if (!concedido) {
        Aviso(
            "Sin permiso de ubicación: se guardará sin el punto en el mapa.",
            Bitacora.colores.textoSuave,
            icono = Icons.Outlined.LocationOff,
        )
        TextButton(onClick = {
            lanzador.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }) { Text("Permitir ubicación", color = Bitacora.colores.acento) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Titulo(texto: String) {
    Text(texto, style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun FormParada(viajeId: String, kmActual: Int?, onCerrar: () -> Unit) {
    val repo = LocalContainer.current.viajes
    val alcance = rememberCoroutineScope()
    val tipos = listOf(TipoEvento.PARADA, TipoEvento.DESCANSO, TipoEvento.CARGA, TipoEvento.DESCARGA)
    var tipo by remember { mutableStateOf(TipoEvento.PARADA) }
    var km by remember { mutableStateOf("") }
    var nota by remember { mutableStateOf("") }
    var errorKm by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var guardando by remember { mutableStateOf(false) }

    Titulo("Registrar parada")
    AvisoUbicacion()
    SelectorOpciones(tipos, tipo, { it.etiqueta }, { tipo = it })
    if (tipo == TipoEvento.DESCANSO) {
        Spacer(Modifier.height(8.dp))
        Text(
            "El descanso queda abierto hasta que toques \"Terminar descanso\". Si dura 30 minutos o más, reinicia tu conteo de horas de manejo.",
            style = MaterialTheme.typography.bodyMedium, color = Bitacora.colores.textoSuave,
        )
    }
    Spacer(Modifier.height(14.dp))
    CampoTexto(km, { km = it; errorKm = null }, "Kilometraje (opcional)", error = errorKm, teclado = KeyboardType.Number,
        sufijo = kmActual?.let { "último: ${com.sistemasdeinformacion.proyecto.domain.Formatos.numero(it.toLong())}" })
    Spacer(Modifier.height(10.dp))
    CampoTexto(nota, { nota = it }, "Nota (opcional)")
    error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
    Spacer(Modifier.height(18.dp))
    BotonPrincipal("Guardar ${tipo.etiqueta.lowercase()}", cargando = guardando, onClick = {
        val kmNum = if (km.isBlank()) null else Validaciones.parsearEntero(km)
        if (km.isNotBlank() && kmNum == null) { errorKm = "Escribe solo números, por ejemplo 214 o 1.250."; return@BotonPrincipal }
        if (kmNum != null && kmActual != null && kmNum < kmActual) { errorKm = "Es menor que el último registrado (${kmActual} km)."; return@BotonPrincipal }
        guardando = true
        alcance.launch {
            repo.registrarEvento(viajeId, tipo, kmNum, nota).onSuccess { onCerrar() }.onFailure { error = mensaje(it); guardando = false }
        }
    })
}

@Composable
private fun FormTerminarDescanso(eventoId: String, onCerrar: () -> Unit) {
    val repo = LocalContainer.current.viajes
    val alcance = rememberCoroutineScope()
    var guardando by remember { mutableStateOf(false) }
    Titulo("Terminar descanso")
    Text("Registra el fin del descanso para volver a la ruta.", style = MaterialTheme.typography.bodyLarge, color = Bitacora.colores.textoSuave)
    Spacer(Modifier.height(18.dp))
    BotonPrincipal("Terminar descanso", cargando = guardando, onClick = {
        guardando = true
        alcance.launch { repo.cerrarDescanso(eventoId); onCerrar() }
    })
}

@Composable
private fun FormGasto(viajeId: String, tipoInicial: TipoGasto, onCerrar: () -> Unit) {
    val repo = LocalContainer.current.viajes
    val alcance = rememberCoroutineScope()
    var tipo by remember { mutableStateOf(tipoInicial) }
    var monto by remember { mutableStateOf("") }
    var litros by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    var errorMonto by remember { mutableStateOf<String?>(null) }
    var errorLitros by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var guardando by remember { mutableStateOf(false) }

    Titulo("Registrar gasto")
    SelectorOpciones(TipoGasto.entries, tipo, { it.etiqueta }, { tipo = it })
    Spacer(Modifier.height(14.dp))
    CampoTexto(monto, { monto = it; errorMonto = null }, "Monto pagado", error = errorMonto, teclado = KeyboardType.Number, sufijo = "CLP")
    if (tipo == TipoGasto.COMBUSTIBLE) {
        Spacer(Modifier.height(10.dp))
        CampoTexto(litros, { litros = it; errorLitros = null }, "Litros cargados", error = errorLitros, teclado = KeyboardType.Decimal, sufijo = "L")
    }
    Spacer(Modifier.height(10.dp))
    CampoTexto(descripcion, { descripcion = it }, if (tipo == TipoGasto.PEAJE) "Plaza de peaje (opcional)" else "Detalle (opcional)")
    error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
    Spacer(Modifier.height(18.dp))
    BotonPrincipal("Guardar gasto", cargando = guardando, onClick = {
        val m = Validaciones.parsearEntero(monto)
        val l = if (tipo == TipoGasto.COMBUSTIBLE) Validaciones.parsearDecimal(litros) else null
        var ok = true
        if (m == null || m <= 0) { errorMonto = "Escribe el monto, por ejemplo 4.100."; ok = false }
        if (tipo == TipoGasto.COMBUSTIBLE && (l == null || l <= 0.0)) { errorLitros = "Escribe los litros cargados."; ok = false }
        if (!ok) return@BotonPrincipal
        guardando = true
        alcance.launch {
            repo.registrarGasto(viajeId, tipo, m!!.toLong(), l, descripcion).onSuccess { onCerrar() }.onFailure { error = mensaje(it); guardando = false }
        }
    })
}

@Composable
private fun FormNovedad(viajeId: String, onCerrar: () -> Unit) {
    val repo = LocalContainer.current.viajes
    val alcance = rememberCoroutineScope()
    var tipo by remember { mutableStateOf(TipoNovedad.INCIDENTE) }
    var gravedad by remember { mutableStateOf(Gravedad.BAJA) }
    var descripcion by remember { mutableStateOf("") }
    var errorDesc by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var guardando by remember { mutableStateOf(false) }

    Titulo("Reportar novedad")
    SelectorOpciones(TipoNovedad.entries, tipo, { it.etiqueta }, { tipo = it })
    Spacer(Modifier.height(14.dp))
    Text("Gravedad", style = MaterialTheme.typography.titleSmall, color = Bitacora.colores.textoSuave)
    Spacer(Modifier.height(6.dp))
    SelectorOpciones(Gravedad.entries, gravedad, { it.etiqueta }, { gravedad = it })
    Spacer(Modifier.height(14.dp))
    CampoTexto(descripcion, { descripcion = it; errorDesc = null }, "Qué pasó", error = errorDesc, lineas = 3)
    error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
    Spacer(Modifier.height(18.dp))
    BotonPrincipal("Guardar novedad", cargando = guardando, onClick = {
        if (descripcion.isBlank()) { errorDesc = "Describe brevemente qué pasó."; return@BotonPrincipal }
        guardando = true
        alcance.launch {
            repo.registrarNovedad(viajeId, tipo, gravedad, descripcion).onSuccess { onCerrar() }.onFailure { error = mensaje(it); guardando = false }
        }
    })
}
