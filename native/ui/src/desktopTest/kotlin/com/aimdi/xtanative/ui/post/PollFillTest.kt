package com.aimdi.xtanative.ui.post

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** poll_fill_test.dart and poll_test.dart's bars. */
class PollFillTest {
    @Test
    fun everyBarGoesFromNothingToItsShare() {
        (0..3).forEach { index ->
            assertEquals(0f, PollFill.growth(0f, index))
            assertEquals(1f, PollFill.growth(1f, index))
        }
    }

    @Test
    fun eachBarStartsAStaggerAfterTheOneAboveAndGrowsForTheSameTime() {
        val total = PollFill.TOTAL_MS.toFloat()
        val stagger = PollFill.STAGGER_MS / total
        val growth = PollFill.GROWTH_MS / total
        (1..3).forEach { index ->
            val start = index * stagger
            assertEquals(0f, PollFill.growth(start, index), "bar ${index + 1} waits")
            assertTrue(PollFill.growth(start, index - 1) > 0f, "while the one above grows")
            val half = PollFill.growth(start + growth / 2, index)
            assertTrue(half > 0f && half < 1f)
            assertEquals(1f, PollFill.growth(start + growth, index), 1e-5f)
        }
        // The last of X's four options starts three staggers in and ends with the fill.
        assertEquals(840, 3 * PollFill.STAGGER_MS + PollFill.GROWTH_MS)
    }

    @Test
    fun theFillWaitsForTheScreenToSlideInAndNotOnceItHas() {
        assertEquals(280L, PollFill.startDelay(openedAt = 1_000, now = 1_000, slideMs = 280))
        assertEquals(180L, PollFill.startDelay(openedAt = 1_000, now = 1_100, slideMs = 280))
        assertEquals(0L, PollFill.startDelay(openedAt = 1_000, now = 5_000, slideMs = 280))
    }

    @Test
    fun withReducedMotionAnOpenedPostsPollIsFullAtOnceAndStaysFull() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val fill = FocalPollFill(scope, openedAt = 0, clock = { 0 })
        assertEquals(0f, fill.progress)
        fill.start(reduced = true)
        assertEquals(1f, fill.progress)
        // Asked again, by the post X sends after the copy at hand, or a poll scrolled back into view: it stays full.
        fill.start(reduced = false)
        assertEquals(1f, fill.progress)
        scope.cancel()
    }

    @Test
    fun aBarIsFilledToItsShareAsFarAsItHasGrown() {
        assertEquals(0.625f, PollBar.fill(0.625, 1f))
        assertEquals(0.3125f, PollBar.fill(0.625, 0.5f))
        assertEquals(0f, PollBar.fill(0.0, 1f), "an option nobody chose has no fill")
    }

    @Test
    fun aPictureSitsAsFarInFromItsBarsStartAsFromItsTopAndTheShareAtTheEnd() {
        assertEquals(PollBar.INSET, PollBar.start(hasImage = true))
        assertEquals(6f, PollBar.INSET)
        assertEquals(12f, PollBar.start(hasImage = false))
        assertEquals(12f, PollBar.END)
        assertEquals(48f, PollBar.IMAGE)
        assertEquals(36f, PollBar.MIN_HEIGHT)
    }
}
