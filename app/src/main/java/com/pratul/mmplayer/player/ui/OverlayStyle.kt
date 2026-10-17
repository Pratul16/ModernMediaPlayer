package com.pratul.mmplayer.player.ui

import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * See-through player overlays: nothing is drawn behind controls and messages, so the video stays
 * visible. White icons and text carry a soft dark shadow instead, which keeps them readable on
 * bright and dark scenes alike.
 */

/** The shadow under all overlay text. */
val OverlayTextShadow = Shadow(color = Color.Black.copy(alpha = 0.8f), offset = Offset(0f, 2f), blurRadius = 8f)

/** [this] text style with the overlay shadow and white colour. */
fun TextStyle.onVideo(color: Color = Color.White): TextStyle = copy(color = color, shadow = OverlayTextShadow)

/**
 * A white icon with a soft shadow under it (a blurred dark copy; on Android 11 and older, where
 * blur isn't available, a slightly offset dark copy).
 */
@Composable
fun ShadowIcon(
    icon: ImageVector,
    contentDescription: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.Black.copy(alpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.75f else 0.45f),
            modifier = Modifier
                .size(size)
                .offset(y = 1.dp)
                .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(3.dp, BlurredEdgeTreatment.Unbounded) else Modifier),
        )
        Icon(imageVector = icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size))
    }
}

/** A see-through button for prompts over the video: outlined, shadowed white text. */
@Composable
fun OverlayButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, primary: Boolean = false) {
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(50))
            .border(if (primary) 2.dp else 1.dp, Color.White.copy(alpha = if (primary) 0.95f else 0.6f), RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            ShadowIcon(icon, contentDescription = null, size = 18.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge.onVideo(), maxLines = 1)
    }
}

/**
 * A see-through choice chip for the options panel: a thin outline, thicker and brighter when
 * selected, with shadowed white text. Same shape of arguments as Material's FilterChip.
 */
@Composable
fun OverlayChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .heightIn(min = 34.dp)
            .clip(shape)
            .border(if (selected) 2.dp else 1.dp, Color.White.copy(alpha = if (selected) 1f else 0.45f), shape)
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides Color.White.copy(alpha = if (enabled) 1f else 0.4f),
            LocalTextStyle provides MaterialTheme.typography.labelLarge.onVideo(Color.White.copy(alpha = if (enabled) 1f else 0.4f))
                .copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
        ) {
            if (selected) {
                ShadowIcon(Icons.Rounded.Check, contentDescription = null, size = 16.dp)
                Spacer(Modifier.width(4.dp))
            } else if (leadingIcon != null) {
                leadingIcon()
                Spacer(Modifier.width(6.dp))
            }
            label()
        }
    }
}
