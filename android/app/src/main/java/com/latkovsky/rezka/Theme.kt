package com.latkovsky.rezka

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFFC82DDE)
val AccentTeal = Color(0xFF3A92A6)
val Bg = Color(0xFF0B0B10)
val SurfaceDark = Color(0xFF15151D)
val SurfaceHigher = Color(0xFF1F1F2A)
val TextDim = Color(0xFF9AA0AE)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = AccentTeal,
    onSecondary = Color.White,
    background = Bg,
    onBackground = Color(0xFFEDEDF2),
    surface = SurfaceDark,
    onSurface = Color(0xFFEDEDF2),
    surfaceVariant = SurfaceHigher,
    onSurfaceVariant = Color(0xFFC3C7D1),
    outline = Color(0xFF3A3A48),
)

@Composable
fun RezkaTheme(content: @Composable () -> Unit) {
    // always dark: it is a cinema app
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()
    MaterialTheme(colorScheme = DarkColors, content = content)
}
