package com.aimdi.xtanative.core.x.client

import com.aimdi.xtanative.core.x.FileAccountHealthStore
import com.aimdi.xtanative.core.x.XAccount
import com.aimdi.xtanative.core.x.XAccountHealth
import com.aimdi.xtanative.core.x.XAccountHealthStore
import com.aimdi.xtanative.core.x.XAnswerException
import com.aimdi.xtanative.core.x.XClient
import com.aimdi.xtanative.core.x.XEndpointRefusedException
import com.aimdi.xtanative.core.x.XNoWorkingAccountException
import com.aimdi.xtanative.core.x.XRateLimitedException
import com.aimdi.xtanative.core.x.XReadLog
import com.aimdi.xtanative.core.x.XRequestException
import com.aimdi.xtanative.core.x.XSigners
import com.aimdi.xtanative.core.x.XUserUnavailableException
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.core.x.signing.SigningUnavailableException
import com.aimdi.xtanative.core.x.signing.TransactionSigner
import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.file.Files
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** XTA's read_activity.dart and readFailureLabel: what Diagnostics' recent reads keep, and nothing more. */
class XReadLogTest {
    private val at = 1_784_548_800_000L

    private fun log(capacity: Int = 60) = XReadLog(capacity, clock = { at })

    @Test
    fun aReadIsKeptWithWhenItStartedAndHowItEnded(): Unit = runBlocking {
        val reads = log()
        reads.track("UserTweets") { "page" }
        val line = reads.snapshot().single()
        assertTrue(line.startsWith("2026-07-20T12:00:00Z UserTweets completed "), line)
        assertTrue(line.endsWith("ms"), line)
    }

    @Test
    fun aFailureIsKeptByItsCategoryNeverItsText(): Unit = runBlocking {
        val reads = log()
        assertFailsWith<XRequestException> { reads.track("SearchTimeline") { throw XRequestException(403, "secret query text") } }
        val line = reads.snapshot().single()
        assertTrue(" SearchTimeline failed (unavailable 403) " in line, line)
        assertFalse("secret" in line)
    }

    @Test
    fun timeoutsAndCancellationsAreOutcomesOfTheirOwn(): Unit = runBlocking {
        val reads = log()
        assertFailsWith<SocketTimeoutException> { reads.track("TweetDetail") { throw SocketTimeoutException("timeout") } }
        assertFailsWith<CancellationException> { reads.track("UserByScreenName") { throw CancellationException("left") } }
        val (timedOut, cancelled) = reads.snapshot()
        assertTrue(" TweetDetail timedOut " in timedOut, timedOut)
        assertTrue(" UserByScreenName cancelled " in cancelled, cancelled)
    }

    @Test
    fun onlyTheNewestReadsAreKept(): Unit = runBlocking {
        val reads = log(capacity = 2)
        listOf("a", "b", "c").forEach { what -> reads.track(what) { Unit } }
        assertEquals(listOf("b", "c"), reads.snapshot().map { it.split(' ')[1] })
        val none = log(capacity = 0)
        none.track("a") { Unit }
        assertTrue(none.snapshot().isEmpty())
    }

    @Test
    fun theCategoriesAreXtas() {
        assertEquals("rateLimited", XReadLog.failureOf(XRateLimitedException()))
        assertEquals("session", XReadLog.failureOf(XNoWorkingAccountException()))
        assertEquals("endpointRefused", XReadLog.failureOf(XEndpointRefusedException("UserTweets")))
        assertEquals("unavailable 404", XReadLog.failureOf(XUserUnavailableException("gone", "Suspended")))
        assertEquals("rateLimited 429", XReadLog.failureOf(XRequestException(429, "")))
        assertEquals("session 401", XReadLog.failureOf(XRequestException(401, "")))
        assertEquals("serviceUnavailable 503", XReadLog.failureOf(XRequestException(503, "")))
        assertEquals("unknown XRequestException", XReadLog.failureOf(XRequestException(400, "")))
        assertEquals("session", XReadLog.failureOf(XAnswerException(32, "Could not authenticate you")))
        assertEquals("unknown XAnswerException", XReadLog.failureOf(XAnswerException(34, "")))
        assertEquals("transactionUnavailable", XReadLog.failureOf(SigningUnavailableException("")))
        assertEquals("connection", XReadLog.failureOf(IOException("")))
        assertEquals("unknown IllegalStateException", XReadLog.failureOf(IllegalStateException("")))
    }

    @Test
    fun theClientKeepsEveryReadItAsksOfX(): Unit = runBlocking {
        val x = XHttp { _, _, _ -> XResponse(503, "") }
        val client = XClient(
            http = x,
            accounts = { listOf(XAccount("ct0-made-up", "reader", mapOf("Cookie" to "auth_token=made-up; ct0=ct0-made-up"))) },
            signers = XSigners({ TransactionSigner(IntArray(48) { it }, "animation") }),
            clock = { at },
        )
        assertFailsWith<XRequestException> { client.profile("someone") }
        val line = client.reads.snapshot().single()
        assertTrue(" UserByScreenName failed (serviceUnavailable 503) " in line, line)
        assertFalse("ct0-made-up" in line)
    }
}

/** XTA keeps each account's health in its accounts table; here one small file keeps it across launches. */
class FileAccountHealthStoreTest {
    private val dir = Files.createTempDirectory("health").toFile()
    private val file = dir.resolve("account-health.json")

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun aFlagOutlivesTheStoreThatWroteIt() {
        FileAccountHealthStore(file).write("ct0-made-up", XAccountHealth(3, 1_000L))
        assertEquals(XAccountHealth(3, 1_000L), FileAccountHealthStore(file).read("ct0-made-up"))
        assertTrue(FileAccountHealthStore(file).read("another").isClean)
    }

    @Test
    fun theFileNamesNoSession() {
        FileAccountHealthStore(file).write("ct0-made-up", XAccountHealth(1))
        assertFalse("ct0-made-up" in file.readText())
    }

    @Test
    fun aCleanAccountIsForgotten() {
        val store: XAccountHealthStore = FileAccountHealthStore(file)
        store.write("ct0-made-up", XAccountHealth(2))
        store.write("ct0-made-up", XAccountHealth())
        assertTrue(FileAccountHealthStore(file).read("ct0-made-up").isClean)
        assertFalse("refusals" in file.readText())
    }

    @Test
    fun anUnreadableFileReadsAsHealthy() {
        file.writeText("not json")
        assertTrue(FileAccountHealthStore(file).read("ct0-made-up").isClean)
    }
}
