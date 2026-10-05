package com.pratul.mmplayer.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pratul.mmplayer.R
import com.pratul.mmplayer.ui.theme.Aurora
import com.pratul.mmplayer.ui.theme.AuroraBackground
import com.pratul.mmplayer.ui.theme.GlowButton
import com.pratul.mmplayer.ui.theme.GradientIconBadge
import com.pratul.mmplayer.ui.theme.GradientText
import com.pratul.mmplayer.ui.theme.glass
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * First-launch walkthrough: welcome, the four gestures most people use (animated live on a mini
 * screen), the smart features, and privacy. Short, skippable, shown once.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pager = rememberPagerState(pageCount = { PAGES })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == PAGES - 1

    AuroraBackground(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                if (!last) {
                    Text(
                        "Skip",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(role = Role.Button, onClick = onDone)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                } else {
                    Spacer(Modifier.height(36.dp))
                }
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                Box(Modifier.fillMaxSize().padding(horizontal = 28.dp), contentAlignment = Alignment.Center) {
                    when (page) {
                        0 -> WelcomePage()
                        1 -> GesturesPage()
                        2 -> SmartPage()
                        else -> PrivacyPage()
                    }
                }
            }
            // Page dots: the current one stretches into a neon pill.
            Row(
                Modifier.fillMaxWidth().padding(vertical = 18.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(PAGES) { index ->
                    val selected = index == pager.currentPage
                    val width by animateDpAsState(if (selected) 26.dp else 8.dp, label = "dot")
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .size(width = width, height = 8.dp)
                            .clip(RoundedCornerShape(50))
                            .then(if (selected) Modifier.background(Aurora.gradient) else Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))),
                    )
                }
            }
            Box(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 20.dp), contentAlignment = Alignment.Center) {
                GlowButton(
                    text = if (last) "Get started" else "Next",
                    icon = Icons.AutoMirrored.Rounded.ArrowForward,
                    onClick = { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                )
            }
        }
    }
}

private const val PAGES = 4

@Composable
private fun PageText(title: String, body: String) {
    GradientText(title, style = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center))
    Spacer(Modifier.height(10.dp))
    Text(
        body,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = 420.dp),
    )
}

@Composable
private fun WelcomePage() {
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "angle")
    val colors = Aurora.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(colors.first().copy(alpha = 0.15f), radius = size.minDimension / 2 - 8.dp.toPx(), style = Stroke(3.dp.toPx()))
                drawArc(
                    brush = Brush.sweepGradient(colors + colors.first()),
                    startAngle = angle,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(8.dp.toPx(), 8.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(size.width - 16.dp.toPx(), size.height - 16.dp.toPx()),
                    style = Stroke(5.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            androidx.compose.foundation.Image(painterResource(R.drawable.ic_mm_logo), contentDescription = null, modifier = Modifier.size(96.dp))
        }
        Spacer(Modifier.height(36.dp))
        PageText("Welcome", "All your videos and music, beautifully played. Plays almost any format, right on your phone.")
    }
}

private enum class Demo(val title: String, val hint: String) {
    SEEK("Swipe sideways to seek", "Drag left or right anywhere on the video"),
    VOLUME("Swipe up or down", "Right side: volume  ·  Left side: brightness"),
    DOUBLE_TAP("Double-tap to skip", "Keep tapping to add up: +10s, +20s, +30s"),
    HOLD("Hold to play faster", "Slide while holding to pick 1.5× … 3×"),
}

