package com.sistemasdeinformacion.proyecto.feature.admin

import android.graphics.BitmapFactory
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
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sistemasdeinformacion.proyecto.data.model.EstadoValidacion
import com.sistemasdeinformacion.proyecto.data.model.EstadoViaje
import com.sistemasdeinformacion.proyecto.data.model.TipoAdjunto
import com.sistemasdeinformacion.proyecto.data.model.desdeValor
import com.sistemasdeinformacion.proyecto.data.repo.AdjuntoRemoto
import com.sistemasdeinformacion.proyecto.data.repo.DetalleViajeRemoto
import com.sistemasdeinformacion.proyecto.domain.CalculosViaje
import com.sistemasdeinformacion.proyecto.domain.Formatos
import com.sistemasdeinformacion.proyecto.ui.componentes.Aviso
import com.sistemasdeinformacion.proyecto.ui.componentes.BotonPrincipal
import com.sistemasdeinformacion.proyecto.ui.componentes.BotonSecundario
import com.sistemasdeinformacion.proyecto.ui.componentes.CaminoRuta
import com.sistemasdeinformacion.proyecto.ui.componentes.CampoTexto
import com.sistemasdeinformacion.proyecto.ui.componentes.EscudoRuta
import com.sistemasdeinformacion.proyecto.ui.componentes.EstadoVacio
import com.sistemasdeinformacion.proyecto.ui.navegacion.LocalContainer
import com.sistemasdeinformacion.proyecto.ui.navegacion.Navegador
import com.sistemasdeinformacion.proyecto.ui.theme.Bitacora
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun AdminDetalleScreen(viajeId: String, nav: Navegador) {
    val container = LocalContainer.current
    val vm = viewModel { AdminDetalleViewModel(viajeId, container.admin, container.conexion) }
    val e by vm.estado.collectAsStateWithLifecycle()
    val c = Bitacora.colores

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.volver() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver") }
            Text("Volver", style = MaterialTheme.typography.bodyLarge, color = c.textoSuave)
        }
        val d = e.detalle
        when {
            e.sinConexion -> Centro { EstadoVacio(TITULO_SIN_CONEXION, TEXTO_SIN_CONEXION, accion = "Reintentar", onAccion = vm::reintentar) }
            d == null && e.error != null -> Centro { EstadoVacio("No se pudo cargar", e.error!!, accion = "Reintentar", onAccion = vm::reintentar) }
            d == null -> Centro { CircularProgressIndicator(color = c.acento) }
            else -> Contenido(d, e, vm)
        }
    }
}

@Composable
private fun Centro(contenido: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { contenido() }
}

