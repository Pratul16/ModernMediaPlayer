package com.pratul.mmplayer.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.pratul.mmplayer.data.settings.ColorTheme

/**
 * Everything that gives a colour theme its character: the accent gradient used for selected
 * states and primary buttons, the three background glows, and how frosted the glass is.
 */
data class Palette(
    val gradient: List<Color>,
    val glowA: Color,
    val glowB: Color,
    val glowC: Color,
    val darkBackground: Color,
    val lightBackground: Color,
    /** 1 = default glass; higher = more frosted (the "Glass" theme). */
    val frost: Float = 1f,
) {
    val accent: Color get() = gradient.first()
    val accentEnd: Color get() = gradient.last()
}

val LocalPalette = staticCompositionLocalOf { paletteFor(ColorTheme.AURORA) }

fun paletteFor(theme: ColorTheme): Palette = when (theme) {
    ColorTheme.AURORA -> Palette(
        gradient = listOf(Color(0xFF8B5CF6), Color(0xFF6366F1), Color(0xFF22D3EE)),
        glowA = Color(0xFF8B5CF6), glowB = Color(0xFF22D3EE), glowC = Color(0xFFF472B6),
        darkBackground = Color(0xFF05050C), lightBackground = Color(0xFFF5F3FF),
    )
    // Apple-style "liquid glass": pastel light, near-black dark, iOS system blue → purple.
    ColorTheme.GLASS -> Palette(
        gradient = listOf(Color(0xFF0A84FF), Color(0xFF5E5CE6), Color(0xFFBF5AF2)),
        glowA = Color(0xFF64D2FF), glowB = Color(0xFFFF9F0A), glowC = Color(0xFFBF5AF2),
        darkBackground = Color(0xFF000000), lightBackground = Color(0xFFF2F2F7),
        frost = 1.7f,
    )
    ColorTheme.SUNSET -> Palette(
        gradient = listOf(Color(0xFFFB923C), Color(0xFFEC4899), Color(0xFF8B5CF6)),
        glowA = Color(0xFFF97316), glowB = Color(0xFFEC4899), glowC = Color(0xFF8B5CF6),
        darkBackground = Color(0xFF0C0608), lightBackground = Color(0xFFFFF5F0),
    )
    ColorTheme.OCEAN -> Palette(
        gradient = listOf(Color(0xFF22D3EE), Color(0xFF3B82F6), Color(0xFF6366F1)),
        glowA = Color(0xFF0EA5E9), glowB = Color(0xFF6366F1), glowC = Color(0xFF2DD4BF),
        darkBackground = Color(0xFF020810), lightBackground = Color(0xFFF0F7FF),
    )
    ColorTheme.EMERALD -> Palette(
        gradient = listOf(Color(0xFF34D399), Color(0xFF14B8A6), Color(0xFFA3E635)),
        glowA = Color(0xFF10B981), glowB = Color(0xFF84CC16), glowC = Color(0xFF14B8A6),
        darkBackground = Color(0xFF030A07), lightBackground = Color(0xFFF1FBF5),
    )
}

/** The Material colour scheme for a palette, derived from the Aurora base schemes. */
fun colorSchemeFor(palette: Palette, dark: Boolean): ColorScheme {
    val base = if (dark) DarkColors else LightColors
    val bg = if (dark) palette.darkBackground else palette.lightBackground
    val primary = if (dark) lighten(palette.accent, 0.25f) else darken(palette.accent, 0.15f)
    val secondary = if (dark) lighten(palette.accentEnd, 0.15f) else darken(palette.accentEnd, 0.25f)
    return base.copy(
        primary = primary,
        onPrimary = if (dark) darken(palette.accent, 0.7f) else Color.White,
        primaryContainer = if (dark) darken(palette.accent, 0.55f) else lighten(palette.accent, 0.8f),
        onPrimaryContainer = if (dark) lighten(palette.accent, 0.8f) else darken(palette.accent, 0.7f),
        secondary = secondary,
        secondaryContainer = if (dark) darken(palette.accentEnd, 0.6f) else lighten(palette.accentEnd, 0.8f),
        onSecondaryContainer = if (dark) lighten(palette.accentEnd, 0.8f) else darken(palette.accentEnd, 0.7f),
        tertiary = if (dark) lighten(palette.glowC, 0.2f) else darken(palette.glowC, 0.2f),
        background = bg,
        surface = bg,
        surfaceDim = bg,
    )
}

private fun lighten(c: Color, amount: Float) = Color(
    red = c.red + (1 - c.red) * amount,
    green = c.green + (1 - c.green) * amount,
    blue = c.blue + (1 - c.blue) * amount,
)

private fun darken(c: Color, amount: Float) = Color(
    red = c.red * (1 - amount),
    green = c.green * (1 - amount),
    blue = c.blue * (1 - amount),
)
