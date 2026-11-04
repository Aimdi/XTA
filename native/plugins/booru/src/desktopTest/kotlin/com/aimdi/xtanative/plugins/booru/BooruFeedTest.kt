package com.aimdi.xtanative.plugins.booru

import com.aimdi.xtanative.networks.booru.BooruPost
import com.aimdi.xtanative.networks.booru.BooruPostPage
import com.aimdi.xtanative.networks.booru.post
import com.aimdi.xtanative.store.StoreDir
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class BooruFeedTest {
    private fun posts(vararg ids: Int): List<BooruPost> = ids.map { post(id = "$it") }

    private fun page(vararg ids: Int, next: String? = "more"): BooruPostPage = BooruPostPage(posts(*ids), next)

    private fun ids(state: BooruFeedState): List<String> = state.posts.map { it.id }

    /** The feed once its first page answered. */
    private suspend fun landed(feed: BooruFeed): BooruFeedState = withTimeout(5_000) { feed.state.first { !it.loading } }

    /** The feed once its next page answered. */
    private suspend fun paged(feed: BooruFeed): BooruFeedState = withTimeout(5_000) { feed.state.first { it.paging != BooruPaging.LOADING } }

    @Test
    fun theFirstPageWithPostsIsShown(): Unit = runBlocking {
        val asked = mutableListOf<Int>()
        val feed = BooruFeed(this, { "site" }) { number ->
            asked += number
            if (number < 3) page() else page(1, 2)
        }
        feed.refresh()
        val state = landed(feed)
        assertEquals(listOf(1, 2, 3), asked)
        assertEquals(listOf("1", "2"), ids(state))
        assertEquals(FeedView.POSTS, state.view)
        feed.close()
    }

    @Test
    fun aFeedStopsLookingAfterFourEmptyPagesWhileTheHostHasMore(): Unit = runBlocking {
        val asked = mutableListOf<Int>()
        val feed = BooruFeed(this, { "site" }) { number ->
            asked += number
            page()
        }
        feed.refresh()
        assertEquals(FeedView.HIDDEN_SO_FAR, landed(feed).view)
        assertEquals(listOf(1, 2, 3, 4), asked)
        feed.close()
    }

    @Test
    fun aFeedWithNoPostsSaysSo(): Unit = runBlocking {
        val feed = BooruFeed(this, { "site" }) { page(next = null) }
        feed.refresh()
        assertEquals(FeedView.EMPTY, landed(feed).view)
        feed.close()
    }

    @Test
    fun aFirstPageThatFailsIsTheFeedsError(): Unit = runBlocking {
        val feed = BooruFeed(this, { "site" }) { throw IllegalStateException("down") }
        feed.refresh()
        val state = landed(feed)
        assertEquals(FeedView.ERROR, state.view)
        assertEquals("down", state.error?.message)
        feed.close()
    }

    @Test
    fun aRefreshThatFailsKeepsThePostsUnderANotice(): Unit = runBlocking {
        var down = false
        val feed = BooruFeed(this, { "site" }) { if (down) throw IllegalStateException("down") else page(1, 2) }
        feed.refresh()
        landed(feed)
        down = true
        feed.refresh()
        val state = landed(feed)
        assertEquals(listOf("1", "2"), ids(state))
        assertEquals("down", state.refreshFailure?.message)
        assertNull(state.error)
        feed.dismissRefreshFailure()
        assertNull(feed.state.value.refreshFailure)
        feed.close()
    }

    @Test
    fun aChangedQueryThatFailsShowsTheErrorNotTheOldPosts(): Unit = runBlocking {
        var down = false
        val feed = BooruFeed(this, { "site" }) { if (down) throw IllegalStateException("down") else page(1) }
        feed.refresh()
        landed(feed)
        down = true
        feed.refresh(changed = true)
        assertEquals(FeedView.ERROR, landed(feed).view)
        feed.close()
    }

    @Test
    fun theCopyKeptOnTheDeviceStandsInWhenTheFirstLoadFails(): Unit = runBlocking {
        val cache = BooruFeedCache(StoreDir(Files.createTempDirectory("booru-feed").toFile()))
        coroutineScope {
            BooruFeed(this, { "site" }, cache, { "latest" }) { page(1, 2, next = null) }.refresh()
        }
        val offline = BooruFeed(this, { "site" }, cache, { "latest" }) { throw IllegalStateException("offline") }
        offline.refresh()
        val state = landed(offline)
        assertEquals(listOf("1", "2"), ids(state))
        assertEquals(BooruPaging.END, state.paging)
        assertEquals("offline", state.refreshFailure?.message)

        val otherSite = BooruFeed(this, { "other" }, cache, { "latest" }) { throw IllegalStateException("offline") }
        otherSite.refresh()
        assertEquals(FeedView.ERROR, landed(otherSite).view)
        offline.close()
        otherSite.close()
    }

    @Test
    fun aNextPageAddsOnlyPostsNotShownYet(): Unit = runBlocking {
        val feed = BooruFeed(this, { "site" }) { number -> if (number == 1) page(1, 2) else page(2, 3, next = null) }
        feed.refresh()
        landed(feed)
        feed.loadMore()
        val state = paged(feed)
        assertEquals(listOf("1", "2", "3"), ids(state))
        assertEquals(BooruPaging.END, state.paging)
        feed.close()
    }

    @Test
    fun aHostThatSendsTheSamePostsForEveryPageEndsTheFeed(): Unit = runBlocking {
        val feed = BooruFeed(this, { "site" }) { page(1, 2) }
        feed.refresh()
        landed(feed)
        feed.loadMore()
        val state = paged(feed)
        assertEquals(listOf("1", "2"), ids(state))
        assertEquals(BooruPaging.END, state.paging)
        feed.close()
    }

    @Test
    fun aNextPageThatFailsWaitsToBeAskedAgain(): Unit = runBlocking {
        var failures = 1
        val feed = BooruFeed(this, { "site" }) { number ->
            when {
                number == 1 -> page(1)
                failures-- > 0 -> throw IllegalStateException("down")
                else -> page(2, next = null)
            }
        }
        feed.refresh()
        landed(feed)
        feed.loadMore()
        assertEquals(BooruPaging.FAILED, paged(feed).paging)
        feed.loadMore()
        assertEquals(BooruPaging.FAILED, feed.state.value.paging)

        feed.retryMore()
        val state = paged(feed)
        assertEquals(listOf("1", "2"), ids(state))
        assertEquals(BooruPaging.END, state.paging)
        feed.close()
    }

    @Test
    fun thePostsTheBlacklistHidCanBeShownInTheirPlaces(): Unit = runBlocking {
        val feed = BooruFeed(this, { "site" }) { BooruPostPage(posts(1, 3), next = null, allowed = posts(1, 2, 3)) }
        feed.refresh()
        val state = landed(feed)
        assertEquals(listOf("1", "3"), ids(state))
        assertEquals(1, state.hidden)

        feed.showHidden()
        assertEquals(listOf("1", "2", "3"), ids(feed.state.value))
        assertTrue(feed.state.value.hiddenShown)
        feed.hideHidden()
        assertEquals(listOf("1", "3"), ids(feed.state.value))
        feed.close()
    }

    @Test
    fun aFeedWhoseSiteChangedStartsOverInsteadOfAddingToIt(): Unit = runBlocking {
        var site = "a"
        val asked = mutableListOf<String>()
        val feed = BooruFeed(this, { site }) { number ->
            asked += "$site:$number"
            page(number)
        }
        feed.refresh()
        landed(feed)
        site = "b"
        assertTrue(feed.isStale)
        feed.loadMore()
        landed(feed)
        assertEquals(listOf("a:1", "b:1"), asked)
        assertFalse(feed.isStale)
        feed.close()
    }
}
