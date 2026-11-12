package com.aimdi.xtanative.core.x.client

import com.aimdi.xtanative.core.x.XAccount
import com.aimdi.xtanative.core.x.XAccountHealth
import com.aimdi.xtanative.core.x.XAccountHealthStore
import com.aimdi.xtanative.core.feed.feedReads
import com.aimdi.xtanative.core.x.XClient
import com.aimdi.xtanative.core.x.XEndpointRefusedException
import com.aimdi.xtanative.core.x.XNoWorkingAccountException
import com.aimdi.xtanative.core.x.XQuoteOrder
import com.aimdi.xtanative.core.x.XRateLimitedException
import com.aimdi.xtanative.core.x.XRequestException
import com.aimdi.xtanative.core.x.XSigners
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.core.x.signing.TransactionSigner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl

/**
 * XTA's account-rotating fetch, as test/x_request_recovery_test.dart pins it:
 * which account is asked next, and what is held against which account.
 */
class XTransportTest {
    private val profile = javaClass.getResource("/x/user_modern.json")!!.readText()
    private var now = 1_700_000_000_000L
    private val health = XAccountHealthStore.inMemory()

    private fun account(name: String) = XAccount("id-$name", name, mapOf("Cookie" to "auth_token=$name; ct0=$name", "x-test-account" to name))

