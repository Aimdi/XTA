package com.aimdi.xtanative.core.feed

import com.aimdi.xtanative.core.feed.Posts.ids
import com.aimdi.xtanative.core.feed.Posts.post
import com.aimdi.xtanative.core.feed.Posts.thread
import com.aimdi.xtanative.core.x.model.XThread
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * XTA's feed_read_position_test, catch_up_split_test, feed_first_page_action_test,
 * held_refresh_test, the gap fill of feed_chunk_logic_test and the unread rules
 * of group_unread_test and home_feed_unread_test, for the native port.
 */
class CatchUpTest {
    private val t0 = Instant.parse("2026-07-01T12:00:00Z")

    private fun at(id: String, time: Instant?) = thread(post(1, at = time), id = id)

    private fun day(day: Int) = Instant.parse("2026-01-%02dT00:00:00Z".format(day))

    @Test
    fun theFollowingFeedKeepsItsPositionUnderItsOwnKey() {
        assertEquals("following", FeedKeys.readPositionKey("-1"))
        assertEquals("abc-uuid", FeedKeys.readPositionKey("abc-uuid"))
        assertEquals("feed.catch_up_mode.7", FeedKeys.catchUpPreference("7"))
        assertTrue(FeedKeys.catchUpPreference("-1") != FeedKeys.catchUpPreference("7"))
    }

    @Test
    fun aThreadIsSeenByIdOrByTimeEqualTimesIncluded() {
        val position = ReadPosition("a", t0)
        assertTrue(position.isSeen(at("a", null)))
        assertTrue(position.isSeen(at("b", t0.minusSeconds(60))))
        assertTrue(position.isSeen(at("b", t0)))
        assertFalse(position.isSeen(at("b", t0.plusSeconds(60))))
        assertFalse(position.isSeen(at("b", null)))
    }

    @Test
    fun theBoundaryIsTheFirstSeenThreadBelowNewOnes() {
        val position = ReadPosition("seen", t0)
        val newer = at("n", t0.plusSeconds(3600))
        val older = at("o", t0.minusSeconds(3600))
        assertEquals(2, position.boundaryIndex(listOf(newer, newer, older)))
        assertNull(position.boundaryIndex(listOf(older, older)), "nothing new: no divider")
        assertNull(position.boundaryIndex(listOf(newer, newer)), "the boundary is not loaded yet")
        assertNull(position.boundaryIndex(emptyList()))
        assertEquals(1, position.boundaryIndex(listOf(at("n", t0.plusSeconds(1)), at("eq", t0))))
    }

    @Test
    fun theNewestRecordableThreadSaysWhenItWasWritten() {
        val dated = at("a", t0)
        assertSame(dated, ReadPosition.newestRecordable(listOf(at("b", null), dated)))
        assertNull(ReadPosition.newestRecordable(listOf(at("b", null))))
        assertNull(ReadPosition.newestRecordable(emptyList()))
    }

    private fun action(threads: List<XThread>, lastSeen: ReadPosition?, evaluated: Boolean = false, offset: Double? = null, atTop: Boolean = true) =
        FirstPageAction.of(threads, lastSeen, evaluated, offset, atTop)

    @Test
    fun unreadPostsAboveTheLastReadOneBringTheReaderBackToIt() {
        val restored = action(listOf(at("c", day(5)), at("b", day(3)), at("a", day(1))), ReadPosition("b", day(3)))
        assertEquals(FirstPageAction.RestoreToBoundary(1), restored)
        val keptPlace = action(listOf(at("c", day(5)), at("b", day(3))), ReadPosition("b", day(3)), offset = 900.0, atTop = false)
        assertFalse(keptPlace is FirstPageAction.RestoreToBoundary, "a place the reader was scrolled to wins")
    }

    @Test
    fun aFirstPageRecordsWhenNothingIsNewOrNothingWasRead() {
        assertEquals("b", (action(listOf(at("b", day(3)), at("a", day(1))), ReadPosition("b", day(3))) as FirstPageAction.RecordPosition).thread.id)
        assertIs<FirstPageAction.RecordPosition>(action(listOf(at("c", day(5))), null))
        assertEquals(FirstPageAction.DoNothing, action(emptyList(), null))
        assertEquals(FirstPageAction.DoNothing, action(listOf(at("c", null)), null), "undated posts have nothing to record")
    }

