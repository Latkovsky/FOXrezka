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

enum class PlayerColorTheme(
    val key: String,
    val title: String,
    val accent: Color,
) {
    NEUTRAL("neutral", "Нейтральная", Color(0xFFE0E2E7)),
    BLUE("blue", "Синяя", Color(0xFF64B5F6)),
    GREEN("green", "Зелёная", Color(0xFF62F514)),
    AMBER("amber", "Янтарная", Color(0xFFFFC45B));

    companion object {
        fun fromKey(key: String?): PlayerColorTheme =
            entries.firstOrNull { it.key == key } ?: NEUTRAL
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
    accent = Color(0xFFB39DDB),
    accentSecondary = Color(0xFFD0BCFF),
    background = Color(0xFF0D0B12),
    surface = Color(0xFF191620),
    surfaceHigher = Color(0xFF282332),
    textDim = Color(0xFFB6AFC2),
    foreground = Color(0xFFF4EFFA),
    foregroundMuted = Color(0xFFD0C8DA),
    outline = Color(0xFF443B50),
)

private val WhitePalette = Palette(
    accent = Color(0xFF315EAA),
    accentSecondary = Color(0xFF547FC1),
    background = Color(0xFFF2F5FA),
    surface = Color(0xFFFFFFFF),
    surfaceHigher = Color(0xFFE5EBF5),
    textDim = Color(0xFF626D7E),
    foreground = Color(0xFF171D28),
    foregroundMuted = Color(0xFF485466),
    outline = Color(0xFFC8D2E1),
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
