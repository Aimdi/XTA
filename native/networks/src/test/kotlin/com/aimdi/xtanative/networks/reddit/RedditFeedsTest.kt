package com.aimdi.xtanative.networks.reddit

import com.aimdi.xtanative.networks.NetworkRequestException
import com.aimdi.xtanative.networks.Page
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

/** The shared listing cache, the merged following feed and the gallery loader, as XTA's reddit_listing_cache_test and reddit_gallery_loader_test hold them. */
class RedditFeedsTest {
    private var now = 1_700_000_000_000L
    private val key = RedditListingKey("dartlang", RedditSort.HOT, null)

    private fun cache(maxEntries: Int = RedditListingCache.MAX_ENTRIES) = RedditListingCache(maxEntries = maxEntries, clock = { now })

    private fun onePost(id: String) = Page(listOf(RedditPost(id = id, title = id, subreddit = "dartlang", permalink = "/r/dartlang/comments/$id/")), null)

    @Test
    fun aListingFetchedForOneSurfaceIsHandedToTheNext() = runBlocking {
        val subject = cache()
        val fetches = AtomicInteger()
        val first = subject.listing(key) { fetches.incrementAndGet(); onePost("a") }
        now += 30_000
        val second = subject.listing(key) { fetches.incrementAndGet(); onePost("b") }

        assertEquals(1, fetches.get())
        assertEquals(first, second)
    }

    @Test
    fun surfacesAskingAtTheSameMomentJoinOneRequest() = runBlocking {
        val subject = cache()
        val gate = CompletableDeferred<Page<RedditPost>>()
        val fetches = AtomicInteger()
        val both = List(2) { async { subject.listing(key) { fetches.incrementAndGet(); gate.await() } } }
        gate.complete(onePost("a"))

        assertEquals(listOf("a", "a"), both.awaitAll().map { it.items.single().id })
        assertEquals(1, fetches.get(), "the second joined the request in flight")
    }

    @Test
    fun aSortOrAWindowOfItsOwnIsAListingOfItsOwn() = runBlocking {
        val subject = cache()
        val fetches = AtomicInteger()
        val count: suspend () -> Page<RedditPost> = { fetches.incrementAndGet(); Page(emptyList(), null) }
        subject.listing(key, fetch = count)
        subject.listing(key.copy(sort = RedditSort.NEW), fetch = count)
        subject.listing(RedditListingKey("dartlang", RedditSort.TOP, RedditTimeFilter.WEEK), fetch = count)
        subject.listing(RedditListingKey("dartlang", RedditSort.TOP, RedditTimeFilter.MONTH), fetch = count)

        assertEquals(4, fetches.get())
    }

    @Test
    fun aForcedRefreshGoesPastItAndIsWhatTheNextReaderIsHanded() = runBlocking {
        val subject = cache()
        val fetches = AtomicInteger()
        val fetch: suspend () -> Page<RedditPost> = { onePost("post${fetches.incrementAndGet()}") }
        subject.listing(key, fetch = fetch)

        assertEquals("post2", subject.listing(key, forceRefresh = true, fetch = fetch).items.single().id)
        assertEquals("post2", subject.listing(key, fetch = fetch).items.single().id)
        assertEquals(2, fetches.get())
    }

    @Test
    fun anOldEntryIsFetchedAgainAndReplacedNotAccumulatedHoweverFastItSettled() = runBlocking {
        // A fetch can be done before its caller awaits it; it has to have settled by then all the same.
        repeat(50) {
            val subject = cache()
            val fetches = AtomicInteger()
            subject.listing(key) { fetches.incrementAndGet(); onePost("a") }
            now += RedditListingCache.TTL_MILLIS + 1_000
            subject.listing(key) { fetches.incrementAndGet(); onePost("a") }

            assertEquals(2, fetches.get())
            assertEquals(1, subject.size)
        }
    }

