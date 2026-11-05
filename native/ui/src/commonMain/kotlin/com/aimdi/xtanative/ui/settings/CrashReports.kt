package com.aimdi.xtanative.ui.settings

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

/** One crash written down on the device: when, the report as text, and whether it is still to be filed on GitHub. */
data class CrashReport(val name: String, val at: Instant, val text: String, val unsent: Boolean = false)

/**
 * Crash reports kept on the device (XTA's crash_reporter.dart, catcher),
 * while the reader has them on. XTA files each as a GitHub issue as it
 * happens; here a crash usually ends the app first, so it is written down,
 * and [CrashReporter] files what is still unsent with the reader's token
 * once the app runs again. The reader can read, copy or clear them; only the
 * newest [KEPT] stay.
 *
 * A crash on the screens' thread also leaves [takeLastCrash] behind, whether
 * reports are on or not: the next start shows it as XTA shows a screen that
 * broke.
 */
class CrashReports(private val folder: File, private val clock: () -> Long = System::currentTimeMillis) {
    /** Writes [error] down; null when it could not be written. Never throws: it runs while the app is going down. */
    fun record(error: Throwable, thread: String, appVersion: String): File? = runCatching {
        folder.mkdirs()
        val at = clock()
        val file = File(folder, "crash-$at.txt")
        file.writeText(text(error, thread, appVersion, Instant.ofEpochMilli(at)))
        if (CrashIssue.shouldReport(error)) File(folder, "crash-$at$UNSENT").writeText("")
        prune()
        file
    }.getOrNull()

    /** The reports, newest first. */
    fun list(): List<CrashReport> = files().mapNotNull { file ->
        val at = atOf(file) ?: return@mapNotNull null
        runCatching { CrashReport(file.name, Instant.ofEpochMilli(at), file.readText(), unsentMarker(file).exists()) }.getOrNull()
    }

    /** [report] is filed, or needs no filing: it is not sent again. */
    fun markSent(report: CrashReport) {
        unsentMarker(File(folder, report.name)).delete()
    }

    fun clear() {
        folder.listFiles { file -> file.isFile && file.name.startsWith("crash-") }?.forEach { it.delete() }
    }

    /** The crash that broke a screen when the app last ran, taken away as it is read; null when there was none. */
    fun takeLastCrash(): CrashReport? = runCatching {
        val file = File(folder, LAST)
        if (!file.exists()) return@runCatching null
        val text = file.readText()
        val at = file.lastModified()
        file.delete()
        CrashReport(LAST, Instant.ofEpochMilli(at), text)
    }.getOrNull()

    /**
     * Writes down every crash no one caught while [enabled] says so, and the
     * last one of the screens' thread always, hands it to [recorded], then
     * lets the platform's own handler go on as it would have.
     */
    fun install(enabled: () -> Boolean, appVersion: String, recorded: () -> Unit = {}) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            if (isScreensThread(thread.name)) recordLast(error, thread.name, appVersion)
            if (runCatching(enabled).getOrDefault(false) && record(error, thread.name, appVersion) != null) runCatching(recorded)
            previous?.uncaughtException(thread, error)
        }
    }

    private fun recordLast(error: Throwable, thread: String, appVersion: String) {
        runCatching {
            folder.mkdirs()
            File(folder, LAST).writeText(text(error, thread, appVersion, Instant.ofEpochMilli(clock())))
        }
    }

    private fun files(): List<File> =
        folder.listFiles { file -> file.isFile && atOf(file) != null }
            ?.sortedByDescending { atOf(it) }
            .orEmpty()

    private fun unsentMarker(report: File): File = File(folder, report.name.removeSuffix(".txt") + UNSENT)

    private fun prune() {
        files().drop(KEPT).forEach { file ->
            file.delete()
            unsentMarker(file).delete()
        }
    }

    companion object {
        const val KEPT = 20
        private const val UNSENT = ".unsent"
        private const val LAST = "last-crash.txt"

        private fun atOf(file: File): Long? =
            file.name.takeIf { it.startsWith("crash-") && it.endsWith(".txt") }?.removePrefix("crash-")?.removeSuffix(".txt")?.toLongOrNull()

        /** Android's main thread and the desktop's event thread, where the screens are drawn. */
        fun isScreensThread(name: String): Boolean = name == "main" || name.startsWith("AWT-EventQueue")

        /** The report, as the issue body XTA files: the build, the system, the error and its stack. */
        fun text(error: Throwable, thread: String, appVersion: String, at: Instant): String {
            val stack = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString().trimEnd()
            return CrashIssue.body(error.toString(), stack, appVersion, "${System.getProperty("os.name")} ${System.getProperty("os.version")}", thread, at)
        }
    }
}
