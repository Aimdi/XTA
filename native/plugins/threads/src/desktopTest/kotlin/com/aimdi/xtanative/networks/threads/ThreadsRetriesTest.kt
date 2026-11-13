package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.networks.threads.ThreadsFailure.NOT_CONFIGURED
import com.aimdi.xtanative.networks.threads.ThreadsFailure.SESSION_SUSPENDED
import com.aimdi.xtanative.networks.threads.ThreadsFailure.THROTTLED
import com.aimdi.xtanative.networks.threads.ThreadsFailure.UNREACHABLE
import java.io.IOException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class ThreadsRetriesTest {
    private val now = 1_000_000L

    @Test
    fun aPassingFailureIsTriedAgainAfterEachOfXtasWaitsOnce() {
        val budget = ThreadsRetryBudget()
        val waits = generateSequence { budget.waitFor(IOException("no route"), now) }.take(10).toList()
        assertEquals(ThreadsRetryBudget.DELAYS_MILLIS, waits, "2, 5, 15, 30 and 60 seconds, then no more")
        assertNull(budget.waitFor(ThreadsException(UNREACHABLE, "503", 503), now))
    }

    @Test
    fun aWaitMetaAskedForIsSatOutOnceASecondPastItsReset() {
        val budget = ThreadsRetryBudget()
        val throttled = ThreadsException(THROTTLED, "429", 429, retryAt = Instant.ofEpochMilli(now + 20_000))
        assertEquals(21_000L, budget.waitFor(throttled, now))
        assertNull(budget.waitFor(throttled, now), "once")
        assertEquals(1_000L, ThreadsRetryBudget().waitFor(throttled, now + 60_000), "a reset already past is a second away")
    }

    @Test
    fun aLongWaitAndWhatOnlyTheReaderCanMendAreNotWaitedFor() {
        val parked = ThreadsException(SESSION_SUSPENDED, "cooling down", retryAt = Instant.ofEpochMilli(now + 30 * 60_000))
        assertNull(ThreadsRetryBudget().waitFor(parked, now), "half an hour is the reader's to sit out, told why")
        assertNull(ThreadsRetryBudget().waitFor(ThreadsException(NOT_CONFIGURED, "no accounts"), now))
        assertNull(ThreadsRetryBudget().waitFor(ThreadsException(THROTTLED, "no reset said"), now))
    }

    @Test
    fun aReadIsTriedAgainUntilItAnswers(): Unit = runBlocking {
        val waited = mutableListOf<Long>()
        var asked = 0
        val answer = ThreadsRetryBudget.retrying(online = { true }, clock = { now }, wait = { waited += it }) {
            if (++asked < 3) throw IOException("flaky") else "posts"
        }
        assertEquals("posts", answer)
        assertEquals(listOf(2_000L, 5_000L), waited)
    }

    @Test
    fun aFailureNotToRetryOrMetOfflineIsThrownAtOnce(): Unit = runBlocking {
        val refused = ThreadsException(NOT_CONFIGURED, "no accounts")
        var asked = 0
        val thrown = assertFailsWith<ThreadsException> {
            ThreadsRetryBudget.retrying(online = { true }, clock = { now }, wait = { error("no wait") }) { asked++; throw refused }
        }
        assertSame(refused, thrown)
        assertEquals(1, asked)

        val waited = mutableListOf<Long>()
        assertFailsWith<IOException> {
            ThreadsRetryBudget.retrying(online = { false }, clock = { now }, wait = { waited += it }) { throw IOException("offline") }
        }
        assertTrue(waited.isEmpty(), "the connection coming back reads again, not a timer")
    }
}
