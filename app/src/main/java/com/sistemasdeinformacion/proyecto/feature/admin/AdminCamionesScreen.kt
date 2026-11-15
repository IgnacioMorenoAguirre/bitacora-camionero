package com.sistemasdeinformacion.proyecto.feature.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sistemasdeinformacion.proyecto.data.remote.CamionDto
import com.sistemasdeinformacion.proyecto.ui.componentes.Aviso
import com.sistemasdeinformacion.proyecto.ui.componentes.BotonPrincipal
import com.sistemasdeinformacion.proyecto.ui.componentes.CampoTexto
import com.sistemasdeinformacion.proyecto.ui.componentes.EstadoVacio
import com.sistemasdeinformacion.proyecto.ui.navegacion.LocalContainer
import com.sistemasdeinformacion.proyecto.ui.theme.Bitacora
import com.sistemasdeinformacion.proyecto.ui.theme.Paleta
import kotlinx.coroutines.delay
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

@Composable
fun AdminCamionesScreen() {
    val container = LocalContainer.current
    val vm = viewModel { AdminCamionesViewModel(container.admin, container.conexion) }
    val e by vm.estado.collectAsStateWithLifecycle()
    val c = Bitacora.colores

    LaunchedEffect(e.mensaje) {
        if (e.mensaje != null) { delay(3_000); vm.mensajeVisto() }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Camiones", style = MaterialTheme.typography.headlineLarge)
        Text("Los choferes eligen entre los camiones activos.", style = MaterialTheme.typography.bodyMedium, color = c.textoSuave)
        Spacer(Modifier.height(12.dp))
        e.mensaje?.let { Aviso(it, VerdeAprobado, Modifier.padding(bottom = 10.dp)) }

        when (val l = e.lista) {
            ListaCamiones.SinConexion -> Centro {
                EstadoVacio(TITULO_SIN_CONEXION, TEXTO_SIN_CONEXION, accion = "Reintentar", onAccion = vm::reintentar)
            }
            is ListaCamiones.Error -> Centro { EstadoVacio("No se pudo cargar", l.mensaje, accion = "Reintentar", onAccion = vm::reintentar) }
            ListaCamiones.Cargando -> Centro { CircularProgressIndicator(color = c.acento) }
            is ListaCamiones.Lista -> {
                if (l.camiones.isEmpty()) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        EstadoVacio("Sin camiones", "Agrega el primero para que los choferes puedan elegirlo.")
                    }
                } else {
                    LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(l.camiones, key = { it.id ?: it.patente }) { camion ->
                            FilaCamion(camion, Modifier.animateItem()) { vm.editar(camion) }
                        }
                    }
                }
                BotonPrincipal("Agregar camión", vm::nuevo, Modifier.padding(vertical = 12.dp))
            }
        }
    }

    e.formulario?.let { FormularioCamion(it, vm) }
}

@Composable
private fun Centro(contenido: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { contenido() }
}

private val formatoRendimiento = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.forLanguageTag("es-CL")).apply {
    groupingSeparator = '.'
    decimalSeparator = ','
})

@Composable
private fun FilaCamion(camion: CamionDto, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Bitacora.colores
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(role = Role.Button, onClickLabel = "Editar camión", onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Placa(camion.patente)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(listOfNotNull("${camion.marca} ${camion.modelo}", camion.anio?.toString()).joinToString(", "), style = MaterialTheme.typography.titleMedium)
            Text("${formatoRendimiento.format(camion.rendimientoKmL)} km/L", style = MaterialTheme.typography.bodyMedium, color = c.textoSuave)
        }
        if (!camion.activo) Etiqueta("Inactivo", c.textoSuave)
    }
}

/** Patente con aspecto de placa: fondo reflectante y borde de tinta. */
@Composable
private fun Placa(patente: String) {
    val forma = RoundedCornerShape(6.dp)
    Text(
        patente,
        style = MaterialTheme.typography.titleMedium,
        color = Paleta.tinta,
        modifier = Modifier
            .clip(forma)
            .background(Paleta.blancoReflectante)
            .border(BorderStroke(2.dp, Paleta.tinta), forma)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormularioCamion(f: FormCamion, vm: AdminCamionesViewModel) {
    val c = Bitacora.colores
    ModalBottomSheet(
        onDismissRequest = vm::cerrarFormulario,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(if (f.id == null) "Nuevo camión" else "Editar camión", style = MaterialTheme.typography.headlineMedium)
            CampoTexto(f.patente, vm::cambiarPatente, "Patente", error = f.errores["patente"], mayusculas = KeyboardCapitalization.Characters)
            CampoTexto(f.marca, vm::cambiarMarca, "Marca", error = f.errores["marca"], mayusculas = KeyboardCapitalization.Words)
            CampoTexto(f.modelo, vm::cambiarModelo, "Modelo", error = f.errores["modelo"], mayusculas = KeyboardCapitalization.Words)
            CampoTexto(f.anio, vm::cambiarAnio, "Año (opcional)", error = f.errores["anio"], teclado = KeyboardType.Number)
            CampoTexto(f.rendimiento, vm::cambiarRendimiento, "Rendimiento", error = f.errores["rendimiento"], teclado = KeyboardType.Decimal, sufijo = "km/L")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Activo", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (f.activo) "Los choferes pueden elegirlo." else "No aparece al crear un viaje.",
                        style = MaterialTheme.typography.bodyMedium, color = c.textoSuave,
                    )
                }
                Switch(
                    checked = f.activo, onCheckedChange = vm::cambiarActivo,
                    colors = SwitchDefaults.colors(checkedTrackColor = c.acento, checkedThumbColor = c.sobreAcento),
                )
            }
            f.error?.let { Aviso(it, c.peligro) }
            BotonPrincipal("Guardar camión", vm::guardar, cargando = f.guardando)
        }
    }
}
