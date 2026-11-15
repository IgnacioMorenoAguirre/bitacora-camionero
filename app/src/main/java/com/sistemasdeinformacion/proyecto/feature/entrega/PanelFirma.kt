package com.sistemasdeinformacion.proyecto.feature.entrega

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sistemasdeinformacion.proyecto.ui.componentes.BotonPrincipal
import com.sistemasdeinformacion.proyecto.ui.componentes.BotonSecundario
import com.sistemasdeinformacion.proyecto.ui.theme.Bitacora
import androidx.compose.ui.graphics.Canvas as LienzoImagen

private val Tinta = Color(0xFF111418)
private val Papel = Color.White

/**
 * Panel para que quien recibe firme con el dedo. "Borrar" limpia el trazo; "Usar firma" entrega la
 * firma como Bitmap (tinta oscura sobre fondo blanco, del tamaño del panel) para guardarla en PNG.
 */
@Composable
fun PanelFirma(onFirma: (Bitmap) -> Unit, modifier: Modifier = Modifier) {
    val trazos = remember { mutableStateListOf<List<Offset>>() }
    val actual = remember { mutableStateListOf<Offset>() }
    var tamano by remember { mutableStateOf(IntSize.Zero) }
    val grosor = with(LocalDensity.current) { 3.dp.toPx() }
    val c = Bitacora.colores

    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(Papel)
                .onSizeChanged { tamano = it }
                .semantics { contentDescription = "Panel de firma. Firma con el dedo." }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { actual.clear(); actual.add(it) },
                        onDrag = { cambio, _ -> cambio.consume(); actual.add(cambio.position) },
                        onDragEnd = { trazos.add(actual.toList()); actual.clear() },
                        onDragCancel = { trazos.add(actual.toList()); actual.clear() },
                    )
                }
                .pointerInput(Unit) {
                    // Un toque deja un punto (útil para iniciales o el punto de una "i").
                    detectTapGestures(onTap = { trazos.add(listOf(it)) })
                },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Canvas(Modifier.matchParentSize()) {
                (trazos + listOf(actual.toList())).forEach { t ->
                    if (t.isNotEmpty()) drawPath(caminoDe(t), Tinta, style = Stroke(grosor, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            if (trazos.isEmpty() && actual.isEmpty()) {
                Text(
                    "Firma aquí", style = MaterialTheme.typography.bodyLarge, color = Tinta.copy(alpha = 0.45f),
                    modifier = Modifier.padding(bottom = 14.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotonSecundario("Borrar", { trazos.clear(); actual.clear() }, Modifier.weight(1f), habilitado = trazos.isNotEmpty())
            BotonPrincipal(
                "Usar firma",
                { exportar(trazos, tamano, grosor)?.let(onFirma) },
                Modifier.weight(1f),
                habilitado = trazos.isNotEmpty(),
            )
        }
        Text(
            "Se guarda junto a la entrega y la verá el administrador.",
            style = MaterialTheme.typography.bodySmall, color = c.textoSuave, modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Un trazo de un solo punto se dibuja como un punto (segmento mínimo con punta redonda). */
private fun caminoDe(puntos: List<Offset>) = Path().apply {
    moveTo(puntos.first().x, puntos.first().y)
    if (puntos.size == 1) lineTo(puntos.first().x + 0.1f, puntos.first().y)
    else puntos.drop(1).forEach { lineTo(it.x, it.y) }
}

private fun exportar(trazos: List<List<Offset>>, tamano: IntSize, grosor: Float): Bitmap? {
    if (tamano.width <= 0 || tamano.height <= 0 || trazos.isEmpty()) return null
    val imagen = ImageBitmap(tamano.width, tamano.height)
    val lienzo = LienzoImagen(imagen)
    lienzo.drawRect(0f, 0f, tamano.width.toFloat(), tamano.height.toFloat(), Paint().apply { color = Papel })
    val tinta = Paint().apply {
        color = Tinta
        style = PaintingStyle.Stroke
        strokeWidth = grosor
        strokeCap = StrokeCap.Round
        strokeJoin = StrokeJoin.Round
        isAntiAlias = true
    }
    trazos.filter { it.isNotEmpty() }.forEach { lienzo.drawPath(caminoDe(it), tinta) }
    return imagen.asAndroidBitmap()
}
