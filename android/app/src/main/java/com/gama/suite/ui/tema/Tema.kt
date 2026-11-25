package com.gama.suite.ui.tema

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gama.comun.R

// Tokens del sistema de diseño GAMA.
object Gama {
    val Primario = Color(0xFF0B6E6E)
    val PrimarioOscuro = Color(0xFF08504F)
    val PrimarioClaro = Color(0xFF7FCFCB)
    val Acento = Color(0xFFF2A900)
    val Texto = Color(0xFF1E2329)
    val Superficie = Color(0xFFF5F6F7)
    val Tarjeta = Color(0xFFFFFFFF)
    val SuperficieOscura = Color(0xFF101417)
    val TarjetaOscura = Color(0xFF171C20)
    val Ok = Color(0xFF1E8E3E)
    val Alerta = Color(0xFFB45F00)
    val Critico = Color(0xFFC5221F)
}

/** IBM Plex Sans (SIL Open Font License 1.1), incluida en la app: no depende de Internet. */
private val Plex = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_bold, FontWeight.Bold),
)

private fun Typography.conFuente(f: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = f),
    displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f),
    headlineLarge = headlineLarge.copy(fontFamily = f, fontWeight = FontWeight.SemiBold),
    headlineMedium = headlineMedium.copy(fontFamily = f, fontWeight = FontWeight.SemiBold),
    headlineSmall = headlineSmall.copy(fontFamily = f, fontWeight = FontWeight.SemiBold),
    titleLarge = titleLarge.copy(fontFamily = f, fontWeight = FontWeight.SemiBold),
    titleMedium = titleMedium.copy(fontFamily = f, fontWeight = FontWeight.SemiBold),
    titleSmall = titleSmall.copy(fontFamily = f, fontWeight = FontWeight.SemiBold),
    bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f),
    bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f, fontWeight = FontWeight.SemiBold),
    labelMedium = labelMedium.copy(fontFamily = f, fontWeight = FontWeight.Medium),
    labelSmall = labelSmall.copy(fontFamily = f, fontWeight = FontWeight.Medium),
)

private val Tipografia = Typography().conFuente(Plex)

private val Formas = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

private val Claro = lightColorScheme(
    primary = Gama.Primario,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE8E6),
    onPrimaryContainer = Color(0xFF00201F),
    secondary = Gama.Acento,
    onSecondary = Gama.Texto,
    secondaryContainer = Color(0xFFFFE8B3),
    onSecondaryContainer = Color(0xFF3D2C00),
    tertiary = Color(0xFF3D5A80),
    onTertiary = Color.White,
    background = Gama.Superficie,
    onBackground = Gama.Texto,
    surface = Gama.Tarjeta,
    onSurface = Gama.Texto,
    surfaceVariant = Color(0xFFE6E9EB),
    onSurfaceVariant = Color(0xFF4A535C),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8F9FA),
    surfaceContainer = Color(0xFFF1F3F4),
    surfaceContainerHigh = Color(0xFFECEEF0),
    surfaceContainerHighest = Color(0xFFE6E9EB),
    outline = Color(0xFFC3CAD0),
    outlineVariant = Color(0xFFDDE3E8),
    error = Gama.Critico,
    onError = Color.White,
)

private val Oscuro = darkColorScheme(
    primary = Gama.PrimarioClaro,
    onPrimary = Color(0xFF003735),
    primaryContainer = Color(0xFF0B5352),
    onPrimaryContainer = Color(0xFFCDE8E6),
    secondary = Gama.Acento,
    onSecondary = Gama.Texto,
    secondaryContainer = Color(0xFF5C4300),
    onSecondaryContainer = Color(0xFFFFE8B3),
    tertiary = Color(0xFFA9C7F0),
    onTertiary = Color(0xFF0B2847),
    background = Gama.SuperficieOscura,
    onBackground = Color(0xFFE8EAED),
    surface = Gama.TarjetaOscura,
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF2A3037),
    onSurfaceVariant = Color(0xFFB8C0C8),
    surfaceContainerLowest = Color(0xFF0B0F12),
    surfaceContainerLow = Color(0xFF14191C),
    surfaceContainer = Color(0xFF1C2226),
    surfaceContainerHigh = Color(0xFF232A2F),
    surfaceContainerHighest = Color(0xFF2A3037),
    outline = Color(0xFF6B757E),
    outlineVariant = Color(0xFF3A424A),
    error = Color(0xFFF28B82),
    onError = Color(0xFF601410),
)

/** Altura mínima de los controles táctiles: 48 dp en móvil, 64 dp en terminal (uso con guantes). */
val LocalTactil = staticCompositionLocalOf<Dp> { 48.dp }

@Composable
fun TemaGama(terminal: Boolean, contenido: @Composable () -> Unit) {
    // La terminal de planta usa siempre el tema oscuro (menos reflejos y fatiga); el móvil sigue al sistema.
    val oscuro = terminal || isSystemInDarkTheme()
    CompositionLocalProvider(LocalTactil provides if (terminal) 64.dp else 48.dp) {
        MaterialTheme(
            colorScheme = if (oscuro) Oscuro else Claro,
            typography = Tipografia,
            shapes = Formas,
            content = contenido,
        )
    }
}
