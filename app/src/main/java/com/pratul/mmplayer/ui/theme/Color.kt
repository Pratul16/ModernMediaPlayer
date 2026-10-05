package com.pratul.mmplayer.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// "Aurora": near-black deep-space surfaces lit by violet, cyan and magenta glows, with a neon
// violet→cyan gradient for everything interactive. Dark is the signature look; light keeps the
// same accents on a soft lavender base.

val NeonViolet = Color(0xFF8B5CF6)
val NeonIndigo = Color(0xFF6366F1)
val NeonCyan = Color(0xFF22D3EE)
val NeonPink = Color(0xFFF472B6)
val NeonMint = Color(0xFF5EEAD4)

/** The accent gradient used for selected states, primary buttons, progress and the logo. */
val NeonGradient = listOf(NeonViolet, NeonIndigo, NeonCyan)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFB4A0FF),
    onPrimary = Color(0xFF1A0B4D),
    primaryContainer = Color(0xFF34237A),
    onPrimaryContainer = Color(0xFFE7E0FF),
    secondary = NeonCyan,
    onSecondary = Color(0xFF00313A),
    secondaryContainer = Color(0xFF0B3C47),
    onSecondaryContainer = Color(0xFFB8F3FF),
    tertiary = NeonPink,
    onTertiary = Color(0xFF4A0A2C),
    tertiaryContainer = Color(0xFF5B1A3D),
    onTertiaryContainer = Color(0xFFFFD8EA),
    background = Color(0xFF05050C),
    onBackground = Color(0xFFEDEBFF),
    surface = Color(0xFF07070F),
    onSurface = Color(0xFFEDEBFF),
    surfaceVariant = Color(0xFF1C1B2E),
    onSurfaceVariant = Color(0xFFA9A5C9),
    outline = Color(0xFF3D3A5C),
    outlineVariant = Color(0xFF242238),
    surfaceDim = Color(0xFF05050C),
    surfaceBright = Color(0xFF26243A),
    surfaceContainerLowest = Color(0xFF040409),
    surfaceContainerLow = Color(0xFF0B0B16),
    surfaceContainer = Color(0xFF11111F),
    surfaceContainerHigh = Color(0xFF171729),
    surfaceContainerHighest = Color(0xFF1E1E33),
    inverseSurface = Color(0xFFEDEBFF),
    inverseOnSurface = Color(0xFF12111F),
)

internal val LightColors = lightColorScheme(
    primary = Color(0xFF6D28D9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE9E1FF),
    onPrimaryContainer = Color(0xFF250A63),
    secondary = Color(0xFF0E7490),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDF6FF),
    onSecondaryContainer = Color(0xFF002A33),
    tertiary = Color(0xFFBE185D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9E8),
    onTertiaryContainer = Color(0xFF3E0020),
    background = Color(0xFFF5F3FF),
    onBackground = Color(0xFF14122A),
    surface = Color(0xFFF7F5FF),
    onSurface = Color(0xFF14122A),
    surfaceVariant = Color(0xFFE6E2F5),
    onSurfaceVariant = Color(0xFF55516F),
    outline = Color(0xFF8C88A8),
    outlineVariant = Color(0xFFD3CFE6),
    surfaceDim = Color(0xFFDCD8EE),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9F7FF),
    surfaceContainer = Color(0xFFF1EEFC),
    surfaceContainerHigh = Color(0xFFEBE8F8),
    surfaceContainerHighest = Color(0xFFE5E1F4),
)
