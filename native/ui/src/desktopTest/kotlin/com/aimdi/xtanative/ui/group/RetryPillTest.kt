package com.aimdi.xtanative.ui.group

import com.aimdi.xtanative.core.feed.FeedProgress
import com.aimdi.xtanative.data.Problem
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RetryPillTest {
    private val someFailed = FeedProgress(answered = 3, total = 3, failed = 1)

    @Test
    fun offeredWhenSomeSourcesFailedAndThePostsCame() {
        assertTrue(GroupFeedRules.offersRetryPill(someFailed, FeedRun()))
    }

    @Test
    fun notWhileReadingOrWithNothingFailed() {
        assertFalse(GroupFeedRules.offersRetryPill(someFailed.copy(loading = true), FeedRun()))
        assertFalse(GroupFeedRules.offersRetryPill(someFailed.copy(failed = 0), FeedRun()))
    }

    @Test
    fun theFailedFirstPageKeepsItsOneRetry() {
        val allFailed = FeedProgress(answered = 1, total = 1, failed = 1)
        assertFalse(GroupFeedRules.offersRetryPill(allFailed, FeedRun()))
        assertFalse(GroupFeedRules.offersRetryPill(someFailed, FeedRun(stale = Problem.Other)))
        assertFalse(GroupFeedRules.offersRetryPill(someFailed, FeedRun(problem = Problem.Other)))
    }
}
