package com.pratul.mmplayer.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pratul.mmplayer.ModernMediaApp
import com.pratul.mmplayer.billing.TipJarState
import com.pratul.mmplayer.ui.theme.GlowButton

@Composable
fun SupportDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val tipJar = remember { (context.applicationContext as ModernMediaApp).container.tipJar }
    val activity = remember(context) { context.findActivity() }

    LaunchedEffect(tipJar) { tipJar.connect() }
    LaunchedEffect(tipJar.problem) {
        tipJar.problem?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            tipJar.problem = null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Support Modern Media Player",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "If you enjoy using Modern Media Player, consider supporting development with a tip or leaving a 5-star review!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(4.dp))

                when (val state = tipJar.state) {
                    TipJarState.Loading -> {
                        Text("Loading tip options…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    is TipJarState.Ready -> {
                        for (option in state.options) {
                            SupportOptionCard(
                                title = "${option.emoji} ${option.title}",
                                price = option.price,
                                description = "Support development",
                                onClick = {
                                    if (activity != null) {
                                        (context.applicationContext as ModernMediaApp).container.appLock.expectExternalReturn()
                                        tipJar.buy(activity, option)
                                    }
                                }
                            )
                        }
                    }
                    is TipJarState.Unavailable -> {
                        SupportOptionCard(
                            title = "☕ Buy a Coffee",
                            price = "Support",
                            description = "Support development on Google Play",
                            onClick = { openPlayStoreReview(context) }
                        )
                    }
                }

                if (tipJar.completedTips > 0) {
                    Text(
                        "💜 Thank you for your support!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            GlowButton(
                text = "Rate 5 Stars ★",
                onClick = { openPlayStoreReview(context) },
                icon = Icons.Rounded.Star
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Maybe Later", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

@Composable
private fun SupportOptionCard(
    title: String,
    price: String,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                price,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

private fun openPlayStoreReview(context: Context) {
    val appPackage = context.packageName
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appPackage")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
    }
    runCatching { context.startActivity(intent) }.onFailure {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$appPackage")))
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
