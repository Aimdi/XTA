package com.aimdi.xtanative.networks.booru

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Pacing one host, as XTA's booru_request_gate.dart does. */
class BooruRequestGateTest {
    @Test
    fun theEleventhRequestInASecondWaitsForTheWindowToMoveOn() = runBlocking {
        val clock = FakeClock()
        val gate = clock.gate()
        repeat(10) { gate.admit() }
        assertTrue(clock.waits.isEmpty())

        gate.admit()

        assertEquals(listOf(1.seconds), clock.waits)
    }

    @Test
    fun requestsWaitTheirTurnInTheOrderTheyAsked() = runBlocking {
        val clock = FakeClock()
        val gate = clock.gate(perSecond = 1)
        val order = mutableListOf<Int>()

        (0..2).map { turn -> launch { gate.admit(); order += turn } }.forEach { it.join() }

        assertEquals(listOf(0, 1, 2), order)
        assertEquals(listOf(1.seconds, 1.seconds), clock.waits)
    }

    @Test
    fun a429Or5xxPausesTheHostLongerEachTimeInARowUntilAnAnswerSucceeds() = runBlocking {
        val clock = FakeClock()
        val gate = clock.gate()
        listOf(429, 503, 500).forEach { status ->
            gate.answered(status)
            gate.admit()
        }
        gate.answered(200)
        gate.answered(429)
        gate.admit()

        assertEquals(listOf(1.seconds, 2.seconds, 4.seconds, 1.seconds), clock.waits)
    }

    @Test
    fun thePauseStopsGrowingAtItsCapAndRetryAfterOverridesIt() = runBlocking {
        val clock = FakeClock()
        val gate = clock.gate(maxPause = 4.seconds)
        repeat(5) { gate.answered(429) }
        gate.admit()
        gate.answered(429, retryAfter = 30.seconds)
        gate.admit()

        assertEquals(listOf(4.seconds, 30.seconds), clock.waits)
    }

    @Test
    fun otherRefusalsDoNotPauseTheHost() = runBlocking {
        val clock = FakeClock()
        val gate = clock.gate()
        listOf(401, 403, 404, 410, 422).forEach { status ->
            gate.answered(status)
            gate.admit()
        }
        assertEquals(emptyList<Duration>(), clock.waits)
    }

    @Test
    fun retryAfterInSecondsIsReadADateIsLeftToTheDoublingPause() {
        assertEquals(7.seconds, BooruRequestGate.retryAfter("7"))
        assertEquals(Duration.ZERO, BooruRequestGate.retryAfter(" 0 "))
        assertNull(BooruRequestGate.retryAfter("Wed, 21 Oct 2026 07:28:00 GMT"))
        assertNull(BooruRequestGate.retryAfter("-1"))
        assertNull(BooruRequestGate.retryAfter(null))
    }
}