    @Test
    fun aRefreshMidScrollRecordsNothingAndTheBoundaryIsDecidedOnce() {
        assertEquals(FirstPageAction.DoNothing, action(listOf(at("c", day(5))), ReadPosition("a", day(1)), evaluated = true, atTop = false))
        assertIs<FirstPageAction.RecordPosition>(action(listOf(at("c", day(5)), at("b", day(3))), ReadPosition("b", day(3)), evaluated = true))
        assertEquals(FirstPageAction.DoNothing, action(listOf(at("c", day(5)), at("b", day(3))), ReadPosition("b", day(3)), offset = 900.0, atTop = false))
    }

    private val seen = { thread: XThread -> thread.id.startsWith("seen") }

    @Test
    fun catchUpCutsAtTheFirstReadThreadAndHandsTheRestBack() {
        val all = listOf(at("a", null), at("b", null))
        assertEquals(CatchUpSplit(all, emptyList(), false), CatchUpSplit.of(all, seen))
        val cut = CatchUpSplit.of(listOf(at("a", null), at("b", null), at("seen1", null), at("c", null)), seen)
        assertEquals(listOf("a", "b"), ids(cut.keep))
        assertEquals(listOf("seen1", "c"), ids(cut.held))
        assertTrue(cut.reachedBoundary)
        val nothing = CatchUpSplit.of(listOf(at("seen1", null), at("a", null)), seen)
        assertTrue(nothing.keep.isEmpty() && nothing.reachedBoundary)
        assertFalse(CatchUpSplit.of(emptyList(), seen).reachedBoundary)
    }

    @Test
    fun anUnfinishedGapOutranksEveryClaimOfBeingCaughtUp() {
        assertEquals(CatchUpMessage.MAY_BE_INCOMPLETE, CatchUpMessage.of(mayBeIncomplete = true, nothingNew = false))
        assertEquals(CatchUpMessage.MAY_BE_INCOMPLETE, CatchUpMessage.of(mayBeIncomplete = true, nothingNew = true))
        assertEquals(CatchUpMessage.NOTHING_NEW, CatchUpMessage.of(mayBeIncomplete = false, nothingNew = true))
        assertEquals(CatchUpMessage.CAUGHT_UP, CatchUpMessage.of(mayBeIncomplete = false, nothingNew = false))
        assertFalse(NewerPage(emptyList(), bridged = false, failures = emptyList()).complete)
        assertFalse(NewerPage(emptyList(), bridged = true, failures = listOf(Exception("one chunk"))).complete)
    }

    @Test
    fun theGapIsFilledWhileTheFreshWindowIsAboveTheStoredOneAndWithinTheBudget() {
        assertTrue(GapFill.shouldContinue(100, 150, pageNonEmpty = true, hasCursor = true, fillsSoFar = 0))
        assertFalse(GapFill.shouldContinue(100, 80, pageNonEmpty = true, hasCursor = true, fillsSoFar = 0), "the fresh posts overlap the stored ones")
        assertFalse(GapFill.shouldContinue(100, 500, pageNonEmpty = true, hasCursor = true, fillsSoFar = 4, maxFills = 4))
        assertFalse(GapFill.shouldContinue(null, 150, pageNonEmpty = true, hasCursor = true, fillsSoFar = 0))
        assertFalse(GapFill.shouldContinue(100, 150, pageNonEmpty = true, hasCursor = false, fillsSoFar = 0))
    }

    @Test
    fun aFeedIsUnreadOnlyWhenTrackedAndReadFromXSinceItsLastReadMark() {
        assertFalse(Unread.has(tracks = false, newestReadAt = 2, lastReadAt = null))
        assertFalse(Unread.has(tracks = true, newestReadAt = null, lastReadAt = null))
        assertTrue(Unread.has(tracks = true, newestReadAt = 2, lastReadAt = null))
        assertTrue(Unread.has(tracks = true, newestReadAt = 2, lastReadAt = 1))
        assertFalse(Unread.has(tracks = true, newestReadAt = 1, lastReadAt = 1))
        assertFalse(Unread.has(tracks = true, newestReadAt = 1, lastReadAt = 2))
    }

