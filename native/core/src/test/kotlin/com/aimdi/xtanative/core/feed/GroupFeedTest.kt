package com.aimdi.xtanative.core.feed

import com.aimdi.xtanative.core.feed.Posts.ids
import com.aimdi.xtanative.core.feed.Posts.page
import com.aimdi.xtanative.core.feed.Posts.post
import com.aimdi.xtanative.core.feed.Posts.thread
import com.aimdi.xtanative.core.x.XAccount
import com.aimdi.xtanative.core.x.XNoAccountException
import com.aimdi.xtanative.core.x.XRateLimitedException
import com.aimdi.xtanative.core.x.XRequestException
import com.aimdi.xtanative.core.x.model.XTimelinePage
import com.aimdi.xtanative.core.x.signing.SigningUnavailableException
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** XTA's group feed rules (feed_search_fallback_test, unrelated_posts_test, feed_chunk_concurrency_test and the feed's page pass), for the native port. */
class GroupFeedTest {
    /** Stands in for X: a search answers by the first key its query contains, page by page. */
    private class Reads(
        var pages: Map<Pair<String, String?>, XTimelinePage> = emptyMap(),
        val failing: MutableMap<String, Throwable> = mutableMapOf(),
        val profiles: Map<String, XTimelinePage> = emptyMap(),
    ) : FeedReads {
        val searches = ArrayList<Pair<String, String?>>()
        val profileReads = ArrayList<String>()
        val withReplies = ArrayList<Boolean>()

        override suspend fun search(query: String, cursor: String?, includeReplies: Boolean): XTimelinePage {
            synchronized(searches) { searches += query to cursor; withReplies += includeReplies }
            failing.entries.firstOrNull { query.contains(it.key) }?.let { throw it.value }
            return pages.entries.firstOrNull { (key, _) -> query.contains(key.first) && key.second == cursor }?.value ?: page()
        }

        override suspend fun userPosts(userId: String): XTimelinePage {
            synchronized(profileReads) { profileReads += userId }
            return profiles[userId] ?: page()
        }
    }

    /** A handle long enough that no two share a search. */
    private fun member(name: String, order: Long) = FeedMember(name, name.padEnd(250, '_'), order)

    private val a = member("a", 1)
    private val b = member("b", 2)

    @Test
    fun aSavedSearchIsSearchedInBracketsBesideTheAccountsAndHasNoProfileToFallBackOn() = runBlocking {
        val nasa = FeedMember("nasa", "nasa", followedAt = 1)
        val list = FeedMember.search("list:123", followedAt = 2)
        val plan = GroupFeedPlan("g", listOf(list, nasa))
        assertEquals(listOf("(from:nasa OR (list:123)) include:nativeretweets"), plan.chunks.map(plan::queryOf))

        val reads = Reads(failing = mutableMapOf("list:123" to XRequestException(403, "refused")), profiles = mapOf("nasa" to page(thread(10, by = "nasa"))))
        val first = GroupFeed(plan, reads).first()
        assertEquals(listOf("10"), ids(first.threads))
        assertEquals(listOf("nasa"), reads.profileReads, "the saved search has no profile of its own")

        val onlySearch = Reads(failing = mutableMapOf("list:9" to XRequestException(403, "refused")))
        val failed = GroupFeed(GroupFeedPlan("g", listOf(FeedMember.search("list:9"))), onlySearch).first()
        assertIs<XRequestException>(failed.failures.single())
        assertTrue(onlySearch.profileReads.isEmpty())
    }

    @Test
    fun aFeedWithoutRepliesAsksItsSearchesToKeepNone() = runBlocking {
        val reads = Reads()
        GroupFeed(GroupFeedPlan("g", listOf(a), includeReplies = false), reads).first()
        GroupFeed(GroupFeedPlan("g", listOf(b)), reads).first()
        assertEquals(listOf(false, true), reads.withReplies)
    }

    @Test
    fun postsByAccountsOutsideAChunkAreNoticedUnlessItHoldsASavedSearch() {
        val members = listOf(FeedMember("1", "nasa"), FeedMember("2", "esa"))
        assertFalse(UnrelatedPosts.found(listOf(thread(1, by = "1"), thread(2, by = "2")), members))
        assertTrue(UnrelatedPosts.found(listOf(thread(1, by = "1"), thread(2, by = "99")), members))
        assertFalse(UnrelatedPosts.found(listOf(thread(post(1, by = "1", screenName = "NASA_official"))), members), "a renamed member is still itself")
        assertFalse(UnrelatedPosts.found(listOf(thread(1, by = "99")), members + FeedMember.search("list:123")))
    }

    @Test
    fun theUnrelatedPostsNoticeStaysUntilTheNextFirstPage() = runBlocking {
        val reads = Reads(
            mapOf(
                ("from:a" to null) to page(thread(100, by = "a"), thread(90, by = "stranger"), next = "a2"),
                ("from:a" to "a2") to page(thread(80, by = "a"), next = null),
            ),
        )
        val feed = GroupFeed(GroupFeedPlan("g", listOf(a)), reads)
        assertTrue(feed.first().unrelated)
        assertTrue(feed.more().unrelated, "later pages keep it up even when they are fine")
        reads.pages = mapOf(("from:a" to null) to page(thread(110, by = "a"), next = null))
        assertFalse(feed.first().unrelated)
    }

    @Test
    fun whatTheRulesHideTakesNoPlaceAndWhatTheyFoldSaysWhy() = runBlocking {
        val rules = FeedRules(mutedKeywords = listOf(MutedKeyword("spam"), MutedKeyword("spoilers", action = KeywordAction.FOLD)))
        val reads = Reads(mapOf(("from:a" to null) to page(thread(100, text = "spam"), thread(90, text = "spoilers"), thread(80, text = "ok"), next = null)))
        val first = GroupFeed(GroupFeedPlan("g", listOf(a), shaping = FeedShaping(rules = rules)), reads).first()
        assertEquals(listOf("90", "80"), ids(first.threads))
        assertEquals(mapOf("90" to "spoilers"), first.foldReasons)
    }

