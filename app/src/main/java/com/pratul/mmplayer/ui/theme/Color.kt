package com.pratul.mmplayer.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Timeless, sober Material 3 design palette: clean indigo/blue accents, deep neutral dark surfaces,
// high legibility and zero gaudy neon effects.

val SoberPrimary = Color(0xFF635BFF)
val SoberSecondary = Color(0xFF38BDF8)
val SoberAccent = Color(0xFF818CF8)

/** Sober accent gradient: subtle indigo to deep blue. */
val SoberGradient = listOf(Color(0xFF635BFF), Color(0xFF4F46E5), Color(0xFF3B82F6))

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF818CF8),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF075985),
    secondaryContainer = Color(0xFF0369A1),
    onSecondaryContainer = Color(0xFFE0F2FE),
    tertiary = Color(0xFF34D399),
    onTertiary = Color(0xFF064E3B),
    tertiaryContainer = Color(0xFF047857),
    onTertiaryContainer = Color(0xFFECFDF5),
    background = Color(0xFF0F1015),
    onBackground = Color(0xFFF3F4F6),
    surface = Color(0xFF161720),
    onSurface = Color(0xFFF3F4F6),
    surfaceVariant = Color(0xFF222432),
    onSurfaceVariant = Color(0xFF9CA3AF),
    outline = Color(0xFF374151),
    outlineVariant = Color(0xFF1F2937),
    surfaceDim = Color(0xFF0F1015),
    surfaceBright = Color(0xFF282A3A),
    surfaceContainerLowest = Color(0xFF0B0C10),
    surfaceContainerLow = Color(0xFF12131A),
    surfaceContainer = Color(0xFF1A1B24),
    surfaceContainerHigh = Color(0xFF21222E),
    surfaceContainerHighest = Color(0xFF2A2B3A),
    inverseSurface = Color(0xFFF3F4F6),
    inverseOnSurface = Color(0xFF111827),
)

internal val LightColors = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEEF2FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF0284C7),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF075985),
    tertiary = Color(0xFF059669),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFECFDF5),
    onTertiaryContainer = Color(0xFF064E3B),
    background = Color(0xFFF9FAFB),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFF3F4F6),
    onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFFD1D5DB),
    outlineVariant = Color(0xFFE5E7EB),
    surfaceDim = Color(0xFFE5E7EB),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9FAFB),
    surfaceContainer = Color(0xFFF3F4F6),
    surfaceContainerHigh = Color(0xFFE5E7EB),
    surfaceContainerHighest = Color(0xFFD1D5DB),
)
