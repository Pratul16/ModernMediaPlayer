package com.pratul.mmplayer.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pratul.mmplayer.data.settings.ColorTheme

/** Whether the Aurora theme is in its dark form; glass and glow effects adapt to it. */
val LocalAuroraDark = staticCompositionLocalOf { true }

private val Base = Typography()

// Tight, heavy display type for a modern, "UI of the future" feel; comfortable body text.
private val AuroraTypography = Base.copy(
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.1).sp),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    labelMedium = Base.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
    labelSmall = Base.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
)

private val AuroraShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun ModernMediaTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    colorTheme: ColorTheme = ColorTheme.AURORA,
    content: @Composable () -> Unit,
) {
    val palette = paletteFor(colorTheme)
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> colorSchemeFor(palette, darkTheme)
    }
    CompositionLocalProvider(LocalAuroraDark provides darkTheme, LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AuroraTypography,
            shapes = AuroraShapes,
        ) {
            // Screens draw straight onto the aurora (no opaque Surface), so set the default
            // text/icon colour here; otherwise text outside a Surface would fall back to black.
            CompositionLocalProvider(LocalContentColor provides colorScheme.onBackground, content = content)
        }
    }
}
