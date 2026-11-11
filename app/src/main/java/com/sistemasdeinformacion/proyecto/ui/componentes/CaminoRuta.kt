package com.sistemasdeinformacion.proyecto.ui.componentes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sistemasdeinformacion.proyecto.ui.theme.Bitacora

@Immutable
data class Hito(
    val clave: String,
    val km: Int?,
    val hora: String,
    val titulo: String,
    val detalle: String?,
    val actual: Boolean = false,
    val alerta: Boolean = false,
)

private val ANCHO_CARRIL = 52.dp

/**
 * Hoja de ruta como un camino vertical con hitos kilométricos. Al entrar, la línea ámbar
 * se dibuja desde el inicio hasta el hito actual (el único movimiento "de escena" de la app).
 */
@Composable
fun CaminoRuta(hitos: List<Hito>, modifier: Modifier = Modifier, animarEntrada: Boolean = true) {
    val c = Bitacora.colores
    val progreso = remember { Animatable(if (animarEntrada) 0f else 1f) }
    var yObjetivo by remember { mutableFloatStateOf(0f) }
    val animar = animarEntrada && animacionesDelSistemaActivas()
    val centroMojon = with(LocalDensity.current) { 12.dp.toPx() }

    LaunchedEffect(hitos.size) {
        if (animar) progreso.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) else progreso.snapTo(1f)
    }

    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val x = ANCHO_CARRIL.toPx() / 2
                val anchoCalzada = 12.dp.toPx()
                drawRoundRect(
                    color = c.camino,
                    topLeft = Offset(x - anchoCalzada / 2, 4.dp.toPx()),
                    size = Size(anchoCalzada, (size.height - 8.dp.toPx()).coerceAtLeast(0f)),
                    cornerRadius = CornerRadius(anchoCalzada / 2),
                )
                val fin = (if (yObjetivo > 0f) yObjetivo else size.height) * progreso.value
                if (fin > 8.dp.toPx()) {
                    drawLine(
                        color = c.acento,
                        start = Offset(x, 8.dp.toPx()),
                        end = Offset(x, fin),
                        strokeWidth = 2.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
                    )
                }
            }
    ) {
        hitos.forEach { hito ->
            FilaHito(
                hito,
                Modifier.onGloballyPositioned { coords ->
                    if (hito.actual) yObjetivo = coords.positionInParent().y + centroMojon
                },
            )
        }
    }
}

@Composable
private fun FilaHito(hito: Hito, modifier: Modifier = Modifier) {
    val c = Bitacora.colores
    val colorTitulo = when {
        hito.alerta -> c.peligro
        hito.actual -> c.acento
        else -> MaterialTheme.colorScheme.onBackground
    }
    Row(modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        Box(Modifier.width(ANCHO_CARRIL), contentAlignment = Alignment.TopCenter) {
            HitoKm(hito.km, hito.actual)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(hito.hora, style = MaterialTheme.typography.bodySmall, color = c.textoSuave)
            Text(hito.titulo, style = MaterialTheme.typography.titleMedium, color = colorTitulo)
            if (!hito.detalle.isNullOrBlank()) {
                Text(hito.detalle, style = MaterialTheme.typography.bodyMedium, color = c.textoSuave)
            }
        }
    }
}

/** Mojón kilométrico: verde con borde blanco; el actual en ámbar. */
@Composable
fun HitoKm(km: Int?, actual: Boolean, modifier: Modifier = Modifier) {
    val c = Bitacora.colores
    val fondo = if (actual) c.acento else c.letrero
    val texto = if (actual) c.sobreAcento else c.letreroBorde
    Text(
        text = km?.toString() ?: "·",
        style = MaterialTheme.typography.labelLarge.copy(fontFamily = com.sistemasdeinformacion.proyecto.ui.theme.BarlowCondensed),
        color = texto,
        textAlign = TextAlign.Center,
        modifier = modifier
            .widthIn(min = 40.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(fondo)
            .border(1.5.dp, if (actual) c.acento else c.letreroBorde, RoundedCornerShape(5.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}
