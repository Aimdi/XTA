package com.aimdi.xtanative.ui.settings

import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.XAccount
import com.aimdi.xtanative.core.x.XClient
import com.aimdi.xtanative.core.x.XEndpoints
import com.aimdi.xtanative.core.x.XRateLimits
import com.aimdi.xtanative.core.x.XSigners
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.core.x.signing.TransactionSigner
import com.aimdi.xtanative.data.SessionStore
import com.aimdi.xtanative.store.StoreDir
import com.aimdi.xtanative.store.settings.AccountPrefs
import com.aimdi.xtanative.store.settings.DataPrefs
import com.aimdi.xtanative.store.settings.GeneralPrefs
import com.aimdi.xtanative.store.settings.MediaPrefs
import com.aimdi.xtanative.store.settings.PostsPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.SettingsStore
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class CrashReportsTest {
    private val dir: File = Files.createTempDirectory("crashes").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun aCrashIsWrittenDownNewestFirstAndOnlyTheNewestStay() {
        var now = 1_000L
        val reports = CrashReports(dir) { now }
        repeat(CrashReports.KEPT + 2) {
            reports.record(IllegalStateException("boom $it"), "main", "1.0")
            now += 1
        }
        val kept = reports.list()
        assertEquals(CrashReports.KEPT, kept.size)
        assertTrue(kept.first().text.contains("boom ${CrashReports.KEPT + 1}"))
        assertTrue(kept.first().text.contains("`1.0`"))
        assertTrue(kept.first().text.contains("CrashReportsTest"))
        assertEquals(Instant.ofEpochMilli(now - 1), kept.first().at)
        reports.clear()
        assertTrue(reports.list().isEmpty())
    }
}

class DiagnosticsTest {
    @Test
    fun theReportNamesAccountsByHandleOnlyAndTheirLimits() {
        val endpoints = XEndpoints()
        val limits = XRateLimits()
        val now = Instant.parse("2026-11-03T10:00:00Z")
        val path = endpoints.url(XEndpoints.SEARCH, emptyMap()).encodedPath
        limits.flag("secret-ct0", path, now.toEpochMilli() + 60_000)
        val report = DiagnosticsReport.of(
            appVersion = "1.0",
            accounts = listOf(XAccount("secret-ct0", "reader", emptyMap()), XAccount("other", null, emptyMap())),
            endpoints = endpoints,
            limits = limits,
            settings = Settings().with(DataPrefs.endpointRegistryFetchedAt, "2026-11-01T00:00:00.000Z"),
            crashReports = 2,
            now = now,
        )
        assertFalse(report.accounts.first().isHealthy)
        assertTrue(XEndpoints.SEARCH in report.accounts.first().rateLimited)
        assertTrue(report.accounts.last().isHealthy)
        val text = report.toPlainText()
        assertTrue(text.contains("@reader: 429 ${XEndpoints.SEARCH}"))
        assertTrue(text.contains("unnamed account: ok"))
        assertFalse(text.contains("secret-ct0"))
        assertTrue(text.contains("last checked 2026-11-01T00:00:00Z"))
        assertTrue(text.contains("crash reports kept: 2"))
        assertEquals(XEndpoints.shipped.size, report.endpoints.size)
        assertFalse(text.contains("local checks:"), "no watchdog, no local checks")
    }

    @Test
    fun theWatchdogsRecordShowsItsLatestLinesInTheReport() {
        val dir = Files.createTempDirectory("stalls").toFile()
        val file = File(dir, MainThreadStalls.FILE)
        assertEquals("not watched on this platform", MainThreadStalls.summary(null))
        assertEquals("no stall recorded", MainThreadStalls.summary(file))
        file.parentFile.mkdirs()
        file.writeText((1..70).joinToString("\n") { "line $it" } + "\n\n")
        val summary = MainThreadStalls.summary(file)
        assertTrue(summary.startsWith("\n  line 11\n"))
        assertTrue(summary.endsWith("  line 70"))
        assertEquals("no stall recorded", MainThreadStalls.describe(" \n\n"))
        val report = DiagnosticsReport.of("1.0", emptyList(), XEndpoints(), XRateLimits(), Settings(), 0, Instant.EPOCH, mainThreadStalls = "no stall recorded")
        assertTrue(report.toPlainText().contains("local checks:\n  android main thread: no stall recorded"))
        dir.deleteRecursively()
    }
}

