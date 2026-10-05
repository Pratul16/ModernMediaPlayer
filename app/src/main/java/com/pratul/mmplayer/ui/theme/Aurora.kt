package com.pratul.mmplayer.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

/** Shared building blocks of the Aurora look: glowing backdrop, frosted glass, neon accents. */
object Aurora {
    /** Accent gradient of the active colour theme. */
    val gradient: Brush
        @Composable @ReadOnlyComposable get() = Brush.linearGradient(LocalPalette.current.gradient)

    @Composable @ReadOnlyComposable
    fun gradient(alpha: Float): Brush = Brush.linearGradient(LocalPalette.current.gradient.map { it.copy(alpha = alpha) })

    val colors: List<Color>
        @Composable @ReadOnlyComposable get() = LocalPalette.current.gradient

    val accent: Color
        @Composable @ReadOnlyComposable get() = LocalPalette.current.accent

    val accentEnd: Color
        @Composable @ReadOnlyComposable get() = LocalPalette.current.accentEnd

    val highlight: Color
        @Composable @ReadOnlyComposable get() = LocalPalette.current.glowC

    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = LocalAuroraDark.current
}

/**
 * Deep-space backdrop with soft violet, cyan and magenta glows. Drawn once per size change on a
 * Canvas (no bitmaps, no blur) so it costs almost nothing while lists scroll above it.
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    val dark = Aurora.isDark
    val base = MaterialTheme.colorScheme.background
    val strength = if (dark) 1f else 0.55f
    val palette = LocalPalette.current
    Box(modifier.background(base)) {
        Canvas(Modifier.fillMaxSize()) {
            val r = max(size.width, size.height)
            fun glow(color: Color, alpha: Float, center: Offset, radius: Float) = drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = alpha * strength), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
            glow(palette.glowA, 0.34f, Offset(size.width * 0.05f, size.height * 0.02f), r * 0.62f)
            glow(palette.glowB, 0.20f, Offset(size.width * 1.0f, size.height * 0.30f), r * 0.50f)
            glow(palette.glowC, 0.14f, Offset(size.width * 0.2f, size.height * 1.02f), r * 0.55f)
        }
        content()
    }
}

/** Frosted-glass surface: a faint vertical sheen plus a thin light edge. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(24.dp), strong: Boolean = false): Modifier = composed {
    val dark = Aurora.isDark
    val frost = LocalPalette.current.frost
    val fill = if (dark) {
        listOf(Color.White.copy(alpha = (if (strong) 0.13f else 0.08f) * frost), Color.White.copy(alpha = (if (strong) 0.06f else 0.025f) * frost))
    } else {
        listOf(Color.White.copy(alpha = if (strong) 0.95f else 0.80f), Color.White.copy(alpha = if (strong) 0.80f else 0.55f))
    }
    val edge = if (dark) {
        listOf(Color.White.copy(alpha = 0.20f * frost), Color.White.copy(alpha = 0.04f * frost))
    } else {
        listOf(Color.White, Color(0x1A3B2A85))
    }
    this
        .clip(shape)
        .background(Brush.verticalGradient(fill))
        .border(1.dp, Brush.verticalGradient(edge), shape)
}

/** A neon gradient edge, e.g. around the selected or most important card. */
fun Modifier.neonBorder(shape: Shape, width: Dp = 1.5.dp): Modifier = composed { border(width, Aurora.gradient, shape) }

/** Primary call to action: neon gradient pill with a soft coloured glow. */
@Composable
fun GlowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .shadow(if (enabled) 18.dp else 0.dp, shape, ambientColor = Aurora.accent, spotColor = Aurora.accent)
            .clip(shape)
            .background(if (enabled) Aurora.gradient else Aurora.gradient(0.35f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

/** Secondary action on glass. */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .glass(RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Round icon button on glass (search, settings, player actions). */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    highlighted: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(size)
            .then(if (highlighted) Modifier.clip(CircleShape).background(Aurora.gradient) else Modifier.glass(CircleShape))
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = if (highlighted) Color.White else tint, modifier = Modifier.size(size * 0.48f))
    }
}

/** An icon inside a neon gradient badge — the app's icon language for sections and actions. */
@Composable
fun GradientIconBadge(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 44.dp, shape: Shape = RoundedCornerShape(14.dp)) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Aurora.gradient),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.52f))
    }
}

/** Thin neon progress bar (watch progress, scanning). */
@Composable
fun NeonProgress(progress: Float, modifier: Modifier = Modifier, height: Dp = 3.dp, track: Color = Color.White.copy(alpha = 0.18f)) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(50))
                .background(Aurora.gradient),
        )
    }
}

/** Text filled with the neon gradient (headings, the app name). */
@Composable
fun GradientText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    Text(text = text, style = style.copy(brush = Aurora.gradient), modifier = modifier)
}

/** Top bars float over the aurora; once content scrolls under them they frost slightly. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun auroraTopBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = Color.Transparent,
    scrolledContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.86f),
)