/** A mini screen where a glowing finger acts out each gesture in turn. */
@Composable
private fun GesturesPage() {
    val transition = rememberInfiniteTransition(label = "demo")
    val clock by transition.animateFloat(
        initialValue = 0f,
        targetValue = Demo.entries.size.toFloat(),
        animationSpec = infiniteRepeatable(tween(Demo.entries.size * 2400, easing = LinearEasing), RepeatMode.Restart),
        label = "clock",
    )
    val demo = Demo.entries[clock.toInt().coerceIn(0, Demo.entries.lastIndex)]
    val t = clock - clock.toInt() // 0..1 within the current demo
    val colors = Aurora.colors

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth(0.85f)
                .aspectRatio(16f / 10f)
                .glass(RoundedCornerShape(28.dp), strong = true),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val glow = colors.first()
                fun finger(x: Float, y: Float, pressed: Float = 1f) {
                    drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.45f), Color.Transparent), Offset(x, y), 46.dp.toPx()), 46.dp.toPx(), Offset(x, y))
                    drawCircle(Color.White.copy(alpha = 0.9f * pressed), 13.dp.toPx(), Offset(x, y))
                }
                fun ease(v: Float) = (1 - kotlin.math.cos(v * PI.toFloat())) / 2
                when (demo) {
                    Demo.SEEK -> {
                        val p = ease((t * 1.4f).coerceIn(0f, 1f))
                        val x = w * (0.3f + 0.4f * p)
                        drawLine(Brush.horizontalGradient(colors), Offset(w * 0.3f, h / 2), Offset(x, h / 2), 6.dp.toPx(), StrokeCap.Round)
                        // Progress bar fills as the finger moves.
                        drawRoundRect(Color.White.copy(alpha = 0.2f), Offset(w * 0.1f, h * 0.86f), androidx.compose.ui.geometry.Size(w * 0.8f, 4.dp.toPx()))
                        drawRoundRect(Brush.horizontalGradient(colors), Offset(w * 0.1f, h * 0.86f), androidx.compose.ui.geometry.Size(w * 0.8f * (0.3f + 0.4f * p), 4.dp.toPx()))
                        finger(x, h / 2)
                    }
                    Demo.VOLUME -> {
                        val p = ease((t * 1.4f).coerceIn(0f, 1f))
                        val y = h * (0.75f - 0.45f * p)
                        drawRoundRect(Color.White.copy(alpha = 0.2f), Offset(w * 0.9f, h * 0.2f), androidx.compose.ui.geometry.Size(6.dp.toPx(), h * 0.6f))
                        drawRoundRect(Brush.verticalGradient(colors.reversed()), Offset(w * 0.9f, h * (0.8f - 0.6f * (0.3f + 0.6f * p))), androidx.compose.ui.geometry.Size(6.dp.toPx(), h * 0.6f * (0.3f + 0.6f * p)))
                        finger(w * 0.72f, y)
                    }
                    Demo.DOUBLE_TAP -> {
                        // Two quick taps, each with an expanding ripple.
                        for (tap in 0..1) {
                            val local = ((t - tap * 0.25f) * 2.5f).coerceIn(0f, 1f)
                            if (local in 0.001f..0.999f) {
                                drawCircle(colors.last().copy(alpha = 0.5f * (1 - local)), 18.dp.toPx() + 60.dp.toPx() * local, Offset(w * 0.75f, h / 2), style = Stroke(3.dp.toPx()))
                            }
                        }
                        val pressed = if ((t % 0.25f) < 0.1f && t < 0.5f) 1f else 0.55f
                        finger(w * 0.75f, h / 2, pressed)
                    }
                    Demo.HOLD -> {
                        val hold = (t * 2f).coerceIn(0f, 1f)
                        val slide = ease(((t - 0.5f) * 2f).coerceIn(0f, 1f))
                        val x = w * (0.5f + 0.18f * slide)
                        drawArc(Brush.sweepGradient(colors + colors.first()), -90f, 360f * hold, false, Offset(x - 26.dp.toPx(), h / 2 - 26.dp.toPx()), androidx.compose.ui.geometry.Size(52.dp.toPx(), 52.dp.toPx()), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                        finger(x, h / 2)
                    }
                }
                // Gentle pulse so the scene never looks frozen.
                drawCircle(Color.White.copy(alpha = 0.04f + 0.03f * sin(t * 2 * PI.toFloat())), w * 0.6f, Offset(w / 2, h / 2))
            }
            if (demo == Demo.HOLD && t > 0.25f) {
                val speed = if (t < 0.6f) "2×" else if (t < 0.8f) "2.25×" else "2.5×"
                Badge(speed, Modifier.align(Alignment.TopCenter).padding(top = 14.dp))
            }
            if (demo == Demo.SEEK && t > 0.15f) Badge("+0:30", Modifier.align(Alignment.TopCenter).padding(top = 14.dp))
            if (demo == Demo.DOUBLE_TAP && t > 0.1f) Badge(if (t < 0.35f) "+10s" else "+20s", Modifier.align(Alignment.TopCenter).padding(top = 14.dp))
        }
        Spacer(Modifier.height(32.dp))
        AnimatedContent(targetState = demo, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) }, label = "caption") { d ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) { PageText(d.title, d.hint) }
        }
    }
}

@Composable
private fun Badge(text: String, modifier: Modifier) {
    Text(
        text,
        color = Color.White,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Aurora.gradient)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, body: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(22.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GradientIconBadge(icon, size = 42.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SmartPage() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PageText("It remembers for you", "A few things happen automatically:")
        Spacer(Modifier.height(8.dp))
        FeatureRow(Icons.Rounded.History, "Continue where you stopped", "Every video resumes at the right second")
        FeatureRow(Icons.Rounded.SkipNext, "Next episode, automatically", "Series are detected from file names")
        FeatureRow(Icons.Rounded.ClosedCaption, "Subtitles found for you", "Movie.srt next to Movie.mkv just works")
        FeatureRow(Icons.Rounded.GraphicEq, "Music keeps playing", "Leave the player, lock the phone — it continues")
    }
}

@Composable
private fun PrivacyPage() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PageText("Private by design", "Nothing leaves your phone. And when you want more privacy:")
        Spacer(Modifier.height(8.dp))
        FeatureRow(Icons.Rounded.VisibilityOff, "Hide folders", "Hidden media can't be seen or played by other apps")
        FeatureRow(Icons.Rounded.Lock, "App lock", "Fingerprint, face, phone PIN or an app PIN — in Settings")
    }
}
