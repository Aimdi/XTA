package com.aimdi.xtanative.core.x.client

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.XAccount
import com.aimdi.xtanative.core.x.XAnswerException
import com.aimdi.xtanative.core.x.XClient
import com.aimdi.xtanative.core.x.XGuest
import com.aimdi.xtanative.core.x.XNoAccountException
import com.aimdi.xtanative.core.x.XSigners
import com.aimdi.xtanative.core.x.XUserUnavailableException
import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XRequestBudget
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.core.x.http.withRequestBudget
import com.aimdi.xtanative.core.x.parse.XParser
import com.aimdi.xtanative.core.x.signing.SigningUnavailableException
import com.aimdi.xtanative.core.x.signing.TransactionSigner
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * What XTA's lib/client does that XTA Native must do alike: search results
 * chained by conversation, one deadline per read, guest reads as XTA sends
 * them, and the errors a screen tells apart.
 */
class XClientParityTest {
    private val account = XAccount("id-a", "a", mapOf("Cookie" to "auth_token=a; ct0=a"))
    private var derived = 0
    private val signers get() = XSigners({ derived++; TransactionSigner(IntArray(48) { it }, "animation") })

    private fun client(x: XHttp, accounts: List<XAccount> = listOf(account), timeout: Long = XRequestBudget.READ_MILLIS, guest: XGuest? = null) =
        XClient(x, { accounts }, signers, guest = guest, requestTimeoutMillis = timeout)

    private fun answering(body: String) = XHttp { _, _, _ -> XResponse(200, body) }

    private fun tweet(id: String, conversation: String, replyTo: String? = null, handle: String = "a") =
        """{"__typename":"Tweet","rest_id":"$id","core":{"user_results":{"result":{"rest_id":"9","core":{"screen_name":"$handle","name":"A"}}}},
            "legacy":{"id_str":"$id","full_text":"t$id","conversation_id_str":"$conversation"${replyTo?.let { ""","in_reply_to_status_id_str":"$it","in_reply_to_screen_name":"a"""" }.orEmpty()}}}"""

    private fun entry(id: String, conversation: String, replyTo: String? = null) =
        """{"entryId":"tweet-$id","sortIndex":"$id","content":{"itemContent":{"tweet_results":{"result":${tweet(id, conversation, replyTo)}}}}}"""

    private val entries = listOf(entry("101", "100", replyTo = "100"), entry("200", "200"), entry("100", "100"))

    private fun searchBody(entries: List<String>) =
        """{"data":{"search_by_raw_query":{"search_timeline":{"timeline":{"instructions":[{"type":"TimelineAddEntries","entries":[${entries.joinToString(",")}]}]}}}}}"""

    // ---- conversations ----

    @Test
    fun aSearchChainsThePostsOfOneConversationNewestConversationFirst() = runBlocking {
        val page = client(answering(searchBody(entries))).search("cats")
        assertEquals(listOf("200", "100"), page.threads.map { it.id })
        assertEquals(listOf("100", "101"), page.threads[1].posts.map { it.id }, "a conversation's posts oldest first")
    }

    @Test
    fun aSearchWithoutRepliesLeavesEveryReplyOut() = runBlocking {
        val page = client(answering(searchBody(entries))).search("cats", includeReplies = false)
        assertEquals(listOf(listOf("200"), listOf("100")), page.threads.map { thread -> thread.posts.map { it.id } })
    }

    @Test
    fun quotesAreNeverFoldedIntoConversations() = runBlocking {
        val page = client(answering(searchBody(entries))).quotes("1")
        assertEquals(listOf("200", "101", "100"), page.threads.map { it.id }, "each quote its own row, newest first")
    }

    @Test
    fun aCommunityChainsItsPostsAndKeepsItsPinnedPostOnTop() = runBlocking {
        val pin = """{"type":"TimelinePinEntry","entry":${entry("50", "50")}}"""
        val body = """{"data":{"communityResults":{"result":{"ranked_community_timeline":{"timeline":{"instructions":[
            {"type":"TimelineAddEntries","entries":[${entries.joinToString(",")}]},$pin]}}}}}}"""
        val page = client(answering(body)).communityPosts("7")
        assertEquals(listOf("50", "200", "100"), page.threads.map { it.id })
        assertEquals(setOf("50"), page.pinnedIds)
        assertEquals(2, page.threads[2].posts.size)
    }

