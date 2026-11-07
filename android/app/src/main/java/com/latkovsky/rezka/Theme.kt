package com.latkovsky.rezka

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

enum class FoxThemeVariant(val key: String, val title: String) {
    DARK("dark", "Темная"),
    GREEN("green", "Зеленая"),
    WHITE("white", "Белая");

    companion object {
        fun fromKey(key: String?): FoxThemeVariant =
            entries.firstOrNull { it.key == key } ?: GREEN
    }
}

private data class Palette(
    val accent: Color,
    val accentSecondary: Color,
    val background: Color,
    val surface: Color,
    val surfaceHigher: Color,
    val textDim: Color,
    val foreground: Color,
    val foregroundMuted: Color,
    val outline: Color,
    val light: Boolean = false,
)

private val GreenPalette = Palette(
    accent = Color(0xFF62F514),
    accentSecondary = Color(0xFFB6FF59),
    background = Color(0xFF080D09),
    surface = Color(0xFF131A14),
    surfaceHigher = Color(0xFF1D281E),
    textDim = Color(0xFFA5B0A3),
    foreground = Color(0xFFEDEDF2),
    foregroundMuted = Color(0xFFC3C7D1),
    outline = Color(0xFF354238),
)

private val DarkPalette = Palette(
    accent = Color(0xFFB9A6FF),
    accentSecondary = Color(0xFF8C77D9),
    background = Color(0xFF09090D),
    surface = Color(0xFF17171F),
    surfaceHigher = Color(0xFF242430),
    textDim = Color(0xFFA5A5B5),
    foreground = Color(0xFFF1F0F7),
    foregroundMuted = Color(0xFFC8C6D3),
    outline = Color(0xFF3A3946),
)

private val WhitePalette = Palette(
    accent = Color(0xFF218A3A),
    accentSecondary = Color(0xFF66A873),
    background = Color(0xFFF5F6F4),
    surface = Color(0xFFFFFFFF),
    surfaceHigher = Color(0xFFE8ECE7),
    textDim = Color(0xFF667067),
    foreground = Color(0xFF171B18),
    foregroundMuted = Color(0xFF4F5850),
    outline = Color(0xFFD0D8D0),
    light = true,
)

private var currentPalette by mutableStateOf(GreenPalette)

val Accent: Color get() = currentPalette.accent
val AccentTeal: Color get() = currentPalette.accentSecondary
val Bg: Color get() = currentPalette.background
val SurfaceDark: Color get() = currentPalette.surface
val SurfaceHigher: Color get() = currentPalette.surfaceHigher
val TextDim: Color get() = currentPalette.textDim

fun setFoxTheme(variant: FoxThemeVariant) {
    currentPalette = when (variant) {
        FoxThemeVariant.DARK -> DarkPalette
        FoxThemeVariant.GREEN -> GreenPalette
        FoxThemeVariant.WHITE -> WhitePalette
    }
}

@Composable
fun FoxrezkaTheme(content: @Composable () -> Unit) {
    val palette = currentPalette
    val colors = if (palette.light) {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            secondary = palette.accentSecondary,
            onSecondary = Color.White,
            background = palette.background,
            onBackground = palette.foreground,
            surface = palette.surface,
            onSurface = palette.foreground,
            surfaceVariant = palette.surfaceHigher,
            onSurfaceVariant = palette.foregroundMuted,
            outline = palette.outline,
        )
    } else {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = Color.Black,
            secondary = palette.accentSecondary,
            onSecondary = Color.Black,
            background = palette.background,
            onBackground = palette.foreground,
            surface = palette.surface,
            onSurface = palette.foreground,
            surfaceVariant = palette.surfaceHigher,
            onSurfaceVariant = palette.foregroundMuted,
            outline = palette.outline,
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}
