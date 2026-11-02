package com.aimdi.xtanative.networks.ehviewer

import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.NetworkRequestException
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** XTA's test/eh_client_test.dart, and each read's URL, headers, pagination and failures. */
class EhClientTest {
    private val account = EhSettings(cookies = "ipb_member_id=1; ipb_pass_hash=abc")
    private val imageHtml = """<img id="img" src="https://cdn.example/page.webp" />"""
    private val listHtml = """<table><tr><td class="gl1c glcat"><div class="cn ct2">Manga</div></td>""" +
        """<td class="gl2c"><div class="glthumb"><div><img src="https://ehgt.org/a.webp" /></div><div><div>2 pages</div></div></div></td>""" +
        """<td class="gl3c glname"><a href="https://e-hentai.org/g/1/abc/"><div class="glink">T</div></a></td></tr></table>""" +
        """<a id="unext" href="https://e-hentai.org/?next=1">&gt;</a>"""

    private fun site(body: String) = FakeEh { XResponse(200, body) }

    private fun client(site: FakeEh, settings: EhSettings = account) = EhClient(site) { settings }

    @Test
    fun imagePagesAskForTheLargestResamplesAsADesktopBrowser() = runBlocking<Unit> {
        val site = site(imageHtml)
        val client = client(site)

        val page = client.imagePage(gid = 9, pageToken = "tok", page = 1)

        assertEquals("https://cdn.example/page.webp", page.imageUrl)
        val sent = site.asked.single()
        assertEquals("https://e-hentai.org/s/tok/9-1", sent.url.toString())
        assertEquals(EhClient.USER_AGENT, sent.headers["User-Agent"])
        assertFalse(sent.headers["User-Agent"]!!.contains("Mobile"))
        assertTrue(sent.headers["Cookie"]!!.contains("uconfig="))
        assertTrue(sent.headers["Cookie"]!!.contains("xr_2400"))
        assertTrue(sent.headers["Cookie"]!!.contains("ipb_member_id=1"))
        assertTrue(client.imageHeaders("https://e-hentai.org/fullimg.php?gid=9").getValue("Cookie").contains("xr_2400"))
        assertEquals("https://e-hentai.org/", client.imageHeaders(page.imageUrl)["Referer"])
    }

    @Test
    fun searchToplistAndWatchedHitTheSitesQueryShapes() = runBlocking<Unit> {
        val site = FakeEh { asked -> XResponse(200, if (asked.url.encodedPath.startsWith("/s/")) imageHtml else listHtml) }
        val client = client(site)

        client.search("flan", EhSearchFilters(minRating = 3, language = EhSearchLanguage.JAPANESE))
        client.toplist(EhToplistPeriod.YESTERDAY)
        client.watched()
        client.imagePage(gid = 1, pageToken = "tok", page = 2, reloadKey = "nl-1")

        val (search, toplist, watched, image) = site.asked.map { it.url }
        assertEquals("/", search.encodedPath)
        assertEquals("flan language:japanese", search.queryParameter("f_search"))
        assertEquals("0", search.queryParameter("f_cats"))
        assertEquals("on", search.queryParameter("f_sr"))
        assertEquals("3", search.queryParameter("f_srdd"))
        assertEquals("/toplist.php", toplist.encodedPath)
        assertEquals("11", toplist.queryParameter("tl"))
        assertEquals("/watched", watched.encodedPath)
        assertEquals("nl-1", image.queryParameter("nl"))
    }

    @Test
    fun theFrontPageAndASearchWithoutFiltersKeepToTheCategoriesChosen() = runBlocking<Unit> {
        val site = site(listHtml)
        val client = client(site, account.copy(categories = setOf(EhCategory.DOUJINSHI, EhCategory.MANGA)))

        val page = client.frontPage()
        client.search("flan")
        client.popular()

        val (front, search, popular) = site.asked.map { it.url }
        assertEquals("https://e-hentai.org/?f_cats=1017", front.toString())
        assertEquals("1017", search.queryParameter("f_cats"))
        assertNull(search.queryParameter("f_sr"))
        assertEquals("https://e-hentai.org/popular", popular.toString())
        assertEquals("T", page.items.single().title)
        assertEquals(2, page.items.single().pageCount)
    }

    @Test
    fun aListReadsOnFromTheLinkItsPageGaveAndNowhereElse() = runBlocking<Unit> {
        val site = site(listHtml)
        val client = client(site)

        val first = client.frontPage()
        client.frontPage(next = first.next)

        assertEquals("https://e-hentai.org/?next=1", site.asked.last().url.toString())
        assertFailsWith<IllegalArgumentException> { client.frontPage(next = "https://elsewhere.example/?next=1") }
        assertEquals(2, site.asked.size, "nothing was sent off the site")
    }

    @Test
    fun exhentaiIsReadSignedInAndChosen() = runBlocking<Unit> {
        val site = site(listHtml)
        client(site, account.copy(useExhentai = true)).popular()
        client(site, EhSettings(useExhentai = true)).popular()

        assertEquals(listOf("exhentai.org", "e-hentai.org"), site.asked.map { it.url.host })
        assertFalse("ipb_" in site.asked.last().headers.getValue("Cookie"), "a guest sends no account")
    }