    @Test
    fun aPopularFeedOrdersEachLoadByLikesAndCapsAnAuthorPerLoad() = runBlocking {
        val reads = Reads(mapOf(("from:a" to null) to page(thread(100, likes = 1), thread(90, likes = 30), thread(80, likes = 20), next = null)))
        val plan = GroupFeedPlan("g", listOf(a), shaping = FeedShaping(sort = FeedSort.POPULAR, postCaps = mapOf("a" to 2)))
        assertEquals(listOf("90", "80"), ids(GroupFeed(plan, reads).first().threads))
    }

    @Test
    fun aRetryReadsOnlyTheChunksThatFailedAndTheProgressSaysSo() = runBlocking {
        val reads = Reads(
            mapOf(("from:a" to null) to page(thread(100, by = "a"), next = null), ("from:b" to null) to page(thread(95, by = "b"), next = null)),
            failing = mutableMapOf("from:b" to IOException("offline")),
        )
        val feed = GroupFeed(GroupFeedPlan("g", listOf(a, b)), reads)
        val first = feed.first()
        assertEquals(listOf("100"), ids(first.threads))
        assertIs<IOException>(first.failures.single())
        assertEquals(FeedProgress(answered = 2, total = 2, failed = 1, loading = false), feed.progress.value)
        assertTrue(feed.progress.value.canRetry)

        reads.failing.clear()
        reads.searches.clear()
        val retried = feed.retryFailed()
        assertEquals(listOf("95"), ids(retried.threads))
        assertTrue(retried.failures.isEmpty())
        assertEquals(1, reads.searches.size)
        assertTrue("from:b" in reads.searches.single().first, "the chunk that answered is not asked again")
        assertFalse(feed.progress.value.canRetry)
    }

    @Test
    fun aChunkThatFailedBeforeTheAppClosedIsReadAgainOnRetry() = runBlocking {
        val reads = Reads(
            mapOf(("from:a" to null) to page(thread(100, by = "a"), next = null), ("from:b" to null) to page(thread(95, by = "b"), next = null)),
            failing = mutableMapOf("from:b" to IOException("offline")),
        )
        val feed = GroupFeed(GroupFeedPlan("g", listOf(a, b)), reads)
        val shown = feed.first().threads
        val saved = SavedFeed.decode(SavedFeed.encode(SavedFeed(shown, feed.state(), savedAt = 5)))!!
        assertEquals(listOf(false, true), saved.state!!.chunks.map { it.failed })
        assertEquals(5L, saved.savedAt)

        reads.failing.clear()
        val again = GroupFeed(GroupFeedPlan("g", listOf(a, b)), reads)
        assertTrue(again.restore(saved.state!!, ids(shown)))
        assertEquals(listOf("95"), ids(again.retryFailed().threads))
    }

    @Test
    fun profilesAreAskedForUnlessTheyCannotGetThroughEither() {
        assertTrue(GroupFeed.fallsBack(XRateLimitedException()))
        assertTrue(GroupFeed.fallsBack(XRequestException(403, "refused")))
        assertTrue(GroupFeed.fallsBack(IllegalStateException("parse")))
        assertFalse(GroupFeed.fallsBack(IOException("offline")))
        assertFalse(GroupFeed.fallsBack(XNoAccountException()))
        assertFalse(GroupFeed.fallsBack(SigningUnavailableException("no signer")))
    }

    @Test
    fun whenNothingCouldBeReadARateLimitIsShownFirstThenARefusal() {
        val refused = XRequestException(403, "refused")
        val limited = XRateLimitedException()
        val other = IllegalStateException("other")
        assertSame(limited, FeedErrors.toShow(listOf(refused, limited, other)))
        assertSame(refused, FeedErrors.toShow(listOf(other, refused)))
        assertSame(other, FeedErrors.toShow(listOf(other)))
        assertNull(FeedErrors.toShow(emptyList()))
    }

    @Test
    fun eachUsableAccountAddsAboutTwoSearchesBetweenThreeAndSix() {
        assertEquals(listOf(3, 3, 4, 6, 6), listOf(0, 1, 2, 3, 12).map(ChunkConcurrency::forAccounts))
        val accounts = listOf("a", "b", "c", "d").map { XAccount(it, it, emptyMap()) }
        assertEquals(2, ChunkConcurrency.usableAccounts(accounts, setOf("d"), broken = { it.id == "c" }))
        assertEquals(2, ChunkConcurrency.usableAccounts(accounts.take(3), setOf("a", "b", "c"), broken = { it.id == "c" }), "with every account off, all working ones count")
        assertEquals(1, ChunkConcurrency.usableAccounts(accounts.take(3), emptySet(), searchLimited = { it.id != "a" }))
        assertEquals(0, ChunkConcurrency.usableAccounts(emptyList(), emptySet()))
    }

    @Test
    fun aPlanReadsItsMembersOldestFirstAndKeepsAPositionOnlyInTimeOrder() {
        val plan = GroupFeedPlan("g", listOf(member("late", 9), member("early", 1)))
        assertEquals(listOf("early", "late"), plan.chunks.map { it.single().id })
        assertTrue(plan.tracksReadPosition(readingPosition = true))
        assertFalse(plan.copy(shaping = FeedShaping(sort = FeedSort.POPULAR)).tracksReadPosition(readingPosition = true))
        assertTrue(plan.copy(catchUp = true).tracksReadPosition(readingPosition = false))
    }
}