    @Test
    fun conversationIdsCompareAsNumbers() {
        val page = XParser.timeline(J.parse(searchBody(listOf(entry("99", "99"), entry("100", "100")))))
        assertEquals(listOf("100", "99"), XParser.conversations(page).threads.map { it.id })
    }

    // ---- one deadline per read ----

    @Test
    fun aReadGivesUpAfterItsOneDeadlineHoweverManyAccountsItTries() {
        var asked = 0
        val slow = XHttp { _, _, _ -> asked++; delay(150); XResponse(404, "") }
        val started = System.nanoTime()
        assertFailsWith<SocketTimeoutException> {
            runBlocking { client(slow, listOf(account, XAccount("id-b", "b", mapOf("Cookie" to "auth_token=b"))), timeout = 200).profile("x") }
        }
        assertTrue((System.nanoTime() - started) / 1_000_000 < 1_000, "the deadline is shared, not one per account")
        assertEquals(2, asked)
    }

    @Test
    fun aSignerIsDerivedWithinTwelveSecondsAndATimeoutIsTriedAgainNextTime() {
        var attempts = 0
        val signers = XSigners(derive = { attempts++; delay(10_000); TransactionSigner(IntArray(48), "a") }, timeoutMillis = 50)
        repeat(2) { assertFailsWith<SocketTimeoutException> { runBlocking { signers.forSession(null) } } }
        assertEquals(2, attempts, "a timeout is no broken signer: it is not held for the cooldown")
        assertEquals(12_000L, XRequestBudget.SIGNING_MILLIS)
    }

    @Test
    fun aSignerDerivedInsideARequestHasNoMoreThanTheRequestsTimeLeft() {
        var budget = 0L
        val signers = XSigners(derive = { budget = currentCoroutineContext()[XRequestBudget]!!.timeoutMillis; TransactionSigner(IntArray(48), "a") })
        runBlocking { withRequestBudget(1_000, "read") { signers.forSession(null) } }
        assertTrue(budget in 1..1_000)
    }

    private class Counting(val answer: (Int) -> Response) {
        var calls = 0
        val http = OkHttpXHttp(OkHttpClient.Builder().addInterceptor { chain -> answer(++calls).newBuilder().request(chain.request()).build() }.build())
    }

    private fun response(code: Int) = Response.Builder()
        .request(okhttp3.Request.Builder().url("https://x.com/").build())
        .protocol(Protocol.HTTP_1_1).code(code).message("").body("".toResponseBody()).build()

    private val url = "https://x.com/i/api/graphql/q/Op".toHttpUrl()

    @Test
    fun everyReaderSharesOneClientSoConnectionsArePooled() {
        assertTrue(OkHttpXHttp.defaultClient === OkHttpXHttp.defaultClient)
        assertTrue(OkHttpXHttp.defaultClient.connectionPool === OkHttpXHttp.defaultClient.newBuilder().build().connectionPool)
    }

    @Test
    fun aFailedRequestIsTriedOnceMoreButNotOneThatTimedOut() = runBlocking {
        val blip = Counting { if (it == 1) throw IOException("reset") else response(200) }
        assertEquals(200, blip.http.get(url, emptyMap(), true).code)
        assertEquals(2, blip.calls)

        val stalled = Counting { throw SocketTimeoutException("timeout") }
        assertFailsWith<InterruptedIOException> { stalled.http.get(url, emptyMap(), true) }
        assertEquals(1, stalled.calls, "a timeout has spent the time a retry would need")

        val gateway = Counting { response(if (it == 1) 503 else 200) }
        assertEquals(200, gateway.http.get(url, emptyMap(), true).code)
    }

    @Test
    fun noRetryIsStartedWithoutTimeLeftForIt() = runBlocking {
        val blip = Counting { throw IOException("reset") }
        assertFailsWith<IOException> { withRequestBudget(150, "read") { blip.http.get(url, emptyMap(), true) } }
        assertEquals(1, blip.calls)
    }

