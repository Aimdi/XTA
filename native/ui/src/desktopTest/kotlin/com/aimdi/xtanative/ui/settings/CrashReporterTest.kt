package com.aimdi.xtanative.ui.settings

import com.aimdi.xtanative.core.x.XNoAccountException
import com.aimdi.xtanative.core.x.XNoWorkingAccountException
import com.aimdi.xtanative.core.x.XRateLimitedException

import com.aimdi.xtanative.store.settings.CrashReportPrefs
import com.aimdi.xtanative.store.settings.Settings
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class CrashReporterTest {
    private val dir: File = Files.createTempDirectory("crash-reporter").toFile()
    private val reports = CrashReports(dir)

    private class Sent(val url: String, val headers: Map<String, String>, val body: String)

    private class FakeGitHub(var status: Int = 201) : CrashReportHttp {
        val sent = mutableListOf<Sent>()

        override suspend fun post(url: String, headers: Map<String, String>, body: String): Int {
            sent += Sent(url, headers, body)
            return status
        }
    }

    private val on = Settings().with(CrashReportPrefs.enabled, true).with(CrashReportPrefs.githubToken, " ghp_secret ")

    private fun reporter(http: CrashReportHttp, settings: Settings = on, now: () -> Long = { 0L }) =
        CrashReporter(http, { settings }, reports, "1.2.3", now)

    private fun issue(message: String = "boom", frame: String = "a.B.c(B.kt:1)"): CrashIssue {
        val error = "java.lang.IllegalStateException: $message"
        val stack = "$error\n\tat $frame\n\tat a.B.d(B.kt:2)"
        return CrashIssue(error, stack, CrashIssue.body(error, stack, "1.2.3", "Linux 6", "main", Instant.EPOCH))
    }

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun theIssueIsFiledInTheReadersRepositoryWithTheirToken() = runBlocking {
        val github = FakeGitHub()
        val settings = on.with(CrashReportPrefs.githubRepo, " me/fork ")
        assertEquals(CrashReportResult.SENT, reporter(github, settings).report(issue()))
        val sent = github.sent.single()
        assertEquals("https://api.github.com/repos/me/fork/issues", sent.url)
        assertEquals("Bearer ghp_secret", sent.headers["Authorization"])
        assertEquals("2022-11-28", sent.headers["X-GitHub-Api-Version"])
        val json = Json.parseToJsonElement(sent.body).jsonObject
        assertEquals("[crash] IllegalStateException: boom", json["title"]?.jsonPrimitive?.content)
        assertTrue(json["body"]!!.jsonPrimitive.content.startsWith("## Crash report (auto)"))
    }

    @Test
    fun theTitleIsCutAt80AndTheBodyCarriesTheBuildTheErrorAndTheStack() {
        val long = issue("x".repeat(100))
        assertEquals("[crash] IllegalStateException: " + "x".repeat(77) + "...", long.title)
        val body = issue().body
        assertTrue(body.contains("- App: `com.aimdi.xtanative` `1.2.3`"))
        assertTrue(body.contains("- OS: `Linux 6`"))
        assertTrue(body.contains("### Error\n```\njava.lang.IllegalStateException: boom\n```"))
        assertTrue(body.contains("### Stack trace\n```\njava.lang.IllegalStateException: boom\n\tat a.B.c(B.kt:1)"))
        assertEquals("(none)", CrashIssue.of(CrashIssue.body("e", "", "1", "os", "main", Instant.EPOCH))?.stack)
    }

    @Test
    fun aKeptReportReadsBackAsTheSameIssue() {
        val text = CrashReports.text(IllegalStateException("kept"), "main", "1.2.3", Instant.EPOCH)
        val read = CrashIssue.of(text)!!
        assertEquals("java.lang.IllegalStateException: kept", read.error)
        assertTrue(read.stack.lineSequence().any { it.trim().startsWith("at ") })
        assertEquals(text, read.body)
        assertNull(CrashIssue.of("no report here"))
    }

    @Test
    fun theSameCrashIsFiledOnceWhileAnotherStillGoes() = runBlocking {
        val github = FakeGitHub()
        val reporter = reporter(github)
        assertEquals(CrashReportResult.SENT, reporter.report(issue()))
        assertEquals(CrashReportResult.DUPLICATE, reporter.report(issue()))
        assertEquals(CrashReportResult.SENT, reporter.report(issue(frame = "a.B.e(B.kt:9)")))
        assertEquals(2, github.sent.size)
    }

    @Test
    fun theAccountErrorsTheScreensExplainAreNotFiled() {
        assertTrue(CrashIssue.shouldReport(IllegalStateException("boom")))
        assertFalse(CrashIssue.shouldReport(XNoAccountException()))
        assertFalse(CrashIssue.shouldReport(XRateLimitedException()))
        assertFalse(CrashIssue.shouldReport(XNoWorkingAccountException()))
        assertFalse(CrashIssue.shouldReport(IOException("offline")))
    }

    @Test
    fun theRepositoryMustBeOwnerSlashName() {
        assertEquals("https://api.github.com/repos/Aimdi/XTA-gamma/issues", CrashReporter.issueApiUrl("Aimdi/XTA-gamma"))
        assertNull(CrashReporter.issueApiUrl("bad"))
        assertNull(CrashReporter.issueApiUrl("/"))
    }

    @Test
    fun theFingerprintIsTheErrorAndItsFirstFourFrames() {
        val error = "java.lang.Error: x"
        val stack = (1..6).joinToString("\n") { "\tat a.B.f$it(B.kt:$it)" }
        assertEquals("$error|at a.B.f1(B.kt:1)|at a.B.f2(B.kt:2)|at a.B.f3(B.kt:3)|at a.B.f4(B.kt:4)", CrashIssue(error, stack, "").fingerprint)
    }

    @Test
    fun noMoreThanFiveAnHourGo() = runBlocking {
        var now = 0L
        val reporter = reporter(FakeGitHub()) { now }
        repeat(CrashReporter.PER_HOUR) { assertEquals(CrashReportResult.SENT, reporter.report(issue("boom $it"))) }
        assertEquals(CrashReportResult.RATE_LIMITED, reporter.report(issue("one too many")))
        now += 60 * 60 * 1000L + 1
        assertEquals(CrashReportResult.SENT, reporter.report(issue("an hour on")))
    }

    @Test
    fun whatHoldsAReportBack() = runBlocking {
        val github = FakeGitHub()
        assertEquals(CrashReportResult.DISABLED, reporter(github, on.with(CrashReportPrefs.enabled, false)).report(issue()))
        assertEquals(CrashReportResult.MISSING_TOKEN, reporter(github, on.with(CrashReportPrefs.githubToken, " ")).report(issue()))
        assertEquals(CrashReportResult.INVALID_REPO, reporter(github, on.with(CrashReportPrefs.githubRepo, "no-slash")).report(issue()))
        assertTrue(github.sent.isEmpty())
        github.status = 401
        assertEquals(CrashReportResult.AUTH_FAILED, reporter(github).report(issue()))
        github.status = 500
        assertEquals(CrashReportResult.FAILED, reporter(github).report(issue()))
        val down = CrashReportHttp { _, _, _ -> throw IOException("offline") }
        assertEquals(CrashReportResult.FAILED, reporter(down).report(issue()))
    }

    @Test
    fun theTestReportGoesWhileReportsAreOffButNotWithoutAToken() = runBlocking {
        val github = FakeGitHub()
        val off = on.with(CrashReportPrefs.enabled, false)
        assertEquals(CrashReportResult.SENT, reporter(github, off).sendTestReport())
        assertTrue(github.sent.single().body.contains("manual test from Settings"))
        assertEquals(CrashReportResult.MISSING_TOKEN, reporter(github, off.with(CrashReportPrefs.githubToken, "")).sendTestReport())
    }

    @Test
    fun keptCrashesAreFiledOldestFirstOnceEachAndNetworkFailuresNotAtAll() = runBlocking {
        var at = 1_000L
        val kept = CrashReports(dir) { at++ }
        kept.record(IllegalStateException("first"), "main", "1.2.3")
        kept.record(IOException("offline"), "main", "1.2.3")
        kept.record(IllegalStateException("second"), "main", "1.2.3")
        val github = FakeGitHub()
        val reporter = CrashReporter(github, { on }, kept, "1.2.3")
        reporter.sendKept()
        assertEquals(listOf("first", "second"), github.sent.map { Json.parseToJsonElement(it.body).jsonObject["title"]!!.jsonPrimitive.content.substringAfterLast(' ') })
        assertTrue(kept.list().none { it.unsent })
        reporter.sendKept()
        assertEquals(2, github.sent.size)
    }

    @Test
    fun aKeptCrashThatCouldNotGoWaitsForTheNextStart() = runBlocking {
        val kept = CrashReports(dir) { 1_000L }
        kept.record(IllegalStateException("later"), "main", "1.2.3")
        val github = FakeGitHub(status = 500)
        CrashReporter(github, { on }, kept, "1.2.3").sendKept()
        assertTrue(kept.list().single().unsent)
        CrashReporter(github, { on.with(CrashReportPrefs.enabled, false) }, kept, "1.2.3").sendKept()
        assertEquals(1, github.sent.size)
        github.status = 201
        CrashReporter(github, { on }, kept, "1.2.3").sendKept()
        assertFalse(kept.list().single().unsent)
    }

    @Test
    fun aScreenThatBrokeIsShownOnceAtTheNextStart() {
        assertTrue(CrashReports.isScreensThread("main"))
        assertTrue(CrashReports.isScreensThread("AWT-EventQueue-0"))
        assertFalse(CrashReports.isScreensThread("DefaultDispatcher-worker-1"))
        assertNull(reports.takeLastCrash())
        val before = Thread.getDefaultUncaughtExceptionHandler()
        try {
            Thread.setDefaultUncaughtExceptionHandler(null)
            var recorded = 0
            reports.install({ false }, "1.2.3") { recorded++ }
            val handler = Thread.getDefaultUncaughtExceptionHandler()
            handler.uncaughtException(Thread("DefaultDispatcher-worker-1"), IllegalStateException("off screen"))
            assertNull(reports.takeLastCrash())
            handler.uncaughtException(Thread("main"), IllegalStateException("drawn"))
            assertTrue(reports.takeLastCrash()!!.text.contains("java.lang.IllegalStateException: drawn"))
            assertNull(reports.takeLastCrash())
            assertTrue(reports.list().isEmpty())
            assertEquals(0, recorded)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(before)
        }
    }
}
