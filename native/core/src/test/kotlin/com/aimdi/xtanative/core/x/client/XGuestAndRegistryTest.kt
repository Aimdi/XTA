package com.aimdi.xtanative.core.x.client

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.XClient
import com.aimdi.xtanative.core.x.XEndpoints
import com.aimdi.xtanative.core.x.XGuest
import com.aimdi.xtanative.core.x.XSigners
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.core.x.model.XConversation
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XTimelinePage
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.core.x.model.XWithheld
import com.aimdi.xtanative.core.x.signing.TransactionSigner
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl

/** XTA's guest_token_test.dart, its endpoint registry's refresh, and show_more_cursor_test.dart's reply checks. */
class XGuestAndRegistryTest {
    private var now = 1_700_000_000_000L
    private val window = 60_000L

    private class Activations : XHttp {
        var issued = 0
        override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean) = XResponse(404, "")
        override suspend fun post(url: HttpUrl, headers: Map<String, String>): XResponse {
            issued++
            return XResponse(200, """{"guest_token":"g$issued"}""")
        }
    }

    private fun quota(remaining: Int, resetAt: Long): (String) -> String? =
        mapOf("x-rate-limit-remaining" to "$remaining", "x-rate-limit-reset" to "${resetAt / 1000}", "x-rate-limit-limit" to "150")::get

    @Test
    fun aGuestTokenIsKeptWhileXSaysNothingOrItHasRequestsLeft() = runBlocking {
        val guest = XGuest(Activations(), clock = { now })
        assertEquals("g1", guest.token())
        assertEquals("g1", guest.token(), "no quota reported yet")
        guest.observe(quota(5, now + window))
        assertEquals("g1", guest.token(), "quota left inside the window")
    }

    @Test
    fun aSpentGuestTokenIsReplacedUntilItsWindowRollsOver() = runBlocking {
        val guest = XGuest(Activations(), clock = { now })
        guest.token()
        guest.observe(quota(0, now + window))
        assertEquals("g2", guest.token(), "spent inside the window: replaced before X refuses it")
        guest.observe(quota(0, now + window))
        now += window - 1
        assertEquals("g3", guest.token(), "a millisecond before the reset it is still spent")
        guest.observe(quota(0, now + 1))
        now += 1
        assertEquals("g3", guest.token(), "at the reset the quota has refilled")
    }

    @Test
    fun aQuotaXReportsOnlyInPartIsNotTrusted() = runBlocking {
        val guest = XGuest(Activations(), clock = { now })
        guest.token()
        guest.observe(mapOf("x-rate-limit-remaining" to "0", "x-rate-limit-reset" to "${(now + window) / 1000}")::get)
        assertEquals("g1", guest.token(), "without its limit X said too little")
    }

    @Test
    fun aGuestReadKeepsTheQuotaXReportsForTheNextRead() = runBlocking {
        val profile = javaClass.getResource("/x/user_modern.json")!!.readText()
        val x = object : XHttp {
            var tokens = 0
            val sentWith = ArrayList<String?>()
            override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean): XResponse {
                sentWith += headers["x-guest-token"]
                val spent = mapOf("x-rate-limit-remaining" to listOf("0"), "x-rate-limit-reset" to listOf("${(now + window) / 1000}"), "x-rate-limit-limit" to listOf("150"))
                return XResponse(200, profile, spent)
            }
            override suspend fun post(url: HttpUrl, headers: Map<String, String>) = XResponse(200, """{"guest_token":"g${++tokens}"}""")
        }
        val reader = XClient(x, { emptyList() }, XSigners({ TransactionSigner(IntArray(48) { it }, "a") }), clock = { now }, guest = XGuest(x, clock = { now }))
        reader.profile("quax_tests")
        reader.profile("quax_tests")
        assertEquals(listOf<String?>("g1", "g2"), x.sentWith)
    }

    // ---- the published registry ----

    private fun client(x: XHttp) = XClient(x, { emptyList() }, XSigners({ TransactionSigner(IntArray(48) { it }, "a") }))

    private class Answering(val answer: (HttpUrl) -> XResponse) : XHttp {
        override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean) = answer(url)
    }

    @Test
    fun aPublishedRegistryIsAppliedAndHandedBackToBeKept() = runBlocking {
        val file = """{"version":1,"endpoints":{"TweetDetail":"AAAAAAAAAAAAAAAAAAAAAA"}}"""
        val asked = ArrayList<HttpUrl>()
        val reader = client(Answering { url -> asked += url; XResponse(200, file) })
        assertEquals(file, reader.refreshEndpoints())
        assertEquals(XEndpoints.OVERRIDES_URL, asked.single().toString())
        assertEquals("AAAAAAAAAAAAAAAAAAAAAA", reader.endpoints.queryId(XEndpoints.TWEET_DETAIL))

        val cold = client(Answering { throw IOException("offline") })
        assertNull(cold.refreshEndpoints(), "a failure is no error")
        cold.endpoints.applyOverrides(J.parse(file))
        assertEquals("AAAAAAAAAAAAAAAAAAAAAA", cold.endpoints.queryId(XEndpoints.TWEET_DETAIL), "the kept file repairs a cold start")
    }

    @Test
    fun aRegistryIsOnlyReadOverHttpsAndOnlyFromAnAnswer() = runBlocking {
        var asked = 0
        val reader = client(Answering { asked++; XResponse(404, "{}") })
        assertNull(reader.refreshEndpoints("http://example.com/endpoints.json"))
        assertNull(reader.refreshEndpoints("not a url"))
        assertEquals(0, asked)
        assertNull(reader.refreshEndpoints("https://example.com/endpoints.json"))
        assertEquals(1, asked)
        assertFalse(reader.endpoints.isOverridden(XEndpoints.TWEET_DETAIL))
    }

    // ---- whether an opened post has replies ----

    private fun post(id: String) = XPost(id, XUser("9", "a", "A"), null, "t")

    private fun conversation(threads: List<XThread>, bottom: String? = null, showMore: String? = null) =
        XConversation.of(XTimelinePage(threads, bottom, null, showMoreCursor = showMore), "1")

    @Test
    fun aPageHasRepliesWhenTheyFollowThePostOrACursorCanLoadThem() {
        val focal = XThread("1", listOf(post("1")))
        assertTrue(conversation(listOf(focal), showMore = "X").hasRepliesOrMore, "a show-more cursor without reply threads")
        assertTrue(conversation(listOf(focal), bottom = "BOTTOM").hasRepliesOrMore)
        assertTrue(conversation(listOf(focal, XThread("2", listOf(post("2"))))).hasReplies)
        assertTrue(conversation(listOf(XThread("1", listOf(post("1"), post("2"))))).hasReplies, "stacked in the post's own thread")
        assertFalse(conversation(listOf(focal)).hasRepliesOrMore, "a page of the post alone is not worth keeping")
        assertFalse(conversation(listOf(XThread("0", listOf(post("0"))), focal)).hasReplies, "what the post answers is no reply")
    }

    @Test
    fun aPostXWithholdsKeepsItsPlaceAroundTheOpenedOne() {
        val gone = XWithheld("0", "This Post was deleted by the Post author.")
        val opened = conversation(listOf(XThread("0", emptyList(), listOf(gone)), XThread("1", listOf(post("1")))))
        assertEquals(listOf(gone), opened.withheldBefore)
        assertTrue(opened.before.isEmpty())

        val suspended = conversation(listOf(XThread("1", emptyList(), listOf(XWithheld("1", "Suspended"))), XThread("2", listOf(post("2")))))
        assertNull(suspended.focal)
        assertEquals("Suspended", suspended.focalWithheld?.reason)
        assertEquals(listOf("2"), suspended.replies.map { it.id }, "the replies still show under the post X withheld")
    }
}
