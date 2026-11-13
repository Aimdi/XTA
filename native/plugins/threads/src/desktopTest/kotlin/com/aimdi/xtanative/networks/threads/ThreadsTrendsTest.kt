package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.x.http.XResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** Meta's trending topics on the empty search page: made-up values, in the shapes the reader is written for. */
class ThreadsTrendsTest {
    /** A search page whose preloaded `BarcelonaSearchTrendingTopicsSectionQuery` answers [trends] under `__bbox.result.data.trends`. */
    private fun searchPage(trends: Any?): String {
        val preload = listOf("adp_BarcelonaSearchTrendingTopicsSectionQueryRelayPreloader_1", mapOf("__bbox" to mapOf("complete" to true, "result" to mapOf("data" to mapOf("trends" to trends)))))
        val script = mapOf("require" to listOf(listOf("ScheduledServerJS", "handle", null, listOf(mapOf("__bbox" to mapOf("require" to listOf(listOf("RelayPrefetchedStreamCache", "next", listOf<Any>(), preload))))))))
        return "<html><script type=\"application/json\" data-sjs>${json(script)}</script></html>"
    }

    @Test
    fun aListOfTrendsIsReadWithTheLinesMetaWritesUnderEach() {
        val page = searchPage(
            listOf(
                mapOf("id" to "1", "title" to "Glaze  recipes", "social_context" to "1.2K threads", "summary" to mapOf("text" to "Potters swap recipes.")),
                mapOf("name" to "#Kilns", "subtitle" to "Crafts"),
                mapOf("title" to "Raku", "search_url" to "/search?q=raku%20firing&serp_type=tags"),
            ),
        )
        assertEquals(
            listOf(
                ThreadsTrend("Glaze recipes", ThreadsSearchTerms("Glaze recipes", tag = false), "1.2K threads", "Potters swap recipes."),
                ThreadsTrend("Kilns", ThreadsSearchTerms("Kilns", tag = true), "Crafts"),
                ThreadsTrend("Raku", ThreadsSearchTerms("raku firing", tag = true)),
            ),
            ThreadsTrends.read(page),
            "spaces folded, a #name a topic, a threads.com search link the search it opens",
        )
    }

    @Test
    fun aConnectionsEdgesAndWrappedNodesAreRead() {
        val page = searchPage(mapOf("edges" to listOf(mapOf("node" to mapOf("trend" to mapOf("trend_name" to "Comets", "query_text" to "comet tonight"))))))
        assertEquals(listOf(ThreadsTrend("Comets", ThreadsSearchTerms("comet tonight", tag = false))), ThreadsTrends.read(page))
    }

    @Test
    fun itemsWithoutANameRepeatsAndOtherShapesAreLeftOutAndNothingThrows() {
        val page = searchPage(
            listOf(
                mapOf("id" to "1"),
                mapOf("title" to mapOf("x" to 1)),
                mapOf("title" to "Moon"),
                mapOf("title" to "moon"),
                "Moon",
                mapOf("title" to "x".repeat(400)),
                mapOf("title" to "Sun", "summary" to "Sun", "social_context" to "Sun"),
            ),
        )
        assertEquals(listOf(ThreadsTrend("Moon", ThreadsSearchTerms("Moon", tag = false)), ThreadsTrend("Sun", ThreadsSearchTerms("Sun", tag = false), "Sun")), ThreadsTrends.read(page))
        assertTrue(ThreadsTrends.read(searchPage(null)).isEmpty())
        assertTrue(ThreadsTrends.read(searchPage("soon")).isEmpty())
        assertTrue(ThreadsTrends.read("<html><script data-sjs>{\"trends\": oops</script></html>").isEmpty())
        assertTrue(ThreadsTrends.read("").isEmpty())
    }

    @Test
    fun atMostTwentyAreKept() {
        val page = searchPage((1..30).map { mapOf("title" to "Trend $it") })
        assertEquals(ThreadsTrends.MAX, ThreadsTrends.read(page).size)
    }

    @Test
    fun aSearchATrendOpenedFindsItsTrend() {
        val trends = listOf(ThreadsTrend("Kilns", ThreadsSearchTerms("Kilns", tag = true), "Crafts"), ThreadsTrend("Moon", ThreadsSearchTerms("Moon", tag = false)))
        assertEquals("Crafts", ThreadsTrends.matching(trends, ThreadsSearchTerms("kilns", tag = true))?.context)
        assertNull(ThreadsTrends.matching(trends, ThreadsSearchTerms("kilns", tag = false)), "the words kilns are not the topic")
        assertNull(ThreadsTrends.matching(trends, ThreadsSearchTerms("sun", tag = false)))
    }

    @Test
    fun theClientReadsTheSearchPageOnceAsAGuestAndKeepsItHalfAnHour() = runBlocking {
        val time = FakeTime()
        val meta = FakeMeta { ok(searchPage(listOf(mapOf("title" to "Moon")))) }
        val client = direct(meta, session = ThreadsSession(cookieHeader = COOKIES), time = time)
        assertEquals(listOf("Moon"), client.trends().map { it.title })
        assertEquals(listOf("Moon"), client.trends().map { it.title })
        assertEquals(listOf("Moon"), client.knownTrends()?.map { it.title })
        assertEquals(1, meta.asked.size, "the second read is the one kept")
        val asked = meta.asked.single()
        assertEquals("GET", asked.method)
        assertEquals("/search", asked.path)
        assertNull(asked.url.query, "the empty search page")
        assertNull(asked.headers["Cookie"], "a guest read: no session is spent")
        time.now += ThreadsDirectClient.TRENDS_TTL_MILLIS
        assertNull(client.knownTrends())
        client.trends()
        client.trends(force = true)
        assertEquals(3, meta.asked.size, "read again once stale, and when asked to")
    }

    @Test
    fun aGuestSentToSignInHasNoTrendsAndAThrottleIsAFailureOfItsOwn() = runBlocking {
        val signIn = FakeMeta { XResponse(302, "", mapOf("Location" to listOf("https://www.threads.com/login"))) }
        assertTrue(direct(signIn).trends().isEmpty())
        val throttled = FakeMeta { XResponse(429, "") }
        val failure = runCatching { direct(throttled).trends() }.exceptionOrNull() as? ThreadsException
        assertEquals(ThreadsFailure.THROTTLED, failure?.failure)
        assertTrue(failure?.retryAt != null, "Retry waits for the throttle's end")
    }
}
