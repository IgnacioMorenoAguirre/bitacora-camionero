package com.sistemasdeinformacion.proyecto.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sistemasdeinformacion.proyecto.R

/** Tokens de la dirección "Señalética de autopista". */
object Paleta {
    val asfalto = Color(0xFF1C1F22)
    val asfaltoProfundo = Color(0xFF16191B)
    val superficie = Color(0xFF26292D)
    val superficieAlta = Color(0xFF2F3337)
    val verdeSenal = Color(0xFF0E6B4C)
    val verdeSenalOscuro = Color(0xFF0A4F38)
    val ambar = Color(0xFFF2A900)
    val blancoReflectante = Color(0xFFF3F2EC)
    val grisBerma = Color(0xFFA9AFB6)
    val grisBermaClaro = Color(0xFFC9CDD1)
    val rojoPare = Color(0xFFC8102E)
    // Tema claro
    val concreto = Color(0xFFECEDEA)
    val tinta = Color(0xFF1C1F22)
    val grisTexto = Color(0xFF5A6067)
}

/** Colores propios que Material 3 no cubre (letrero, camino, sincronización). */
@Immutable
data class ColoresBitacora(
    val letrero: Color,
    val letreroBorde: Color,
    val letreroCamino: Color,
    val acento: Color,
    val sobreAcento: Color,
    val camino: Color,
    val textoSuave: Color,
    val peligro: Color,
    val esOscuro: Boolean,
)

val LocalColoresBitacora = staticCompositionLocalOf {
    ColoresBitacora(
        Paleta.verdeSenal, Paleta.blancoReflectante, Paleta.verdeSenalOscuro, Paleta.ambar, Paleta.asfalto,
        Paleta.superficieAlta, Paleta.grisBerma, Paleta.rojoPare, true,
    )
}

val Barlow = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_bold, FontWeight.Bold),
)

val BarlowCondensed = FontFamily(
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
    Font(R.font.barlow_condensed_extrabold, FontWeight.ExtraBold),
)

private val tipografia = Typography(
    displayLarge = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, lineHeight = 46.sp),
    displayMedium = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, lineHeight = 38.sp),
    displaySmall = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 32.sp),
    headlineLarge = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 30.sp),
    headlineMedium = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 27.sp),
    headlineSmall = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 23.sp),
    titleLarge = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 29.sp),
    titleMedium = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

private val formas = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val esquemaOscuro = darkColorScheme(
    primary = Paleta.ambar,
    onPrimary = Paleta.asfalto,
    secondary = Paleta.verdeSenal,
    onSecondary = Paleta.blancoReflectante,
    background = Paleta.asfalto,
    onBackground = Paleta.blancoReflectante,
    surface = Paleta.asfalto,
    onSurface = Paleta.blancoReflectante,
    surfaceVariant = Paleta.superficie,
    onSurfaceVariant = Paleta.grisBerma,
    surfaceContainerLowest = Paleta.asfaltoProfundo,
    surfaceContainerLow = Paleta.superficie,
    surfaceContainer = Paleta.superficie,
    surfaceContainerHigh = Paleta.superficieAlta,
    surfaceContainerHighest = Paleta.superficieAlta,
    outline = Color(0xFF4A4F55),
    outlineVariant = Paleta.superficieAlta,
    error = Color(0xFFFF6B6B),
    onError = Paleta.asfalto,
)

private val esquemaClaro = lightColorScheme(
    primary = Paleta.verdeSenal,
    onPrimary = Paleta.blancoReflectante,
    secondary = Paleta.verdeSenal,
    onSecondary = Paleta.blancoReflectante,
    background = Paleta.concreto,
    onBackground = Paleta.tinta,
    surface = Paleta.concreto,
    onSurface = Paleta.tinta,
    surfaceVariant = Color.White,
    onSurfaceVariant = Paleta.grisTexto,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF6F6F3),
    surfaceContainerHighest = Color(0xFFE2E4E1),
    outline = Color(0xFFB9BDB8),
    outlineVariant = Color(0xFFDCDEDA),
    error = Paleta.rojoPare,
)

@Composable
fun TemaBitacora(claro: Boolean = false, content: @Composable () -> Unit) {
    val propios = if (claro) {
        ColoresBitacora(
            letrero = Paleta.verdeSenal, letreroBorde = Paleta.blancoReflectante, letreroCamino = Paleta.verdeSenalOscuro,
            acento = Paleta.ambar, sobreAcento = Paleta.asfalto, camino = Color(0xFFD5D8D3),
            textoSuave = Paleta.grisTexto, peligro = Paleta.rojoPare, esOscuro = false,
        )
    } else {
        ColoresBitacora(
            letrero = Paleta.verdeSenal, letreroBorde = Paleta.blancoReflectante, letreroCamino = Paleta.verdeSenalOscuro,
            acento = Paleta.ambar, sobreAcento = Paleta.asfalto, camino = Paleta.superficieAlta,
            textoSuave = Paleta.grisBerma, peligro = Color(0xFFFF6B6B), esOscuro = true,
        )
    }
    CompositionLocalProvider(LocalColoresBitacora provides propios) {
        MaterialTheme(
            colorScheme = if (claro) esquemaClaro else esquemaOscuro,
            typography = tipografia,
            shapes = formas,
            content = content,
        )
    }
}

/** Acceso corto: `Bitacora.colores.acento`. */
object Bitacora {
    val colores: ColoresBitacora
        @Composable get() = LocalColoresBitacora.current
}
