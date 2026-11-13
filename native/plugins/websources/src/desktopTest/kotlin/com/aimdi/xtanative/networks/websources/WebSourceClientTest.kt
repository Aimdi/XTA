package com.aimdi.xtanative.networks.websources

import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.NetworkRequestException
import com.aimdi.xtanative.networks.websources.WebSourceFixtures.source
import com.aimdi.xtanative.networks.websources.WebSourceFixtures.text
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class WebSourceClientTest {
    private val lobsters = source(WebSourceFixtures.links())

    private class FakeSites(val answer: (HttpUrl, Int) -> XResponse) : XHttp {
        val asked = ArrayList<HttpUrl>()
        val headers = ArrayList<Map<String, String>>()
        val redirects = ArrayList<Boolean>()
        val times = ArrayList<Long>()

        override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean): XResponse {
            asked += url
            this.headers += headers
            redirects += followRedirects
            times += System.nanoTime() / 1_000_000
            return answer(url, asked.size)
        }

        override suspend fun post(url: HttpUrl, headers: Map<String, String>): XResponse = error("a web source never posts")
    }

    private fun client(sites: XHttp) = WebSourceClient(sites, spacingMillis = 0)

    private fun ok(body: String) = XResponse(200, body)

    private fun redirect(code: Int, to: String) = XResponse(code, "", mapOf("Location" to listOf(to)))

    @Test
    fun asksAListedSiteForThePageWithGetOnlyAndItsOwnName() = runBlocking {
        val sites = FakeSites { _, _ -> ok("[]") }

        assertEquals("[]", client(sites).page(lobsters, "https://lobste.rs/page/1.json".toHttpUrl(), WebSourcePageType.JSON))
        assertEquals("XTA web sources", sites.headers.single()["User-Agent"])
        assertEquals("application/json", sites.headers.single()["Accept"])
        assertEquals(setOf("User-Agent", "Accept"), sites.headers.single().keys, "no cookie, no account")
        assertEquals(listOf(false), sites.redirects, "redirects are followed by hand")
    }

    @Test
    fun eachPageTypeIsAskedForInItsOwnKind() = runBlocking {
        val sites = FakeSites { _, _ -> ok("") }
        val client = client(sites)
        val url = "https://lobste.rs/x".toHttpUrl()

        client.page(lobsters, url, WebSourcePageType.HTML)
        client.page(lobsters, url, WebSourcePageType.RSS)

        assertEquals(
            listOf("text/html,application/xhtml+xml", "application/rss+xml, application/atom+xml, application/xml, text/xml"),
            sites.headers.map { it["Accept"] },
        )
    }

    @Test
    fun refusesAnUnlistedSiteAndPlainHttpWithoutAsking() = runBlocking {
        val sites = FakeSites { _, _ -> ok("") }

        for (url in listOf("https://elsewhere.org/", "http://lobste.rs/", "https://lobste.rs:8443/")) {
            val refused = assertFailsWith<WebSourceLimitException> { client(sites).page(lobsters, url.toHttpUrl(), WebSourcePageType.JSON) }
            assertEquals(WebSourceLimitException.Kind.BLOCKED, refused.kind)
        }
        assertTrue(sites.asked.isEmpty())
    }

    @Test
    fun followsARedirectOnItsSitesAndNotOneThatLeavesThem() = runBlocking {
        val sites = FakeSites { url, _ ->
            when (url.encodedPath) {
                "/old" -> redirect(301, "/new")
                "/new" -> ok("moved")
                else -> redirect(302, "https://elsewhere.org/")
            }
        }

        assertEquals("moved", client(sites).page(lobsters, "https://lobste.rs/old".toHttpUrl(), WebSourcePageType.JSON))
        val away = assertFailsWith<WebSourceLimitException> { client(sites).page(lobsters, "https://lobste.rs/away".toHttpUrl(), WebSourcePageType.JSON) }
        assertEquals(WebSourceLimitException.Kind.BLOCKED, away.kind)
        assertEquals(listOf("/old", "/new", "/away"), sites.asked.map { it.encodedPath }, "elsewhere.org is never asked")
    }

    @Test
    fun stopsAfterThreeRedirects() = runBlocking {
        val sites = FakeSites { _, asked -> redirect(302, "/loop$asked") }

        val refused = assertFailsWith<WebSourceLimitException> { client(sites).page(lobsters, "https://lobste.rs/loop".toHttpUrl(), WebSourcePageType.JSON) }

        assertEquals(WebSourceLimitException.Kind.BLOCKED, refused.kind)
        assertEquals(4, sites.asked.size)
    }

    @Test
    fun reportsAnotherStatusAPageTooLargeAndNoAnswer(): Unit = runBlocking {
        val url = "https://lobste.rs/x".toHttpUrl()

        val status = assertFailsWith<NetworkRequestException> { client(FakeSites { _, _ -> XResponse(503, "") }).page(lobsters, url, WebSourcePageType.JSON) }
        assertEquals(503, status.code)
        assertEquals("websources", status.network)

        val large = assertFailsWith<WebSourceLimitException> {
            client(FakeSites { _, _ -> ok("x".repeat(WebSourceClient.MAX_PAGE_BYTES + 1)) }).page(lobsters, url, WebSourcePageType.JSON)
        }
        assertEquals(WebSourceLimitException.Kind.TOO_LARGE, large.kind)

        val declared = assertFailsWith<WebSourceLimitException> {
            client(FakeSites { _, _ -> XResponse(200, "", mapOf("Content-Length" to listOf("${WebSourceClient.MAX_PAGE_BYTES + 1}"))) })
                .page(lobsters, url, WebSourcePageType.JSON)
        }
        assertEquals(WebSourceLimitException.Kind.TOO_LARGE, declared.kind, "a page that says it is too large is not read")

        assertFailsWith<IOException> { client(FakeSites { _, _ -> throw IOException("offline") }).page(lobsters, url, WebSourcePageType.JSON) }
    }

    @Test
    fun waitsBetweenTwoRequestsOfTheSameSource() = runBlocking {
        val sites = FakeSites { _, _ -> ok("[]") }
        val client = WebSourceClient(sites, spacingMillis = 120)

        coroutineScope {
            listOf("https://lobste.rs/1", "https://lobste.rs/2").map { async { client.page(lobsters, it.toHttpUrl(), WebSourcePageType.JSON) } }.awaitAll()
        }

        assertTrue(sites.times[1] - sites.times[0] >= 110, "${sites.times[1] - sites.times[0]} ms apart")
    }

    @Test
    fun readsASourceFileFromAnyHttpsAddressAndNotOverHttp() = runBlocking {
        val client = client(FakeSites { _, _ -> ok("{}") })

        assertEquals("{}", client.file("https://example.org/source.json".toHttpUrl()))
        val refused = assertFailsWith<WebSourceLimitException> { client.file("http://example.org/source.json".toHttpUrl()) }
        assertEquals(WebSourceLimitException.Kind.BLOCKED, refused.kind)
    }

    @Test
    fun aSourceFileTooLargeIsRefusedAsOver64Kb() = runBlocking {
        val client = client(FakeSites { _, _ -> ok("x".repeat(WebSources.MAX_FILE_BYTES + 2)) })

        assertEquals(WebSourceFile.Refused(WebSourceRefusal.TOO_LARGE, "64 KB"), client.sourceFile("https://example.org/big.json".toHttpUrl()))
    }

    @Test
    fun aListIsReadPageByPageUntilOneBringsNothingNew() = runBlocking {
        // Every page repeats the first, as a site past its last page can.
        val sites = FakeSites { _, _ -> ok(text("lobsters_page.json")) }
        val client = client(sites)

        val first = client.list(lobsters, lobsters.feeds.first())
        assertEquals(listOf("abc", "def"), first.items.map { it.id })
        val second = client.list(lobsters, lobsters.feeds.first(), cursor = first.next, shown = first.items.map { it.key }.toSet())

        assertTrue(second.items.isEmpty())
        assertNull(second.next)
        assertEquals(listOf("/page/1.json", "/page/2.json"), sites.asked.map { it.encodedPath })
    }

    @Test
    fun aCursorListPassesItsCursorOn() = runBlocking {
        val lemmy = source(WebSourceFixtures.posts())
        val sites = FakeSites { _, asked -> ok(if (asked == 1) text("lemmy_page.json") else """{"posts": [], "next_page": null}""") }
        val client = client(sites)

        val first = client.list(lemmy, lemmy.feeds.single())
        val second = client.list(lemmy, lemmy.feeds.single(), cursor = first.next)

        assertEquals(listOf("", "Pa7"), sites.asked.map { it.queryParameter("page_cursor") })
        assertEquals(1, first.items.size)
        assertTrue(second.items.isEmpty())
        assertNull(second.next)
    }

    @Test
    fun aSearchIsReadWithItsQueryAndNeedsOne() = runBlocking {
        val sites = FakeSites { _, _ -> ok("[]") }

        client(sites).list(lobsters, lobsters.search!!, query = "dart & flutter")
        assertEquals("dart & flutter", sites.asked.single().queryParameter("q"))
        val refused = assertFailsWith<WebSourceLimitException> { client(sites).list(lobsters, lobsters.search!!) }
        assertEquals(WebSourceLimitException.Kind.BLOCKED, refused.kind)
    }

    @Test
    fun anArticleIsThePostsOwnHtmlElseThePageItsRuleNames() = runBlocking {
        val lemmy = source(WebSourceFixtures.posts())
        val sites = FakeSites { _, _ -> ok("""{"post_view": {"post": {"body": "<p>Full</p>"}}}""") }
        val client = client(sites)
        val post = WebSourcePages.read(lemmy, lemmy.feeds.single(), text("lemmy_page.json"), WebSourcePages.listUrl(lemmy.feeds.single())!!).items.single()

        assertEquals("<p>Mine</p>", client.article(lemmy, post.copy(html = "<p>Mine</p>")))
        assertTrue(sites.asked.isEmpty())
        assertEquals("<p>Full</p>", client.article(lemmy, post))
        assertEquals("https://lemmy.world/api/v3/post?id=7", sites.asked.single().toString())
        assertNull(client.article(lobsters, post), "a source without an article rule")
    }

    @Test
    fun anArticleOffTheSourcesSitesIsNotRead() = runBlocking {
        val sites = FakeSites { _, _ -> ok("<div class=\"body\">x</div>") }
        val tildes = source(WebSourceFixtures.html()).let { it.copy(article = WebSourceArticle("{link}", WebSourcePageType.HTML, "div.body")) }
        val item = WebSourceItem(sourceId = "tildes", id = "1", title = "t", link = "https://elsewhere.org/1", values = mapOf("link" to "https://elsewhere.org/1"))

        assertNull(client(sites).article(tildes, item))
        assertTrue(sites.asked.isEmpty())
    }

    @Test
    fun theCatalogueIsReadFromXtasPublishedList() = runBlocking {
        val sites = FakeSites { _, _ -> ok(text("published/sources.json")) }

        val listings = client(sites).catalogue()

        assertEquals("https://raw.githubusercontent.com/Aimdi/XTA/HEAD/sources.json", sites.asked.single().toString())
        assertEquals(listOf("lobsters", "devto", "lemmy-world", "nasa-images", "tildes"), listings.map { it.id })
    }

    @Test
    fun anUpdateIsReadFromWhereTheSourceCameAndAFailureChangesNothing() = runBlocking {
        val installed = InstalledWebSource(lobsters, agreedHosts = listOf("lobste.rs"), origin = "https://example.org/lobsters.json".toHttpUrl())
        val newer = WebSourceFixtures.links(version = 2).toString()

        val updated = client(FakeSites { _, _ -> ok(newer) }).update(installed)
        assertEquals(2L, updated?.source?.version)
        assertNull(client(FakeSites { _, _ -> XResponse(404, "") }).update(installed))
        assertNull(client(FakeSites { _, _ -> throw IOException("offline") }).update(installed))
        assertNull(client(FakeSites { _, _ -> ok(newer) }).update(installed.copy(origin = null)))
    }
}
