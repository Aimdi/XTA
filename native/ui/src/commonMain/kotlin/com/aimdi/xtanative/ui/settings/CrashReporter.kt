package com.aimdi.xtanative.ui.settings

import com.aimdi.xtanative.core.x.XNoAccountException
import com.aimdi.xtanative.core.x.XRateLimitedException
import com.aimdi.xtanative.store.settings.CrashReportPrefs
import com.aimdi.xtanative.store.settings.Settings
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** How filing a crash report went, as XTA's CrashReportResult says it. */
enum class CrashReportResult { SENT, DISABLED, IGNORED, MISSING_TOKEN, DUPLICATE, RATE_LIMITED, INVALID_REPO, AUTH_FAILED, FAILED }

/**
 * One crash as the GitHub issue XTA files for it (crash_reporter.dart): its
 * [error] line (`java.lang.IllegalStateException: boom`) and [stack], read
 * back from a kept report's text ([of]).
 */
data class CrashIssue(val error: String, val stack: String, val body: String) {
    /** `[crash] IllegalStateException: boom`, the message cut at 80 characters as XTA cuts it. */
    val title: String
        get() {
            val line = error.replace('\n', ' ').trim()
            val type = line.substringBefore(": ").substringAfterLast('.')
            val message = line.substringAfter(": ", "").ifEmpty { line }
            val clipped = if (message.length > 80) message.take(77) + "..." else message
            return "[crash] $type: $clipped"
        }

    /** What tells one crash from another, as XTA's fingerprintOf: the error, and the first four frames by their place in the code. */
    val fingerprint: String
        get() = error + "|" + stack.lineSequence().map { it.trim() }.filter { it.startsWith("at ") }.take(4).joinToString("|")

    companion object {
        private const val FENCE = "```"

        /** The issue in a kept report's [text]; null when it is no report. */
        fun of(text: String): CrashIssue? {
            val error = section(text, "### Error") ?: return null
            return CrashIssue(error, section(text, "### Stack trace").orEmpty(), text)
        }

        private fun section(text: String, heading: String): String? {
            val lines = text.lines()
            val start = lines.indexOf(heading).takeIf { it >= 0 && lines.getOrNull(it + 1) == FENCE } ?: return null
            val end = (start + 2 until lines.size).firstOrNull { lines[it] == FENCE } ?: lines.size
            return lines.subList(start + 2, end).joinToString("\n")
        }

        /**
         * XTA files no network failure that slipped through, nor a cancelled
         * job, nor the account errors the screens explain (no account, none
         * working, every one rate-limited): only the app's own faults.
         */
        fun shouldReport(error: Throwable): Boolean = when (error) {
            is IOException, is CancellationException, is XNoAccountException, is XRateLimitedException -> false
            else -> true
        }

        /** The issue's body, as XTA writes it, with the thread and the time here. */
        fun body(error: String, stack: String, appVersion: String, os: String, thread: String, at: Instant, context: String? = null): String = buildString {
            appendLine("## Crash report (auto)")
            appendLine()
            appendLine("- App: `$PACKAGE` `$appVersion`")
            appendLine("- OS: `$os`")
            appendLine("- Thread: `$thread`")
            appendLine("- At: `$at`")
            if (!context.isNullOrEmpty()) appendLine("- Context: `$context`")
            appendLine()
            appendLine("### Error")
            appendLine(FENCE)
            appendLine(error)
            appendLine(FENCE)
            appendLine()
            appendLine("### Stack trace")
            appendLine(FENCE)
            appendLine(stack.ifEmpty { "(none)" })
            append(FENCE)
        }

        const val PACKAGE = "com.aimdi.xtanative"
    }
}

/** The GitHub call the reporter makes: its status code. */
fun interface CrashReportHttp {
    suspend fun post(url: String, headers: Map<String, String>, body: String): Int
}

/**
 * Opt-in crash reports that open GitHub issues with the reader's own token
 * (XTA's CrashReporter): never a token of the app's, off by default. The
 * crashes [reports] kept are filed oldest first; a crash filed already in
 * this run is not filed again, and no more than [PER_HOUR] go in an hour.
 */
