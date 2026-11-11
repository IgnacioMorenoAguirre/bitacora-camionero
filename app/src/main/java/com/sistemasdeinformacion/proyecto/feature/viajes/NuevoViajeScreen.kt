package com.sistemasdeinformacion.proyecto.feature.viajes

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sistemasdeinformacion.proyecto.data.local.CamionEntity
import com.sistemasdeinformacion.proyecto.data.repo.DatosNuevoViaje
import com.sistemasdeinformacion.proyecto.data.repo.DatosRevision
import com.sistemasdeinformacion.proyecto.data.repo.ViajeEnCursoException
import com.sistemasdeinformacion.proyecto.data.repo.ViajesRepositorio
import com.sistemasdeinformacion.proyecto.domain.Validaciones
import com.sistemasdeinformacion.proyecto.ui.componentes.BotonPrincipal
import com.sistemasdeinformacion.proyecto.ui.componentes.CampoTexto
import com.sistemasdeinformacion.proyecto.ui.componentes.SelectorOpciones
import com.sistemasdeinformacion.proyecto.ui.navegacion.HojaDeRuta
import com.sistemasdeinformacion.proyecto.ui.navegacion.LocalContainer
import com.sistemasdeinformacion.proyecto.ui.navegacion.Navegador
import com.sistemasdeinformacion.proyecto.ui.theme.Bitacora
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NuevoViajeViewModel(private val repo: ViajesRepositorio) : ViewModel() {
    var paso by mutableStateOf(0)
    // Paso 1: viaje
    var cliente by mutableStateOf("")
    var origen by mutableStateOf("")
    var destino by mutableStateOf("")
    var ruta by mutableStateOf("")
    var carga by mutableStateOf("")
    var peso by mutableStateOf("")
    var refrigeracion by mutableStateOf(false)
    var precio by mutableStateOf("")
    // Paso 2: camión
    var camionId by mutableStateOf<String?>(null)
    var kmInicial by mutableStateOf("")
    var observaciones by mutableStateOf("")
    // Paso 3: revisión
    var neumaticos by mutableStateOf(false)
    var luces by mutableStateOf(false)
    var frenos by mutableStateOf(false)
    var niveles by mutableStateOf(false)
    var documentos by mutableStateOf(false)
    var comentario by mutableStateOf("")

    var errores by mutableStateOf<Map<String, String>>(emptyMap())
    var errorGeneral by mutableStateOf<String?>(null)
    var viajeExistente by mutableStateOf<String?>(null)
    var guardando by mutableStateOf(false)

    val camiones = repo.camiones().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun limpiar(clave: String) {
        if (clave in errores) errores = errores - clave
    }

    fun validarPaso(): Boolean {
        val e = mutableMapOf<String, String>()
        when (paso) {
            0 -> {
                if (cliente.isBlank()) e["cliente"] = "Escribe el cliente."
                if (origen.isBlank()) e["origen"] = "Escribe el origen."
                if (destino.isBlank()) e["destino"] = "Escribe el destino."
                if (carga.isBlank()) e["carga"] = "Escribe el tipo de carga."
                if ((Validaciones.parsearDecimal(peso) ?: 0.0) <= 0.0) e["peso"] = "Escribe el peso en kg, por ejemplo 18.500."
                if (Validaciones.parsearEntero(precio) == null) e["precio"] = "Escribe el precio del flete, por ejemplo 900.000."
            }
            1 -> {
                if (camionId == null) e["camion"] = "Elige el camión que vas a usar."
                if (Validaciones.parsearEntero(kmInicial) == null) e["km"] = "Escribe el kilometraje del odómetro."
            }
            2 -> if (!(neumaticos && luces && frenos && niveles && documentos) && comentario.isBlank()) {
                e["comentario"] = "Cuenta qué falló en la revisión."
            }
        }
        errores = e
        return e.isEmpty()
    }

    fun siguiente(alCrear: (String) -> Unit) {
        if (!validarPaso()) return
        if (paso < 2) { paso++; return }
        guardando = true
        viewModelScope.launch {
            repo.crearViaje(
                DatosNuevoViaje(
                    camionId = camionId, cliente = cliente, origen = origen, destino = destino, ruta = ruta,
                    tipoCarga = carga, pesoKg = Validaciones.parsearDecimal(peso)!!, refrigeracion = refrigeracion,
                    precio = Validaciones.parsearEntero(precio)!!.toLong(), kmInicial = Validaciones.parsearEntero(kmInicial)!!,
                    observaciones = observaciones,
                ),
                DatosRevision(neumaticos, luces, frenos, niveles, documentos, comentario),
            ).onSuccess(alCrear).onFailure {
                guardando = false
                if (it is ViajeEnCursoException) {
                    errorGeneral = "Ya tienes un viaje en curso. Termínalo antes de crear otro."
                    viajeExistente = it.viajeId
                } else errorGeneral = "No se pudo guardar el viaje. Intenta de nuevo."
            }
        }
    }
}

private val titulosPaso = listOf("Datos del viaje", "Camión", "Revisión antes de salir")

