package com.aimdi.xtanative.networks.reddit

import com.aimdi.xtanative.networks.NetworkRequestException
import com.aimdi.xtanative.networks.Page
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Why a Reddit read sometimes did not load, held off: a stalled host may not
 * hold a screen for minutes, one slow answer may not park a route, and the
 * requests made at once stay few.
 */
class RedditReliabilityTest {
    @Test
    fun aListingWhoseHostsAllStallFailsWithinOneBudgetNotOneWaitPerRoute() {
        val fake = FakeReddit { delay(5_000); page(WWW_LISTING) }
        val client = RedditClient(fake, readBudgetMillis = 300)
        val started = System.currentTimeMillis()

        assertFailsWith<IOException> { runBlocking { client.listing("dartlang") } }
        assertTrue(System.currentTimeMillis() - started < 2_000, "one budget for the whole walk")
        assertEquals(1, fake.asked.size, "the routes after the budget ran out are not asked")
    }

    @Test
    fun aThreadWhoseHostsAllStallFailsWithinOneBudget() {
        val fake = FakeReddit { delay(5_000); page("") }
        val client = RedditClient(fake, readBudgetMillis = 300)
        val started = System.currentTimeMillis()

        assertFailsWith<IOException> { runBlocking { client.thread("/r/dartlang/comments/abc123/dart_4_is_out/") } }
        assertTrue(System.currentTimeMillis() - started < 2_000)
        assertEquals(1, fake.asked.size)
    }

    @Test
    fun aSlowFirstRouteLeavesTheRestOfTheBudgetToTheNext() = runBlocking {
        val fake = FakeReddit { if (it.isWww) { delay(5_000); page(WWW_LISTING) } else page(LISTING_HTML) }
        val listing = RedditClient(fake, readBudgetMillis = 3_000, timeoutMillis = 200).listing("dartlang")

        assertEquals("abc123", listing.items.single().id)
        assertEquals(listOf("www.reddit.com", "old.reddit.com"), fake.hosts)
    }

    @Test
    fun aTimeoutDoesNotParkARouteButABlockDoes() = runBlocking {
        var wwwStalls = true
        var htmlFails = false
        val fake = FakeReddit {
            when {
                it.isWww && wwwStalls -> throw SocketTimeoutException("slow")
                it.isWww -> page(WWW_LISTING)
                it.url.host == "old.reddit.com" && !it.url.encodedPath.endsWith(".json") -> if (htmlFails) page("", 500) else page(LISTING_HTML)
                else -> page("", 500)
            }
        }
        val client = RedditClient(fake)
        client.listing("dartlang")
        wwwStalls = false
        htmlFails = true
        fake.asked.size.let { before ->
            client.listing("flutterdev")
            assertTrue(fake.asked.drop(before)[1].isWww, "a timed-out www is asked right after the route that answered last")
        }

        var oldFails = false
        val blocked = FakeReddit {
            when {
                it.isWww -> page("<html>blocked by network security</html>")
                it.url.encodedPath.endsWith(".json") || oldFails -> page("", 500)
                else -> page(LISTING_HTML)
            }
        }
        val parked = RedditClient(blocked)
        parked.listing("dartlang")
        oldFails = true
        val before = blocked.asked.size
        runCatching { parked.listing("flutterdev") }
        val second = blocked.asked.drop(before)
        assertEquals(second.size - 1, second.indexOfFirst { it.isWww }, "a blocked www is asked last until its wait is over")
    }

    @Test
    fun aRetryAfterSetsHowLongARouteWaits() = runBlocking {
        var now = 1_000_000L
        var throttled = true
        var htmlFails = false
        val fake = FakeReddit {
            when {
                it.isWww && throttled -> page("", 429, mapOf("Retry-After" to listOf("30")))
                it.isWww -> page(WWW_LISTING)
                it.url.encodedPath.endsWith(".json") -> page("", 500)
                htmlFails -> page("", 500)
                else -> page(LISTING_HTML)
            }
        }
        val client = RedditClient(fake, clock = { now })
        client.listing("dartlang")
        throttled = false
        htmlFails = true
        now += 31_000
        val before = fake.asked.size
        client.listing("flutterdev")
        assertTrue(fake.asked.drop(before)[1].isWww, "after its Retry-After www is back in its place, not parked for five minutes")
    }

    @Test
    fun theWaitRedditNamesIsReadOffItsAnswer() {
        val transport = RedditTransport(FakeReddit { page("") }, RedditClient.WEB_USER_AGENT, clock = { 1_000L })
        val url = "https://www.reddit.com/r/dartlang/".toHttpUrl()

        assertEquals(31_000L, transport.failureOf(page("", 429, mapOf("Retry-After" to listOf("30"))), url).retryAt?.toEpochMilli())
        assertEquals(6_000L, transport.failureOf(page("", 429, mapOf("x-ratelimit-reset" to listOf("5"))), url).retryAt?.toEpochMilli())
        assertEquals(null, transport.failureOf(page("", 403), url).retryAt)
        val dated = transport.failureOf(page("", 503, mapOf("Retry-After" to listOf("Thu, 01 Jan 1970 00:00:11 GMT"))), url)
        assertEquals(11_000L, dated.retryAt?.toEpochMilli())
    }

