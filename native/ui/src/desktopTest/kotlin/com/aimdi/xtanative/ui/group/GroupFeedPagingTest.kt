package com.aimdi.xtanative.ui.group

import com.aimdi.xtanative.core.feed.CatchUpMessage
import com.aimdi.xtanative.core.feed.FeedPage
import com.aimdi.xtanative.core.feed.FeedPause
import com.aimdi.xtanative.core.feed.FeedRules
import com.aimdi.xtanative.core.feed.FeedShaping
import com.aimdi.xtanative.core.feed.KeywordAction
import com.aimdi.xtanative.core.feed.MutedKeyword
import com.aimdi.xtanative.core.feed.ReadPosition
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XUser
import java.io.IOException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupFeedPagingTest {
    private val start = Instant.parse("2026-05-01T00:00:00Z")

    private fun thread(id: Long, likes: Long = 0, text: String = "post $id") =
        XThread(id.toString(), listOf(XPost(id.toString(), XUser("a", "a", "A"), start.plusSeconds(id), text, likeCount = likes)))

    private fun page(vararg ids: Long, hasMore: Boolean = true, failures: List<Throwable> = emptyList(), unrelated: Boolean = false) =
        FeedPage(ids.map { thread(it) }, failures, hasMore, unrelated = unrelated)

    private fun ids(shown: FeedShown) = shown.threads.map { it.id.toLong() }

    @Test
    fun aFirstPageIsShownWholeWithoutCatchUp() {
        val shown = GroupFeedPaging.first(page(9, 8, 7, unrelated = true), pageCap = null, isSeen = null)
        assertEquals(listOf(9L, 8L, 7L), ids(shown))
        assertTrue(shown.canLoadMore)
        assertTrue(shown.unrelated)
        assertNull(shown.pause)
    }

    @Test
    fun catchUpStopsAtTheLastReadPostAndHoldsTheRestForLater() {
        val seen = ReadPosition.of(thread(7))::isSeen
        val shown = GroupFeedPaging.first(page(9, 8, 7, 6), pageCap = null, isSeen = seen)
        assertEquals(listOf(9L, 8L), ids(shown))
        assertEquals(FeedPause.CAUGHT_UP, shown.pause)
        assertFalse(shown.canLoadMore)
        assertTrue(shown.stops.canContinue)
        assertEquals(CatchUpMessage.CAUGHT_UP, shown.catchUpMessage)
        val older = GroupFeedPaging.resumed(shown)
        assertEquals(listOf(9L, 8L, 7L, 6L), ids(older))
        assertTrue(older.canLoadMore)
        val next = GroupFeedPaging.more(older, page(5, 4), seen)
        assertEquals(listOf(9L, 8L, 7L, 6L, 5L, 4L), ids(next), "read past it once, catch-up does not stop again")
    }

    @Test
    fun theCardSaysNothingNewOrThatThePostsInBetweenMayBeMissing() {
        val seen = ReadPosition.of(thread(9))::isSeen
        val nothing = GroupFeedPaging.first(page(9, 8), pageCap = null, isSeen = seen)
        assertEquals(CatchUpMessage.NOTHING_NEW, nothing.catchUpMessage)
        val failing = GroupFeedPaging.first(page(10, 9, failures = listOf(IOException("x"))), pageCap = null, isSeen = seen)
        assertEquals(CatchUpMessage.MAY_BE_INCOMPLETE, failing.catchUpMessage)
    }

    @Test
    fun zenModeStopsAfterItsPagesAndCanReadOnAnyway() {
        var shown = GroupFeedPaging.first(page(9, 8), pageCap = 2, isSeen = null)
        assertTrue(shown.canLoadMore)
        shown = GroupFeedPaging.more(shown, page(8, 7, 6), isSeen = null)
        assertEquals(listOf(9L, 8L, 7L, 6L), ids(shown), "a thread already shown is not shown twice")
        assertEquals(FeedPause.PAGE_CAP, shown.pause)
        assertFalse(shown.canLoadMore)
        val resumed = GroupFeedPaging.resumed(shown)
        assertTrue(resumed.canLoadMore)
        assertNull(resumed.pause)
    }

    @Test
    fun aRetryPutsWhatTheFailedSourcesBroughtInTheFeedsOrder() {
        val shown = GroupFeedPaging.first(page(9, 5, failures = listOf(IOException("x"))), pageCap = null, isSeen = null)
        assertTrue(shown.incomplete)
        val retried = GroupFeedPaging.retried(shown, FeedPage(listOf(thread(7), thread(9)), emptyList(), hasMore = false), popular = false)
        assertEquals(listOf(9L, 7L, 5L), ids(retried))
        assertFalse(retried.incomplete)
        val popular = GroupFeedPaging.retried(
            GroupFeedPaging.first(FeedPage(listOf(thread(9, likes = 1), thread(5, likes = 50)), emptyList(), true), null, null),
            FeedPage(listOf(thread(7, likes = 10)), emptyList(), false),
            popular = true,
        )
        assertEquals(listOf(5L, 7L, 9L), ids(popular))
    }

    @Test
    fun aCachedFeedShowsAsTheRulesShapeItNow() {
        val shaping = FeedShaping(rules = FeedRules(mutedKeywords = listOf(MutedKeyword("spoiler", action = KeywordAction.FOLD), MutedKeyword("hidden"))))
        val cached = listOf(thread(3, text = "a spoiler"), thread(2, text = "hidden one"), thread(1), thread(1))
        val preview = GroupFeedPaging.preview(cached, shaping, start)
        assertEquals(listOf(3L, 1L), ids(preview))
        assertEquals(mapOf("3" to "spoiler"), preview.folds)
    }

    @Test
    fun theDividerSitsAboveTheFirstReadThreadOnlyWhenSomethingIsNew() {
        val threads = listOf(thread(9), thread(8), thread(7))
        val seen = ReadPosition.of(thread(8))
        assertEquals(1, GroupFeedPaging.dividerIndex(threads, seen, tracks = true, catchUp = false))
        assertNull(GroupFeedPaging.dividerIndex(threads, ReadPosition.of(thread(9)), tracks = true, catchUp = false))
        assertNull(GroupFeedPaging.dividerIndex(threads, seen, tracks = true, catchUp = true), "catch-up mode ends on a card instead")
        assertNull(GroupFeedPaging.dividerIndex(threads, seen, tracks = false, catchUp = false))
    }

    @Test
    fun aRetryInCatchUpModeHoldsWhatWasReadBeforeUnderTheStop() {
        val seen = ReadPosition.of(thread(7))::isSeen
        val shown = GroupFeedPaging.first(page(9, 8, 7, 6, failures = listOf(IOException("x"))), pageCap = null, isSeen = seen)
        assertEquals(listOf(9L, 8L), ids(shown))
        val retried = GroupFeedPaging.retried(shown, FeedPage(listOf(thread(10), thread(5)), emptyList(), hasMore = false), popular = false, isSeen = seen)
        assertEquals(listOf(10L, 9L, 8L), ids(retried), "what is new goes on screen")
        assertEquals(listOf(7L, 6L, 5L), retried.stops.held.map { it.id.toLong() }, "what was read waits under the card")
        assertEquals(listOf(10L, 9L, 8L, 7L, 6L, 5L), ids(GroupFeedPaging.resumed(retried)))

        val plain = GroupFeedPaging.first(page(9, 8), pageCap = null, isSeen = seen)
        val stopped = GroupFeedPaging.retried(plain, FeedPage(listOf(thread(6)), emptyList(), hasMore = true), popular = false, isSeen = seen)
        assertEquals(listOf(9L, 8L), ids(stopped))
        assertEquals(FeedPause.CAUGHT_UP, stopped.pause, "a retry that reaches the read posts stops there")
        assertTrue(stopped.stops.canContinue)

        val passed = GroupFeedPaging.resumed(shown)
        val after = GroupFeedPaging.retried(passed, FeedPage(listOf(thread(5)), emptyList(), hasMore = false), popular = false, isSeen = seen)
        assertEquals(listOf(9L, 8L, 7L, 6L, 5L), ids(after), "read past the stop, it stops no more")
    }

    @Test
    fun aHoleBetweenTheNewPostsAndTheKeptOnesKeepsTheCardFromSayingTheReaderIsDone() {
        val seen = ReadPosition.of(thread(7))::isSeen
        val shown = GroupFeedPaging.first(FeedPage(listOf(thread(9), thread(7)), emptyList(), hasMore = true, gap = true), pageCap = null, isSeen = seen)
        assertEquals(CatchUpMessage.MAY_BE_INCOMPLETE, shown.catchUpMessage)
    }

    @Test
    fun aFirstLoadsPreviewKeepsItsOrderOnceTheReaderScrolled() {
        val first = GroupFeedPaging.arrived(emptyList(), page(9, 5), scrolled = false)
        assertEquals(listOf(9L, 5L), ids(first))
        val unscrolled = GroupFeedPaging.arrived(first.threads, page(9, 7, 5), scrolled = false)
        assertEquals(listOf(9L, 7L, 5L), ids(unscrolled), "until the reader scrolls, the preview is the newest order")
        val scrolled = GroupFeedPaging.arrived(first.threads, page(9, 7, 5), scrolled = true)
        assertEquals(listOf(9L, 5L, 7L), ids(scrolled), "then what arrives goes under what is on screen")
        val final = GroupFeedPaging.inPreviewOrder(scrolled.threads, listOf(thread(9), thread(8), thread(7), thread(5)))
        assertEquals(listOf(9L, 5L, 7L, 8L), final.map { it.id.toLong() })
    }
}