    @Test
    fun theEndpointRegistryIsWaitedForTenSecondsAtMost() = runBlocking {
        var budget: Long? = null
        val x = object : XHttp {
            override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean): XResponse {
                budget = currentCoroutineContext()[XRequestBudget]?.timeoutMillis
                return XResponse(200, """{"version":1,"endpoints":{}}""")
            }
        }
        val applied = client(x).refreshEndpoints()
        assertEquals("""{"version":1,"endpoints":{}}""", applied)
        assertEquals(10_000L, budget)
    }

    // ---- guests ----

    private class GuestX(val code: Int, val body: String = "{}") : XHttp {
        val sent = ArrayList<Map<String, String>>()
        override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean): XResponse {
            sent += headers
            return XResponse(code, body)
        }

        override suspend fun post(url: HttpUrl, headers: Map<String, String>) = XResponse(200, """{"guest_token":"g1"}""")
    }

    @Test
    fun aGuestReadSendsOnlyWhatXtaSendsAndIsNotSigned() = runBlocking {
        val x = GuestX(200, javaClass.getResource("/x/user_modern.json")!!.readText())
        client(x, accounts = emptyList(), guest = XGuest(x)).profile("quax_tests")
        assertEquals(setOf("authorization", "x-guest-token", "x-twitter-active-user", "user-agent"), x.sent.single().keys)
        assertEquals(XGuest.BEARER, x.sent.single()["authorization"])
        assertTrue(XGuest.BEARER.startsWith("Bearer "))
        val agent = x.sent.single().getValue("user-agent")
        assertTrue(agent.startsWith("Mozilla/") && "Pragma" !in agent, "the user agent is the header's value, not a printed map")
        assertEquals(0, derived, "no signer is derived for a guest")
    }

    @Test
    fun aGuestThatIsRateLimitedOrRefusedNeedsAnAccount() {
        for (code in listOf(429, 404, 500)) {
            val x = GuestX(code)
            assertFailsWith<XNoAccountException>("$code") { runBlocking { client(x, accounts = emptyList(), guest = XGuest(x)).profile("x") } }
        }
    }

    // ---- signer failures ----

    @Test
    fun aSignerThatCannotBeDerivedIsSigningUnavailable() {
        val broken = XSigners(derive = { throw IllegalStateException("Couldn't get KEY_BYTE indices") })
        val error = assertFailsWith<SigningUnavailableException> { runBlocking { broken.forSession(null) } }
        assertIs<IllegalStateException>(error.cause)
        val read = XClient(answering("{}"), { listOf(account) }, XSigners(derive = { throw IndexOutOfBoundsException("frame") }))
        assertFailsWith<SigningUnavailableException> { runBlocking { read.profile("x") } }
    }

    // ---- profiles ----

    @Test
    fun aProfileErrorWinsOverTheAccountBesideIt() {
        val profile = javaClass.getResource("/x/user_modern.json")!!.readText()
        val withError = profile.trimEnd().removeSuffix("}") + ""","errors":[{"code":63,"message":"User has been suspended."}]}"""
        assertEquals(63, assertFailsWith<XAnswerException> { runBlocking { client(answering(withError)).profile("x") } }.errorCode)
        assertEquals(0, assertFailsWith<XAnswerException> { runBlocking { client(answering("""{"errors":[]}""")).profile("x") } }.errorCode)
    }

    @Test
    fun noAccountIsNotFoundAndAnUnavailableOneWithoutReasonIsNoSuspension() {
        assertNull(assertFailsWith<XUserUnavailableException> { runBlocking { client(answering("""{"data":{"user":{}}}""")).profile("x") } }.reason)
        val unavailable = assertFailsWith<XUserUnavailableException> {
            runBlocking { client(answering("""{"data":{"user":{"result":{"__typename":"UserUnavailable"}}}}""")).profile("x") }
        }
        assertEquals("UserUnavailable", unavailable.reason, "X's -1, not the not-found page")
    }

    // ---- parsing ----

    @Test
    fun altTextFallsBackToTheMediasDescription() {
        val media = XParser.mediaItem(J.parse("""{"media_url_https":"https://pbs.twimg.com/a.jpg","type":"photo","additional_media_info":{"description":"A cat"}}"""))!!
        assertEquals("A cat", media.alt)
        val both = XParser.mediaItem(J.parse("""{"media_url_https":"u","ext_alt_text":"Alt","additional_media_info":{"description":"A cat"}}"""))!!
        assertEquals("Alt", both.alt)
        assertNull(XParser.mediaItem(J.parse("""{"media_url_https":"u","type":"photo"}"""))!!.alt, "a picture without a description has none")
    }

    @Test
    fun anAccountWithoutAHandleAndItsPostsAreKept() {
        val user = XParser.user(J.parse("""{"rest_id":"9","core":{"name":"Nameless"}}"""))!!
        assertEquals("", user.screenName)
        val post = XParser.post(J.parse(tweet("5", "5", handle = "").replace(""""screen_name":"",""", "")))!!
        assertEquals("", post.author.screenName)
        assertEquals("https://x.com/i/status/5", post.url)
    }
}
