package com.pratul.mmplayer.utils

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Debug-only: logs the main thread's stack whenever it stays blocked longer than [thresholdMs],
 * so freezes can be traced without root access to /data/anr.
 */
object MainThreadWatchdog {
    private const val TAG = "MainThreadWatchdog"

    fun start(thresholdMs: Long = 2_000L) {
        val main = Handler(Looper.getMainLooper())
        Thread({
            var reportedFor = 0L
            while (true) {
                val answered = AtomicBoolean(false)
                val postedAt = SystemClock.uptimeMillis()
                main.post { answered.set(true) }
                Thread.sleep(thresholdMs)
                if (!answered.get() && reportedFor != postedAt) {
                    reportedFor = postedAt
                    val stack = Looper.getMainLooper().thread.stackTrace.joinToString("\n") { "    at $it" }
                    Log.w(TAG, "Main thread blocked for over ${thresholdMs}ms:\n$stack")
                }
                while (!answered.get()) Thread.sleep(100)
            }
        }, "main-thread-watchdog").apply { isDaemon = true }.start()
    }
}
