package com.pratul.mmplayer.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import androidx.media3.common.C
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Rational
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.VideoSize
import com.pratul.mmplayer.R
import com.pratul.mmplayer.data.settings.AppSettings
import com.pratul.mmplayer.data.settings.OrientationMode
import com.pratul.mmplayer.player.ui.PlayerHost
import com.pratul.mmplayer.player.ui.PlayerScreen
import com.pratul.mmplayer.ui.theme.ModernMediaTheme
import com.pratul.mmplayer.ui.lock.AppLockGate
import com.pratul.mmplayer.ModernMediaApp
import kotlinx.coroutines.launch

/**
 * Full-screen video player. A separate Activity so it can own orientation, immersive mode and
 * picture-in-picture without affecting the library UI. Opens files from inside the app and from
 * other apps (file managers, browsers' downloads, messaging apps) through ACTION_VIEW.
 */
class PlayerActivity : ComponentActivity(), PlayerHost {

    private val viewModel: PlayerViewModel by viewModels()
    private val inPip = mutableStateOf(false)
    private var isPlaying = false
    private var videoSize = VideoSize.UNKNOWN
    private var orientationOverride: OrientationMode? = null
    private var brightnessRestored = false

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_PIP_PLAY_PAUSE -> viewModel.setPlaying(!isPlaying)
                ACTION_PIP_REWIND -> pipSeekBy(-PIP_SEEK_MS)
                ACTION_PIP_FORWARD -> pipSeekBy(PIP_SEEK_MS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        val pipActions = IntentFilter().apply {
            addAction(ACTION_PIP_PLAY_PAUSE)
            addAction(ACTION_PIP_REWIND)
            addAction(ACTION_PIP_FORWARD)
        }
        ContextCompat.registerReceiver(this, pipReceiver, pipActions, ContextCompat.RECEIVER_NOT_EXPORTED)

        if (!handleIntent(intent)) return

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settings.collect(::applyWindowSettings)
            }
        }

        setContent {
            val playerSettings by viewModel.settings.collectAsStateWithLifecycle()
            ModernMediaTheme(darkTheme = true, dynamicColor = false, colorTheme = playerSettings.colorTheme) {
                val appLock = (application as ModernMediaApp).container.appLock
                val appLocked by appLock.locked.collectAsStateWithLifecycle()
                val player = viewModel.state.collectAsStateWithLifecycle().value.player
                // Opened while locked (e.g. a video tapped in another app): hold playback until unlocked.
                val heldByLock = androidx.compose.runtime.remember { booleanArrayOf(false) }
                androidx.compose.runtime.LaunchedEffect(appLocked, player) {
                    if (appLocked && player != null && !heldByLock[0]) {
                        heldByLock[0] = true
                        viewModel.setPlaying(false)
                    } else if (!appLocked && heldByLock[0]) {
                        heldByLock[0] = false
                        viewModel.setPlaying(true)
                    }
                }
                AppLockGate(appLock) {
                    PlayerScreen(viewModel = viewModel, host = this, inPip = inPip.value)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent): Boolean {
        val uri: Uri? = intent.data
        if (uri == null) {
            Toast.makeText(this, "Nothing to play", Toast.LENGTH_SHORT).show()
            finish()
            return false
        }
        viewModel.open(uri, intent.type, intent.getLongArrayExtra(EXTRA_QUEUE)?.toList())
        return true
    }

    private fun applyWindowSettings(settings: AppSettings) {
        if (settings.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (settings.immersiveMode) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        if (!brightnessRestored) {
            brightnessRestored = true
            if (settings.rememberBrightness && settings.lastBrightness >= 0f) setBrightness(settings.lastBrightness)
        }
        applyOrientation()
        updatePipParams()
    }

    private fun applyOrientation() {
        val mode = orientationOverride ?: viewModel.settings.value.orientation
        requestedOrientation = when (mode) {
            OrientationMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            OrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            OrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            OrientationMode.SYSTEM -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            OrientationMode.FOLLOW_VIDEO -> when {
                videoSize.width <= 0 -> requestedOrientation
                videoSize.width * videoSize.pixelWidthHeightRatio >= videoSize.height ->
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
        }
    }

    // --- PlayerHost ---

    override fun exit() = finish()

    override fun enterPip() {
        if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            Toast.makeText(this, "Picture-in-picture is not supported on this device", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching { enterPictureInPictureMode(buildPipParams()) }
    }

    override fun brightness(): Float {
        val current = window.attributes.screenBrightness
        if (current >= 0f) return current
        val system = runCatching { Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) }.getOrDefault(128)
        return system / 255f
    }

    override fun setBrightness(value: Float) {
        window.attributes = window.attributes.apply { screenBrightness = value.coerceIn(0.01f, 1f) }
    }

    override fun setOrientationOverride(mode: OrientationMode?) {
        orientationOverride = mode
        applyOrientation()
    }

    override fun openWithAnotherApp(file: FileInfo) {
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(file.uri, file.mimeType ?: "video/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { startActivity(Intent.createChooser(view, "Open with")) }
            .onFailure { Toast.makeText(this, "No other app can open this file", Toast.LENGTH_SHORT).show() }
    }

    override fun onPlaybackChanged(isPlaying: Boolean, videoSize: VideoSize) {
        val sizeChanged = videoSize != this.videoSize
        this.isPlaying = isPlaying
        this.videoSize = videoSize
        if (sizeChanged && orientationOverride == null) applyOrientation()
        updatePipParams()
    }

    // --- Picture-in-picture ---

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Android 12+ enters PiP automatically through setAutoEnterEnabled.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && shouldAutoPip()) enterPip()
    }

    private fun shouldAutoPip() = viewModel.settings.value.pictureInPicture && isPlaying && videoSize.width > 0

    private fun pipAction(action: String, icon: Int, title: String, requestCode: Int): RemoteAction {
        val intent = PendingIntent.getBroadcast(
            this,
            requestCode,
            Intent(action).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return RemoteAction(Icon.createWithResource(this, icon), title, title, intent)
    }

    /** Jumps by [deltaMs] from the picture-in-picture buttons, kept inside the video. */
    private fun pipSeekBy(deltaMs: Long) {
        val player = viewModel.state.value.player ?: return
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + deltaMs).coerceIn(0, duration))
    }

    private fun updatePipParams() {
        runCatching { setPictureInPictureParams(buildPipParams()) }
    }

    private fun buildPipParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        if (videoSize.width > 0 && videoSize.height > 0) {
            val width = (videoSize.width * videoSize.pixelWidthHeightRatio).toInt()
            val ratio = width.toFloat() / videoSize.height
            // Android rejects ratios outside roughly 1:2.39..2.39:1.
            val clamped = ratio.coerceIn(1f / 2.39f, 2.39f)
            builder.setAspectRatio(Rational((clamped * 1000).toInt(), 1000))
        }
        // ⟲10  ▶/❚❚  10⟳ — Android shows up to three buttons in the small window.
        builder.setActions(
            listOf(
                pipAction(ACTION_PIP_REWIND, R.drawable.ic_pip_replay10, "Back 10 seconds", requestCode = 1),
                pipAction(ACTION_PIP_PLAY_PAUSE, if (isPlaying) R.drawable.ic_pip_pause else R.drawable.ic_pip_play, if (isPlaying) "Pause" else "Play", requestCode = 0),
                pipAction(ACTION_PIP_FORWARD, R.drawable.ic_pip_forward10, "Forward 10 seconds", requestCode = 2),
            ),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(shouldAutoPip())
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
        // The PiP window was dismissed (not expanded back): stop playback.
        if (!isInPictureInPictureMode && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            viewModel.setPlaying(false)
        }
    }

    override fun onStop() {
        super.onStop()
        // Audio (and video with "Play audio in background") keeps playing via the media service.
        if (!isInPictureInPictureMode && !viewModel.shouldContinueInBackground()) viewModel.setPlaying(false)
    }

    override fun onDestroy() {
        unregisterReceiver(pipReceiver)
        super.onDestroy()
    }

    companion object {
        private const val ACTION_PIP_PLAY_PAUSE = "com.pratul.mmplayer.PIP_PLAY_PAUSE"
        private const val ACTION_PIP_REWIND = "com.pratul.mmplayer.PIP_REWIND"
        private const val ACTION_PIP_FORWARD = "com.pratul.mmplayer.PIP_FORWARD"
        private const val PIP_SEEK_MS = 10_000L

        /** Library ids to play in order (playlist, favorites, shuffle); the data uri is the one to start with. */
        private const val EXTRA_QUEUE = "com.pratul.mmplayer.QUEUE"

        fun intent(context: Context, uri: Uri, mimeType: String? = null, queueIds: List<Long>? = null): Intent =
            Intent(context, PlayerActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setDataAndType(uri, mimeType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .apply { if (queueIds != null) putExtra(EXTRA_QUEUE, queueIds.toLongArray()) }
    }
}
