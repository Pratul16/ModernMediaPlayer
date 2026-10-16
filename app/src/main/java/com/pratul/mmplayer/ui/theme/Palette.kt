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
        gradient = listOf(Color(0xFF635BFF), Color(0xFF4F46E5), Color(0xFF3B82F6)),
        glowA = Color(0xFF635BFF), glowB = Color(0xFF38BDF8), glowC = Color(0xFF818CF8),
        darkBackground = Color(0xFF0F1015), lightBackground = Color(0xFFF9FAFB),
    )
    ColorTheme.GLASS -> Palette(
        gradient = listOf(Color(0xFF0A84FF), Color(0xFF5E5CE6), Color(0xFF007AFF)),
        glowA = Color(0xFF0A84FF), glowB = Color(0xFF5E5CE6), glowC = Color(0xFF007AFF),
        darkBackground = Color(0xFF101015), lightBackground = Color(0xFFF2F2F7),
        frost = 1.0f,
    )
    ColorTheme.SUNSET -> Palette(
        gradient = listOf(Color(0xFFEA580C), Color(0xFFDC2626), Color(0xFFE11D48)),
        glowA = Color(0xFFEA580C), glowB = Color(0xFFDC2626), glowC = Color(0xFFE11D48),
        darkBackground = Color(0xFF140E10), lightBackground = Color(0xFFFFF5F0),
    )
    ColorTheme.OCEAN -> Palette(
        gradient = listOf(Color(0xFF0284C7), Color(0xFF2563EB), Color(0xFF1D4ED8)),
        glowA = Color(0xFF0284C7), glowB = Color(0xFF2563EB), glowC = Color(0xFF1D4ED8),
        darkBackground = Color(0xFF0C131A), lightBackground = Color(0xFFF0F7FF),
    )
    ColorTheme.EMERALD -> Palette(
        gradient = listOf(Color(0xFF059669), Color(0xFF0D9488), Color(0xFF10B981)),
        glowA = Color(0xFF059669), glowB = Color(0xFF0D9488), glowC = Color(0xFF10B981),
        darkBackground = Color(0xFF0A120E), lightBackground = Color(0xFFF1FBF5),
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
