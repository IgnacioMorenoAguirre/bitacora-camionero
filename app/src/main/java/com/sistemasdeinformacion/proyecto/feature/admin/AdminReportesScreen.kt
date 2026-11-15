package com.sistemasdeinformacion.proyecto.feature.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sistemasdeinformacion.proyecto.domain.FilaReporte
import com.sistemasdeinformacion.proyecto.domain.Formatos
import com.sistemasdeinformacion.proyecto.domain.Reporte
import com.sistemasdeinformacion.proyecto.ui.componentes.EstadoVacio
import com.sistemasdeinformacion.proyecto.ui.componentes.SelectorOpciones
import com.sistemasdeinformacion.proyecto.ui.navegacion.LocalContainer
import com.sistemasdeinformacion.proyecto.ui.theme.Bitacora
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** Qué se compara en las barras. */
private enum class Metrica(val etiqueta: String) { KM("Km"), GANANCIA("Ganancia") }

private val fmtMes = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("es-CL"))

private fun contar(n: Int, uno: String, varios: String) = if (n == 1) "1 $uno" else "$n $varios"

private fun nombreMes(m: YearMonth): String = fmtMes.format(m).replaceFirstChar { it.uppercase() }

@Composable
fun AdminReportesScreen() {
    val container = LocalContainer.current
    val vm = viewModel { AdminReportesViewModel(container.admin, container.conexion) }
    val e by vm.estado.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Reportes", style = MaterialTheme.typography.headlineLarge)
        Text("Viajes finalizados según su fecha de salida.", style = MaterialTheme.typography.bodyMedium, color = Bitacora.colores.textoSuave)
        Spacer(Modifier.height(8.dp))
        SelectorMes(e.mes, e.puedeAvanzar, onAnterior = vm::mesAnterior, onSiguiente = vm::mesSiguiente)
        Spacer(Modifier.height(8.dp))

        val reporte = e.reporte
        when {
            e.sinConexion -> Centro {
                EstadoVacio(TITULO_SIN_CONEXION, TEXTO_SIN_CONEXION, accion = "Reintentar", onAccion = vm::reintentar)
            }
            e.error != null -> Centro {
                EstadoVacio("No se pudo cargar", e.error!!, accion = "Reintentar", onAccion = vm::reintentar)
            }
            e.cargando || reporte == null -> Centro { CircularProgressIndicator(color = Bitacora.colores.acento) }
            reporte.totales.viajes == 0 -> Centro {
                EstadoVacio("Sin viajes finalizados", "No hay viajes finalizados en ${nombreMes(e.mes).lowercase()}. Prueba con otro mes.")
            }
            else -> ContenidoReporte(reporte)
        }
    }
}

@Composable
private fun Centro(contenido: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { contenido() }
}

@Composable
private fun SelectorMes(mes: YearMonth, puedeAvanzar: Boolean, onAnterior: () -> Unit, onSiguiente: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onAnterior) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Mes anterior")
        }
        Text(
            nombreMes(mes), style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onSiguiente, enabled = puedeAvanzar) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Mes siguiente")
        }
    }
}

@Composable
private fun ContenidoReporte(r: Reporte) {
    val c = Bitacora.colores
    var metrica by rememberSaveable { mutableStateOf(Metrica.KM) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Cifra("Viajes", Formatos.numero(r.totales.viajes.toLong()), Modifier.weight(1f))
            Cifra("Recorridos", Formatos.km(r.totales.km), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Cifra("Gastos", Formatos.pesos(r.totales.gastos), Modifier.weight(1f))
            Cifra(
                "Ganancia", Formatos.pesos(r.totales.ganancia), Modifier.weight(1f),
                color = if (r.totales.ganancia < 0) c.peligro else MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Etiqueta(contar(r.aprobados, "aprobado", "aprobados"), VerdeAprobado)
            Etiqueta(contar(r.pendientes, "por revisar", "por revisar"), MaterialTheme.colorScheme.onBackground)
            Etiqueta(contar(r.observados, "observado", "observados"), c.peligro)
        }
        Spacer(Modifier.height(20.dp))
        SelectorOpciones(Metrica.entries, metrica, { it.etiqueta }, { metrica = it })
        Spacer(Modifier.height(16.dp))
        Seccion("Por chofer", r.porChofer, metrica)
        Spacer(Modifier.height(20.dp))
        Seccion("Por camión", r.porCamion, metrica)
        Spacer(Modifier.height(16.dp))
    }
}

/** Total del mes en cifras grandes (Barlow Condensed). */
@Composable
private fun Cifra(titulo: String, valor: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onBackground) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(titulo, style = MaterialTheme.typography.labelMedium, color = Bitacora.colores.textoSuave)
        Text(valor, style = MaterialTheme.typography.displaySmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Seccion(titulo: String, filas: List<FilaReporte>, metrica: Metrica) {
    Text(titulo, style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(8.dp))
    val valor: (FilaReporte) -> Long = { if (metrica == Metrica.KM) it.km.toLong() else it.ganancia }
    val texto: (FilaReporte) -> String = { if (metrica == Metrica.KM) Formatos.km(it.km) else Formatos.pesos(it.ganancia) }
    val maximo = filas.maxOfOrNull { abs(valor(it)) }?.takeIf { it > 0 } ?: 1L
    // Ordenadas por la métrica elegida, de mayor a menor.
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        filas.sortedByDescending(valor).forEach { f ->
            Barra(f, texto(f), abs(valor(f)).toFloat() / maximo, negativa = valor(f) < 0)
        }
    }
}

/** Barra horizontal simple: nombre y valor arriba, la barra proporcional al mayor del grupo. */
@Composable
private fun Barra(f: FilaReporte, valor: String, fraccion: Float, negativa: Boolean) {
    val c = Bitacora.colores
    val viajes = if (f.viajes == 1) "1 viaje" else "${f.viajes} viajes"
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "${f.clave}: $valor, $viajes" }) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(f.clave, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(valor, style = MaterialTheme.typography.titleSmall, color = if (negativa) c.peligro else MaterialTheme.colorScheme.onBackground)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraccion.coerceIn(0.02f, 1f))
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (negativa) c.peligro else c.letrero)
            )
        }
        Text(viajes, style = MaterialTheme.typography.bodySmall, color = c.textoSuave, modifier = Modifier.padding(top = 2.dp))
    }
}