    @Test
    fun aGalleryPageAsksForItsPreviewSheet() = runBlocking<Unit> {
        val site = site(galleryHtml())
        val client = client(site)

        val detail = client.galleryDetail(gid = 9, token = "tok")
        client.previewSheet(gid = 9, token = "tok", sheet = 2)
        val tile = client.previewForPage(gid = 9, token = "tok", page = 45)

        val (first, third, forPage) = site.asked.map { it.url }
        assertEquals("https://e-hentai.org/g/9/tok/", first.toString())
        assertEquals("2", third.queryParameter("p"))
        assertEquals("2", forPage.queryParameter("p"), "page 45 sits on sheet 2 of 20 a sheet")
        assertEquals("Sommer Book", detail.gallery.title)
        assertNull(tile, "the fixture's sheet holds pages 1 to 4")
        assertNull(client.previewForPage(gid = 9, token = "tok", page = 0))
        assertEquals(3, site.asked.size)
    }

    @Test
    fun failedAnswersAreTypedByTheirStatus() = runBlocking<Unit> {
        val kinds = listOf(401, 403, 404, 429, 500, 503).map { code ->
            val error = assertFailsWith<NetworkRequestException> { client(FakeEh { XResponse(code, "") }).popular() }
            assertEquals(EhClient.NETWORK, error.network)
            assertEquals(code, error.code)
            EhErrorKind.of(error)
        }
        assertEquals(
            listOf(
                EhErrorKind.UNAUTHORIZED,
                EhErrorKind.UNAUTHORIZED,
                EhErrorKind.NOT_FOUND,
                EhErrorKind.RATE_LIMITED,
                EhErrorKind.BAD_RESPONSE,
                EhErrorKind.BAD_RESPONSE,
            ),
            kinds,
        )
    }

    @Test
    fun aBanPageIsABanWhicheverPageItStandsFor() = runBlocking<Unit> {
        val client = client(site(fixture("banned.html")))
        val reads = listOf<suspend () -> Unit>(
            { client.frontPage() },
            { client.galleryDetail(9, "tok") },
            { client.imagePage(9, "tok", 1) },
        )
        reads.forEach { read ->
            val error = assertFailsWith<EhException> { read() }
            assertEquals(EhErrorKind.BANNED, error.kind)
        }
    }

    @Test
    fun aGalleryBehindTheContentWarningSaysSoAndAnyOtherPageThatDoesNotReadIsABadResponse() = runBlocking<Unit> {
        val warned = assertFailsWith<EhException> { client(site(fixture("content_warning.html")), account.copy(adultConsent = false)).galleryDetail(9, "tok") }
        assertEquals(EhErrorKind.CONTENT_WARNING, warned.kind)
        val signIn = assertFailsWith<EhException> { client(site("<p>Please log in</p>")).galleryDetail(9, "tok") }
        assertEquals(EhErrorKind.BAD_RESPONSE, signIn.kind)
        val noImage = assertFailsWith<EhException> { client(site("<p>Key missing</p>")).imagePage(9, "tok", 1) }
        assertEquals(EhErrorKind.BAD_RESPONSE, EhErrorKind.of(noImage))
    }

    @Test
    fun theConsentGoesWithEveryPageReadAndCanBeWithheld() = runBlocking<Unit> {
        val site = site(listHtml)
        client(site).popular()
        client(site, account.copy(adultConsent = false)).popular()

        val (given, withheld) = site.asked.map { it.headers.getValue("Cookie") }
        assertTrue(given.endsWith("uconfig=xr_2400-ts_l-nw_1; nw=1"))
        assertEquals("ipb_member_id=1; ipb_pass_hash=abc; uconfig=xr_2400-ts_l", withheld)
    }

    @Test
    fun galleryMetaPostsGdataToTheApiWithTheAccountsOwnCookies() = runBlocking<Unit> {
        val site = site(fixture("gdata.json"))

        val gallery = client(site).galleryMeta(gid = 1, token = "abc")

        val sent = site.asked.single()
        assertEquals("https://api.e-hentai.org/api.php", sent.url.toString())
        assertEquals("application/json", sent.contentType)
        assertEquals("""{"method":"gdata","gidlist":[[1,"abc"]],"namespace":1}""", sent.body)
        assertEquals("application/json", sent.headers["Accept"])
        assertEquals("ipb_member_id=1; ipb_pass_hash=abc", sent.headers["Cookie"], "the API gets the account, not the page settings")
        assertEquals(EhCategory.WESTERN, gallery?.category)
        assertNull(client(site("""{"gmetadata":[]}""")).galleryMeta(gid = 2, token = "x"))
    }

    @Test
    fun tagSuggestionsAskTheApiAndAnyFailureSuggestsNothing() = runBlocking<Unit> {
        val site = site(fixture("tagsuggest.json"))
        val guest = client(site, EhSettings())

        assertTrue(guest.suggestTags(" b ").isEmpty())
        assertTrue(site.asked.isEmpty(), "a single letter asks for nothing")
        assertEquals(listOf("female:big breasts", "parody:blue archive"), guest.suggestTags(" big b ").map { it.raw })
        assertEquals("""{"method":"tagsuggest","text":"big b"}""", site.asked.single().body)
        assertNull(site.asked.single().headers["Cookie"], "a guest sends no cookies to the API")
        assertTrue(client(FakeEh { XResponse(500, "") }).suggestTags("big b").isEmpty())
        assertTrue(client(site("<html>")).suggestTags("big b").isEmpty())
    }

    @Test
    fun noConnectionStaysTheIOExceptionItIs() = runBlocking<Unit> {
        val client = client(FakeEh { throw IOException("unreachable") })
        val error = assertFailsWith<IOException> { client.frontPage() }
        assertEquals(EhErrorKind.NETWORK, EhErrorKind.of(error))
    }
}
