package com.aimdi.xta

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Writes down where the main thread is stuck whenever it stops running posted work.
 *
 * Every platform channel, including the database plugin, needs the main thread, while Dart networking does not. A
 * launch where requests to X finish and every database call waits is the signature of a blocked main thread, and the
 * only thing that names the blocker is its stack at that moment. The record lives in the app's files directory, where
 * the diagnostics report reads it back once the thread answers again.
 */
class MainThreadWatchdog @JvmOverloads constructor(context: Context, private val stallAfterMs: Long = 5_000L) : Runnable {
    private val handler = Handler(Looper.getMainLooper())
    private val file = File(context.filesDir, "diagnostics/main-thread-stalls.txt")
    private val stamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)

    fun start() {
        file.delete()
        Thread(this, "xta-main-watchdog").apply { isDaemon = true }.start()
    }

    override fun run() {
        var stalledSince = 0L
        var reported = false
        while (!Thread.currentThread().isInterrupted) {
            val ran = AtomicBoolean(false)
            handler.post { ran.set(true) }
            try {
                Thread.sleep(TICK_MS)
            } catch (_: InterruptedException) {
                return
            }
            val now = SystemClock.uptimeMillis()
            if (ran.get()) {
                if (stalledSince != 0L) {
                    append("${stamp.format(Date())} main thread resumed after ${(now - stalledSince) / 1000} s")
                }
                stalledSince = 0L
                reported = false
                continue
            }
            if (stalledSince == 0L) stalledSince = now - TICK_MS
            if (!reported && now - stalledSince >= stallAfterMs) {
                reported = true
                report((now - stalledSince) / 1000)
            }
        }
    }

    private fun report(seconds: Long) {
        val frames = Looper.getMainLooper().thread.stackTrace.take(MAX_FRAMES).joinToString("\n") { "  at $it" }
        Log.w(TAG, "main thread blocked for $seconds s\n$frames")
        append("${stamp.format(Date())} main thread blocked for $seconds s\n$frames")
    }

    private fun append(text: String) {
        try {
            file.parentFile?.mkdirs()
            if (file.length() > MAX_BYTES) file.delete()
            file.appendText(text + "\n")
        } catch (e: Exception) {
            Log.w(TAG, "could not record the main thread stall", e)
        }
    }

    private companion object {
        const val TAG = "XtaWatchdog"
        const val TICK_MS = 1_000L
        const val MAX_FRAMES = 40
        const val MAX_BYTES = 64 * 1024L
    }
}
