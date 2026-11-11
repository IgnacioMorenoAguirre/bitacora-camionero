package com.sistemasdeinformacion.proyecto.ui.componentes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sistemasdeinformacion.proyecto.ui.theme.Bitacora

/**
 * El viaje en curso dibujado como un letrero verde de autopista: borde blanco interior,
 * escudo con la ruta y un tramo de camino con la posición del camión.
 *
 * @param avance 0..1 cuando se conoce la distancia del viaje; null muestra solo el camino.
 */
@Composable
fun LetreroRuta(
    ruta: String?,
    origen: String,
    destino: String,
    detalle: String,
    textoIzquierda: String,
    textoDerecha: String,
    modifier: Modifier = Modifier,
    avance: Float? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = Bitacora.colores
    val forma = RoundedCornerShape(16.dp)
    Box(
        modifier
            .fillMaxWidth()
            .clip(forma)
            .background(c.letrero)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(5.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .border(2.dp, c.letreroBorde, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            if (!ruta.isNullOrBlank()) {
                EscudoRuta(ruta)
                Spacer(Modifier.height(10.dp))
            }
            Text(
                "$origen a $destino",
                style = MaterialTheme.typography.displaySmall,
                color = c.letreroBorde,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(detalle, style = MaterialTheme.typography.bodyMedium, color = c.letreroBorde.copy(alpha = 0.88f))
            Spacer(Modifier.height(14.dp))
            TramoCamino(avance)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(textoIzquierda, style = MaterialTheme.typography.headlineSmall, color = c.letreroBorde)
                Text(textoDerecha, style = MaterialTheme.typography.headlineSmall, color = c.letreroBorde.copy(alpha = 0.9f))
            }
        }
    }
}

@Composable
fun EscudoRuta(ruta: String, modifier: Modifier = Modifier) {
    val c = Bitacora.colores
    Text(
        ruta.uppercase(),
        style = MaterialTheme.typography.headlineSmall,
        color = c.letrero,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(c.letreroBorde)
            .padding(horizontal = 8.dp, vertical = 1.dp),
    )
}

@Composable
private fun TramoCamino(avance: Float?) {
    val c = Bitacora.colores
    val animado = remember { Animatable(0f) }
    val duracion = if (animacionesDelSistemaActivas()) 900 else 0
    LaunchedEffect(avance) {
        animado.animateTo(avance ?: 0f, tween(duracion, easing = FastOutSlowInEasing))
    }
    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(c.letreroCamino, cornerRadius = r)
        if (avance != null) {
            drawRoundRect(c.acento, size = Size(size.width * animado.value, size.height), cornerRadius = r)
        }
        val y = size.height / 2
        drawLine(
            color = c.letreroBorde.copy(alpha = 0.5f),
            start = Offset(6.dp.toPx(), y),
            end = Offset(size.width - 6.dp.toPx(), y),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx())),
        )
    }
}
