package com.aimdi.xtanative.networks.pixiv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer

class PixivSauceNaoTest {
    @Test
    fun theHtmlPageGivesEachPixivWorkOnceWithSimilarityTitleAndArtist() {
        val matches = PixivSauceNao.parse(Fixtures.text("saucenao.html"))
        assertEquals(listOf(81234567L, 99887766L), matches.map { it.illustId })
        assertEquals(PixivSauceMatch(81234567, 94.57, "夏の空", "Hoshino"), matches.first())
        assertEquals(PixivSauceMatch(99887766, 71.2, "Night walk", "Tsuki"), matches.last())
    }

    @Test
    fun aPageWithoutResultBlocksStillGivesThePixivLinksOnIt() {
        val matches = PixivSauceNao.parse("""<p><a href="https://www.pixiv.net/artworks/42">x</a><a href="https://example.com/artworks/43">y</a></p>""")
        assertEquals(listOf(PixivSauceMatch(42)), matches)
    }

    @Test
    fun aJsonAnswerReadsPixivIdOrAnExtUrlAndSkipsOtherSites() {
        val body = """{"header": {"status": 0}, "results": [
            {"header": {"similarity": "93.10"}, "data": {"pixiv_id": 555, "title": "Snow", "member_name": "Yuki"}},
            {"header": {"similarity": 80}, "data": {"ext_urls": ["https://danbooru.donmai.us/posts/1", "https://www.pixiv.net/member_illust.php?illust_id=777"], "author_name": "Kaze"}},
            {"header": {"similarity": "70"}, "data": {"ext_urls": ["https://danbooru.donmai.us/posts/2"]}},
            {"header": null, "data": "reshaped"}]}"""
        val matches = PixivSauceNao.parse(body)
        assertEquals(listOf(PixivSauceMatch(555, 93.1, "Snow", "Yuki"), PixivSauceMatch(777, 80.0, "", "Kaze")), matches)
    }

    @Test
    fun brokenJsonAndEmptyPagesGiveNoMatches() {
        assertTrue(PixivSauceNao.parse("""{"results": [""").isEmpty())
        assertTrue(PixivSauceNao.parse("").isEmpty())
    }

    /** saucenao.com as an interceptor answers it, keeping what was asked. */
    private fun saucenao(code: Int, body: String, asked: MutableList<Request>): OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
        asked += chain.request()
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("answer")
            .body(body.toResponseBody("text/html; charset=utf-8".toMediaType())).build()
    }.build()

    @Test
    fun thePictureGoesUpAsAFormToSauceNaoAloneAndItsMatchesAreRead(): Unit = runBlocking {
        val asked = mutableListOf<Request>()
        val matches = PixivSauceNao.search(byteArrayOf(1, 2, 3), saucenao(200, Fixtures.text("saucenao.html"), asked))
        assertEquals(listOf(81234567L, 99887766L), matches.map { it.illustId })
        val request = asked.single()
        assertEquals("POST", request.method)
        assertEquals(PixivSauceNao.SEARCH_URL, request.url.toString())
        assertNull(request.header("Authorization"), "no Pixiv token goes with it")
        val form = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
        assertTrue("name=\"file\"; filename=\"image.png\"" in form, form)
        assertTrue("Content-Type: image/png" in form, form)
    }

    @Test
    fun aRateLimitIsSaidSoAndAnyOtherRefusalIsAnAnswerOfNoUse(): Unit = runBlocking {
        val limited = runCatching { PixivSauceNao.search(byteArrayOf(1), saucenao(429, "", mutableListOf())) }.exceptionOrNull()
        assertEquals(PixivFailure.RATE_LIMITED, PixivFailure.of(limited!!))
        val refused = runCatching { PixivSauceNao.search(byteArrayOf(1), saucenao(403, "", mutableListOf())) }.exceptionOrNull()
        assertEquals(PixivFailure.BAD_RESPONSE, PixivFailure.of(refused!!), "a refusal of SauceNAO's says nothing of the Pixiv account")
    }

    @Test
    fun idsComeFromArtworksOrIllustIdOnPixivAlone() {
        assertEquals(123L, PixivSauceNao.idFromLink("https://www.pixiv.net/artworks/123"))
        assertEquals(124L, PixivSauceNao.idFromLink("https://www.pixiv.net/en/artworks/124?foo=1"))
        assertEquals(125L, PixivSauceNao.idFromLink("https://www.pixiv.net/member_illust.php?mode=medium&illust_id=125"))
        for (link in listOf("https://pixiv.net/i/126", "https://evil.example/artworks/127", "https://www.pixiv.net/artworks/abc", "not a link")) {
            assertNull(PixivSauceNao.idFromLink(link), link)
        }
    }
}