@Composable
private fun Contenido(d: DetalleViajeRemoto, e: EstadoAdminDetalle, vm: AdminDetalleViewModel) {
    val c = Bitacora.colores
    val v = d.viaje
    val local = d.comoDetalleLocal()
    val viaje = local.viaje!!
    val r = CalculosViaje.resumen(viaje, local.gastos, d.camion?.rendimientoKmL)
    val estado = desdeValor(v.estado, EstadoViaje.EN_CURSO)
    val validacion = e.validacion ?: EstadoValidacion.PENDIENTE

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(bottom = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Bitácora", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f, fill = false))
            val (texto, color) = etiquetaEstado(estado, validacion)
            Etiqueta(texto, color)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            v.ruta?.let { EscudoRuta(it) }
            Text("${v.origen} a ${v.destino}", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            listOfNotNull(d.chofer, d.camion?.let { "${it.patente}, ${it.marca} ${it.modelo}" } ?: "sin camión", v.cliente).joinToString(", "),
            style = MaterialTheme.typography.bodyMedium, color = c.textoSuave,
        )
        Text(
            "Salió ${Formatos.fechaLarga(viaje.fechaSalida)}" + (viaje.fechaLlegada?.let { ". Llegó ${Formatos.fechaLarga(it)}" } ?: ""),
            style = MaterialTheme.typography.bodyMedium, color = c.textoSuave,
        )

        if (d.excedioHoras) {
            Aviso("Superó las 5 horas de manejo continuo en este viaje.", c.peligro, Modifier.padding(top = 12.dp), Icons.Outlined.Timer)
        }
        when (validacion) {
            EstadoValidacion.APROBADO -> Aviso("Bitácora aprobada.", VerdeAprobado, Modifier.padding(top = 12.dp))
            EstadoValidacion.OBSERVADO -> Aviso("Observada: ${v.comentarioValidacion ?: "sin comentario"}", c.peligro, Modifier.padding(top = 12.dp))
            EstadoValidacion.PENDIENTE -> Unit
        }

        Spacer(Modifier.height(18.dp))
        Linea("Kilómetros", r.kmRecorridos?.let { Formatos.km(it) } ?: "—")
        Linea("Duración", r.duracionMs?.let { Formatos.duracion(it) } ?: "—")
        Linea("Precio del flete", Formatos.pesos(v.precio))
        Linea("Gastos", Formatos.pesos(r.gastoTotal))
        Linea("Combustible cargado", Formatos.litros(r.litrosReales))
        r.litrosEstimados?.let { Linea("Combustible estimado", Formatos.litros(it)) }
        Linea("Margen", Formatos.pesos(r.ganancia), fuerte = true)
        v.observaciones?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(8.dp))
            Text("Nota del chofer: $it", style = MaterialTheme.typography.bodyMedium, color = c.textoSuave)
        }

        Spacer(Modifier.height(22.dp))
        Text("Hoja de ruta", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(10.dp))
        if (e.hitos.isEmpty()) {
            Text("El chofer aún no registra eventos.", style = MaterialTheme.typography.bodyMedium, color = c.textoSuave)
        } else {
            CaminoRuta(e.hitos, animarEntrada = false)
        }

        if (d.adjuntos.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            Text("Entrega", style = MaterialTheme.typography.headlineSmall)
            d.adjuntos.sortedBy { it.adjunto.tipo }.forEach { Adjunto(it) }
        }

        Spacer(Modifier.height(24.dp))
        Text("Revisión del administrador", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(10.dp))
        e.mensaje?.let { Aviso(it, VerdeAprobado, Modifier.padding(bottom = 10.dp)) }
        e.errorValidacion?.let { Aviso(it, c.peligro, Modifier.padding(bottom = 10.dp)) }
        if (e.muestraRevision) {
            CampoTexto(
                e.comentario, vm::cambiarComentario, "Qué debe corregir el chofer (para observar)",
                error = e.errorComentario, lineas = 3,
            )
            Spacer(Modifier.height(12.dp))
            BotonPrincipal("Aprobar", vm::aprobar, habilitado = e.puedeAprobar, cargando = e.guardando)
            Spacer(Modifier.height(10.dp))
            BotonSecundario("Observar", vm::observar, habilitado = e.puedeObservar)
        } else if (validacion == EstadoValidacion.APROBADO && e.mensaje == null) {
            Text("Ya está aprobada y no se puede modificar.", style = MaterialTheme.typography.bodyMedium, color = c.textoSuave)
        }
        e.motivoSinValidar?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = c.textoSuave)
        }
    }
}

@Composable
private fun Linea(etiqueta: String, valor: String, fuerte: Boolean = false) {
    val estilo = if (fuerte) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(etiqueta, style = estilo, modifier = Modifier.weight(1f))
        Text(valor, style = estilo)
    }
}

@Composable
private fun Adjunto(a: AdjuntoRemoto) {
    val c = Bitacora.colores
    val esFirma = a.adjunto.tipo == TipoAdjunto.FIRMA.valor
    Spacer(Modifier.height(12.dp))
    Text(
        if (esFirma) "Firma de ${a.adjunto.nombreReceptor ?: "quien recibió"}" else "Foto de la entrega",
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(Modifier.height(6.dp))
    val url = a.url
    if (url == null) {
        Text("El archivo aún no llega al servidor. Se verá cuando el chofer sincronice.", style = MaterialTheme.typography.bodyMedium, color = c.textoSuave)
        return
    }
    val imagen by produceState<Result<ImageBitmap>?>(null, url) {
        value = withContext(Dispatchers.IO) { runCatching { descargarImagen(url) } }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .clip(MaterialTheme.shapes.medium)
            // La firma es trazo oscuro sobre fondo transparente: necesita fondo claro.
            .background(if (esFirma) Color.White else MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        val res = imagen
        when {
            res == null -> CircularProgressIndicator(color = c.acento)
            res.isSuccess -> Image(
                res.getOrThrow(),
                contentDescription = if (esFirma) "Firma de quien recibió" else "Foto de la entrega",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
            )
            else -> Text("No se pudo cargar la imagen. Vuelve a abrir el viaje.", style = MaterialTheme.typography.bodyMedium, color = c.textoSuave, modifier = Modifier.padding(16.dp))
        }
    }
}

/** Baja la imagen de la URL firmada y la reduce a un ancho razonable para pantalla. */
private fun descargarImagen(url: String, anchoMax: Int = 1280): ImageBitmap {
    val conexion = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 20_000
    }
    val bytes = try {
        conexion.inputStream.use { it.readBytes() }
    } finally {
        conexion.disconnect()
    }
    val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, limites)
    var muestra = 1
    while (limites.outWidth / (muestra * 2) >= anchoMax) muestra *= 2
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = muestra })
        ?: error("Imagen ilegible")
    return bitmap.asImageBitmap()
}
