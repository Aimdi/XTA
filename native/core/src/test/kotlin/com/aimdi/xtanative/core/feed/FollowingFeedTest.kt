package com.aimdi.xtanative.core.feed

import com.aimdi.xtanative.core.x.XRequestException
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XTimelinePage
import com.aimdi.xtanative.core.x.model.XUser
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class FollowingFeedTest {
    /** Names long enough that no two share a search, so each account is one chunk. */
    private fun member(name: String, order: Long) = FeedMember("id-$name", name.padEnd(250, '_'), order)

    private val a = member("a", 1)
    private val b = member("b", 2)

    private fun post(id: Long, by: FeedMember, repostOf: XPost? = null, replyTo: String? = null) =
        XPost(id.toString(), XUser(by.id, by.screenName, by.screenName), null, "post $id", reposted = repostOf, replyToScreenName = replyTo)

    private fun page(vararg posts: XPost, next: String?) = XTimelinePage(posts.map { XThread(it.id, listOf(it)) }, next, null)

    /** Stands in for X: each account's search answers page by page, by cursor. */
    private class FakeReads(
        var pages: Map<Pair<String, String?>, XTimelinePage>,
        val failing: Map<String, Throwable> = emptyMap(),
        val profiles: Map<String, XTimelinePage> = emptyMap(),
    ) : FeedReads {
        val searches = ArrayList<Pair<String, String?>>()
        val profileReads = ArrayList<String>()

        override suspend fun search(query: String, cursor: String?): XTimelinePage {
            val who = Regex("from:(\\w)").find(query)!!.groupValues[1]
            synchronized(searches) { searches += who to cursor }
            failing[who]?.let { throw it }
            return pages[who to cursor] ?: XTimelinePage(emptyList(), null, null)
        }

        override suspend fun userPosts(userId: String): XTimelinePage {
            synchronized(profileReads) { profileReads += userId }
            return profiles[userId] ?: XTimelinePage(emptyList(), null, null)
        }
    }

    private fun ids(page: FeedPage) = page.threads.map { it.id.toLong() }

    @Test
    fun pagesFromDifferentChunksComeOutInTimeOrder() = runBlocking {
        val reads = FakeReads(
            mapOf(
                ("a" to null) to page(post(100, a), post(90, a), post(80, a), next = "a2"),
                ("a" to "a2") to page(post(70, a), post(60, a), next = "a3"),
                ("a" to "a3") to page(post(30, a), next = null),
                ("b" to null) to page(post(95, b), post(50, b), next = "b2"),
                ("b" to "b2") to page(post(40, b), next = null),
            ),
        )
        val feed = FollowingFeed(listOf(a, b), reads, concurrency = 1)
        assertEquals(2, feed.chunkCount)

        val first = feed.first()
        assertEquals(listOf(100L, 95, 90, 80), ids(first), "b's 50 waits until a has been read past it")
        assertTrue(first.hasMore)

        val more = feed.more()
        assertEquals(listOf(70L, 60, 50, 40, 30), ids(more))
        assertFalse(more.hasMore)
        assertEquals(listOf("a" to null, "b" to null, "a" to "a2", "a" to "a3", "b" to "b2"), reads.searches)
    }

    @Test
    fun aFailedSearchFallsBackToTheAccountsOwnPosts() = runBlocking {
        val reads = FakeReads(
            mapOf(("b" to null) to page(post(95, b), post(50, b), next = "b2")),
            failing = mapOf("a" to XRequestException(403, "refused")),
            profiles = mapOf(a.id to page(post(85, a), post(75, a), next = "ignored")),
        )
        val first = FollowingFeed(listOf(a, b), reads).first()
        assertEquals(listOf(95L, 85, 75, 50), ids(first))
        assertTrue(first.failures.isEmpty(), "the fallback brought posts")
        assertEquals(listOf(a.id), reads.profileReads)
    }

    @Test
    fun noConnectionMeansNoFallback() = runBlocking {
        val reads = FakeReads(
            mapOf(("b" to null) to page(post(95, b), next = null)),
            failing = mapOf("a" to IOException("offline")),
        )
        val first = FollowingFeed(listOf(a, b), reads).first()
        assertEquals(listOf(95L), ids(first))
        assertIs<IOException>(first.failures.single())
        assertTrue(reads.profileReads.isEmpty())
    }

    @Test
    fun repostsAndRepliesStayOutWhenTheFeedLeavesThemOut() = runBlocking {
        val original = post(10, b)
        val reads = FakeReads(
            mapOf(("a" to null) to page(post(100, a), post(90, a, repostOf = original), post(80, a, replyTo = "c"), next = null)),
        )
        val first = FollowingFeed(listOf(a), reads, includeReplies = false, includeReposts = false).first()
        assertEquals(listOf(100L), ids(first))
    }

    private val original = mapOf(
        ("a" to null) to page(post(100, a), post(90, a), post(80, a), next = "a2"),
        ("a" to "a2") to page(post(70, a), post(60, a), next = "a3"),
        ("a" to "a3") to page(post(30, a), next = null),
        ("b" to null) to page(post(95, b), post(50, b), next = "b2"),
        ("b" to "b2") to page(post(40, b), next = null),
    )

    @Test
    fun newerPostsAreReadDownToTheTopShownAndTheReadingBelowIsKept() = runBlocking {
        val reads = FakeReads(original)
        val feed = FollowingFeed(listOf(a, b), reads, concurrency = 1)
        assertEquals(listOf(100L, 95, 90, 80), ids(feed.first()))

        // Later: both accounts have written since.
        reads.pages = original + mapOf(
            ("a" to null) to page(post(120, a), post(110, a), next = "a-new"),
            ("a" to "a-new") to page(post(100, a), post(90, a), next = "a2"),
            ("b" to null) to page(post(130, b), post(95, b), post(50, b), next = "b2"),
        )
        val newer = feed.newer(than = 100)
        assertEquals(listOf(130L, 120, 110), newer.threads.map { it.id.toLong() })
        assertTrue(newer.bridged)

        assertEquals(listOf(70L, 60, 50, 40, 30), ids(feed.more()), "loading more goes on below what was shown")
    }

    @Test
    fun moreWrittenThanReadIsNotBridged() = runBlocking {
        val reads = FakeReads(mapOf(("a" to null) to page(post(100, a), next = null)))
        val feed = FollowingFeed(listOf(a), reads)
        feed.first()
        reads.pages = mapOf(
            ("a" to null) to page(post(400, a), post(390, a), next = "p2"),
            ("a" to "p2") to page(post(380, a), next = "p3"),
            ("a" to "p3") to page(post(370, a), next = "p4"),
        )
        val newer = feed.newer(than = 100, pagesPerChunk = 3)
        assertFalse(newer.bridged, "three pages did not reach the top shown")
        assertEquals(listOf(400L, 390, 380, 370), newer.threads.map { it.id.toLong() })
    }

    @Test
    fun aSavedFeedReadsOnWhereItWasLeft() = runBlocking {
        val first = FollowingFeed(listOf(a, b), FakeReads(original), concurrency = 1)
        val shown = first.first().threads
        val saved = SavedFeed.decode(SavedFeed.encode(SavedFeed(shown, first.state())))!!
        assertEquals(shown.map { it.id }, saved.threads.map { it.id })

        val reads = FakeReads(original)
        val again = FollowingFeed(listOf(a, b), reads, concurrency = 1)
        assertTrue(again.restore(saved.state!!, saved.threads.map { it.id }))
        assertEquals(listOf(70L, 60, 50, 40, 30), ids(again.more()))
        assertTrue(reads.searches.none { it.second == null }, "nothing is read from the top again")

        val others = FollowingFeed(listOf(a), FakeReads(original))
        assertFalse(others.restore(saved.state!!, emptyList()), "other follows cannot read on from it")
    }

    @Test
    fun aPlaceReadsBackAsWritten() {
        assertEquals(FeedPlace("123", 48), FeedPlace.decode(FeedPlace.encode(FeedPlace("123", 48))))
        assertEquals(null, FeedPlace.decode("{}"))
    }

    @Test
    fun anEmptyFollowingListIsAnEmptyFeed() = runBlocking {
        val first = FollowingFeed(emptyList(), FakeReads(emptyMap())).first()
        assertTrue(first.threads.isEmpty())
        assertFalse(first.hasMore)
    }
}
