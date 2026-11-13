package com.aimdi.xtanative.core.x.client

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.XAccount
import com.aimdi.xtanative.core.x.XClient
import com.aimdi.xtanative.core.x.XEndpoints
import com.aimdi.xtanative.core.x.XGuest
import com.aimdi.xtanative.core.x.XNoAccountException
import com.aimdi.xtanative.core.x.XRateLimitedException
import com.aimdi.xtanative.core.x.XRequestException
import com.aimdi.xtanative.core.x.XSigners
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.core.x.signing.TransactionSigner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl

class XClientTest {
    private val profileBody = javaClass.getResource("/x/user_modern.json")!!.readText()

    private fun account(name: String) = XAccount(
        id = "ct0-$name",
        screenName = name,
        headers = mapOf("Cookie" to "auth_token=$name; ct0=ct0-$name", "x-csrf-token" to "ct0-$name", "authorization" to "Bearer from-$name"),
    )

    private class FakeX(val answer: (Int, Map<String, String>) -> XResponse) : XHttp {
        val asked = ArrayList<Pair<HttpUrl, Map<String, String>>>()
        override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean): XResponse {
            asked += url to headers
            return answer(asked.size, headers)
        }
        val cookies get() = asked.map { it.second["Cookie"] }
    }

    private var now = 1_700_000_000_000L
    private var derived = 0

    private fun client(x: XHttp, accounts: List<XAccount>) = XClient(
        http = x,
        accounts = { accounts },
        signers = XSigners({ derived++; TransactionSigner(IntArray(48) { it }, "animation") }, clock = { now }),
        clock = { now },
    )

    @Test
    fun aReadCarriesTheSessionTheBearerAndASignature() = runBlocking {
        val x = FakeX { _, _ -> XResponse(200, profileBody) }
        val (user, _) = client(x, listOf(account("maya"))).profile("@quax_tests")
        assertEquals("quax_tests", user.screenName)

        val (url, headers) = x.asked.single()
        assertEquals("twitter.com", url.host)
        assertEquals("/i/api/graphql/AMIBMjtxEEATh4z8V9GtRg/UserByScreenName", url.encodedPath)
        assertTrue(url.queryParameter("variables")!!.contains("\"screen_name\":\"quax_tests\""))
        assertEquals("auth_token=maya; ct0=ct0-maya", headers["Cookie"])
        assertEquals("ct0-maya", headers["x-csrf-token"])
        assertEquals("Bearer from-maya", headers["authorization"], "the account's own header wins")
        assertTrue(headers["x-client-transaction-id"]!!.length > 40)
    }

    @Test
    fun aRefusingAccountHandsTheReadToTheNext() = runBlocking {
        val x = FakeX { call, _ -> if (call == 1) XResponse(403, "") else XResponse(200, profileBody) }
        client(x, listOf(account("a"), account("b"))).profile("quax_tests")
        assertEquals(listOf("auth_token=a; ct0=ct0-a", "auth_token=b; ct0=ct0-b"), x.cookies)
    }

    @Test
    fun aRateLimitedAccountIsSkippedUntilXSaysItResets() = runBlocking {
        val reset = (now / 1000 + 60).toString()
        val x = FakeX { _, headers ->
            if (headers["Cookie"]!!.startsWith("auth_token=a;") && now < 1_700_000_060_000L) {
                XResponse(429, "", mapOf("x-rate-limit-reset" to listOf(reset)))
            } else {
                XResponse(200, profileBody)
            }
        }
        val reader = client(x, listOf(account("a"), account("b")))
        reader.profile("quax_tests")
        reader.profile("quax_tests")
        assertEquals(listOf("auth_token=a; ct0=ct0-a", "auth_token=b; ct0=ct0-b", "auth_token=b; ct0=ct0-b"), x.cookies)

        now += 61_000
        reader.profile("quax_tests")
        assertEquals("auth_token=a; ct0=ct0-a", x.cookies.last(), "past the reset the account is asked again")
    }

    @Test
    fun everyAccountRateLimitedSaysSo() {
        val x = FakeX { _, _ -> XResponse(429, "") }
        assertFailsWith<XRateLimitedException> { runBlocking { client(x, listOf(account("a"), account("b"))).profile("quax_tests") } }
    }

    @Test
    fun noAccountSaysSo() {
        assertFailsWith<XNoAccountException> { runBlocking { client(FakeX { _, _ -> XResponse(200, "") }, emptyList()).profile("x") } }
    }

    @Test
    fun a404FromTheOnlyAccountIsSurfacedAtOnceWithoutDerivingTheSignerAgain() {
        val x = FakeX { _, _ -> XResponse(404, "") }
        val reader = client(x, listOf(account("a")))
        repeat(3) {
            val error = assertFailsWith<XRequestException> { runBlocking { reader.profile("quax_tests") } }
            assertEquals(404, error.code)
        }
        assertEquals(3, x.asked.size, "one request per read, as XTA's transport")
        assertEquals(1, derived, "a refusal never throws the signer away")
    }

    @Test
    fun aProfilesTabsAskForTheirOwnOperations() = runBlocking {
        val page = javaClass.getResource("/x/user_originals_page.json")!!.readText()
        val x = FakeX { _, _ -> XResponse(200, page) }
        val reader = client(x, listOf(account("a")))
        reader.userReposts("42")
        reader.userReplies("42", cursor = "c1")
        reader.userMedia("42")
        val (reposts, replies, media) = x.asked.map { it.first }
        assertTrue(reposts.encodedPath.endsWith("/UserRepostsTimeline"))
        assertTrue(replies.encodedPath.endsWith("/UserTweetsAndReplies"))
        assertTrue(replies.queryParameter("variables")!!.contains("\"withCommunity\":true"))
        assertTrue(replies.queryParameter("variables")!!.contains("\"cursor\":\"c1\""))
        assertTrue(media.encodedPath.endsWith("/UserMedia"))
        assertEquals(media.queryParameter("fieldToggles"), media.queryParameter("filedToggles"))
        assertTrue(reposts.queryParameter("variables")!!.contains("\"userId\":\"42\""))
    }

    @Test
    fun aServerErrorIsNotPassedToTheNextAccount() {
        val x = FakeX { _, _ -> XResponse(500, "") }
        val error = assertFailsWith<XRequestException> { runBlocking { client(x, listOf(account("a"), account("b"))).profile("quax_tests") } }
        assertEquals(500, error.code)
        assertEquals(1, x.asked.size)
    }

    @Test
    fun withoutAnAccountAGuestReadsWithItsOwnBearerAndToken() = runBlocking {
        var tokens = 0
        val x = object : XHttp {
            val asked = ArrayList<Map<String, String>>()
            override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean): XResponse {
                asked += headers
                return if (headers["x-guest-token"] == "g1") XResponse(403, "") else XResponse(200, profileBody)
            }
            override suspend fun post(url: HttpUrl, headers: Map<String, String>): XResponse {
                tokens++
                assertEquals(XGuest.BEARER, headers["authorization"])
                return XResponse(200, """{"guest_token":"g$tokens"}""")
            }
        }
        val reader = XClient(
            http = x,
            accounts = { emptyList() },
            signers = XSigners({ derived++; TransactionSigner(IntArray(48) { it }, "animation") }, clock = { now }),
            clock = { now },
            guest = XGuest(x, clock = { now }),
        )
        assertEquals("quax_tests", reader.profile("quax_tests").first.screenName)
        assertEquals(listOf("g1", "g2"), x.asked.map { it["x-guest-token"] }, "a refused token is replaced once")
        assertTrue(x.asked.all { it["authorization"] == XGuest.BEARER && it["Cookie"] == null })

        reader.profile("quax_tests")
        assertEquals(2, tokens, "the token that worked is kept")
    }

    @Test
    fun aGuestXDoesNotAnswerIsNoAccount() {
        val x = object : XHttp {
            override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean) = XResponse(404, "")
            override suspend fun post(url: HttpUrl, headers: Map<String, String>) = XResponse(200, """{"guest_token":"g"}""")
        }
        val reader = XClient(x, { emptyList() }, XSigners({ TransactionSigner(IntArray(48) { it }, "a") }), guest = XGuest(x))
        assertFailsWith<XNoAccountException> { runBlocking { reader.search("from:nasa") } }
    }

    @Test
    fun publishedIdsReplaceShippedOnesOnlyWhenTheyLookLikeIds() {
        val endpoints = XEndpoints()
        val taken = endpoints.applyOverrides(
            J.parse("""{"endpoints": {"UserByScreenName": "abcdefghijklmnop1234", "TweetDetail": "../../../evil", "Unknown": "abcdefghijklmnop1234"}}"""),
        )
        assertEquals(1, taken)
        assertEquals("/i/api/graphql/abcdefghijklmnop1234/UserByScreenName", endpoints.url(XEndpoints.USER_BY_SCREEN_NAME, emptyMap()).encodedPath)
        assertEquals(XEndpoints.shipped.getValue(XEndpoints.TWEET_DETAIL).queryId, endpoints.queryId(XEndpoints.TWEET_DETAIL))
    }
}