    private class FakeX(val answer: (call: Int, account: String) -> XResponse) : XHttp {
        val asked = ArrayList<String>()
        override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean): XResponse {
            asked += headers.getValue("x-test-account")
            return answer(asked.size, asked.last())
        }
    }

    private fun client(x: XHttp, accounts: List<XAccount>, disabled: Set<String> = emptySet()) = XClient(
        http = x,
        accounts = { accounts },
        signers = XSigners({ TransactionSigner(IntArray(48) { it }, "animation") }, clock = { now }),
        clock = { now },
        health = health,
        disabledAccounts = { disabled },
    )

    private fun read(client: XClient) = runBlocking { client.profile("quax_tests") }

    private fun ok() = XResponse(200, profile)

    private val resetIn5Minutes get() = mapOf("x-rate-limit-reset" to listOf("${(now + 300_000) / 1000}"))

    @Test
    fun a429RotatesToAnotherAccountAndRemembersTheLimitedOne() {
        val x = FakeX { call, _ -> if (call == 1) XResponse(429, "", resetIn5Minutes) else ok() }
        val reader = client(x, listOf(account("a"), account("b")))
        read(reader)
        assertEquals(2, x.asked.toSet().size)
        assertTrue(reader.rateLimits.isLimited("id-${x.asked.first()}", "/i/api/graphql/AMIBMjtxEEATh4z8V9GtRg/UserByScreenName", now))
    }

    @Test
    fun a404IsBlamedOnAnAccountOnlyAfterAnotherAccountIsAnswered() {
        val x = FakeX { call, _ -> if (call == 1) XResponse(404, "") else ok() }
        read(client(x, listOf(account("a"), account("b"))))
        assertEquals(1, health.read("id-${x.asked.first()}").consecutiveNotFound, "the second account proves the first 404 was its own")
        assertEquals(0, health.read("id-${x.asked.last()}").consecutiveNotFound)
    }

    @Test
    fun a401FromASignedOutAccountMovesOnToAnother() {
        val x = FakeX { call, _ -> if (call == 1) XResponse(401, """{"errors":[{"message":"Could not authenticate you","code":32}]}""") else ok() }
        read(client(x, listOf(account("a"), account("b"))))
        assertEquals(2, x.asked.toSet().size)
        assertEquals(1, health.read("id-${x.asked.first()}").consecutiveNotFound)
    }

    @Test
    fun a403FromALockedAccountMovesOnToAnother() {
        val x = FakeX { call, _ -> if (call == 1) XResponse(403, "") else ok() }
        read(client(x, listOf(account("a"), account("b"))))
        assertEquals(2, x.asked.toSet().size)
        assertEquals(1, health.read("id-${x.asked.first()}").consecutiveNotFound)
        assertEquals(0, health.read("id-${x.asked.last()}").consecutiveNotFound)
    }

    @Test
    fun a401FromEveryAccountIsSurfacedWithoutBlamingAnyone() {
        val x = FakeX { _, _ -> XResponse(401, "") }
        val error = assertFailsWith<XRequestException> { read(client(x, listOf(account("a"), account("b")))) }
        assertEquals(401, error.code)
        assertTrue(listOf("id-a", "id-b").all { health.read(it).isClean })
    }

    @Test
    fun a403FromEveryAccountIsTriedOnOneMoreAccountOnly() {
        val x = FakeX { _, _ -> XResponse(403, "") }
        val error = assertFailsWith<XRequestException> { read(client(x, listOf(account("a"), account("b"), account("c")))) }
        assertEquals(403, error.code)
        assertEquals(2, x.asked.size, "a refusal is retried on one more account, as a 404 is")
        assertTrue(listOf("id-a", "id-b", "id-c").all { health.read(it).isClean })
    }

    @Test
    fun a404FromEveryAccountIsTheEndpointsRefusalNotTheAccounts() {
        val x = FakeX { _, _ -> XResponse(404, "") }
        val error = assertFailsWith<XEndpointRefusedException> { read(client(x, listOf(account("a"), account("b")))) }
        assertEquals(404, error.code)
        assertEquals(2, x.asked.size, "surfaced once each account was asked, as XTA's transport does")
        assertTrue(listOf("id-a", "id-b").all { health.read(it).isClean })
    }

    @Test
    fun a404FromAccountsEachFlaggedBeforeSaysTheirSignInsAreBroken() {
        listOf("id-a", "id-b").forEach { health.write(it, XAccountHealth(3, now - 60_000)) }
        val x = FakeX { _, _ -> XResponse(404, "") }
        assertFailsWith<XNoWorkingAccountException> { read(client(x, listOf(account("a"), account("b")))) }
    }

    @Test
    fun aRefusalProvenThreeTimesFlagsTheAccountWhichIsThenAskedLast() {
        val reader = client(FakeX { _, account -> if (account == "a") XResponse(404, "") else ok() }, listOf(account("a"), account("b")))
        repeat(6) { read(reader) }
        assertTrue(health.read("id-a").flagged(now))
        val x = FakeX { _, account -> if (account == "a") XResponse(404, "") else ok() }
        val later = client(x, listOf(account("a"), account("b")))
        repeat(4) { read(later) }
        assertEquals(listOf("b", "b", "b", "b"), x.asked, "a flagged account is no longer asked first")
    }

    @Test
    fun aSuccessClearsAnAccountsRefusals() {
        health.write("id-a", XAccountHealth(2))
        read(client(FakeX { _, _ -> ok() }, listOf(account("a"))))
        assertTrue(health.read("id-a").isClean)
    }

    @Test
    fun a400IsSurfacedWithoutAskingAnotherAccount() {
        val x = FakeX { _, _ -> XResponse(400, "") }
        assertEquals(400, assertFailsWith<XRequestException> { read(client(x, listOf(account("a"), account("b")))) }.code)
        assertEquals(1, x.asked.size)
    }

    @Test
    fun a429FromEveryAccountIsARateLimitWithTheFirstReset() {
        val x = FakeX { call, _ -> XResponse(429, "", mapOf("x-rate-limit-reset" to listOf("${(now + call * 60_000) / 1000}"))) }
        val error = assertFailsWith<XRateLimitedException> { read(client(x, listOf(account("a"), account("b")))) }
        assertEquals(now + 60_000, error.resetsAt, "the first account to free up")
    }

    @Test
    fun aSuccessLiftsAnOldRateLimitFlag() {
        val reader = client(FakeX { _, _ -> ok() }, listOf(account("a")))
        val path = "/i/api/graphql/AMIBMjtxEEATh4z8V9GtRg/UserByScreenName"
        reader.rateLimits.flag("id-a", path, now + 300_000)
        read(reader)
        assertFalse(reader.rateLimits.isLimited("id-a", path, now))
    }

    // Ported from QuaX commit 6dca796: X reports the quota left with every answer.
    @Test
    fun anAccountWhoseQuotaXReportsSpentIsSetAsideForTheNextRead() {
        val spent = mapOf("x-rate-limit-remaining" to listOf("0"), "x-rate-limit-reset" to listOf("${(now + 300_000) / 1000}"))
        val x = FakeX { _, _ -> XResponse(200, profile, spent) }
        val reader = client(x, listOf(account("a"), account("b")))
        read(reader)
        read(reader)
        assertEquals(2, x.asked.size, "a spent quota is no failure: no retry, no extra request")
        assertEquals(2, x.asked.toSet().size, "the second read goes to the account with quota left")
    }

    @Test
    fun aSpentQuotaOnEveryAccountStillSendsTheRead() {
        val spent = mapOf("x-rate-limit-remaining" to listOf("0"), "x-rate-limit-reset" to listOf("${(now + 300_000) / 1000}"))
        val x = FakeX { _, _ -> XResponse(200, profile, spent) }
        val reader = client(x, listOf(account("a")))
        read(reader)
        read(reader)
        assertEquals(2, x.asked.size, "flags only order accounts; X decides whether the quota is spent")
    }

    @Test
    fun accountsTurnedOffForFeedsAreAskedAfterTheOthers() {
        val x = FakeX { _, _ -> ok() }
        val reader = client(x, listOf(account("a"), account("b")), disabled = setOf("id-a"))
        repeat(3) { read(reader) }
        assertEquals(listOf("b", "b", "b"), x.asked)
        read(client(x, listOf(account("a")), disabled = setOf("id-a")))
        assertEquals("a", x.asked.last(), "with every account off, the reads still need someone")
    }

    @Test
    fun aChosenAccountsHomeTimelineIsReadWithThatAccountAlone() {
        val x = FakeX { _, _ -> XResponse(429, "", mapOf("x-rate-limit-reset" to listOf("invalid"))) }
        val reader = client(x, listOf(account("a"), account("b")))
        val error = assertFailsWith<XRateLimitedException> { runBlocking { reader.homeTimeline(account("b")) } }
        assertEquals(listOf("b"), x.asked, "no other account stands in")
        val path = "/i/api/graphql/-SBRQaLiRjWvTFD5SI403A/HomeTimeline"
        assertTrue(reader.rateLimits.isLimited("id-b", path, now), "a malformed reset still sets the account aside")
        assertEquals(now + 15 * 60_000, error.resetsAt, "for X's usual fifteen minutes")
    }

    private val noQuotes = """{"data":{"search_by_raw_query":{"search_timeline":{"timeline":{"instructions":[]}}}}}"""

    private val searchPath = "/i/api/graphql/BYppgupFFS2yqVmmRNjlsw/SearchTimeline"

    private fun quotes(client: XClient) = runBlocking { client.quotes("1", order = XQuoteOrder.LATEST) }

    @Test
    fun anAccountXRefusedWithADated429IsNotAskedAgainBeforeTheReset() {
        val x = FakeX { _, _ -> XResponse(429, "", resetIn5Minutes) }
        val reader = client(x, listOf(account("a")))
        assertFailsWith<XRateLimitedException> { quotes(reader) }
        val again = assertFailsWith<XRateLimitedException> { quotes(reader) }
        assertEquals(listOf("a"), x.asked, "the second read spends no request X would refuse")
        assertEquals((now + 300_000) / 1000 * 1000, again.resetsAt, "and still counts down to X's reset")
        now += 301_000
        assertFailsWith<XRateLimitedException> { quotes(reader) }
        assertEquals(listOf("a", "a"), x.asked, "asked again once the reset passed")
    }

    @Test
    fun a429WithoutAResetStillLetsTheAccountBeAskedLast() {
        val x = FakeX { call, _ -> if (call == 1) XResponse(429, "") else XResponse(200, noQuotes) }
        val reader = client(x, listOf(account("a")))
        assertFailsWith<XRateLimitedException> { quotes(reader) }
        quotes(reader)
        assertEquals(listOf("a", "a"), x.asked, "a guessed fifteen minutes only orders the account")
    }

    @Test
    fun aRateLimitOnTheSearchLeavesOtherReadsAlone() {
        val x = FakeX { _, _ -> XResponse(429, "", resetIn5Minutes) }
        val reader = client(x, listOf(account("a")))
        assertFailsWith<XRateLimitedException> { quotes(reader) }
        assertTrue(reader.rateLimits.isLimited("id-a", searchPath, now))
        assertFalse(reader.rateLimits.isLimited("id-a", "/i/api/graphql/AMIBMjtxEEATh4z8V9GtRg/UserByScreenName", now))
    }

    @Test
    fun accountsTurnedOffForFeedsStandInOnceTheOthersAreLimited() {
        val x = FakeX { _, account -> if (account == "a") XResponse(429, "", resetIn5Minutes) else XResponse(200, noQuotes) }
        val reader = client(x, listOf(account("a"), account("b")), disabled = setOf("id-b"))
        quotes(reader)
        assertEquals(listOf("a", "b"), x.asked, "the quotes are read with the spare account, not shown as throttled")
        quotes(reader)
        assertEquals(listOf("a", "b", "b"), x.asked, "and the limited account is left alone until X's reset")
    }

    @Test
    fun theFeedsKeepToTheAccountsLeftOnForThem() {
        val x = FakeX { _, account -> if (account == "a") XResponse(429, "", resetIn5Minutes) else XResponse(200, noQuotes) }
        val reader = client(x, listOf(account("a"), account("b")), disabled = setOf("id-b"))
        assertFailsWith<XRateLimitedException> { runBlocking { reader.feedReads().search("from:a", null, includeReplies = true) } }
        assertEquals(listOf("a"), x.asked, "the Following feed never reads with an account turned off for it")
    }
}