@Composable
fun NuevoViajeScreen(nav: Navegador) {
    val container = LocalContainer.current
    val vm = viewModel { NuevoViajeViewModel(container.viajes) }
    val camiones by vm.camiones.collectAsStateWithLifecycle()
    val c = Bitacora.colores

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (vm.paso > 0) vm.paso-- else nav.volver() }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
            }
            Text(if (vm.paso > 0) "Paso anterior" else "Volver", style = MaterialTheme.typography.bodyLarge, color = c.textoSuave)
        }
        Column(Modifier.padding(horizontal = 18.dp)) {
            Text("Nuevo viaje", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(10.dp))
            IndicadorPasos(vm.paso)
            Spacer(Modifier.height(6.dp))
            Text("Paso ${vm.paso + 1} de 3: ${titulosPaso[vm.paso]}", style = MaterialTheme.typography.titleSmall, color = c.textoSuave)
        }
        AnimatedContent(
            targetState = vm.paso,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(240)) { dir * it / 5 } + fadeIn(tween(240))) togetherWith
                    (slideOutHorizontally(tween(180)) { -dir * it / 5 } + fadeOut(tween(180)))
            },
            label = "pasos",
            modifier = Modifier.weight(1f),
        ) { paso ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (paso) {
                    0 -> PasoViaje(vm)
                    1 -> PasoCamion(vm, camiones)
                    else -> PasoRevision(vm)
                }
                vm.errorGeneral?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                    vm.viajeExistente?.let { id ->
                        TextButton(onClick = { nav.reemplazar(HojaDeRuta(id)) }) { Text("Abrir el viaje en curso", color = c.acento) }
                    }
                }
            }
        }
        BotonPrincipal(
            texto = if (vm.paso < 2) "Continuar" else "Iniciar viaje",
            cargando = vm.guardando,
            onClick = { vm.siguiente { id -> nav.reemplazar(HojaDeRuta(id)) } },
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun IndicadorPasos(paso: Int) {
    val c = Bitacora.colores
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (i <= paso) c.acento else MaterialTheme.colorScheme.surfaceContainerHigh)
            )
        }
    }
}

@Composable
private fun PasoViaje(vm: NuevoViajeViewModel) {
    CampoTexto(vm.cliente, { vm.cliente = it; vm.limpiar("cliente") }, "Cliente", error = vm.errores["cliente"], mayusculas = KeyboardCapitalization.Words)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CampoTexto(vm.origen, { vm.origen = it; vm.limpiar("origen") }, "Origen", Modifier.weight(1f), error = vm.errores["origen"], mayusculas = KeyboardCapitalization.Words)
        CampoTexto(vm.destino, { vm.destino = it; vm.limpiar("destino") }, "Destino", Modifier.weight(1f), error = vm.errores["destino"], mayusculas = KeyboardCapitalization.Words)
    }
    CampoTexto(vm.ruta, { vm.ruta = it }, "Ruta principal (opcional, ej. 5 SUR)", mayusculas = KeyboardCapitalization.Characters)
    CampoTexto(vm.carga, { vm.carga = it; vm.limpiar("carga") }, "Tipo de carga", error = vm.errores["carga"])
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CampoTexto(vm.peso, { vm.peso = it; vm.limpiar("peso") }, "Peso", Modifier.weight(1f), error = vm.errores["peso"], teclado = KeyboardType.Decimal, sufijo = "kg")
        CampoTexto(vm.precio, { vm.precio = it; vm.limpiar("precio") }, "Precio del flete", Modifier.weight(1f), error = vm.errores["precio"], teclado = KeyboardType.Number, sufijo = "CLP")
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Requiere refrigeración", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(
            checked = vm.refrigeracion, onCheckedChange = { vm.refrigeracion = it },
            colors = SwitchDefaults.colors(checkedTrackColor = Bitacora.colores.acento, checkedThumbColor = Bitacora.colores.sobreAcento),
        )
    }
}

@Composable
private fun PasoCamion(vm: NuevoViajeViewModel, camiones: List<CamionEntity>) {
    Text("¿Qué camión vas a usar?", style = MaterialTheme.typography.titleMedium)
    SelectorOpciones(camiones.map { it.id }, vm.camionId, { id -> camiones.first { it.id == id }.let { "${it.patente}  ${it.marca}" } }, { vm.camionId = it; vm.limpiar("camion") })
    vm.errores["camion"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    CampoTexto(vm.kmInicial, { vm.kmInicial = it; vm.limpiar("km") }, "Kilometraje al salir", error = vm.errores["km"], teclado = KeyboardType.Number, sufijo = "km")
    CampoTexto(vm.observaciones, { vm.observaciones = it }, "Observaciones (opcional)", lineas = 2)
}

@Composable
private fun PasoRevision(vm: NuevoViajeViewModel) {
    Text("Marca lo que revisaste y está en orden.", style = MaterialTheme.typography.bodyLarge, color = Bitacora.colores.textoSuave)
    ItemRevision("Neumáticos", vm.neumaticos) { vm.neumaticos = it }
    ItemRevision("Luces", vm.luces) { vm.luces = it }
    ItemRevision("Frenos", vm.frenos) { vm.frenos = it }
    ItemRevision("Niveles de aceite y agua", vm.niveles) { vm.niveles = it }
    ItemRevision("Documentos al día", vm.documentos) { vm.documentos = it }
    CampoTexto(vm.comentario, { vm.comentario = it; vm.limpiar("comentario") }, "Comentario de la revisión", error = vm.errores["comentario"], lineas = 2)
}

@Composable
private fun ItemRevision(texto: String, marcado: Boolean, onCambio: (Boolean) -> Unit) {
    val c = Bitacora.colores
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(role = Role.Checkbox) { onCambio(!marcado) }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(marcado, onCambio, colors = CheckboxDefaults.colors(checkedColor = c.acento, checkmarkColor = c.sobreAcento))
        Spacer(Modifier.width(4.dp))
        Text(texto, style = MaterialTheme.typography.bodyLarge)
    }
}
