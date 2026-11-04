package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.threads.ThreadsFailure.NOT_CONFIGURED
import com.aimdi.xtanative.networks.threads.ThreadsFailure.NO_SUCH_FEED
import com.aimdi.xtanative.networks.threads.ThreadsFailure.SESSION_SUSPENDED
import com.aimdi.xtanative.networks.threads.ThreadsFailure.THROTTLED
import com.aimdi.xtanative.networks.threads.ThreadsFailure.UNAUTHORIZED
import com.aimdi.xtanative.networks.threads.ThreadsFailure.UNREACHABLE
import java.io.IOException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Retrying by what Meta said, and backing off when it says to: XTA's
 * threads_retry_test and threads_account_safety_test. A session that behaves
 * like a script is the one thing that can cost the reader their account.
 */
class ThreadsFailuresTest {
    private val time = FakeTime()
    private val minutes = 60 * 1000L

    private fun failure(read: suspend () -> Any?): ThreadsException = assertFailsWith { runBlocking { read() } }

    @Test
    fun aSessionThrottleWaitsForTheCooldownMetasAnswerArmedAndSoDoesTheNextRead() {
        val meta = FakeMeta { XResponse(429, "{}") }
        val client = direct(meta, ThreadsSession(COOKIES), time = time)

        val throttled = failure { client.searchUsers("maya") }
        assertEquals(THROTTLED, throttled.failure)
        assertEquals(429, throttled.code)
        assertEquals(ThreadsRetry.AT_RESET, throttled.retry)
        assertEquals(Instant.ofEpochMilli(time.now + 30 * minutes), throttled.retryAt)
        assertTrue(client.isSessionParked)

        val parked = failure { client.searchUsers("maya") }
        assertEquals(SESSION_SUSPENDED, parked.failure)
        assertEquals(ThreadsRetry.AT_RESET, parked.retry)
        assertEquals(throttled.retryAt, parked.retryAt)
        assertEquals(1, meta.asked.size, "nothing reaches Meta while the session is parked")
    }

    @Test
    fun pleaseWaitAFewMinutesIsAThrottleWhateverTheStatus() {
        val throttled = failure { direct(FakeMeta { ok("Please wait a few minutes before you try again.") }, ThreadsSession(COOKIES), time = time).currentUser() }
        assertEquals(THROTTLED, throttled.failure)
    }

    @Test
    fun aLostSessionWaitsForTheReaderAndIsParked() {
        val meta = FakeMeta { XResponse(403, """{"message":"login_required","logout_reason":8}""") }
        val client = direct(meta, ThreadsSession(COOKIES), time = time)
        val lost = failure { client.searchUsers("maya") }
        assertEquals(SESSION_SUSPENDED, lost.failure)
        assertEquals(ThreadsRetry.MANUAL, lost.retry)
        assertNull(lost.retryAt)
        assertTrue(client.isSessionParked, "coming straight back for more is how a block becomes a ban")
    }

    @Test
    fun aRefusedSessionIsUnauthorizedAndParksNothing() {
        val client = direct(FakeMeta { XResponse(401, "{}") }, ThreadsSession(COOKIES), time = time)
        val refused = failure { client.currentUser() }
        assertEquals(UNAUTHORIZED, refused.failure)
        assertEquals(ThreadsRetry.MANUAL, refused.retry)
        assertFalse(client.isSessionParked)
    }

    @Test
    fun aGuestThrottleWaitsAsLongAsAnyLimitWithoutAStatedResetAndParksNoSession() {
        val client = direct(FakeMeta { XResponse(429, "") }, ThreadsSession(COOKIES), time = time)
        val throttled = failure { client.searchPosts("kiln") }
        assertEquals(THROTTLED, throttled.failure)
        assertEquals(ThreadsRetry.AT_RESET, throttled.retry)
        assertEquals(Instant.ofEpochMilli(time.now + 15 * minutes), throttled.retryAt)
        assertFalse(client.isSessionParked)
    }

