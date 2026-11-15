package com.sistemasdeinformacion.proyecto.feature.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sistemasdeinformacion.proyecto.data.model.EstadoValidacion
import com.sistemasdeinformacion.proyecto.data.model.EstadoViaje
import com.sistemasdeinformacion.proyecto.data.model.desdeValor
import com.sistemasdeinformacion.proyecto.data.remote.PerfilDto
import com.sistemasdeinformacion.proyecto.data.remote.desdeIso
import com.sistemasdeinformacion.proyecto.domain.Formatos
import com.sistemasdeinformacion.proyecto.ui.componentes.EstadoVacio
import com.sistemasdeinformacion.proyecto.ui.componentes.HitoKm
import com.sistemasdeinformacion.proyecto.ui.componentes.SelectorOpciones
import com.sistemasdeinformacion.proyecto.ui.navegacion.AdminDetalle
import com.sistemasdeinformacion.proyecto.ui.navegacion.LocalContainer
import com.sistemasdeinformacion.proyecto.ui.navegacion.Navegador
import com.sistemasdeinformacion.proyecto.ui.theme.Bitacora

/** Textos del estado sin red; los comparten las pantallas del administrador. */
const val TITULO_SIN_CONEXION = "Sin conexión"
const val TEXTO_SIN_CONEXION = "Las pantallas de administración necesitan internet."

internal val VerdeAprobado = Color(0xFF4CD97B)

@Composable
fun AdminViajesScreen(nav: Navegador) {
    val container = LocalContainer.current
    val vm = viewModel { AdminViajesViewModel(container.admin, container.conexion) }
    val e by vm.estado.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Viajes", style = MaterialTheme.typography.headlineLarge)
        Text("Se actualiza solo cuando un chofer sincroniza.", style = MaterialTheme.typography.bodyMedium, color = Bitacora.colores.textoSuave)
        Spacer(Modifier.height(12.dp))

        when (val l = e.lista) {
            ListaViajes.SinConexion -> CentroVertical {
                EstadoVacio(TITULO_SIN_CONEXION, TEXTO_SIN_CONEXION, accion = "Reintentar", onAccion = vm::reintentar)
            }
            is ListaViajes.Error -> CentroVertical {
                EstadoVacio("No se pudo cargar", l.mensaje, accion = "Reintentar", onAccion = vm::reintentar)
            }
            ListaViajes.Cargando -> CentroVertical { CircularProgressIndicator(color = Bitacora.colores.acento) }
            is ListaViajes.Lista -> LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "filtros", contentType = "filtros") { Filtros(e, vm) }
                if (l.filas.isEmpty()) {
                    item(key = "vacio", contentType = "vacio") { EstadoVacio("Sin viajes", "No hay viajes con estos filtros.") }
                }
                items(l.filas, key = { it.resumen.id }, contentType = { "viaje" }) { f ->
                    FilaViajeAdmin(f, Modifier.animateItem()) { nav.ir(AdminDetalle(f.resumen.id)) }
                }
                if (l.limiteAlcanzado) {
                    item(key = "limite", contentType = "limite") {
                        Text(
                            "Se muestran los viajes más recientes. Usa los filtros para ver otros.",
                            style = MaterialTheme.typography.bodyMedium, color = Bitacora.colores.textoSuave,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CentroVertical(contenido: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { contenido() }
}

@Composable
private fun Filtros(e: EstadoAdminViajes, vm: AdminViajesViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectorOpciones(Periodo.entries, e.periodo, { it.etiqueta }, vm::elegirPeriodo)
        SelectorOpciones(listOf<EstadoViaje?>(null) + EstadoViaje.entries, e.filtro.estado, { it?.etiqueta ?: "Todos" }, vm::elegirEstado)
        SelectorOpciones(
            listOf<EstadoValidacion?>(null) + EstadoValidacion.entries, e.filtro.validacion,
            { it?.etiqueta ?: "Toda validación" }, vm::elegirValidacion,
        )
        if (e.choferes.isNotEmpty()) SelectorChofer(e.choferes, e.filtro.choferId, vm::elegirChofer)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun SelectorChofer(choferes: List<PerfilDto>, elegido: String?, onElegir: (String?) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    val nombre = choferes.firstOrNull { it.id == elegido }?.nombreCompleto ?: "Todos los choferes"
    Box {
        TextButton(onClick = { abierto = true }) {
            Text("Chofer: $nombre", color = Bitacora.colores.acento, style = MaterialTheme.typography.labelLarge)
            Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = Bitacora.colores.acento)
        }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            DropdownMenuItem(text = { Text("Todos los choferes") }, onClick = { onElegir(null); abierto = false })
            choferes.forEach { p ->
                DropdownMenuItem(text = { Text(p.nombreCompleto) }, onClick = { onElegir(p.id); abierto = false })
            }
        }
    }
}

@Composable
private fun FilaViajeAdmin(f: FilaViaje, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Bitacora.colores
    val r = f.resumen
    val estado = desdeValor(r.estado, EstadoViaje.EN_CURSO)
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HitoKm(r.kmRecorridos, actual = estado == EstadoViaje.EN_CURSO)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${r.origen} a ${r.destino}", style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(r.chofer, r.patente, Formatos.fechaCorta(r.fechaSalida.desdeIso())).joinToString(", "),
                style = MaterialTheme.typography.bodyMedium, color = c.textoSuave,
            )
            if (f.excedioHoras) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Timer, contentDescription = null, tint = c.peligro, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Más de 5 h seguidas al volante", style = MaterialTheme.typography.labelMedium, color = c.peligro)
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        val (texto, color) = etiquetaEstado(estado, desdeValor(r.validacion, EstadoValidacion.PENDIENTE))
        Etiqueta(texto, color)
    }
}

/** Estado corto del viaje para la lista y el detalle. */
@Composable
internal fun etiquetaEstado(estado: EstadoViaje, validacion: EstadoValidacion): Pair<String, Color> {
    val c = Bitacora.colores
    return when {
        estado == EstadoViaje.EN_CURSO -> "En curso" to c.acento
        estado == EstadoViaje.CANCELADO -> "Cancelado" to c.textoSuave
        validacion == EstadoValidacion.APROBADO -> "Aprobado" to VerdeAprobado
        validacion == EstadoValidacion.OBSERVADO -> "Observado" to c.peligro
        else -> "Por revisar" to MaterialTheme.colorScheme.onBackground
    }
}

@Composable
internal fun Etiqueta(texto: String, color: Color) {
    Text(
        texto,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
