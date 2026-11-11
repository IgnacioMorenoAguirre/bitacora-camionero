package com.sistemasdeinformacion.proyecto.feature.viajes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sistemasdeinformacion.proyecto.data.model.EstadoValidacion
import com.sistemasdeinformacion.proyecto.data.model.TipoGasto
import com.sistemasdeinformacion.proyecto.domain.CalculosViaje
import com.sistemasdeinformacion.proyecto.domain.Formatos
import com.sistemasdeinformacion.proyecto.ui.componentes.Aviso
import com.sistemasdeinformacion.proyecto.ui.componentes.BotonPrincipal
import com.sistemasdeinformacion.proyecto.ui.componentes.EscudoRuta
import com.sistemasdeinformacion.proyecto.ui.navegacion.HojaDeRuta
import com.sistemasdeinformacion.proyecto.ui.navegacion.Inicio
import com.sistemasdeinformacion.proyecto.ui.navegacion.LocalContainer
import com.sistemasdeinformacion.proyecto.ui.navegacion.Navegador
import com.sistemasdeinformacion.proyecto.ui.theme.Bitacora

@Composable
fun ResumenScreen(viajeId: String, nav: Navegador) {
    val container = LocalContainer.current
    val d by remember(viajeId) { container.viajes.detalle(viajeId) }.collectAsStateWithLifecycle(null)
    val c = Bitacora.colores
    val v = d?.viaje

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.volver() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver") }
            Text("Volver", style = MaterialTheme.typography.bodyLarge, color = c.textoSuave)
        }
        if (d == null || v == null) return@Column
        val r = CalculosViaje.resumen(v, d!!.gastos, d!!.camion?.rendimientoKmL)

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Text("Resumen del viaje", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                v.ruta?.let { EscudoRuta(it) }
                Text("${v.origen} a ${v.destino}", style = MaterialTheme.typography.titleMedium)
            }
            val (estado, color) = when (v.validacion) {
                EstadoValidacion.APROBADO -> "Bitácora aprobada" to Color(0xFF4CD97B)
                EstadoValidacion.OBSERVADO -> "Observada: ${v.comentarioValidacion ?: "revisa los datos"}" to c.peligro
                EstadoValidacion.PENDIENTE -> "Pendiente de revisión del administrador" to c.textoSuave
            }
            Aviso(estado, color, Modifier.padding(top = 12.dp))
            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Cifra(r.kmRecorridos?.let { Formatos.km(it) } ?: "—", "recorridos", Modifier.weight(1f))
                Cifra(r.duracionMs?.let { Formatos.duracion(it) } ?: "—", "de viaje", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Cifra(Formatos.pesos(r.ganancia), "de ganancia", Modifier.weight(1f), destacada = true)
                Cifra(Formatos.pesos(r.gastoTotal), "en gastos", Modifier.weight(1f))
            }

            Spacer(Modifier.height(22.dp))
            Text("Gastos", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Linea("Precio del flete", Formatos.pesos(v.precio))
            TipoGasto.entries.forEach { t -> r.gastoPorTipo[t]?.let { Linea(t.etiqueta, "-" + Formatos.pesos(it)) } }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Linea("Ganancia", Formatos.pesos(r.ganancia), fuerte = true)

            Spacer(Modifier.height(22.dp))
            Text("Combustible", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Linea("Cargado", Formatos.litros(r.litrosReales))
            Linea("Estimado por km", r.litrosEstimados?.let { Formatos.litros(it) } ?: "Sin rendimiento del camión")
            r.pesosEstimados?.let { Linea("Estimado en pesos", Formatos.pesos(it)) }
            if (r.litrosEstimados != null && r.litrosReales > 0) {
                val dif = r.litrosReales - r.litrosEstimados
                Text(
                    if (dif > 0) "Se cargaron ${Formatos.litros(dif)} más de lo estimado." else "Se cargaron ${Formatos.litros(-dif)} menos de lo estimado.",
                    style = MaterialTheme.typography.bodyMedium, color = c.textoSuave, modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
        Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
            BotonPrincipal("Ver hoja de ruta", { nav.ir(HojaDeRuta(viajeId)) })
            androidx.compose.material3.TextButton(onClick = { nav.raiz(Inicio) }, modifier = Modifier.fillMaxWidth()) {
                Text("Volver al inicio", color = c.acento)
            }
        }
    }
}

@Composable
private fun Cifra(valor: String, etiqueta: String, modifier: Modifier = Modifier, destacada: Boolean = false) {
    val c = Bitacora.colores
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(if (destacada) c.letrero else MaterialTheme.colorScheme.surfaceContainer)
            .padding(14.dp)
    ) {
        Text(valor, style = MaterialTheme.typography.headlineLarge, color = if (destacada) c.letreroBorde else MaterialTheme.colorScheme.onBackground)
        Text(etiqueta, style = MaterialTheme.typography.bodyMedium, color = if (destacada) c.letreroBorde.copy(alpha = 0.85f) else c.textoSuave)
    }
}

@Composable
private fun Linea(etiqueta: String, valor: String, fuerte: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(etiqueta, style = if (fuerte) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(valor, style = if (fuerte) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge)
    }
}
