package com.aimdi.xtanative.core.feed

import com.aimdi.xtanative.core.feed.Posts.ids
import com.aimdi.xtanative.core.feed.Posts.page
import com.aimdi.xtanative.core.feed.Posts.post
import com.aimdi.xtanative.core.feed.Posts.thread
import com.aimdi.xtanative.core.x.XAccount
import com.aimdi.xtanative.core.x.XNoAccountException
import com.aimdi.xtanative.core.x.XRateLimitedException
import com.aimdi.xtanative.core.x.XRequestException
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XTimelinePage
import com.aimdi.xtanative.core.x.signing.SigningUnavailableException
import java.io.IOException
import java.io.InterruptedIOException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** XTA's group feed rules (feed_search_fallback_test, unrelated_posts_test, feed_chunk_concurrency_test and the feed's page pass), for the native port. */
class GroupFeedTest {
    /** Stands in for X: a search answers by the first key its query contains, page by page. */
    private class Reads(
        var pages: Map<Pair<String, String?>, XTimelinePage> = emptyMap(),
        val failing: MutableMap<String, Throwable> = mutableMapOf(),
        val profiles: Map<String, XTimelinePage> = emptyMap(),
        /** Searches (by a key their query contains) that never answer. */
        val silent: MutableSet<String> = mutableSetOf(),
        /** Cursors whose page never comes. */
        val stuck: MutableSet<String> = mutableSetOf(),
    ) : FeedReads {
        val searches = ArrayList<Pair<String, String?>>()
        val profileReads = ArrayList<String>()
        val withReplies = ArrayList<Boolean>()
        val searchReplies = ArrayList<Boolean>()

        override suspend fun search(query: String, cursor: String?, includeReplies: Boolean): XTimelinePage {
            synchronized(searches) { searches += query to cursor; searchReplies += includeReplies }
            if (silent.any { query.contains(it) } || cursor in stuck) delay(Long.MAX_VALUE)
            failing.entries.firstOrNull { query.contains(it.key) }?.let { throw it.value }
            return pages.entries.firstOrNull { (key, _) -> query.contains(key.first) && key.second == cursor }?.value ?: page()
        }

        override suspend fun userPosts(userId: String, includeReplies: Boolean): XTimelinePage {
            synchronized(profileReads) {
                profileReads += userId
                withReplies += includeReplies
            }
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
        assertEquals(listOf(false, true), reads.searchReplies)
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
        assertTrue(GroupFeed.fallsBack(SearchTimeoutException(20_000)), "a search that timed out is no lost connection")
        assertTrue(GroupFeed.fallsBack(InterruptedIOException("socket timed out")))
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

    // ---- feed_search_fallback_test: the search's time and the fallback on every page ----

    @Test
    fun aSearchThatGivesNoAnswerInItsTimeFallsBackToTheProfilesWithTheFeedsReplies() = runBlocking {
        val reads = Reads(silent = mutableSetOf("from:a"), profiles = mapOf("a" to page(thread(50, by = "a"))))
        val first = GroupFeed(GroupFeedPlan("g", listOf(a), includeReplies = true), reads, searchMillis = 50).first()
        assertEquals(listOf("50"), ids(first.threads))
        assertTrue(first.failures.isEmpty())
        assertEquals(listOf(true), reads.withReplies, "the profiles are asked with their replies when the feed shows replies")

        val without = Reads(silent = mutableSetOf("from:a"))
        val failed = GroupFeed(GroupFeedPlan("g", listOf(a), includeReplies = false), without, searchMillis = 50).first()
        assertIs<SearchTimeoutException>(failed.failures.single(), "with nothing from the profiles, the search's failure is kept")
        assertEquals(listOf(false), without.withReplies)
    }

    @Test
    fun aLaterPageWhoseSearchFailsFallsBackToo() = runBlocking {
        val reads = Reads(
            mapOf(("from:a" to null) to page(thread(100, by = "a"), thread(90, by = "a"), next = "a2")),
            profiles = mapOf("a" to page(thread(100, by = "a"), thread(70, by = "a"))),
        )
        val feed = GroupFeed(GroupFeedPlan("g", listOf(a)), reads)
        assertEquals(listOf("100", "90"), ids(feed.first().threads))
        reads.failing["from:a"] = XRateLimitedException()
        val more = feed.more()
        assertEquals(listOf("70"), ids(more.threads), "what is already shown is not shown again")
        assertTrue(more.failures.isEmpty())
        assertFalse(more.hasMore, "a profile's page has no search cursor to read on from")
    }

    // ---- the first page over the kept one (_feed.dart's stored chunk rows and gap fill) ----

    private fun keep(feed: GroupFeed, threads: List<XThread>) = runBlocking {
        SavedFeed.decode(SavedFeed.encode(SavedFeed(threads, feed.state(), savedAt = 1, key = feed.plan.readKey)))!!
    }

    @Test
    fun aFeedOpenedAgainReadsWhatWasWrittenSinceDownToTheKeptPostsAndShowsThemUnderIt() = runBlocking {
        val plan = GroupFeedPlan("g", listOf(a))
        val reads = Reads(mapOf(("from:a" to null) to page(thread(100, by = "a"), thread(90, by = "a"), next = "a2")))
        val before = GroupFeed(plan, reads)
        val saved = keep(before, before.first().threads)

        reads.pages = mapOf(
            ("from:a" to null) to page(thread(130, by = "a"), thread(120, by = "a"), next = "n2"),
            ("from:a" to "n2") to page(thread(110, by = "a"), thread(100, by = "a"), next = "n3"),
            ("from:a" to "a2") to page(thread(80, by = "a"), next = null),
        )
        reads.searches.clear()
        val feed = GroupFeed(plan, reads)
        val first = feed.reopen(saved)!!
        assertEquals(listOf("130", "120", "110", "100", "90"), ids(first.threads))
        assertFalse(first.gap)
        assertEquals(listOf(null, "n2"), reads.searches.map { it.second }, "paged down until the fresh posts met the kept ones")
        assertEquals(listOf("80"), ids(feed.more().threads), "loading more reads on below the kept posts")

        val lastSeen = ReadPosition("100", null)
        val reaches = FirstPageAction.reaches(first.threads, lastSeen, first.hasMore, first.gap)
        assertEquals(FirstPageAction.RestoreToBoundary(3), FirstPageAction.of(first.threads, lastSeen, false, null, true, reaches))
    }

    @Test
    fun aHoleLeftAfterTheLastExtraPageIsSaidAndMarksNothingRead() = runBlocking {
        val plan = GroupFeedPlan("g", listOf(a))
        val reads = Reads(mapOf(("from:a" to null) to page(thread(100, by = "a"), next = "a2")))
        val before = GroupFeed(plan, reads)
        val saved = keep(before, before.first().threads)
        reads.pages = mapOf(
            ("from:a" to null) to page(thread(200, by = "a"), next = "p2"),
            ("from:a" to "p2") to page(thread(190, by = "a"), next = "p3"),
            ("from:a" to "p3") to page(thread(180, by = "a"), next = "p4"),
            ("from:a" to "p4") to page(thread(170, by = "a"), next = "p5"),
            ("from:a" to "p5") to page(thread(160, by = "a"), next = "p6"),
        )
        val first = GroupFeed(plan, reads).reopen(saved)!!
        assertTrue(first.gap)
        assertEquals(listOf("200", "190", "180", "170", "160", "100"), ids(first.threads))
        val elsewhere = ReadPosition("50", Instant.EPOCH)
        assertFalse(FirstPageAction.reaches(first.threads, elsewhere, first.hasMore, first.gap))
        assertEquals(FirstPageAction.DoNothing, FirstPageAction.of(first.threads, elsewhere, true, null, true, reachesLastSeen = false))
    }

    @Test
    fun aKeptFeedOfOtherSearchesOrRulesIsNotReadOnFrom() = runBlocking {
        val plan = GroupFeedPlan("g", listOf(a))
        val reads = Reads(mapOf(("from:a" to null) to page(thread(100, by = "a"), next = "a2")))
        val before = GroupFeed(plan, reads)
        val saved = keep(before, before.first().threads)
        assertNull(GroupFeed(GroupFeedPlan("g", listOf(a, b)), reads).reopen(saved))
        assertNull(GroupFeed(plan.copy(shaping = FeedShaping(sort = FeedSort.POPULAR)), reads).reopen(saved))
        assertNull(GroupFeed(plan, reads).reopen(saved.copy(state = null)))
        assertNull(GroupFeed(plan, reads).reopen(saved.copy(key = null)), "a feed kept before its key was written is read from the top")
    }

    @Test
    fun aChunkThatFailedToReadWhatWasWrittenSinceReadsItOnRetry() = runBlocking {
        val plan = GroupFeedPlan("g", listOf(a, b))
        val reads = Reads(
            mapOf(("from:a" to null) to page(thread(100, by = "a"), next = null), ("from:b" to null) to page(thread(95, by = "b"), next = null)),
        )
        val before = GroupFeed(plan, reads)
        val saved = keep(before, before.first().threads)
        reads.pages = mapOf(("from:a" to null) to page(thread(120, by = "a"), thread(100, by = "a"), next = null), ("from:b" to null) to page(thread(110, by = "b"), thread(95, by = "b"), next = null))
        reads.failing["from:b"] = IOException("offline")
        val feed = GroupFeed(plan, reads)
        val first = feed.reopen(saved)!!
        assertEquals(listOf("120", "100", "95"), ids(first.threads))
        assertIs<IOException>(first.failures.single())
        assertTrue(feed.progress.value.canRetry)

        reads.failing.clear()
        assertEquals(listOf("110"), ids(feed.retryFailed().threads))
        assertFalse(feed.progress.value.canRetry)
    }

    @Test
    fun keptPostsAreReadAgainUnderTodaysRulesButKeepTheirOrderAndCaps() {
        val kept = listOf(thread(100, text = "spoilers"), thread(90), thread(80), thread(70), thread(60, text = "hidden"))
        val rules = FeedRules(mutedKeywords = listOf(MutedKeyword("spoilers", action = KeywordAction.FOLD), MutedKeyword("hidden")))
        val again = FeedShaping(rules = rules, postCaps = mapOf("a" to 1), zen = true).again(kept, Instant.EPOCH)
        assertEquals(listOf("100", "90", "80", "70"), ids(again.threads), "each load's caps were applied when it was read")
        assertEquals(mapOf("100" to "spoilers"), again.foldReasons)
    }

    // ---- first loads, plan changes, interrupted loads ----

    @Test
    fun aFirstLoadShowsEachChunkAsItAnswers() = runBlocking {
        val reads = Reads(
            mapOf(("from:a" to null) to page(thread(100, by = "a"), thread(90, by = "a"), next = "a2"), ("from:b" to null) to page(thread(95, by = "b"), next = "b2")),
            silent = mutableSetOf("from:b"),
        )
        val previews = ArrayList<List<String>>()
        val feed = GroupFeed(GroupFeedPlan("g", listOf(a, b)), reads)
        val job = launch { feed.first { previews += ids(it.threads) } }
        delay(200)
        job.cancelAndJoin()
        assertEquals(listOf(listOf("100", "90")), previews, "a's posts show before b answers")
    }

    @Test
    fun aPlanThatReadsTheSameIsTakenWithoutReadingAgain() {
        val plan = GroupFeedPlan("g", listOf(a))
        val feed = GroupFeed(plan, Reads())
        val zen = plan.copy(shaping = plan.shaping.copy(zen = true, postCaps = mapOf("a" to 2)), catchUp = true)
        assertTrue(feed.replan(zen))
        assertSame(zen, feed.plan)
        assertFalse(feed.replan(plan.copy(includeReplies = false)), "other searches are another feed")
        assertFalse(feed.replan(plan.copy(shaping = FeedShaping(sort = FeedSort.POPULAR))))
        assertFalse(feed.replan(plan.copy(shaping = FeedShaping(rules = FeedRules(minLikes = 10)))))
        assertSame(zen, feed.plan)
    }

    @Test
    fun aLoadStoppedHalfwayLosesNothingItTook() = runBlocking {
        val reads = Reads(
            mapOf(
                ("from:a" to null) to page(thread(100, by = "a"), next = "a2"),
                ("from:a" to "a2") to page(thread(90, by = "a"), next = "a3"),
                ("from:a" to "a3") to page(thread(80, by = "a"), next = null),
                ("from:b" to null) to page(thread(95, by = "b"), next = null),
            ),
            stuck = mutableSetOf("a3"),
        )
        val feed = GroupFeed(GroupFeedPlan("g", listOf(a, b)), reads)
        assertEquals(listOf("100"), ids(feed.first().threads))
        val job = launch { feed.more() }
        delay(200)
        job.cancelAndJoin()
        reads.stuck.clear()
        assertEquals(listOf("95", "90", "80"), ids(feed.more().threads))
    }
}
