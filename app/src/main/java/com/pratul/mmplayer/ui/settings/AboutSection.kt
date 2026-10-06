package com.pratul.mmplayer.ui.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pratul.mmplayer.ModernMediaApp
import com.pratul.mmplayer.R
import com.pratul.mmplayer.billing.TipJarState
import com.pratul.mmplayer.ui.theme.Aurora
import com.pratul.mmplayer.ui.theme.GradientIconBadge
import com.pratul.mmplayer.ui.theme.GradientText
import com.pratul.mmplayer.ui.theme.glass

/**
 * About & developer: app and version, the developer, tips, contact / feedback / privacy policy,
 * and open-source licences. Developer details come from res/values/strings.xml.
 */
@Composable
fun AboutDeveloperSection() {
    val context = LocalContext.current
    val version = remember { appVersion(context) }
    val developer = stringResource(R.string.developer_name)
    val email = stringResource(R.string.developer_email)
    val tagline = stringResource(R.string.developer_tagline)
    val privacyUrl = stringResource(R.string.privacy_policy_url)
    val sourceUrl = stringResource(R.string.source_code_url)
    val appName = stringResource(R.string.app_name)

    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // App card: the launcher icon's layers, so it matches the home screen.
        Column(
            Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(28.dp), strong = true)
                .padding(vertical = 24.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(88.dp).clip(RoundedCornerShape(24.dp))) {
                Image(painterResource(R.drawable.ic_mm_logo), null, Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
            }
            Spacer(Modifier.height(12.dp))
            GradientText(appName, style = MaterialTheme.typography.headlineSmall)
            Text("Version $version", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text("Play · Remember · Private", style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }

        if (developer.isNotBlank()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(24.dp))
                    .padding(16.dp),
            ) {
                Text("Developer", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(developer, style = MaterialTheme.typography.titleLarge)
                if (tagline.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(tagline, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        TipCard()

        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp))) {
            if (email.isNotBlank()) {
                LinkRow(Icons.Rounded.Email, "Contact the developer", email) {
                    sendEmail(context, email, appName, "")
                }
                HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                LinkRow(Icons.Rounded.BugReport, "Send feedback or report a problem", "Opens an email with your app and Android version") {
                    sendEmail(
                        context, email, "$appName feedback (v$version)",
                        "\n\n—\nApp version: $version\nAndroid: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                            "Phone: ${Build.MANUFACTURER} ${Build.MODEL}",
                    )
                }
            }
            if (privacyUrl.isNotBlank()) {
                HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                LinkRow(Icons.Rounded.Policy, "Privacy policy", "Your media and history stay on your phone") {
                    openLink(context, privacyUrl)
                }
            }
            if (sourceUrl.isNotBlank()) {
                HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                LinkRow(Icons.Rounded.Code, "Source code", "Free software under the GNU GPL v3") {
                    openLink(context, sourceUrl)
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(24.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("License", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                "$appName © 2026 $developer. Free software: you can redistribute and modify it under the " +
                    "GNU General Public License v3 (or later). It comes with no warranty.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text("Open-source components", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            listOf(
                "AndroidX, Jetpack Compose, Media3, Room" to "Apache License 2.0",
                "FFmpeg decoder for Media3 (Jellyfin build)" to "GNU GPL v3",
                "libVLC" to "GNU LGPL 2.1+",
                "Coil" to "Apache License 2.0",
                "Material icons" to "Apache License 2.0",
            ).forEach { (name, license) ->
                Column {
                    Text(name, style = MaterialTheme.typography.bodyMedium)
                    Text(license, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Text(
            "No ads · No tracking · Your media stays on this phone",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

/** "Buy me a coffee": tip sizes with Google Play prices, or why tips aren't available here. */
@Composable
private fun TipCard() {
    val context = LocalContext.current
    val tipJar = (context.applicationContext as ModernMediaApp).container.tipJar
    // Reconnect each time the page opens, in case Google Play was unavailable earlier.
    LaunchedEffect(tipJar) { tipJar.connect() }
    LaunchedEffect(tipJar.problem) {
        tipJar.problem?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            tipJar.problem = null
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(24.dp))
            .padding(16.dp),
    ) {
        Text("Buy me a coffee ☕", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Enjoying the app? A small tip keeps it ad-free and growing. Totally optional, and every feature stays free.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        when (val state = tipJar.state) {
            TipJarState.Loading -> Text("Loading prices…", style = MaterialTheme.typography.bodySmall)
            is TipJarState.Unavailable -> Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            is TipJarState.Ready -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (option in state.options) {
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .glass(RoundedCornerShape(18.dp), strong = true)
                            .clickable {
                                (context.applicationContext as ModernMediaApp).container.appLock.expectExternalReturn()
                                context.findActivity()?.let { tipJar.buy(it, option) }
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(option.emoji, style = MaterialTheme.typography.titleLarge)
                        Text(option.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, textAlign = TextAlign.Center)
                        Text(option.price, style = MaterialTheme.typography.titleSmall, color = Aurora.accent, maxLines = 1)
                    }
                }
            }
        }
        if (tipJar.hasPendingTip) {
            Spacer(Modifier.height(8.dp))
            Text(
                "⏳ A tip is waiting for payment approval. It completes automatically once approved.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (tipJar.completedTips > 0) {
            Spacer(Modifier.height(8.dp))
            Text("💜 Thank you for your support!", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GradientIconBadge(icon, size = 40.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun appVersion(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private fun sendEmail(context: Context, to: String, subject: String, body: String) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .putExtra(Intent.EXTRA_TEXT, body)
    (context.applicationContext as ModernMediaApp).container.appLock.expectExternalReturn()
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No email app found. Write to $to", Toast.LENGTH_LONG).show()
    }
}

private fun openLink(context: Context, url: String) {
    (context.applicationContext as ModernMediaApp).container.appLock.expectExternalReturn()
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No browser found", Toast.LENGTH_SHORT).show()
    }
}
