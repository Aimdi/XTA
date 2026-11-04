package com.aimdi.xtanative.networks.deepmarks

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.NetworkRequestException
import java.io.IOException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl

/** The rules XTA's deepmarks_client_test encodes, against a fake Deepmarks answering from fixtures. */
class DeepmarksClientTest {
    private val secretHex = "67dea2ed018072d675f5415ecfaed7d2597555e202d85b3d65ea4e58d2d92ffa"
    private val publicHex = "7e7e9c42a91bfef19fa929e5fda1b72e0ebc1a4c1141673e2794234d86addf4e"

    private class Sent(val method: String, val url: HttpUrl, val headers: Map<String, String>, val body: String?, val contentType: String?)

    /** Stands in for the Deepmarks API, answering each request with [answer]. */
    private class FakeDeepmarks(private val answer: (Sent) -> XResponse) : XHttp {
        val sent = mutableListOf<Sent>()

        override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean) = reply(Sent("GET", url, headers, null, null))

        override suspend fun post(url: HttpUrl, headers: Map<String, String>, body: String, contentType: String) =
            reply(Sent("POST", url, headers, body, contentType))

        private fun reply(request: Sent): XResponse {
            sent += request
            return answer(request)
        }
    }

    private fun fixture(name: String) = javaClass.getResource("/deepmarks/$name")!!.readText()

    private fun json(body: String, code: Int = 200) = XResponse(code, body, mapOf("content-type" to listOf("application/json")))

    private fun account(base: String = "", key: String = "k") = DeepmarksAccount.of(base, key)!!

    private fun event() = Nostr.signWebBookmark(
        secretKeyHex = secretHex,
        url = "https://x.com/jack/status/20",
        title = "Jack (@jack): just setting up my twttr",
        now = Instant.parse("2026-07-25T00:00:00Z"),
    )

    private fun failure(block: suspend () -> Unit): Throwable = runCatching { runBlocking { block() } }.exceptionOrNull()!!

    @Test
    fun theSignedEventIsPostedToTheDocumentedEndpoint() = runBlocking {
        val http = FakeDeepmarks { json(fixture("publish.json")) }
        val event = event()
        val result = DeepmarksClient(http).publishBookmark(account(key = "dmk_live_x"), event)

        assertEquals("44611071971bc0eb5ce9be9b7856ef0dab41a752ffefb4c9690e80a0815d791b", result.eventId)
        assertEquals(listOf("wss://relay.deepmarks.org", "wss://relay.damus.io"), result.publishedTo)
        assertEquals(listOf("wss://nos.lol"), result.failedRelays)
        assertFalse(result.reachedNoRelay, "one relay holding it is enough")

        val sent = http.sent.single()
        assertEquals("POST", sent.method)
        assertEquals("https://api.deepmarks.org/api/v1/bookmarks", sent.url.toString())
        assertEquals("Bearer dmk_live_x", sent.headers["Authorization"])
        assertEquals("application/json", sent.contentType)
        // The body is the complete signed event the API requires.
        val body = J.parse(sent.body!!)
        assertEquals(setOf("id", "pubkey", "created_at", "kind", "tags", "content", "sig"), body.keys)
        assertEquals(39701, body["kind"].int)
        assertEquals(publicHex, body["pubkey"].string)
        assertEquals(listOf("d", "https://x.com/jack/status/20"), body["tags"][0].list.map { it.string })
        assertEquals(event.toJson().toString(), sent.body)
    }

    @Test
    fun aCustomApiBaseForSelfHostedOrLocalInstancesIsHonoured() = runBlocking {
        val http = FakeDeepmarks { json("""{"eventId":"abc"}""") }
        val result = DeepmarksClient(http).publishBookmark(account(base = "http://localhost:4000"), event())
        assertEquals("http://localhost:4000/api/v1/bookmarks", http.sent.single().url.toString())
        assertEquals("abc", result.eventId)
    }

    @Test
    fun anAnswerWithoutAnEventIdKeepsTheEventsOwn() = runBlocking {
        val event = event()
        assertEquals(event.id, DeepmarksClient(FakeDeepmarks { json("{}") }).publishBookmark(account(), event).eventId)
    }

    @Test
    fun anEventThatReachedTheApiButNoRelayIsFlagged() = runBlocking {
        val result = DeepmarksClient(FakeDeepmarks { json(fixture("publish_no_relay.json")) }).publishBookmark(account(), event())
        assertTrue(result.reachedNoRelay)
    }

    @Test
    fun noApiKeyIsNoAccountSoNothingCallsOut() {
        assertNull(DeepmarksAccount.of("", "   "))
        assertNull(DeepmarksAccount.of("http://localhost:4000", ""))
    }

    @Test
    fun theApiKeyIsSentTrimmed() = runBlocking {
        val http = FakeDeepmarks { json(fixture("bookmarks.json")) }
        DeepmarksClient(http).verify(account(key = "  dmk_live_x\n"))
        assertEquals("Bearer dmk_live_x", http.sent.single().headers["Authorization"])
    }

    @Test
    fun eachDocumentedStatusMeansSomethingActionable() {
        val cases = mapOf(
            400 to DeepmarksProblem.REJECTED,
            401 to DeepmarksProblem.UNAUTHORIZED,
            402 to DeepmarksProblem.NOT_LIFETIME_MEMBER,
            403 to DeepmarksProblem.KEY_MISMATCH,
            429 to DeepmarksProblem.RATE_LIMITED,
            503 to DeepmarksProblem.BAD_SERVER,
        )
        for ((code, problem) in cases) {
            val error = failure { DeepmarksClient(FakeDeepmarks { json("""{"error":"nope"}""", code) }).publishBookmark(account(), event()) }
            assertEquals(code, (error as NetworkRequestException).code)
            assertEquals(DeepmarksClient.NETWORK, error.network)
            assertEquals(problem, DeepmarksProblem.of(error), "HTTP $code")
        }
    }

    @Test
    fun anHtmlPageIsAWrongAddressNotASuccess() {
        val page = XResponse(200, "<html>nginx</html>", mapOf("content-type" to listOf("text/html")))
        val error = failure { DeepmarksClient(FakeDeepmarks { page }).publishBookmark(account("https://wrong.example.com"), event()) }
        assertEquals(DeepmarksProblem.BAD_SERVER, DeepmarksProblem.of(error))
    }

    @Test
    fun anUnreachableHostIsANetworkFailure() {
        val error = failure { DeepmarksClient(FakeDeepmarks { throw IOException("No route to host") }).publishBookmark(account(), event()) }
        assertEquals(DeepmarksProblem.NETWORK, DeepmarksProblem.of(error))
    }

    @Test
    fun aBaseThatIsNotAnAddressIsANetworkFailureAsXtasFailedRequestWas() {
        val http = FakeDeepmarks { json("{}") }
        for (base in listOf("not a url", "localhost:4000", "ftp://deepmarks.example.org")) {
            val error = failure { DeepmarksClient(http).publishBookmark(account(base), event()) }
            assertTrue(error is IOException, base)
            assertEquals(DeepmarksProblem.NETWORK, DeepmarksProblem.of(error), base)
        }
        assertTrue(http.sent.isEmpty())
    }

    @Test
    fun verifyingGivesTheAccountsPubkeyWhenABookmarkRevealsIt() = runBlocking {
        val http = FakeDeepmarks { json(fixture("bookmarks.json")) }
        assertEquals(publicHex, DeepmarksClient(http).verify(account()))

        val sent = http.sent.single()
        assertEquals("GET", sent.method)
        assertEquals("/api/v1/bookmarks", sent.url.encodedPath)
        assertEquals("1", sent.url.queryParameter("limit"))
    }

    @Test
    fun verifyingAnAccountWithNoBookmarksYetIsNullNotAMismatch() = runBlocking {
        assertNull(DeepmarksClient(FakeDeepmarks { json("""{"bookmarks":[],"count":0,"mode":"list"}""") }).verify(account()))
    }

    @Test
    fun verifyingSurfacesTheLifetimeTierRequirement() {
        val error = failure {
            DeepmarksClient(FakeDeepmarks { json("""{"error":"api access is available to lifetime-tier members"}""", 402) }).verify(account())
        }
        assertEquals(DeepmarksProblem.NOT_LIFETIME_MEMBER, DeepmarksProblem.of(error))
    }
}
