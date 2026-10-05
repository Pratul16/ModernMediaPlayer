package com.pratul.mmplayer.player.ui

import android.content.Context
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

data class GestureConfig(
    val enabled: Boolean = true,
    val seek: Boolean = true,
    val volume: Boolean = true,
    val brightness: Boolean = true,
    val doubleTapSeek: Boolean = true,
    val doubleTapPlayPause: Boolean = true,
    val pinchZoom: Boolean = true,
    val longPress: Boolean = true,
    val locked: Boolean = false,
)

enum class TapZone { LEFT, CENTER, RIGHT }

enum class VerticalSide { LEFT, RIGHT }

interface PlayerGestureListener {
    fun onSingleTap()

    /** A double tap, or one more tap in a running seek streak (see [PlayerGestureLayout]). */
    fun onDoubleTap(zone: TapZone)
    fun onSeekDrag(fractionOfWidth: Float)
    fun onSeekEnd()
    fun onVerticalDrag(side: VerticalSide, deltaFractionOfHeight: Float)
    fun onVerticalEnd()
    fun onZoom(scaleFactor: Float)
    fun onZoomEnd()
    fun onLongPressStart()

    /** Horizontal movement while still holding after a long press, as a fraction of the width. */
    fun onLongPressDrag(fractionOfWidth: Float)
    fun onLongPressEnd()
}

/**
 * Wraps the PlayerView and turns touches into player gestures.
 *
 * Every event passes through [dispatchTouchEvent] first, so the end of a gesture (finger lifted)
 * is always seen, even when a child such as the seek bar has taken over the touch. That guarantees
 * a long-press speed-up is always undone on release.
 *
 * Ownership: when the controls are hidden (or the screen is locked) the whole gesture belongs to
 * this layout. When they are visible, taps reach the controls and only swipes, pinches and
 * long-presses are taken over; the controls then receive ACTION_CANCEL.
 */
class PlayerGestureLayout(context: Context) : FrameLayout(context) {

    var config = GestureConfig()
    var listener: PlayerGestureListener? = null
    var isControllerVisible: () -> Boolean = { false }

    private enum class Mode { NONE, SEEK, VERTICAL, ZOOM, LONG_PRESS }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop * 2
    private var mode = Mode.NONE
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var longPressX = 0f
    private var verticalSide = VerticalSide.RIGHT
    private var controllerVisibleAtDown = false

    /** True while children (the controls) are receiving the current gesture. */
    private var childrenReceiving = false

    // Seek streak: after a double tap on a side, each further tap on that side seeks again at once.
    private var streakZone: TapZone? = null
    private var streakUntil = 0L
    private var gestureIsStreakTap = false

    private val tapDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            // A tap while the controls were showing was already handled by the controls (they hide).
            if (!controllerVisibleAtDown || config.locked) listener?.onSingleTap()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (config.locked || !config.enabled) return false
            val zone = zoneOf(e.x)
            val allowed = if (zone == TapZone.CENTER) config.doubleTapPlayPause else config.doubleTapSeek
            if (!allowed) return false
            listener?.onDoubleTap(zone)
            if (zone != TapZone.CENTER) startStreak(zone)
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            if (config.locked || !config.enabled || !config.longPress || mode != Mode.NONE) return
            if (streakActive()) return // fast tapping, not a deliberate hold
            mode = Mode.LONG_PRESS
            longPressX = e.x
            listener?.onLongPressStart()
        }
    })

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            if (config.locked || !config.enabled || !config.pinchZoom) return false
            endActiveGesture()
            mode = Mode.ZOOM
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            listener?.onZoom(detector.scaleFactor)
            return true
        }
    })

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            controllerVisibleAtDown = isControllerVisible()
            childrenReceiving = false
        }

        observe(ev)

        val ownsGesture = config.locked || !controllerVisibleAtDown || mode != Mode.NONE || gestureIsStreakTap
        if (ownsGesture) {
            if (childrenReceiving) {
                // Take the gesture away from the controls cleanly.
                val cancel = MotionEvent.obtain(ev).apply { setAction(MotionEvent.ACTION_CANCEL) }
                super.dispatchTouchEvent(cancel)
                cancel.recycle()
                childrenReceiving = false
            }
            return true
        }
        childrenReceiving = true
        return super.dispatchTouchEvent(ev)
    }

    private fun observe(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                lastY = ev.y
                mode = Mode.NONE
                gestureIsStreakTap = streakActive() && zoneOf(ev.x) == streakZone
                if (gestureIsStreakTap) {
                    streakZone?.let { listener?.onDoubleTap(it) }
                    startStreak(streakZone!!)
                }
            }
            MotionEvent.ACTION_MOVE -> when {
                config.locked || !config.enabled -> Unit
                mode == Mode.LONG_PRESS -> listener?.onLongPressDrag((ev.x - longPressX) / width.coerceAtLeast(1))
                ev.pointerCount == 1 && !gestureIsStreakTap -> onMove(ev)
            }
        }

        // Streak taps are already handled; keep them away from the tap detector so they are not
        // also counted as single taps or the first half of a new double tap.
        if (!gestureIsStreakTap) {
            tapDetector.setIsLongpressEnabled(config.enabled && config.longPress && !config.locked)
            scaleDetector.onTouchEvent(ev)
            tapDetector.onTouchEvent(ev)
        }

        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            endActiveGesture()
            gestureIsStreakTap = false
        }
    }

    private fun onMove(ev: MotionEvent) {
        val dx = ev.x - downX
        val dy = ev.y - downY
        if (mode == Mode.NONE) {
            when {
                abs(dx) > touchSlop && abs(dx) > abs(dy) && config.seek -> mode = Mode.SEEK
                abs(dy) > touchSlop && abs(dy) > abs(dx) -> {
                    verticalSide = if (downX < width / 2f) VerticalSide.LEFT else VerticalSide.RIGHT
                    val allowed = if (verticalSide == VerticalSide.LEFT) config.brightness else config.volume
                    if (allowed) {
                        mode = Mode.VERTICAL
                        lastY = ev.y
                    }
                }
            }
        }
        when (mode) {
            Mode.SEEK -> listener?.onSeekDrag(dx / width.coerceAtLeast(1))
            Mode.VERTICAL -> {
                // Upward movement increases the value.
                listener?.onVerticalDrag(verticalSide, (lastY - ev.y) / height.coerceAtLeast(1))
                lastY = ev.y
            }
            else -> Unit
        }
    }

    private fun endActiveGesture() {
        val ended = mode
        mode = Mode.NONE
        when (ended) {
            Mode.SEEK -> listener?.onSeekEnd()
            Mode.VERTICAL -> listener?.onVerticalEnd()
            Mode.ZOOM -> listener?.onZoomEnd()
            Mode.LONG_PRESS -> listener?.onLongPressEnd()
            Mode.NONE -> Unit
        }
    }

    private fun zoneOf(x: Float): TapZone = when {
        x < width / 3f -> TapZone.LEFT
        x > width * 2 / 3f -> TapZone.RIGHT
        else -> TapZone.CENTER
    }

    private fun startStreak(zone: TapZone) {
        streakZone = zone
        streakUntil = SystemClock.uptimeMillis() + STREAK_WINDOW_MS
    }

    private fun streakActive() = streakZone != null && SystemClock.uptimeMillis() < streakUntil

    override fun onDetachedFromWindow() {
        // Never leave a gesture (e.g. a 2x long press) running when the view goes away.
        endActiveGesture()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val STREAK_WINDOW_MS = 1_000L
    }
}
