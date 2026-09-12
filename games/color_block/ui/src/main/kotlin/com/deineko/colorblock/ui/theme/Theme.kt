package com.deineko.colorblock.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Same token set as the HTML preview, translated to Material3 color roles.
private val LightColors = lightColorScheme(
    primary = Color(0xFF128F88),
    onPrimary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCEFEC),
    onSecondaryContainer = Color(0xFF0B6E68),
    background = Color(0xFFFBF6EC),
    onBackground = Color(0xFF2B2620),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF2B2620),
    onSurfaceVariant = Color(0xFF7A7266),
    outline = Color(0xFFEAE0C8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF3FD9C7),
    onPrimary = Color(0xFF12302C),
    secondaryContainer = Color(0xFF1E3C39),
    onSecondaryContainer = Color(0xFF6BE6D7),
    background = Color(0xFF17130F),
    onBackground = Color(0xFFF3ECDF),
    surface = Color(0xFF221C16),
    onSurface = Color(0xFFF3ECDF),
    onSurfaceVariant = Color(0xFFA79C89),
    outline = Color(0xFF3A2F22),
)

/** Board/piece colors that stay constant across themes -- physical brick colors, not UI chrome. */
object GamePalette {
    val baseplateLight = Color(0xFFE8DFC8)
    val baseplateStudLight = Color(0xFFD8CBA9)
    val baseplateDark = Color(0xFF2E2820)
    val baseplateStudDark = Color(0xFF3D362A)

    val world = listOf(
        Color(0xFF57C6B4), Color(0xFF4FA8D8), Color(0xFFE8B84B), Color(0xFFE38A4E), Color(0xFFA57BD1),
        Color(0xFF57C6B4), Color(0xFF4FA8D8), Color(0xFFE8B84B), Color(0xFFE38A4E), Color(0xFFA57BD1),
    )
}

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp),
)

@Composable
fun ColorBlockTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}