    @Test
    fun aFailureIsHandedToTheNextSurfaceThenForgottenSoonerThanASuccess() = runBlocking {
        val subject = cache()
        val fetches = AtomicInteger()
        val failing: suspend () -> Page<RedditPost> = { fetches.incrementAndGet(); throw notFound("gone") }

        assertFailsWith<NetworkRequestException> { subject.listing(key, fetch = failing) }
        assertFailsWith<NetworkRequestException> { subject.listing(key, fetch = failing) }
        assertEquals(1, fetches.get(), "the second surface was handed the same failure")
        now += RedditListingCache.FAILURE_TTL_MILLIS + 1_000
        assertFailsWith<NetworkRequestException> { subject.listing(key, fetch = failing) }
        assertEquals(2, fetches.get())

        val good = RedditListingKey("flutterdev", RedditSort.HOT, null)
        subject.listing(good) { fetches.incrementAndGet(); onePost("a") }
        now += RedditListingCache.FAILURE_TTL_MILLIS + 1_000
        subject.listing(good) { fetches.incrementAndGet(); onePost("a") }
        assertEquals(3, fetches.get(), "a good listing outlives the failure window")
    }

    @Test
    fun itStopsGrowingDroppingTheLeastRecentlyUsed() = runBlocking {
        val subject = cache(maxEntries = 2)
        val empty: suspend () -> Page<RedditPost> = { Page(emptyList(), null) }
        fun keyOf(name: String) = RedditListingKey(name, RedditSort.HOT, null)
        subject.listing(keyOf("a"), fetch = empty)
        subject.listing(keyOf("b"), fetch = empty)
        subject.listing(keyOf("a"), fetch = empty)
        subject.listing(keyOf("c"), fetch = empty)
        assertEquals(2, subject.size)

        val fetches = AtomicInteger()
        val counted: suspend () -> Page<RedditPost> = { fetches.incrementAndGet(); Page(emptyList(), null) }
        subject.listing(keyOf("a"), fetch = counted)
        assertEquals(0, fetches.get(), "a was used most recently")
        subject.listing(keyOf("b"), fetch = counted)
        assertEquals(1, fetches.get(), "b was the least recently used")

        subject.clear()
        assertEquals(0, subject.size)
    }

    /** Followed communities over the API, each answering with [posts]; one in [failing] is private. */
    private class Communities(val posts: Map<String, List<String>>, val failing: Set<String> = emptySet()) {
        val http = FakeReddit { asked ->
            val name = asked.url.pathSegments[1]
            if (name in failing) page("", 404) else page(listingBody(children = posts[name].orEmpty().joinToString(",")))
        }
        val requests: List<String> get() = http.urls.map { it.pathSegments.drop(1).joinToString("/") }
        val feed = RedditFollowingFeed(RedditClient(http))
    }

    private val signedIn = RedditSession(userToken = "tok")

    private fun post(sub: String, id: String, at: Long? = 1_769_000_000, stickied: Boolean = false, score: Int = 0, over18: Boolean = false) =
        """{"kind":"t3","data":{"id":"$id","title":"$id","subreddit":"$sub","permalink":"/r/$sub/comments/$id/","score":$score,""" +
            """"stickied":$stickied,"over_18":$over18${at?.let { ",\"created_utc\":$it" }.orEmpty()}}}"""

    @Test
    fun everySurfaceReadingTheSameCommunitiesPaysForOneFetchBetweenThem() = runBlocking {
        val reddit = Communities(mapOf("dartlang" to listOf(post("dartlang", "a")), "flutterdev" to listOf(post("flutterdev", "b"))))
        reddit.feed.posts(listOf("dartlang", "flutterdev"), limit = 10, session = signedIn)
        reddit.feed.posts(listOf("dartlang", "flutterdev"), limit = 10, session = signedIn)
        reddit.feed.posts(listOf("dartlang", "flutterdev"), session = signedIn)

        assertEquals(2, reddit.requests.size, "two communities, not two per surface")
    }

    @Test
    fun aGroupSharingACommunityWithTheTabDoesNotFetchItAgainAndOneNamedTwiceIsReadOnce() = runBlocking {
        val reddit = Communities(mapOf("dartlang" to listOf(post("dartlang", "a")), "androiddev" to listOf(post("androiddev", "c"))))
        reddit.feed.posts(listOf("dartlang", "dartlang"), session = signedIn)
        reddit.feed.posts(listOf("dartlang", "androiddev"), session = signedIn)

        assertEquals(listOf("dartlang/hot", "androiddev/hot"), reddit.requests)
    }

