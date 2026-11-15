package com.sistemasdeinformacion.proyecto.feature.viajes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sistemasdeinformacion.proyecto.ui.componentes.Aviso
import com.sistemasdeinformacion.proyecto.ui.componentes.BotonPrincipal
import com.sistemasdeinformacion.proyecto.ui.componentes.CampoTexto
import com.sistemasdeinformacion.proyecto.ui.navegacion.LocalContainer
import com.sistemasdeinformacion.proyecto.ui.navegacion.Navegador
import com.sistemasdeinformacion.proyecto.ui.theme.Bitacora

/** El chofer corrige los datos del viaje que el administrador observó; al guardar queda otra vez por revisar. */
@Composable
fun CorregirViajeScreen(viajeId: String, nav: Navegador) {
    val container = LocalContainer.current
    val vm = viewModel { CorregirViajeViewModel(viajeId, container.viajes) }
    val c = Bitacora.colores

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.volver() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver") }
            Text("Volver", style = MaterialTheme.typography.bodyLarge, color = c.textoSuave)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Corregir viaje", style = MaterialTheme.typography.headlineLarge)
            vm.comentarioAdmin?.let { Aviso("Observación del administrador: $it", c.peligro) }
            if (vm.listo) {
                CampoTexto(vm.cliente, { vm.cliente = it; vm.limpiar("cliente") }, "Cliente", error = vm.errores["cliente"], mayusculas = KeyboardCapitalization.Words)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CampoTexto(vm.origen, { vm.origen = it; vm.limpiar("origen") }, "Origen", Modifier.weight(1f), error = vm.errores["origen"], mayusculas = KeyboardCapitalization.Words)
                    CampoTexto(vm.destino, { vm.destino = it; vm.limpiar("destino") }, "Destino", Modifier.weight(1f), error = vm.errores["destino"], mayusculas = KeyboardCapitalization.Words)
                }
                CampoTexto(vm.carga, { vm.carga = it; vm.limpiar("carga") }, "Tipo de carga", error = vm.errores["carga"])
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CampoTexto(vm.peso, { vm.peso = it; vm.limpiar("peso") }, "Peso", Modifier.weight(1f), error = vm.errores["peso"], teclado = KeyboardType.Decimal, sufijo = "kg")
                    CampoTexto(vm.precio, { vm.precio = it; vm.limpiar("precio") }, "Precio del flete", Modifier.weight(1f), error = vm.errores["precio"], teclado = KeyboardType.Number, sufijo = "CLP")
                }
                CampoTexto(vm.observaciones, { vm.observaciones = it; vm.limpiar("observaciones") }, "Observaciones (opcional)", lineas = 2)
                Text(
                    "Al guardar, el viaje vuelve a quedar por revisar y se envía al administrador.",
                    style = MaterialTheme.typography.bodyMedium, color = c.textoSuave,
                )
            }
            vm.errorGeneral?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge) }
        }
        BotonPrincipal(
            "Guardar corrección",
            cargando = vm.guardando,
            habilitado = vm.listo,
            onClick = { vm.guardar { nav.volver() } },
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
        )
    }
}
