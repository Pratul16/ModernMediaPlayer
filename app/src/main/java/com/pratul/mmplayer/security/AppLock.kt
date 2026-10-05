package com.pratul.mmplayer.security

import android.app.Activity
import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** How the app is unlocked. */
enum class LockMethod(val label: String) {
    /** The phone's own fingerprint / face / PIN / pattern / password. */
    SYSTEM("Phone screen lock"),

    /** A PIN used only by this app. */
    PIN("App PIN"),
}

/** How long the app may be in the background before it locks again. */
enum class LockDelay(val millis: Long, val label: String) {
    IMMEDIATELY(0, "Immediately"),
    SECONDS_30(30_000, "30 seconds"),
    MINUTE_1(60_000, "1 minute"),
    MINUTES_5(300_000, "5 minutes"),
}

data class LockConfig(
    val enabled: Boolean = false,
    val method: LockMethod = LockMethod.SYSTEM,
    val delay: LockDelay = LockDelay.SECONDS_30,
    val hasPin: Boolean = false,
)

/**
 * Optional app lock (off by default). When on, the app and the player are covered by a lock screen
 * at start and whenever they return after being in the background longer than [LockConfig.delay].
 *
 * The app PIN is never stored: only a salted PBKDF2 hash. Several wrong PINs in a row impose a
 * growing wait. Recent-apps thumbnails are hidden while the lock is on (Android 13+).
 */
class AppLock(private val context: Context) {

    private val prefs = context.getSharedPreferences("app_lock", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(readConfig())
    val config: StateFlow<LockConfig> = _config.asStateFlow()

    // Locked from process start when enabled.
    private val _locked = MutableStateFlow(_config.value.enabled)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var startedActivities = 0
    private var changingConfig = false
    private var backgroundSince = 0L
    private var externalUntil = 0L

    /** Watches every activity so the lock knows when the whole app goes to the background. */
    fun attach(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                applyRecentsPolicy(activity)
                if (changingConfig) {
                    changingConfig = false
                    return
                }
                if (startedActivities++ == 0 && backgroundSince > 0) onForeground()
            }

            override fun onActivityStopped(activity: Activity) {
                // A rotation stops and restarts the activity; that's not leaving the app.
                if (activity.isChangingConfigurations) {
                    changingConfig = true
                    return
                }
                if (--startedActivities <= 0) {
                    startedActivities = 0
                    backgroundSince = SystemClock.elapsedRealtime()
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun onForeground() {
        val now = SystemClock.elapsedRealtime()
        val away = now - backgroundSince
        backgroundSince = 0
        val cfg = _config.value
        if (!cfg.enabled) return
        // Coming back from a screen the app itself opened (share sheet, "allow access" dialog).
        if (now < externalUntil) {
            externalUntil = 0
            return
        }
        if (away >= cfg.delay.millis) _locked.value = true
    }

    /**
     * Call right before opening another app's screen on the user's behalf (share sheet, system
     * consent dialog), so returning from it doesn't ask to unlock again.
     */
    fun expectExternalReturn() {
        externalUntil = SystemClock.elapsedRealtime() + EXTERNAL_GRACE_MS
    }

    fun unlock() {
        _locked.value = false
    }

    /** True when the phone has a PIN, pattern, password or biometric set up. */
    fun isDeviceSecure(): Boolean = context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    fun enable(method: LockMethod) {
        prefs.edit().putBoolean(K_ENABLED, true).putString(K_METHOD, method.name).apply()
        refresh()
    }

    fun disable() {
        prefs.edit().putBoolean(K_ENABLED, false).apply()
        _locked.value = false
        refresh()
    }

    fun setDelay(delay: LockDelay) {
        prefs.edit().putString(K_DELAY, delay.name).apply()
        refresh()
    }

    fun setMethod(method: LockMethod) {
        prefs.edit().putString(K_METHOD, method.name).apply()
        refresh()
    }

    suspend fun setPin(pin: String) = withContext(Dispatchers.Default) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(K_SALT, salt.b64())
            .putString(K_HASH, hash(pin, salt).b64())
            .putInt(K_FAILS, 0)
            .putLong(K_LOCKOUT, 0)
            .apply()
        refresh()
    }

    /** Milliseconds to wait before another PIN attempt is allowed (0 = may try now). */
    fun pinWaitMs(): Long = (prefs.getLong(K_LOCKOUT, 0) - System.currentTimeMillis()).coerceAtLeast(0)

    suspend fun verifyPin(pin: String): Boolean = withContext(Dispatchers.Default) {
        if (pinWaitMs() > 0) return@withContext false
        val salt = prefs.getString(K_SALT, null)?.unb64() ?: return@withContext false
        val expected = prefs.getString(K_HASH, null)?.unb64() ?: return@withContext false
        val ok = MessageDigest.isEqual(expected, hash(pin, salt))
        if (ok) {
            prefs.edit().putInt(K_FAILS, 0).putLong(K_LOCKOUT, 0).apply()
        } else {
            val fails = prefs.getInt(K_FAILS, 0) + 1
            // 5 wrong in a row → 30 s, then doubling, capped at 15 min.
            val wait = if (fails >= MAX_FREE_FAILS) (30_000L shl (fails - MAX_FREE_FAILS).coerceAtMost(5)).coerceAtMost(900_000L) else 0L
            prefs.edit().putInt(K_FAILS, fails).putLong(K_LOCKOUT, if (wait > 0) System.currentTimeMillis() + wait else 0).apply()
        }
        ok
    }

    private fun applyRecentsPolicy(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.setRecentsScreenshotEnabled(!_config.value.enabled)
        }
    }

    private fun refresh() {
        _config.value = readConfig()
    }

    private fun readConfig() = LockConfig(
        enabled = prefs.getBoolean(K_ENABLED, false),
        method = prefs.getString(K_METHOD, null)?.let { runCatching { LockMethod.valueOf(it) }.getOrNull() } ?: LockMethod.SYSTEM,
        delay = prefs.getString(K_DELAY, null)?.let { runCatching { LockDelay.valueOf(it) }.getOrNull() } ?: LockDelay.SECONDS_30,
        hasPin = prefs.contains(K_HASH),
    )

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun ByteArray.b64() = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.unb64() = Base64.decode(this, Base64.NO_WRAP)

    private companion object {
        const val K_ENABLED = "enabled"
        const val K_METHOD = "method"
        const val K_DELAY = "delay"
        const val K_SALT = "salt"
        const val K_HASH = "hash"
        const val K_FAILS = "fails"
        const val K_LOCKOUT = "lockout_until"
        const val ITERATIONS = 60_000
        const val MAX_FREE_FAILS = 5
        const val EXTERNAL_GRACE_MS = 3 * 60_000L
    }
}