    @Test
    fun aForcedReadReachesRedditAgain() = runBlocking {
        val reddit = Communities(mapOf("dartlang" to listOf(post("dartlang", "a"))))
        reddit.feed.posts(listOf("dartlang"), session = signedIn)
        reddit.feed.posts(listOf("dartlang"), forceRefresh = true, session = signedIn)

        assertEquals(2, reddit.requests.size)
    }

    @Test
    fun oneBadCommunityDoesNotEmptyTheFeedAndIsNotAskedAgainByTheNextSurface() = runBlocking {
        val reddit = Communities(mapOf("dartlang" to listOf(post("dartlang", "a"))), failing = setOf("private"))
        val first = reddit.feed.posts(listOf("private", "dartlang"), session = signedIn)
        val second = reddit.feed.posts(listOf("private", "dartlang"), session = signedIn)

        assertEquals(listOf("a"), first.map { it.id })
        assertEquals(listOf("a"), second.map { it.id })
        assertEquals(2, reddit.requests.size, "one try each, the failure remembered as well as the success")
    }

    @Test
    fun nothingToShowIsARealAnswerNotAReasonToKeepTheOldPosts() = runBlocking {
        val reddit = Communities(mapOf("dartlang" to listOf(post("dartlang", "a")), "quiet" to emptyList()))

        assertEquals(listOf("a"), reddit.feed.posts(listOf("dartlang"), session = signedIn).map { it.id })
        assertTrue(reddit.feed.posts(listOf("quiet"), session = signedIn).isEmpty())
        assertTrue(reddit.feed.posts(emptyList(), session = signedIn).isEmpty())
    }

    @Test
    fun newestFirstPinnedDroppedTrimmedToWhatTheSurfaceShows() = runBlocking {
        val reddit = Communities(
            mapOf(
                "dartlang" to listOf(
                    post("dartlang", "pinned", at = 1_769_007_200, stickied = true),
                    post("dartlang", "old", at = 1_769_000_000),
                    post("dartlang", "new", at = 1_769_007_200),
                    post("dartlang", "spare", at = 1_769_000_000),
                ),
            ),
        )

        assertEquals(listOf("new", "old"), reddit.feed.posts(listOf("dartlang"), limit = 2, session = signedIn).map { it.id }, "the pin is gone and the fourth trimmed")
    }

    @Test
    fun anotherSessionThrowsAwayWhatWasCachedForTheOldOne() = runBlocking {
        val reddit = Communities(mapOf("dartlang" to listOf(post("dartlang", "a"))))
        reddit.feed.posts(listOf("dartlang"), session = signedIn)
        reddit.feed.posts(listOf("dartlang"), session = RedditSession(userToken = "another"))

        assertEquals(2, reddit.requests.size, "the cached listing answered a different Reddit")
    }

    @Test
    fun theSortAndItsWindowAreWhatTheFeedAsksFor() = runBlocking {
        val reddit = Communities(mapOf("dartlang" to listOf(post("dartlang", "a"))))
        reddit.feed.posts(listOf("dartlang"), sort = RedditSort.NEW, session = signedIn)
        reddit.feed.posts(listOf("dartlang"), sort = RedditSort.TOP, timeFilter = RedditTimeFilter.WEEK, session = signedIn)

        assertEquals(listOf("dartlang/new", "dartlang/top"), reddit.requests)
        assertEquals("week", reddit.http.urls.last().queryParameter("t"))
    }

    @Test
    fun hidingNsfwFiltersTheMergedFeed() = runBlocking {
        val reddit = Communities(mapOf("dartlang" to listOf(post("dartlang", "safe"), post("dartlang", "adult", over18 = true))))

        assertEquals(listOf("safe"), reddit.feed.posts(listOf("dartlang"), hideNsfw = true, session = signedIn).map { it.id })
    }

