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
import androidx.compose.ui.text.font.FontWeight
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
 * Sober, clean background: solid dark canvas with very subtle, soft depth.
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    val dark = Aurora.isDark
    val base = MaterialTheme.colorScheme.background
    val palette = LocalPalette.current
    Box(modifier.background(base)) {
        Canvas(Modifier.fillMaxSize()) {
            val r = max(size.width, size.height)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(palette.glowA.copy(alpha = if (dark) 0.06f else 0.03f), Color.Transparent),
                    center = Offset(size.width * 0.1f, size.height * 0.05f),
                    radius = r * 0.5f,
                ),
                radius = r * 0.5f,
                center = Offset(size.width * 0.1f, size.height * 0.05f),
            )
        }
        content()
    }
}

/** Sober Material 3 surface container: clean, soft border and neutral fill. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(20.dp), strong: Boolean = false): Modifier = composed {
    val dark = Aurora.isDark
    val fill = if (dark) {
        Color(if (strong) 0xFF20222E else 0xFF181922)
    } else {
        Color(if (strong) 0xFFFFFFFF else 0xFFF3F4F6)
    }
    val edge = if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    this
        .clip(shape)
        .background(fill)
        .border(1.dp, edge, shape)
}

/** Subtle solid border for focused or active items. */
fun Modifier.neonBorder(shape: Shape, width: Dp = 1.dp): Modifier = composed {
    border(width, MaterialTheme.colorScheme.primary, shape)
}

/** Sober primary button: solid Material 3 pill button with clean typography. */
@Composable
fun GlowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(50)
    val bg = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    Row(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
        Text(text, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
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

/** An icon inside a clean, sober primary badge. */
@Composable
fun GradientIconBadge(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 44.dp, shape: Shape = RoundedCornerShape(14.dp)) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(size * 0.52f))
    }
}

/** Clean progress bar. */
@Composable
fun NeonProgress(progress: Float, modifier: Modifier = Modifier, height: Dp = 3.dp, track: Color = MaterialTheme.colorScheme.surfaceVariant) {
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
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/** Clean heading text. */
@Composable
fun GradientText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    Text(text = text, style = style.copy(color = MaterialTheme.colorScheme.primary), modifier = modifier)
}

/** Top bars float over the aurora; once content scrolls under them they frost slightly. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun auroraTopBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = Color.Transparent,
    scrolledContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.86f),
)