class SettingsStartTest {
    private val dir: File = Files.createTempDirectory("start").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun theLaunchStepsRunAndTheSessionFollowsTheSettings(): Unit = runBlocking {
        val settings = SettingsStore(StoreDir(File(dir, "store")))
        val sessions = SessionStore(File(dir, "session.json"), initial = Session(includeReposts = false))
        val keeping = launch { SettingsStart.keepSessionInStep(settings, sessions) }
        withTimeout(5_000) {
            while (settings.state.value == null || sessions.state.value == null) delay(10)
        }
        // A first launch: the cards are still to be seen, and the prototype's switch was carried over.
        assertFalse(settings.state.value!![GeneralPrefs.introSeen])
        withTimeout(5_000) { while (settings.state.value!![PostsPrefs.includeReposts]) delay(10) }
        settings.set(MediaPrefs.autoplay, true)
        settings.set(PostsPrefs.includeReplies, false)
        withTimeout(5_000) {
            while (sessions.state.value!!.includeReplies || !sessions.state.value!!.autoplay) delay(10)
        }
        assertFalse(sessions.state.value!!.includeReposts)
        keeping.cancel()
    }

    @Test
    fun theAccountsTurnedOffReachTheClientsGateAsTheyChange(): Unit = runBlocking {
        val settings = SettingsStore(StoreDir(File(dir, "store")))
        var off: Set<String>? = null
        val keeping = launch { SettingsStart.keepAccountsOffInStep(settings) { off = it } }
        withTimeout(5_000) { while (off == null) delay(10) }
        assertEquals(emptySet<String>(), off)
        settings.set(AccountPrefs.disabledAccountIds, listOf("2", "3"))
        withTimeout(5_000) { while (off != setOf("2", "3")) delay(10) }
        settings.set(AccountPrefs.disabledAccountIds, listOf("3"))
        withTimeout(5_000) { while (off != setOf("3")) delay(10) }
        keeping.cancel()
    }

    private fun client() = XClient(
        http = XHttp { _, _, _ -> XResponse(500, "") },
        accounts = { emptyList() },
        signers = XSigners({ TransactionSigner(IntArray(48) { it }, "animation") }),
    )

    private fun registry(id: String) = """{"endpoints":{"${XEndpoints.SEARCH}":"$id"}}"""

    @Test
    fun theKeptEndpointsApplyFirstAndThePublishedOnesAreReadOnceADayAndKept(): Unit = runBlocking {
        val settings = SettingsStore(StoreDir(File(dir, "store")))
        val now = Instant.parse("2026-11-05T12:00:00Z")
        val asked = ArrayList<String>()
        val first = client()
        SettingsStart.refreshEndpoints(settings, first, { now }) { url -> registry("Fresh_Id_123456789012").also { asked += url } }
        assertEquals("Fresh_Id_123456789012", first.endpoints.queryId(XEndpoints.SEARCH))
        assertEquals(registry("Fresh_Id_123456789012"), settings.get(DataPrefs.endpointRegistryCache))

        // The next launch, within the day: the kept copy is in force without asking again.
        val next = client()
        SettingsStart.refreshEndpoints(settings, next, { now.plusSeconds(3_600) }) { url -> null.also { asked += url } }
        assertEquals("Fresh_Id_123456789012", next.endpoints.queryId(XEndpoints.SEARCH))
        assertEquals(1, asked.size, "read at most once a day")

        // A day later it is read again; a failure keeps the copy and its time.
        SettingsStart.refreshEndpoints(settings, client(), { now.plusSeconds(25 * 3_600) }) { url -> null.also { asked += url } }
        assertEquals(2, asked.size)
        assertEquals(registry("Fresh_Id_123456789012"), settings.get(DataPrefs.endpointRegistryCache))

        // Turned off: the kept copy still applies, as XTA's applyCached, but nothing is read.
        settings.set(DataPrefs.endpointRegistryEnabled, false)
        val off = client()
        SettingsStart.refreshEndpoints(settings, off, { now.plusSeconds(50 * 3_600) }) { url -> null.also { asked += url } }
        assertEquals(2, asked.size)
        assertEquals("Fresh_Id_123456789012", off.endpoints.queryId(XEndpoints.SEARCH))
    }
}
