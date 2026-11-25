package com.gama.mdm

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.gama.comun.R

/** IBM Plex Sans (SIL Open Font License 1.1), la misma tipografía de la app GAMA. */
private val Plex = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_bold, FontWeight.Bold),
)

private val Tipografia = Typography().let { b ->
    Typography(
        displaySmall = b.displaySmall.copy(fontFamily = Plex),
        headlineSmall = b.headlineSmall.copy(fontFamily = Plex, fontWeight = FontWeight.SemiBold),
        titleLarge = b.titleLarge.copy(fontFamily = Plex, fontWeight = FontWeight.SemiBold),
        titleMedium = b.titleMedium.copy(fontFamily = Plex, fontWeight = FontWeight.SemiBold),
        titleSmall = b.titleSmall.copy(fontFamily = Plex, fontWeight = FontWeight.SemiBold),
        bodyLarge = b.bodyLarge.copy(fontFamily = Plex),
        bodyMedium = b.bodyMedium.copy(fontFamily = Plex),
        bodySmall = b.bodySmall.copy(fontFamily = Plex),
        labelLarge = b.labelLarge.copy(fontFamily = Plex, fontWeight = FontWeight.SemiBold),
        labelMedium = b.labelMedium.copy(fontFamily = Plex, fontWeight = FontWeight.Medium),
        labelSmall = b.labelSmall.copy(fontFamily = Plex, fontWeight = FontWeight.Medium),
    )
}

private val Esquema = lightColorScheme(
    primary = Color(0xFF0B6E6E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE8E6),
    onPrimaryContainer = Color(0xFF00201F),
    secondary = Color(0xFFF2A900),
    onSecondary = Color(0xFF1E2329),
    background = Color(0xFFF5F6F7),
    onBackground = Color(0xFF1E2329),
    surface = Color.White,
    onSurface = Color(0xFF1E2329),
    surfaceVariant = Color(0xFFE6E9EB),
    onSurfaceVariant = Color(0xFF4A535C),
    outline = Color(0xFFC3CAD0),
    error = Color(0xFFC5221F),
)

@Composable
fun TemaMdm(contenido: @Composable () -> Unit) =
    MaterialTheme(colorScheme = Esquema, typography = Tipografia, content = contenido)
