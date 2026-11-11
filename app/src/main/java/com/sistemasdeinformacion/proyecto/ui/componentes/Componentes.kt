package com.sistemasdeinformacion.proyecto.ui.componentes

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sistemasdeinformacion.proyecto.sync.EstadoSync
import com.sistemasdeinformacion.proyecto.ui.theme.Bitacora

/** true si el usuario no desactivó las animaciones del sistema (accesibilidad). */
@Composable
fun animacionesDelSistemaActivas(): Boolean {
    val ctx = LocalContext.current
    return remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}

/** Acción principal de la pantalla: ámbar, grande, una por pantalla. */
@Composable
fun BotonPrincipal(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cargando: Boolean = false,
    habilitado: Boolean = true,
    icono: ImageVector? = null,
) {
    val c = Bitacora.colores
    Button(
        onClick = onClick,
        enabled = habilitado && !cargando,
        modifier = modifier.fillMaxWidth().heightIn(min = 58.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = c.acento,
            contentColor = c.sobreAcento,
            disabledContainerColor = c.acento.copy(alpha = 0.35f),
            disabledContentColor = c.sobreAcento.copy(alpha = 0.6f),
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    ) {
        if (cargando) {
            CircularProgressIndicator(Modifier.size(22.dp), color = c.sobreAcento, strokeWidth = 2.5.dp)
        } else {
            if (icono != null) {
                Icon(icono, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(texto, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Acción secundaria con borde, para acciones importantes que no son la principal. */
@Composable
fun BotonSecundario(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, habilitado: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = habilitado,
        modifier = modifier.fillMaxWidth().heightIn(min = 54.dp),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = if (habilitado) 1f else 0.3f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
    ) {
        Text(texto, style = MaterialTheme.typography.titleMedium)
    }
}

/** Botón cuadrado de acción rápida (Combustible, Peaje, Novedad). */
@Composable
fun AccionRapida(texto: String, icono: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, habilitado: Boolean = true) {
    val c = Bitacora.colores
    Column(
        modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(enabled = habilitado, role = Role.Button, onClick = onClick)
            .alpha(if (habilitado) 1f else 0.4f)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icono, contentDescription = null, tint = c.acento, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(texto, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground)
    }
}

private enum class FaseSync { SIN_SENAL, ENVIANDO, PENDIENTE, AL_DIA }

/** "Sin señal. 3 registros se enviarán solos." / "Enviando…" / "Todo enviado". */
@Composable
fun IndicadorSync(estado: EstadoSync, modifier: Modifier = Modifier) {
    val c = Bitacora.colores
    val fase = when {
        estado.enviando -> FaseSync.ENVIANDO
        estado.pendientes == 0 -> FaseSync.AL_DIA
        !estado.conectado -> FaseSync.SIN_SENAL
        else -> FaseSync.PENDIENTE
    }
    AnimatedContent(
        targetState = fase to estado.pendientes,
        transitionSpec = {
            (fadeIn(tween(220)) + slideInVertically { it / 2 }) togetherWith (fadeOut(tween(160)) + slideOutVertically { -it / 2 })
        },
        label = "indicadorSync",
        modifier = modifier,
    ) { (f, n) ->
        val (color, texto) = when (f) {
            FaseSync.SIN_SENAL -> c.acento to if (n == 1) "Sin señal. 1 registro se enviará solo." else "Sin señal. $n registros se enviarán solos."
            FaseSync.PENDIENTE -> c.acento to if (n == 1) "1 registro por enviar." else "$n registros por enviar."
            FaseSync.ENVIANDO -> c.textoSuave to "Enviando…"
            FaseSync.AL_DIA -> Color(0xFF4CD97B) to "Todo enviado"
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            PuntoEstado(color, pulsar = f == FaseSync.ENVIANDO)
            Spacer(Modifier.width(8.dp))
            Text(texto, style = MaterialTheme.typography.bodyMedium, color = color)
        }
    }
}

@Composable
private fun PuntoEstado(color: Color, pulsar: Boolean) {
    val alfa = if (pulsar) {
        val t = rememberInfiniteTransition(label = "pulso")
        val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "alfa")
        a
    } else 1f
    Box(Modifier.size(9.dp).alpha(alfa).clip(CircleShape).background(color))
}

/** Campo de texto con etiqueta arriba y error debajo. */
@Composable
fun CampoTexto(
    valor: String,
    onCambio: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    teclado: KeyboardType = KeyboardType.Text,
    mayusculas: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    contrasena: Boolean = false,
    sufijo: String? = null,
    lineas: Int = 1,
    imeAction: ImeAction = ImeAction.Next,
    onListo: (() -> Unit)? = null,
) {
    val c = Bitacora.colores
    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = valor,
            onValueChange = onCambio,
            label = { Text(etiqueta) },
            isError = error != null,
            singleLine = lineas == 1,
            minLines = lineas,
            suffix = sufijo?.let { { Text(it) } },
            visualTransformation = if (contrasena) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (contrasena) KeyboardType.Password else teclado,
                capitalization = mayusculas,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(onDone = { onListo?.invoke() }),
            shape = MaterialTheme.shapes.small,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = c.acento,
                focusedLabelColor = c.acento,
                cursorColor = c.acento,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        }
    }
}

/** Pantalla vacía que dice qué hacer. */
@Composable
fun EstadoVacio(titulo: String, texto: String, modifier: Modifier = Modifier, accion: String? = null, onAccion: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(titulo, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(texto, style = MaterialTheme.typography.bodyLarge, color = Bitacora.colores.textoSuave, textAlign = TextAlign.Center)
        if (accion != null && onAccion != null) {
            Spacer(Modifier.height(20.dp))
            BotonPrincipal(accion, onAccion)
        }
    }
}

/** Aviso destacado (alerta de horas, viaje restaurado). */
@Composable
fun Aviso(texto: String, color: Color, modifier: Modifier = Modifier, icono: ImageVector? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icono != null) {
            Icon(icono, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

/** Grupo de opciones tipo "segmentado" con chips grandes. */
@Composable
fun <T> SelectorOpciones(opciones: List<T>, seleccion: T?, etiqueta: (T) -> String, onElegir: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = Bitacora.colores
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        opciones.forEach { op ->
            val elegido = op == seleccion
            Text(
                etiqueta(op),
                style = MaterialTheme.typography.labelLarge,
                color = if (elegido) c.sobreAcento else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (elegido) c.acento else MaterialTheme.colorScheme.surfaceContainer)
                    .clickable(role = Role.RadioButton) { onElegir(op) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}
