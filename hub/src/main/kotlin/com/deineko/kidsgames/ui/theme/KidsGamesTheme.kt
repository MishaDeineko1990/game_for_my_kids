package com.deineko.kidsgames.ui.theme

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

private val LightColors = lightColorScheme(
    primary = Color(0xFFFF6F59),
    onPrimary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCEFEC),
    onSecondaryContainer = Color(0xFF0B6E68),
    background = Color(0xFFFBF6EC),
    onBackground = Color(0xFF2B2620),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF2B2620),
    onSurfaceVariant = Color(0xFF7A7266),
    outline = Color(0xFFEAE0C8),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF8B77),
    onPrimary = Color(0xFF4A1508),
    secondaryContainer = Color(0xFF1E3C39),
    onSecondaryContainer = Color(0xFF6BE6D7),
    background = Color(0xFF17130F),
    onBackground = Color(0xFFF3ECDF),
    surface = Color(0xFF221C16),
    onSurface = Color(0xFFF3ECDF),
    onSurfaceVariant = Color(0xFFA79C89),
    outline = Color(0xFF3A2F22),
    error = Color(0xFFFFB4AB),
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 11.sp),
)

@Composable
fun KidsGamesTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}