    @Test
    fun undatedPostsAmongDatedOnesAreOrderedByScoreAndNeverThrow() {
        val posts = (0 until 200).map { i ->
            RedditPost(
                id = "p$i",
                title = "t",
                subreddit = "x",
                permalink = "/r/x/comments/p$i/",
                score = (i * 7919) % 97,
                createdAt = if (i % 3 == 0) null else Instant.ofEpochSecond(1_769_000_000L + (i * 104_729L) % 5_000),
            )
        }

        val sorted = newestFirst(posts)
        assertEquals(posts.toSet(), sorted.toSet())
        val dated = sorted.filter { it.createdAt != null }
        assertEquals(dated.sortedByDescending { it.createdAt }.map { it.createdAt }, newestFirst(dated).map { it.createdAt })
    }

    private val two = listOf("https://i.redd.it/1.jpg", "https://i.redd.it/2.jpg")

    /** A loader whose answers per permalink are scripted call by call, counting what it was asked. */
    private class Scripted(val script: Map<String, List<List<String>>>) {
        val asked = HashMap<String, Int>()
        val loader = RedditGalleryLoader(fetch = { permalink ->
            val n = synchronized(asked) { asked.merge(permalink, 1, Int::plus)!! }
            val answers = script[permalink] ?: listOf(emptyList())
            answers[(n - 1).coerceAtMost(answers.size - 1)]
        })
    }

    @Test
    fun aGalleryAnswerIsFetchedOnceAndReused() = runBlocking {
        val scripted = Scripted(mapOf("/p/a" to listOf(two)))
        scripted.loader.images("/p/a")
        scripted.loader.images("/p/a")

        assertEquals(1, scripted.asked["/p/a"])
        assertEquals(two, scripted.loader.known("/p/a"))
    }

    @Test
    fun anEmptyAnswerIsAskedAgainAndCanRecoverButNotForever() = runBlocking {
        val recovering = Scripted(mapOf("/p/a" to listOf(emptyList(), two)))
        assertTrue(recovering.loader.images("/p/a").isEmpty())
        assertEquals(two, recovering.loader.images("/p/a"))
        assertEquals(2, recovering.asked["/p/a"])

        val plain = Scripted(mapOf("/p/plain" to listOf(emptyList())))
        repeat(10) { plain.loader.images("/p/plain") }
        assertEquals(RedditGalleryLoader.EMPTY_RETRIES, plain.asked["/p/plain"], "more than once, far fewer than every scroll-past")
    }

    @Test
    fun whatTheLoaderHoldsIsBoundedOldestFirstOut() = runBlocking {
        val scripted = Scripted((0 until 300).associate { "/p/$it" to listOf(two) })
        (0 until 300).forEach { scripted.loader.images("/p/$it") }

        assertTrue(scripted.loader.size <= RedditGalleryLoader.CACHE_CAP)
        assertNotNull(scripted.loader.known("/p/299"))
        assertNull(scripted.loader.known("/p/0"))
    }

    @Test
    fun aFetchThatThrowsDoesNotPinItsFailureForeverHoweverFastItFails() = runBlocking {
        // A fetch that fails at once can be done before its caller awaits it; the next ask must still be a new one.
        repeat(50) {
            val asked = AtomicInteger()
            val loader = RedditGalleryLoader(fetch = { if (asked.incrementAndGet() == 1) throw IllegalStateException("boom") else two })

            assertFailsWith<IllegalStateException> { loader.images("/p/a") }
            assertEquals(two, loader.images("/p/a"))
        }
    }

    @Test
    fun twoCardsAskingAtOnceShareOneRequest() = runBlocking {
        val gate = CompletableDeferred<List<String>>()
        val fetches = AtomicInteger()
        val loader = RedditGalleryLoader(fetch = { fetches.incrementAndGet(); gate.await() })
        val both = List(2) { async { loader.images("/p/a") } }
        gate.complete(two)

        val answers = both.awaitAll()
        assertEquals(listOf(two, two), answers)
        assertSame(answers[0], answers[1])
        assertEquals(1, fetches.get())
    }
}
