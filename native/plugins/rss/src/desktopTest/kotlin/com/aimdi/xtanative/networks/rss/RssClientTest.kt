package com.aimdi.xtanative.networks.rss

import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.NetworkRequestException
import java.io.IOException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl

class RssClientTest {
    private fun fixture(name: String) = RssFixtures.text(name)

    /** Answers each address from [pages]; any other is a 404. */
    private class FakeWeb(val pages: Map<String, String>) : XHttp {
        val asked = ArrayList<String>()
        val headers = ArrayList<Map<String, String>>()
        val redirects = ArrayList<Boolean>()

        override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean): XResponse {
            asked += url.toString()
            this.headers += headers
            redirects += followRedirects
            return when (val body = pages[url.toString()]) {
                null -> XResponse(404, "")
                "offline" -> throw IOException("offline")
                else -> XResponse(200, body)
            }
        }
    }

    @Test
    fun aChannelIsReadWithXtasUserAgentAndAccept() = runBlocking {
        val web = FakeWeb(mapOf("https://www.slowboring.com/feed" to fixture("slow_boring.xml")))

        val channel = RssClient(web).channel("https://www.slowboring.com/feed")

        assertEquals("Slow Boring", channel.title)
        assertEquals(listOf("https://www.slowboring.com/feed"), web.asked)
        assertEquals("XTA RSS plugin", web.headers.single()["User-Agent"])
        assertEquals(
            "application/rss+xml, application/atom+xml, application/xml, text/xml, text/html;q=0.8, */*;q=0.5",
            web.headers.single()["Accept"],
        )
        assertEquals(listOf(true), web.redirects, "redirects are followed, as Dart's client does")
    }

    @Test
    fun itemsAreNamedAfterTheFollowedFeedAndBorrowItsIcon() = runBlocking {
        val web = FakeWeb(mapOf("https://news.example/index.rdf" to fixture("rdf.xml")))
        val feed = RssFeed(id = "news", feedUrl = "https://news.example/index.rdf", name = "My news", iconUrl = "https://news.example/icon.png")

        val page = RssClient(web).items(feed)

        assertNull(page.next, "a feed is one page")
        assertEquals(listOf("news", "news"), page.items.map { it.feedId })
        assertEquals(listOf("My news", "My news"), page.items.map { it.feedTitle })
        assertEquals(listOf("https://news.example/icon.png", "https://news.example/icon.png"), page.items.map { it.imageUrl })
        assertEquals("https://news.example/index.rdf::https://news.example/story/1", page.items[0].id, "ids stay the parser's")
    }

    @Test
    fun aRefusedReadIsANetworkRequestException() = runBlocking {
        val failure = assertFailsWith<NetworkRequestException> { RssClient(FakeWeb(emptyMap())).channel("https://gone.example/feed") }

        assertEquals("rss", failure.network)
        assertEquals(404, failure.code)
    }

    @Test
    fun noConnectionStaysAnIOException() {
        val web = FakeWeb(mapOf("https://down.example/feed" to "offline"))
        assertFailsWith<IOException> { runBlocking { RssClient(web).channel("https://down.example/feed") } }
    }

    @Test
    fun aSitesPageNamesItsFeedAndTheFeedIsReadOnce() = runBlocking {
        val web = FakeWeb(
            mapOf(
                "https://blog.example.org/" to fixture("site.html"),
                "https://blog.example.org/feed.xml" to fixture("atom_rich.xml"),
            ),
        )

        val feed = RssClient(web).lookup("blog.example.org")

        assertEquals(listOf("https://blog.example.org/", "https://blog.example.org/feed.xml"), web.asked)
        assertEquals(
            RssFeed(
                id = "https://blog.example.org/feed.xml",
                feedUrl = "https://blog.example.org/feed.xml",
                name = "Rich Atom",
                siteUrl = "https://atom.example/",
                iconUrl = "https://atom.example/logo.png",
                description = "Everything Atom can say",
            ),
            feed,
        )
    }

    @Test
    fun aNamedFeedThatFailsGivesWayToTheNextThenToTheUsualPaths() = runBlocking {
        val web = FakeWeb(
            mapOf(
                "https://blog.example.org/about" to fixture("site.html"),
                "https://cdn.example.org/atom.xml" to "<html><body>Not a feed</body></html>",
                "https://blog.example.org/rss" to fixture("slow_boring.xml"),
            ),
        )

        val url = RssClient(web).resolveFeedUrl("https://blog.example.org/about")

        assertEquals("https://blog.example.org/rss", url)
        assertEquals(
            listOf(
                "https://blog.example.org/about",
                "https://blog.example.org/feed.xml",
                "https://cdn.example.org/atom.xml",
                "https://blog.example.org/feed",
                "https://blog.example.org/rss",
            ),
            web.asked,
        )
    }

    @Test
    fun aPastedFeedAddressIsReadOnceAndNamedAfterItsHostWhenUntitled() = runBlocking {
        val web = FakeWeb(mapOf("https://quiet.example/feed" to "<rss><channel><item><link>https://quiet.example/1</link></item></channel></rss>"))

        val feed = RssClient(web).lookup("https://quiet.example/feed")

        assertEquals(listOf("https://quiet.example/feed"), web.asked)
        assertEquals("quiet.example", feed.name)
        assertEquals("https://quiet.example/feed", feed.feedUrl)
    }

    @Test
    fun whatIsNoAddressOrHasNoFeedIsAnRssException() = runBlocking {
        val notAUrl = assertFailsWith<RssException> { RssClient(FakeWeb(emptyMap())).lookup("   ") }
        assertEquals(RssException.Reason.NOT_A_URL, notAUrl.reason)

        val web = FakeWeb(mapOf("https://plain.example/" to "<html><head></head><body>Nothing</body></html>"))
        val none = assertFailsWith<RssException> { RssClient(web).lookup("https://plain.example/") }
        assertEquals(RssException.Reason.NO_FEED, none.reason)
        assertEquals(7, web.asked.size, "the page, then the six usual paths")
    }

    @Test
    fun aSitePageThatCannotBeReadFailsTheLookup() = runBlocking {
        val failure = assertFailsWith<NetworkRequestException> { RssClient(FakeWeb(emptyMap())).lookup("https://gone.example/") }
        assertEquals(404, failure.code)
    }

    @Test
    fun theTimelineMergesEveryFeedNewestFirstAndCountsTheOnesThatFail() = runBlocking {
        val web = FakeWeb(
            mapOf(
                "https://news.example/index.rdf" to fixture("rdf.xml"),
                "https://www.slowboring.com/feed" to fixture("slow_boring.xml"),
            ),
        )
        val feeds = listOf(
            RssFeed(id = "news", feedUrl = "https://news.example/index.rdf", name = "News"),
            RssFeed(id = "gone", feedUrl = "https://gone.example/feed", name = "Gone"),
            RssFeed(id = "slow", feedUrl = "https://www.slowboring.com/feed", name = "Slow"),
        )

        val timeline = RssClient(web).timeline(feeds)

        assertEquals(1, timeline.failed)
        assertEquals(listOf("First story", "Second story", "Hello"), timeline.items.map { it.title })
        assertEquals(Instant.parse("2026-10-29T08:00:00Z"), timeline.items.first().publishedAt)
    }
}