    @Test
    fun aHostThatDidNotAnswerIsTriedAgainOnTheUsualBackoff() {
        val unreachable = failure { direct(FakeMeta { XResponse(503, "down") }, time = time).searchPosts("kiln") }
        assertEquals(UNREACHABLE, unreachable.failure)
        assertEquals(503, unreachable.code)
        assertEquals(ThreadsRetry.BACKOFF, unreachable.retry)
        assertEquals(ThreadsRetry.BACKOFF, ThreadsException(NO_SUCH_FEED, "no posts").retry)
        assertEquals(ThreadsRetry.BACKOFF, failure { direct(FakeMeta { XResponse(404, "") }, time = time).guestReplies("nobody") }.retry)
    }

    @Test
    fun aConnectionThatFailedStaysTheTransportsErrorAndIsTriedAgain() {
        val thrown = assertFailsWith<IOException> {
            runBlocking { direct(FakeMeta { throw IOException("no route to host") }, time = time).searchPosts("kiln") }
        }
        assertEquals("no route to host", thrown.message, "not wrapped")
        assertEquals(ThreadsRetry.BACKOFF, threadsRetryOf(thrown))
    }

    @Test
    fun withoutASetupOrWithTheSessionRefusedNothingRetriesOnItsOwn() {
        listOf(NOT_CONFIGURED, UNAUTHORIZED, SESSION_SUSPENDED, THROTTLED).forEach { kind ->
            assertEquals(ThreadsRetry.MANUAL, threadsRetryOf(ThreadsException(kind, "no")), kind.name)
        }
        assertEquals("threads", ThreadsException(NOT_CONFIGURED, "no").network)
    }

    @Test
    fun aSessionReadWithoutCookiesIsNotConfiguredAndAsksNothing() {
        val meta = FakeMeta { ok("{}") }
        assertEquals(NOT_CONFIGURED, failure { direct(meta, ThreadsSession("sessionid=only"), time = time).currentUser() }.failure)
        assertTrue(meta.asked.isEmpty())
    }

    @Test
    fun aThrottleIsRememberedPastARestart() {
        val memory = ThreadsMemory.InMemory()
        failure { direct(FakeMeta { XResponse(429, "Please wait a few minutes") }, ThreadsSession(COOKIES), memory, time).currentUser() }
        assertTrue(memory.kept.cooldownUntil != null)

        // A new client is what the reader gets by force-quitting and reopening.
        val restarted = FakeMeta { ok("""{"user":{"username":"me","pk":"1"}}""") }
        val parked = failure { direct(restarted, ThreadsSession(COOKIES), memory, time).currentUser() }
        assertEquals(SESSION_SUSPENDED, parked.failure)
        assertTrue(restarted.asked.isEmpty(), "nothing may reach Meta while the session is parked")
    }

    @Test
    fun anEndedCooldownLetsReadingResumeAndClearsItself() = runBlocking {
        val memory = ThreadsMemory.InMemory(ThreadsKept(cooldownUntil = time.now - minutes))
        val client = direct(FakeMeta { ok("""{"user":{"username":"me","pk":"1"}}""") }, ThreadsSession(COOKIES), memory, time)
        assertEquals("me", client.currentUser().username)
        assertNull(memory.kept.cooldownUntil)
    }

    @Test
    fun guestReadsGoOnWhileTheSessionCoolsDown() = runBlocking {
        val memory = ThreadsMemory.InMemory(ThreadsKept(cooldownUntil = time.now + 60 * minutes))
        val meta = FakeMeta { asked -> ok(if (asked.method == "GET") profilePage() else graphql(postJson("3", text = "during cooldown"))) }
        val client = direct(meta, ThreadsSession(COOKIES), memory, time)
        assertEquals(listOf("during cooldown"), client.guestAccount("zuck").items.map { it.text })
        assertTrue(client.isSessionParked)
    }
}
