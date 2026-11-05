package com.aimdi.xtanative.ui.common

import com.aimdi.xtanative.data.Problem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReadRetriesTest {
    @Test
    fun aFailureThatNeedsTheReaderIsNeverRetriedOnItsOwn() {
        assertEquals(ReadRetry.MANUAL, ReadRetries.of(Problem.NoAccount))
        assertEquals(ReadRetry.MANUAL, ReadRetries.of(Problem.NoWorkingAccount))
        assertEquals(ReadRetry.MANUAL, ReadRetries.of(Problem.EndpointRefused))
        assertEquals(ReadRetry.MANUAL, ReadRetries.of(Problem.Refused(401)))
        assertNull(ReadRetries.recoverable(Problem.NoAccount))
    }

    @Test
    fun aRateLimitWaitsForItsResetAndEverythingElseBacksOff() {
        assertEquals(ReadRetry.AT_RESET, ReadRetries.of(Problem.RateLimitedUntil(5)))
        assertEquals(ReadRetry.AT_RESET, ReadRetries.of(Problem.Refused(429)))
        assertEquals(ReadRetry.BACKOFF, ReadRetries.of(Problem.Offline))
        assertEquals(ReadRetry.BACKOFF, ReadRetries.of(Problem.Refused(503)))
        assertEquals(Problem.Offline, ReadRetries.recoverable(Problem.Offline))
    }

    @Test
    fun theKindsNameFailuresAsXtasNoticesDo() {
        assertEquals(ReadFailureKind.CONNECTION, ReadFailureKind.of(Problem.Offline))
        assertEquals(ReadFailureKind.SESSION, ReadFailureKind.of(Problem.NoAccount))
        assertEquals(ReadFailureKind.TRANSACTION_UNAVAILABLE, ReadFailureKind.of(Problem.Signing))
        assertEquals(ReadFailureKind.UNAVAILABLE, ReadFailureKind.of(Problem.Refused(404)))
        assertEquals(ReadFailureKind.SERVICE_UNAVAILABLE, ReadFailureKind.of(Problem.Refused(502)))
        assertEquals(ReadFailureKind.UNKNOWN, ReadFailureKind.of(Problem.Refused(418)))
    }

    @Test
    fun theBackoffGrowsOnceThroughItsStepsAndStartsOverWhenRenewed() {
        val budget = ReadRetryBudget()
        val waits = generateSequence { budget.waitFor(ReadRetry.BACKOFF, null, 0)?.also { budget.spend(ReadRetry.BACKOFF) } }.toList()
        assertEquals(listOf(2_000L, 5_000L, 15_000L, 30_000L, 60_000L), waits)
        budget.renew()
        assertEquals(2_000L, budget.waitFor(ReadRetry.BACKOFF, null, 0))
    }

    @Test
    fun aRateLimitIsRetriedOnceASecondAfterItsKnownReset() {
        val budget = ReadRetryBudget()
        assertNull(budget.waitFor(ReadRetry.AT_RESET, null, 0), "an unknown reset waits for the reader")
        assertEquals(11_000L, budget.waitFor(ReadRetry.AT_RESET, 10_000, 0))
        assertEquals(1_000L, budget.waitFor(ReadRetry.AT_RESET, 10_000, 50_000), "a reset that passed while away still earns its retry")
        budget.spend(ReadRetry.AT_RESET)
        assertNull(budget.waitFor(ReadRetry.AT_RESET, 10_000, 0))
        assertNull(budget.waitFor(ReadRetry.MANUAL, null, 0))
    }
}
