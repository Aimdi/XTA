package com.aimdi.xtanative

import android.os.Process
import android.os.SystemClock

/**
 * Where a cold start's time goes, for native/PERF.md: each step marks how
 * long after the process began it was reached, and the first frame writes
 * them down in one line of the log, which CI's comparison reads
 * (tool/compare.sh). A handful of clock reads and one line a start.
 */
object StartMarks {
    private val marks = ArrayList<Pair<String, Long>>(8)
    private var written = false

    /** [step] is reached now. On the main thread. */
    fun mark(step: String) {
        if (!written && marks.none { it.first == step }) marks += step to SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
    }

    /** The first frame is drawn: the steps so far go to the log, once a process. */
    fun firstFrame() {
        if (written) return
        mark("first frame")
        written = true
        println("XTA Native: start: " + marks.joinToString(", ") { (step, at) -> "$step $at ms" })
    }
}
