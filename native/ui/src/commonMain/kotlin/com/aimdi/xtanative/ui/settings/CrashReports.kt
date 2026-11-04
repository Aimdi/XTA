package com.aimdi.xtanative.ui.settings

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

/** One crash written down on the device: when, and the report as text. */
data class CrashReport(val name: String, val at: Instant, val text: String)

/**
 * Crash reports kept on the device (XTA's crash_reporter.dart, catcher). XTA
 * opens a GitHub issue with the reader's own token; XTA Native posts nothing
 * anywhere, so a crash is written down here, when the reader turned that on,
 * for them to read, copy into a report of their own, or clear. Only the
 * newest [KEPT] stay.
 */
class CrashReports(private val folder: File, private val clock: () -> Long = System::currentTimeMillis) {
    /** Writes [error] down; null when it could not be written. Never throws: it runs while the app is going down. */
    fun record(error: Throwable, thread: String, appVersion: String): File? = runCatching {
        folder.mkdirs()
        val at = clock()
        val file = File(folder, "crash-$at.txt")
        file.writeText(text(error, thread, appVersion, Instant.ofEpochMilli(at)))
        prune()
        file
    }.getOrNull()

    /** The reports, newest first. */
    fun list(): List<CrashReport> = files().mapNotNull { file ->
        val at = file.name.removePrefix("crash-").removeSuffix(".txt").toLongOrNull() ?: return@mapNotNull null
        runCatching { CrashReport(file.name, Instant.ofEpochMilli(at), file.readText()) }.getOrNull()
    }

    fun clear() {
        files().forEach { it.delete() }
    }

    /**
     * Writes down every crash no one caught while [enabled] says so, then
     * lets the platform's own handler end the app as it would have.
     */
    fun install(enabled: () -> Boolean, appVersion: String) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            if (runCatching(enabled).getOrDefault(false)) record(error, thread.name, appVersion)
            previous?.uncaughtException(thread, error)
        }
    }

    private fun files(): List<File> =
        folder.listFiles { file -> file.isFile && file.name.startsWith("crash-") && file.name.endsWith(".txt") }
            ?.sortedByDescending { it.name.removePrefix("crash-").removeSuffix(".txt").toLongOrNull() ?: 0 }
            .orEmpty()

    private fun prune() {
        files().drop(KEPT).forEach { it.delete() }
    }

    companion object {
        const val KEPT = 20

        /** The report, shaped like the issue body XTA files: the build, the system, the error and its stack. */
        fun text(error: Throwable, thread: String, appVersion: String, at: Instant): String {
            val stack = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString().trimEnd()
            return buildString {
                appendLine("## Crash report")
                appendLine()
                appendLine("- App: `XTA Native` `$appVersion`")
                appendLine("- OS: `${System.getProperty("os.name")} ${System.getProperty("os.version")}`")
                appendLine("- Thread: `$thread`")
                appendLine("- At: `$at`")
                appendLine()
                appendLine("### Error")
                appendLine("```")
                appendLine(error.toString())
                appendLine("```")
                appendLine()
                appendLine("### Stack trace")
                appendLine("```")
                appendLine(stack)
                append("```")
            }
        }
    }
}
