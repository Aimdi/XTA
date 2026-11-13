package com.aimdi.xtanative.ui.post

import com.aimdi.xtanative.data.Problem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What the quotes and reposts lists retry on their own. */
class QuotesRetryTest {
    @Test
    fun aRateLimitOnTheFirstPageIsRetriedAtItsReset() {
        val failed = Paged<String>(loading = false, problem = Problem.RateLimitedUntil(5_000))
        assertEquals(Problem.RateLimitedUntil(5_000), failed.retryable)
    }

    @Test
    fun aFailedNextPageIsRetriedUnderWhatIsShown() {
        val more = Paged(items = listOf("a"), loading = false, cursor = "c", moreProblem = Problem.Offline)
        assertEquals(Problem.Offline, more.retryable)
    }

    @Test
    fun nothingIsRetriedWhileReadingOrWhereTheReaderMustAct() {
        assertNull(Paged<String>(problem = Problem.RateLimitedUntil(5_000)).retryable, "still reading")
        assertNull(Paged<String>(loading = false, moreProblem = Problem.Offline, loadingMore = true).retryable)
        assertNull(Paged<String>(loading = false, problem = Problem.NoAccount).retryable, "an account must be added first")
        assertNull(Paged<String>(loading = false, problem = Problem.EndpointRefused).retryable)
        assertNull(Paged<String>(loading = false).retryable)
    }
}
