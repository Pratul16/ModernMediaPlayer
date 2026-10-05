package com.pratul.mmplayer

import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.animation.AccelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.rememberCoroutineScope
import com.pratul.mmplayer.data.settings.SettingKeys
import com.pratul.mmplayer.ui.lock.AppLockGate
import com.pratul.mmplayer.ui.onboarding.OnboardingScreen
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pratul.mmplayer.data.settings.AppSettings
import com.pratul.mmplayer.data.settings.ThemeMode
import com.pratul.mmplayer.ui.ModernMediaAppRoot
import com.pratul.mmplayer.ui.theme.ModernMediaTheme

class MainActivity : ComponentActivity() {

    @Volatile private var settingsLoaded = false
    private val settingsRepository by lazy { (application as ModernMediaApp).container.settingsRepository }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // On a cold start let the intro animation finish (~1 s); also wait for the saved theme so
        // the app never flashes the wrong one. Returning to the app skips the wait.
        val introUntil = if (savedInstanceState == null) SystemClock.uptimeMillis() + INTRO_MS else 0L
        splashScreen.setKeepOnScreenCondition { !settingsLoaded || SystemClock.uptimeMillis() < introUntil }
        splashScreen.setOnExitAnimationListener { provider ->
            // The mark zooms toward the viewer and fades while the backdrop dissolves into the app.
            provider.iconView.animate()
                .scaleX(1.8f)
                .scaleY(1.8f)
                .alpha(0f)
                .setDuration(EXIT_MS)
                .setInterpolator(AccelerateInterpolator())
                .start()
            provider.view.animate()
                .alpha(0f)
                .setStartDelay(EXIT_MS / 4)
                .setDuration(EXIT_MS)
                .withEndAction { provider.remove() }
                .start()
        }

        setContent {
            val settings: AppSettings? by settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = null)
            settingsLoaded = settings != null
            settings?.let { ModernMediaContent(it) }
        }
    }

    @Composable
    private fun ModernMediaContent(settings: AppSettings) {
        val darkTheme = when (settings.themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        // Status/navigation bar icons follow the in-app theme, not only the system theme.
        DisposableEffect(darkTheme) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { darkTheme },
            )
            onDispose { }
        }
        ModernMediaTheme(darkTheme = darkTheme, dynamicColor = settings.dynamicColor, colorTheme = settings.colorTheme) {
            val scope = rememberCoroutineScope()
            AppLockGate((application as ModernMediaApp).container.appLock) {
                Crossfade(targetState = settings.onboardingDone, animationSpec = tween(450), label = "onboarding") { done ->
                    if (done) {
                        ModernMediaAppRoot()
                    } else {
                        OnboardingScreen(onDone = { scope.launch { settingsRepository.set(SettingKeys.ONBOARDING_DONE, true) } })
                    }
                }
            }
        }
    }

    private companion object {
        const val INTRO_MS = 1_000L
        const val EXIT_MS = 320L
        // Same scrims androidx.activity uses for three-button navigation.
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
