package com.sistemasdeinformacion.proyecto.feature.entrega

import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sistemasdeinformacion.proyecto.data.model.TipoAdjunto
import com.sistemasdeinformacion.proyecto.domain.Formatos
import com.sistemasdeinformacion.proyecto.ui.componentes.BotonPrincipal
import com.sistemasdeinformacion.proyecto.ui.componentes.BotonSecundario
import com.sistemasdeinformacion.proyecto.ui.componentes.CampoTexto
import com.sistemasdeinformacion.proyecto.ui.navegacion.LocalContainer
import com.sistemasdeinformacion.proyecto.ui.navegacion.Navegador
import com.sistemasdeinformacion.proyecto.ui.navegacion.Resumen
import com.sistemasdeinformacion.proyecto.ui.theme.Bitacora
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Autoridad del FileProvider declarado en el manifiesto (${applicationId}.archivos). */
private fun autoridadArchivos(paquete: String) = "$paquete.archivos"

/** Entrega y cierre del viaje: km final, quién recibe y una foto de la guía o la firma (opcional). */
@Composable
fun EntregaScreen(viajeId: String, nav: Navegador) {
    val container = LocalContainer.current
    val contexto = LocalContext.current
    val vm = viewModel {
        EntregaViewModel(
            viajeId, container.viajes, container.archivosAdjuntos, createSavedStateHandle(),
            prepararFoto = FotoEntrega::preparar,
        )
    }
    val detalle by vm.detalle.collectAsStateWithLifecycle()
    val c = Bitacora.colores
    val v = detalle?.viaje
    val ultimoKm = vm.ultimoKm
    var firmando by rememberSaveable { mutableStateOf(false) }

    // Cámara del sistema: escribe en un archivo privado que se le presta por el FileProvider (sin permiso CAMERA).
    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> vm.fotoTomada(ok) }
    val tomarFoto = {
        val archivo = vm.archivoParaFoto()
        try {
            camara.launch(FileProvider.getUriForFile(contexto, autoridadArchivos(contexto.packageName), archivo))
        } catch (_: ActivityNotFoundException) {
            vm.camaraNoDisponible()
        } catch (_: SecurityException) {
            vm.camaraNoDisponible()
        } catch (_: IllegalArgumentException) {
            vm.camaraNoDisponible()
        }
    }

    // Mientras se cierra el viaje no se puede salir: se cortaría a medias y se perdería la foto o la firma.
    BackHandler(enabled = vm.guardando) {}

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.volver() }, enabled = !vm.guardando) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver") }
            Text("Volver", style = MaterialTheme.typography.bodyLarge, color = c.textoSuave)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Text("Entrega", style = MaterialTheme.typography.headlineLarge)
            v?.let {
                Text("Cierra el viaje a ${it.destino} para ${it.cliente}.", style = MaterialTheme.typography.bodyLarge, color = c.textoSuave)
            }
            Spacer(Modifier.height(18.dp))
            CampoTexto(
                vm.km, vm::cambiarKm, "Kilometraje al llegar", error = vm.errorKm, teclado = KeyboardType.Number,
                sufijo = ultimoKm?.let { "último: ${Formatos.numero(it.toLong())}" },
            )
            Spacer(Modifier.height(12.dp))
            CampoTexto(vm.receptor, { vm.receptor = it }, "Quién recibe la carga (opcional)", mayusculas = KeyboardCapitalization.Words)

            Spacer(Modifier.height(20.dp))
            Text("Respaldo de la entrega", style = MaterialTheme.typography.headlineSmall)
            Text("Opcional: una foto de la guía firmada o la firma de quien recibe.", style = MaterialTheme.typography.bodyMedium, color = c.textoSuave)
            Spacer(Modifier.height(10.dp))
            val adjunto = vm.adjunto
            if (adjunto == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BotonSecundario("Tomar foto de la guía", tomarFoto, Modifier.weight(1f), habilitado = !vm.guardando)
                    BotonSecundario("Pedir firma", { firmando = true }, Modifier.weight(1f), habilitado = !vm.guardando)
                }
            } else {
                VistaAdjunto(adjunto, habilitado = !vm.guardando, onQuitar = vm::quitarAdjunto)
            }
            vm.errorAdjunto?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text("La hora y la ubicación de la entrega se registran solas.", style = MaterialTheme.typography.bodyMedium, color = c.textoSuave)
            Spacer(Modifier.height(12.dp))
        }
        BotonPrincipal(
            "Finalizar viaje",
            cargando = vm.guardando || vm.preparandoAdjunto,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            onClick = { vm.finalizar { nav.raizCon(Resumen(viajeId)) } },
        )
    }

    if (firmando) {
        DialogoFirma(
            onFirma = { bitmap ->
                vm.usarFirma { salida -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, salida) }
                firmando = false
            },
            onCerrar = { firmando = false },
        )
    }
}

@Composable
private fun VistaAdjunto(a: AdjuntoListo, habilitado: Boolean, onQuitar: () -> Unit) {
    val c = Bitacora.colores
    val esFirma = a.tipo == TipoAdjunto.FIRMA
    val imagen by produceState<ImageBitmap?>(null, a.archivo) {
        value = withContext(Dispatchers.IO) { runCatching { FotoEntrega.miniatura(a.archivo)?.asImageBitmap() }.getOrNull() }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .clip(MaterialTheme.shapes.small)
                .background(if (esFirma) Color.White else MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            imagen?.let {
                Image(
                    it, contentDescription = if (esFirma) "Firma de quien recibe" else "Foto de la guía",
                    contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (esFirma) "Firma lista" else "Foto de la guía lista",
                style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onQuitar, enabled = habilitado) {
                Text(if (esFirma) "Quitar firma" else "Quitar foto", color = c.peligro)
            }
        }
    }
}

@Composable
private fun DialogoFirma(onFirma: (Bitmap) -> Unit, onCerrar: () -> Unit) {
    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Firma de quien recibe", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = onCerrar) { Text("Cancelar", color = MaterialTheme.colorScheme.onBackground) }
                }
                Spacer(Modifier.height(10.dp))
                PanelFirma(onFirma = onFirma)
            }
        }
    }
}