    @Test
    fun aRouteWaitsForAsLongAsItWasToldTo() {
        var now = 0L
        val routes = PublicRoutes { now }
        routes.refuse(PublicRoute.WWW, 1_000)
        assertEquals(PublicRoute.WWW, routes.inOrder().last())
        now += 1_001
        assertEquals(PublicRoute.WWW, routes.inOrder().first())
    }

    @Test
    fun noMoreThanThreeRequestsAreOnTheirWayAtOnce() = runBlocking {
        val now = AtomicInteger()
        val most = AtomicInteger()
        val fake = FakeReddit {
            most.accumulateAndGet(now.incrementAndGet(), ::maxOf)
            delay(50)
            now.decrementAndGet()
            page(WWW_LISTING)
        }
        val client = RedditClient(fake)
        (1..9).map { async { client.listing("community$it") } }.awaitAll()

        assertEquals(9, fake.asked.size)
        assertEquals(RedditTransport.MAX_IN_FLIGHT, most.get())
    }

    @Test
    fun withoutAnAccountAnIconAsksOldRedditsPageOnce() = runBlocking {
        val fake = FakeReddit { page("", 403) }
        RedditClient(fake).icon("dartlang")

        assertEquals(1, fake.urls.count { it.host == "old.reddit.com" && it.encodedPath == "/r/dartlang/" })
    }

    @Test
    fun anEmptyWwwPageIsAskedOfTheOtherRoutesBeforeItIsBelieved() = runBlocking {
        val fake = FakeReddit { if (it.isWww) page(WWW_EMPTY) else page(LISTING_HTML) }
        val listing = RedditClient(fake).listing("dartlang")

        assertEquals("abc123", listing.items.single().id)
        assertEquals(listOf("www.reddit.com", "old.reddit.com"), fake.hosts)
    }

    @Test
    fun anEmptyPageIsKeptOnlyAsBrieflyAsAFailure() = runBlocking {
        var now = 0L
        val cache = RedditListingCache(clock = { now })
        val key = RedditListingKey("dartlang", RedditSort.HOT, null)
        var reads = 0
        repeat(2) { cache.listing(key) { reads++; Page(emptyList<RedditPost>(), null) } }
        assertEquals(1, reads)
        now += RedditListingCache.FAILURE_TTL_MILLIS
        cache.listing(key) { reads++; Page(emptyList<RedditPost>(), null) }
        assertEquals(2, reads, "an empty page is what a passing refusal can look like")
    }

    @Test
    fun theFeedSaysWhichCommunitiesDidNotLoad() = runBlocking {
        val fake = FakeReddit {
            when {
                it.url.queryParameter("name") == "gone" || "/r/gone" in it.url.encodedPath -> page("", 403)
                it.isWww -> page(WWW_LISTING)
                else -> page("", 500)
            }
        }
        val read = RedditFollowingFeed(RedditClient(fake)).read(listOf("dartlang", "gone"))

        assertEquals(listOf("abc123"), read.posts.map { it.id })
        assertEquals(setOf("gone"), read.failures.keys)
        assertEquals(403, (read.failure as NetworkRequestException).code)
    }

    @Test
    fun anAccountsRefusedTokenIsReportedAndTheListingReadWithoutIt() = runBlocking {
        val rejected = mutableListOf<String>()
        val fake = FakeReddit { if (it.url.host == "oauth.reddit.com") page("", 401) else page(WWW_LISTING) }
        val client = RedditClient(fake, onUserTokenRejected = { synchronized(rejected) { rejected += it } })
        val listing = client.listing("dartlang", session = RedditSession(userToken = "user-token"))

        assertEquals("abc123", listing.items.single().id, "the public routes still serve the feed")
        assertEquals(listOf("user-token"), rejected)
    }

    @Test
    fun readsOnAColdAppOnlyTokenShareOneRequestForIt() = runBlocking {
        val fake = FakeReddit {
            if (it.url.encodedPath.contains("access_token")) {
                delay(100)
                page(tokenBody())
            } else {
                page(listingBody())
            }
        }
        val client = RedditClient(fake)
        (1..5).map { async { client.listing("community$it", session = RedditSession(clientId = "cid")) } }.awaitAll()

        assertEquals(1, fake.asked.count { it.url.encodedPath.contains("access_token") })
    }

    @Test
    fun theApiAgentHasTheShapeRedditAsksFor() {
        val agent = RedditClient.API_USER_AGENT
        assertTrue(Regex("""^(linux|android):com\.aimdi\.xtanative\.reddit:\d+\.\d+\.\d+ \(by [^)]+\)$""").matches(agent), agent)
        assertTrue(agent.startsWith("linux:"), "these tests run on the desktop")
        val manifest = java.io.File("plugin.json").takeIf { it.exists() } ?: java.io.File("native/plugins/reddit/plugin.json")
        assertTrue("\"version\": \"${RedditClient.PLUGIN_VERSION}\"" in manifest.readText(), "the agent's version is plugin.json's")
    }
}
