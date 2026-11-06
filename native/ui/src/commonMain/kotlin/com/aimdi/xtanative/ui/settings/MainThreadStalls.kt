package com.aimdi.xtanative.ui.settings

import java.io.File

/**
 * What the Android app's main-thread watchdog (MainThreadWatchdog) wrote
 * down, as XTA's main_thread_stalls.dart reads it for the diagnostics report.
 */
object MainThreadStalls {
    /** Where the watchdog writes, under the app's files. */
    const val FILE = "diagnostics/main-thread-stalls.txt"

    /** The most recent lines kept: enough for one stack and its resume line. */
    const val LINES = 60

    /** The report's line for [file]; a null [file] is a platform the watchdog does not run on. */
    fun summary(file: File?): String {
        if (file == null) return "not watched on this platform"
        return try {
            if (!file.exists()) "no stall recorded" else describe(file.readText())
        } catch (error: Exception) {
            "unreadable (${error::class.simpleName})"
        }
    }

    /** The tail of the record: the latest stall, its stack, and whether the thread came back. */
    fun describe(contents: String): String {
        val lines = contents.split('\n').filter { it.isNotBlank() }
        if (lines.isEmpty()) return "no stall recorded"
        return "\n" + lines.takeLast(LINES).joinToString("\n") { "  $it" }
    }
}
