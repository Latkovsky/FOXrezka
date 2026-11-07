package com.latkovsky.rezka

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFFFF7A1A)
val AccentTeal = Color(0xFFFFB45C)
val Bg = Color(0xFF100D0A)
val SurfaceDark = Color(0xFF1B1713)
val SurfaceHigher = Color(0xFF29221B)
val TextDim = Color(0xFFB8AA9B)

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
fun FoxrezkaTheme(content: @Composable () -> Unit) {
    // always dark: it is a cinema app
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()
    MaterialTheme(colorScheme = DarkColors, content = content)
}
