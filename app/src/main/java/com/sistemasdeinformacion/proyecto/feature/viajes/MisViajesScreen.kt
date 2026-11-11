package com.sistemasdeinformacion.proyecto.feature.viajes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sistemasdeinformacion.proyecto.data.local.ViajeEntity
import com.sistemasdeinformacion.proyecto.data.local.kmRecorridos
import com.sistemasdeinformacion.proyecto.data.model.EstadoValidacion
import com.sistemasdeinformacion.proyecto.data.model.EstadoViaje
import com.sistemasdeinformacion.proyecto.domain.Formatos
import com.sistemasdeinformacion.proyecto.ui.componentes.EstadoVacio
import com.sistemasdeinformacion.proyecto.ui.componentes.HitoKm
import com.sistemasdeinformacion.proyecto.ui.componentes.SelectorOpciones
import com.sistemasdeinformacion.proyecto.ui.navegacion.HojaDeRuta
import com.sistemasdeinformacion.proyecto.ui.navegacion.LocalContainer
import com.sistemasdeinformacion.proyecto.ui.navegacion.Navegador
import com.sistemasdeinformacion.proyecto.ui.navegacion.NuevoViaje
import com.sistemasdeinformacion.proyecto.ui.theme.Bitacora

private enum class Filtro(val etiqueta: String) { TODOS("Todos"), EN_CURSO("En curso"), FINALIZADOS("Finalizados") }

@Composable
fun MisViajesScreen(nav: Navegador) {
    val container = LocalContainer.current
    val viajes by remember { container.viajes.viajes() }.collectAsStateWithLifecycle(null)
    var filtro by remember { mutableStateOf(Filtro.TODOS) }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Mis viajes", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(12.dp))
        SelectorOpciones(Filtro.entries, filtro, { it.etiqueta }, { filtro = it })
        Spacer(Modifier.height(12.dp))
        val lista = viajes ?: return@Column
        val visibles = lista.filter {
            when (filtro) {
                Filtro.TODOS -> true
                Filtro.EN_CURSO -> it.estado == EstadoViaje.EN_CURSO
                Filtro.FINALIZADOS -> it.estado == EstadoViaje.FINALIZADO
            }
        }
        if (visibles.isEmpty()) {
            EstadoVacio(
                "Sin viajes por aquí",
                if (lista.isEmpty()) "Cuando crees tu primer viaje aparecerá en esta lista." else "No hay viajes con este filtro.",
                accion = if (lista.isEmpty()) "Crear viaje" else null,
                onAccion = { nav.ir(NuevoViaje) },
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(visibles, key = { it.id }) { v ->
                    FilaViaje(v, Modifier.animateItem()) { nav.ir(HojaDeRuta(v.id)) }
                }
            }
        }
    }
}

@Composable
private fun FilaViaje(v: ViajeEntity, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Bitacora.colores
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HitoKm(v.kmRecorridos, actual = v.estado == EstadoViaje.EN_CURSO)
        Spacer(Modifier.padding(start = 12.dp))
        Column(Modifier.weight(1f)) {
            Text("${v.origen} a ${v.destino}", style = MaterialTheme.typography.titleMedium)
            Text(
                "${Formatos.fechaCorta(v.fechaSalida)}, ${v.cliente}, ${Formatos.pesos(v.precio)}",
                style = MaterialTheme.typography.bodyMedium, color = c.textoSuave,
            )
        }
        val (texto, color) = when {
            v.estado == EstadoViaje.EN_CURSO -> "En curso" to c.acento
            v.validacion == EstadoValidacion.APROBADO -> "Aprobado" to Color(0xFF4CD97B)
            v.validacion == EstadoValidacion.OBSERVADO -> "Observado" to c.peligro
            else -> "Por revisar" to c.textoSuave
        }
        Text(
            texto,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