class CrashReporter(
    private val http: CrashReportHttp,
    private val settings: suspend () -> Settings,
    private val reports: CrashReports,
    private val appVersion: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val filed = HashSet<String>()
    private var windowStart = 0L
    private var inWindow = 0

    /** Files [issue] unless the rules above hold it back; [force] (the test report) passes them all but the token. */
    suspend fun report(issue: CrashIssue, force: Boolean = false): CrashReportResult = mutex.withLock { send(issue, force) }

    /** The kept crashes not filed yet, oldest first, until one cannot be: it waits for the next start. */
    suspend fun sendKept(): Unit = mutex.withLock {
        val unsent = withContext(Dispatchers.IO) { reports.list() }.filter { it.unsent }.reversed()
        for (report in unsent) {
            val issue = CrashIssue.of(report.text)
            val result = if (issue == null) CrashReportResult.IGNORED else send(issue, force = false)
            if (result in DONE) reports.markSent(report) else break
        }
    }

    /** XTA's "Send test crash report", from Settings. */
    suspend fun sendTestReport(): CrashReportResult {
        val stack = Throwable().stackTrace.joinToString("\n") { "\tat $it" }
        val body = CrashIssue.body(TEST_ERROR, stack, appVersion, os(), Thread.currentThread().name, Instant.ofEpochMilli(clock()), "manual test from Settings")
        return report(CrashIssue(TEST_ERROR, stack, body), force = true)
    }

    private suspend fun send(issue: CrashIssue, force: Boolean): CrashReportResult {
        val prefs = settings()
        if (!force && !prefs[CrashReportPrefs.enabled]) return CrashReportResult.DISABLED
        val token = prefs[CrashReportPrefs.githubToken].trim()
        if (token.isEmpty()) return CrashReportResult.MISSING_TOKEN
        if (!force && issue.fingerprint in filed) return CrashReportResult.DUPLICATE
        val url = issueApiUrl(repository(prefs)) ?: return CrashReportResult.INVALID_REPO
        if (!force && !takeFromBudget()) return CrashReportResult.RATE_LIMITED
        val status = try {
            http.post(url, headers(token), buildJsonObject { put("title", issue.title); put("body", issue.body) }.toString())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return CrashReportResult.FAILED
        }
        return when (status) {
            201 -> CrashReportResult.SENT.also { filed += issue.fingerprint }
            401, 403 -> CrashReportResult.AUTH_FAILED
            else -> CrashReportResult.FAILED
        }
    }

    private fun takeFromBudget(): Boolean {
        val now = clock()
        if (now - windowStart > HOUR) {
            windowStart = now
            inWindow = 0
        }
        if (inWindow >= PER_HOUR) return false
        inWindow++
        return true
    }

    companion object {
        const val PER_HOUR = 5
        private const val HOUR = 60 * 60 * 1000L
        private const val TEST_ERROR = "java.lang.Exception: XTA crash-report test"

        /** A kept report is done with once filed, held back as a duplicate, or not one to file. */
        private val DONE = setOf(CrashReportResult.SENT, CrashReportResult.DUPLICATE, CrashReportResult.IGNORED)

        /** The repository the reader named, else XTA's. */
        fun repository(settings: Settings): String = settings[CrashReportPrefs.githubRepo].trim().ifEmpty { CrashReportPrefs.githubRepo.default }

        /** GitHub's address for opening an issue in [repo] (`owner/name`); null when [repo] is not one. */
        fun issueApiUrl(repo: String): String? {
            val parts = repo.split('/')
            if (parts.size != 2 || parts.any { it.isEmpty() }) return null
            return "https://api.github.com/repos/${parts[0]}/${parts[1]}/issues"
        }

        fun headers(token: String): Map<String, String> = mapOf(
            "Accept" to "application/vnd.github+json",
            "Authorization" to "Bearer $token",
            "X-GitHub-Api-Version" to "2022-11-28",
            "Content-Type" to "application/json",
        )

        private fun os(): String = "${System.getProperty("os.name")} ${System.getProperty("os.version")}"
    }
}

/** The GitHub call over OkHttp, given up after 30 seconds. */
class OkHttpCrashReports(client: OkHttpClient = OkHttpClient()) : CrashReportHttp {
    private val client = client.newBuilder().callTimeout(30, TimeUnit.SECONDS).build()

    override suspend fun post(url: String, headers: Map<String, String>, body: String): Int = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()
        client.newCall(request).execute().use { it.code }
    }
}