    @Test
    fun popularFeedsAndTheMediaGridKeepNoReadPosition() {
        assertFalse(Unread.tracks(popular = true, readingPosition = true, catchUp = false))
        assertTrue(Unread.tracks(popular = false, readingPosition = false, catchUp = true))
        assertFalse(Unread.tracks(popular = false, readingPosition = true, catchUp = false, mediaOnly = true))
    }

    @Test
    fun theFollowingFeedsLastReadPrefersItsKeyOverXtasOlderOne() {
        assertEquals(2L, Unread.lastReadForFollowing(mapOf("following" to 2L, "-1" to 1L)))
        assertEquals(1L, Unread.lastReadForFollowing(mapOf("-1" to 1L)))
        assertNull(Unread.lastReadForFollowing(emptyMap()))
    }

    @Test
    fun aHeldRefreshWaitsForTheTopAndRunsOnceForManyChanges() {
        val atTop = HeldRefresh()
        assertTrue(atTop.request(atTop = true))
        assertFalse(atTop.isPending)

        val held = HeldRefresh()
        repeat(3) { assertFalse(held.request(atTop = false)) }
        assertTrue(held.isPending)
        assertTrue(held.returnedToTop())
        assertFalse(held.returnedToTop())
        assertFalse(HeldRefresh().returnedToTop())

        val cleared = HeldRefresh().apply { request(atTop = false) }
        assertTrue(cleared.request(atTop = true))
        assertFalse(cleared.isPending)
    }

    @Test
    fun anUnknownScrollPositionKeepsTheLastAnswer() {
        assertTrue(HeldRefresh.atTop(0.0, lastKnownAtTop = false))
        assertFalse(HeldRefresh.atTop(400.0, lastKnownAtTop = true))
        assertFalse(HeldRefresh.atTop(null, lastKnownAtTop = false))
        assertTrue(HeldRefresh.atTop(null, lastKnownAtTop = true))
    }

    @Test
    fun catchUpStopsAtTheLastReadPostAndShowsTheRestWhenAsked() {
        val page = listOf(at("new1", null), at("seen1", null), at("old", null))
        val stopped = FeedStops().page(page, first = true, isSeen = seen, hasMore = true)
        assertEquals(listOf("new1"), ids(stopped.shown))
        assertEquals(FeedPause.CAUGHT_UP, stopped.stops.pause)
        assertTrue(stopped.stops.canContinue)

        val resumed = stopped.stops.resume()
        assertEquals(listOf("seen1", "old"), ids(resumed.shown), "what was cut off is shown, not asked for again")
        assertEquals(null, resumed.stops.pause)
        val next = resumed.stops.page(listOf(at("seen2", null)), first = false, isSeen = seen, hasMore = true)
        assertEquals(listOf("seen2"), ids(next.shown), "past the boundary once, catch-up does not stop again")

        val refreshed = next.stops.page(page, first = true, isSeen = seen, hasMore = true)
        assertEquals(FeedPause.CAUGHT_UP, refreshed.stops.pause, "a new first page stops again")
    }

    @Test
    fun zenModesPageCapPausesAfterSoManyPagesAndNotOnTheLast() {
        var stops = FeedStops(pageCap = 2)
        stops = stops.page(listOf(at("1", null)), first = true, isSeen = null, hasMore = true).stops
        assertEquals(null, stops.pause)
        val capped = stops.page(listOf(at("2", null)), first = false, isSeen = null, hasMore = true)
        assertEquals(listOf("2"), ids(capped.shown))
        assertEquals(FeedPause.PAGE_CAP, capped.stops.pause)
        val resumed = capped.stops.resume().stops
        assertEquals(null, resumed.page(listOf(at("3", null)), first = false, isSeen = null, hasMore = true).stops.pause)

        val last = FeedStops(pageCap = 1).page(listOf(at("1", null)), first = true, isSeen = null, hasMore = false)
        assertEquals(null, last.stops.pause, "nothing is left to hold back")
        val endsAtBoundary = FeedStops().page(listOf(at("seen", null)), first = true, isSeen = seen, hasMore = false)
        assertFalse(endsAtBoundary.stops.copy(held = emptyList()).canContinue)
    }
}
