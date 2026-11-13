package com.aimdi.xtanative.networks.karakeep

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.NetworkRequestException
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** The rules XTA's karakeep_client_test encodes, against a fake Karakeep answering from fixtures. */
class KarakeepClientTest {
    private class Sent(val method: String, val url: HttpUrl, val headers: Map<String, String>, val body: String?, val contentType: String?)

    /** Stands in for the user's Karakeep, answering each request with [answer]. */
    private class FakeKarakeep(private val answer: (Sent) -> XResponse) : XHttp {
        val sent = mutableListOf<Sent>()

        override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean) = reply(Sent("GET", url, headers, null, null))

        override suspend fun post(url: HttpUrl, headers: Map<String, String>, body: String, contentType: String) =
            reply(Sent("POST", url, headers, body, contentType))

        private fun reply(request: Sent): XResponse {
            sent += request
            return answer(request)
        }
    }

    private fun fixture(name: String) = javaClass.getResource("/karakeep/$name")!!.readText()

    private fun json(body: String, code: Int) = XResponse(code, body, mapOf("content-type" to listOf("application/json; charset=utf-8")))

    private fun server(address: String = "k.example.com", key: String = "k") = KarakeepServer.of(address, key)!!

    private fun failure(block: suspend () -> Unit): Throwable = runCatching { runBlocking { block() } }.exceptionOrNull()!!

    private suspend fun savedBody(title: String? = null, note: String? = null): J {
        val http = FakeKarakeep { json("{}", 201) }
        KarakeepClient(http).saveLink(server(), "https://example.com", title, note)
        return J.parse(http.sent.single().body!!)
    }

    @Test
    fun aLongTitleIsCutWithoutBreakingAnEmojiInTwo() {
        val emoji = "😀"
        val title = "a".repeat(KarakeepClient.MAX_TITLE_LENGTH - 1) + emoji + "tail"
        assertEquals("a".repeat(KarakeepClient.MAX_TITLE_LENGTH - 1), cutTitle(title))
        assertEquals("b".repeat(KarakeepClient.MAX_TITLE_LENGTH), cutTitle("b".repeat(KarakeepClient.MAX_TITLE_LENGTH + 5)))
        assertEquals("short $emoji", cutTitle("short $emoji"))
    }

    @Test
    fun aMissingSchemeIsAssumedToBeHttps() {
        assertEquals("https://karakeep.example.com".toHttpUrl(), karakeepBaseUrl("karakeep.example.com"))
    }

    @Test
    fun anExplicitSchemeHostAndPortAreKept() {
        assertEquals("http://192.168.1.10:3000".toHttpUrl(), karakeepBaseUrl("http://192.168.1.10:3000"))
    }

    @Test
    fun aTrailingSlashAndTheApiPathCopiedFromTheDocsAreDropped() {
        for (raw in listOf("https://k.example.com/", "https://k.example.com/api", "https://k.example.com/api/v1", "https://k.example.com/api/v1/")) {
            assertEquals("https://k.example.com".toHttpUrl(), karakeepBaseUrl(raw), raw)
        }
    }

    @Test
    fun aPathPrefixFromAReverseProxyIsKept() {
        assertEquals("https://home.example.com/karakeep".toHttpUrl(), karakeepBaseUrl("https://home.example.com/karakeep/api/v1"))
    }

    @Test
    fun whatIsNotAServerAddressIsRefused() {
        for (raw in listOf("", "   ", "ftp://k.example.com", "not a url")) {
            assertNull(karakeepBaseUrl(raw), raw)
        }
    }

    @Test
    fun missingConfigurationIsNoServerSoNoRequestIsMade() {
        assertNull(KarakeepServer.of("", ""))
        assertNull(KarakeepServer.of("k.example.com", "  "))
        assertNull(KarakeepServer.of("not a url", "k"))
    }

    @Test
    fun aLinkBookmarkIsPostedWithTheApiKey() = runBlocking {
        val http = FakeKarakeep { json(fixture("bookmark_created.json"), 201) }
        val result = KarakeepClient(http).saveLink(server("karakeep.example.com", "secret"), "https://x.com/jack/status/20", "Jack (@jack): just setting up my twttr")

        assertEquals(KarakeepSaveResult(KarakeepSaveOutcome.SAVED, "ieidlxygmwj87oxz5hxttoc8"), result)
        val sent = http.sent.single()
        assertEquals("POST", sent.method)
        assertEquals("https://karakeep.example.com/api/v1/bookmarks", sent.url.toString())
        assertEquals("Bearer secret", sent.headers["Authorization"])
        assertEquals("application/json", sent.headers["Accept"])
        assertEquals("application/json", sent.contentType)
        assertEquals("""{"type":"link","url":"https://x.com/jack/status/20","title":"Jack (@jack): just setting up my twttr"}""", sent.body)
    }

    @Test
    fun a200MeansKarakeepAlreadyHadTheUrl() = runBlocking {
        val http = FakeKarakeep { json(fixture("bookmark_existing.json"), 200) }
        val result = KarakeepClient(http).saveLink(server("https://karakeep.example.com", "secret"), "https://example.com")
        assertEquals(KarakeepSaveResult(KarakeepSaveOutcome.ALREADY_SAVED, "q2w8e4r6t0y3u5i7o9p1a2s4"), result)
    }

    @Test
    fun aReverseProxyPathPrefixIsRespected() = runBlocking {
        val http = FakeKarakeep { json("{}", 201) }
        KarakeepClient(http).saveLink(server("https://home.example.com/karakeep"), "https://example.com")
        assertEquals("https://home.example.com/karakeep/api/v1/bookmarks", http.sent.single().url.toString())
    }

    @Test
    fun anEmptyTitleIsLeftOutRatherThanSentBlank() = runBlocking {
        assertFalse("title" in savedBody(title = "   ").keys)
    }

    @Test
    fun anOverLongTitleIsCutToWhatKarakeepAccepts() = runBlocking {
        assertEquals(KarakeepClient.MAX_TITLE_LENGTH, savedBody(title = "x".repeat(900))["title"].string!!.length)
    }

    @Test
    fun aNoteIsSentTrimmedAndOnlyWhenItSaysSomething() = runBlocking {
        assertEquals("from XTA", savedBody(note = "  from XTA ")["note"].string)
        assertFalse("note" in savedBody(note = " ").keys)
    }

    @Test
    fun aRejectedKeyIsReportedAsSuchNotAsAGenericFailure() {
        val error = failure { KarakeepClient(FakeKarakeep { json("""{"error":"unauthorized"}""", 401) }).saveLink(server(key = "bad"), "https://example.com") }
        assertEquals(401, (error as NetworkRequestException).code)
        assertEquals(KarakeepClient.NETWORK, error.network)
        assertEquals(KarakeepProblem.UNAUTHORIZED, KarakeepProblem.of(error))
    }

    @Test
    fun anHtmlPageMeansTheAddressIsNotAKarakeep() {
        val page = XResponse(200, "<!doctype html><html><body>nginx</body></html>", mapOf("content-type" to listOf("text/html")))
        val error = failure { KarakeepClient(FakeKarakeep { page }).saveLink(server("wrong.example.com"), "https://example.com") }
        assertTrue(error is NotKarakeepException)
        assertEquals(KarakeepProblem.BAD_SERVER, KarakeepProblem.of(error))
    }

    @Test
    fun aDeadHostIsReportedAsANetworkProblem() {
        val error = failure { KarakeepClient(FakeKarakeep { throw IOException("No route to host") }).saveLink(server(), "https://example.com") }
        assertEquals(KarakeepProblem.NETWORK, KarakeepProblem.of(error))
    }

    @Test
    fun karakeepsOwnErrorIsAServerProblem() {
        val error = failure { KarakeepClient(FakeKarakeep { json("""{"code":"INTERNAL_SERVER_ERROR"}""", 500) }).saveLink(server(), "https://example.com") }
        assertEquals(KarakeepProblem.SERVER, KarakeepProblem.of(error))
    }

    @Test
    fun verifyingPassesWhenTheBookmarksEndpointAnswers() = runBlocking {
        val http = FakeKarakeep { json(fixture("bookmarks.json"), 200) }
        KarakeepClient(http).verify(server())

        val sent = http.sent.single()
        assertEquals("GET", sent.method)
        assertEquals("/api/v1/bookmarks", sent.url.encodedPath)
        assertEquals("1", sent.url.queryParameter("limit"))
        assertEquals("Bearer k", sent.headers["Authorization"])
    }

    @Test
    fun verifyingSurfacesABadKey() {
        val error = failure { KarakeepClient(FakeKarakeep { json("{}", 403) }).verify(server(key = "nope")) }
        assertEquals(KarakeepProblem.UNAUTHORIZED, KarakeepProblem.of(error))
    }
}
