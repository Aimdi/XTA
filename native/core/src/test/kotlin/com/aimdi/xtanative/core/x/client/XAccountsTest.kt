package com.aimdi.xtanative.core.x.client

import com.aimdi.xtanative.core.x.XAccount
import com.aimdi.xtanative.core.x.XAccountHealth
import com.aimdi.xtanative.core.x.XAccountHealthStore
import com.aimdi.xtanative.core.x.XAccountLoad
import com.aimdi.xtanative.core.x.XAccountPolicy
import com.aimdi.xtanative.core.x.XAccountSelector
import com.aimdi.xtanative.core.x.XRateLimits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** XTA's account_selector_test.dart, rate_limit_tracker_test.dart and account_load_test.dart. */
class XAccountsTest {
    private val now = 1_784_548_800_000L
    private fun account(id: String) = XAccount(id, id, emptyMap())

    /** Every pick over [turns] turns: XTA picks at random among ties, this takes turns. */
    private fun picks(selector: XAccountSelector, turns: Int = 12) = (0 until turns).map { selector.pick(emptySet(), it)!!.id }.toSet()

    @Test
    fun noAccountIsLeftWhenEveryOneWasTried() {
        assertNull(XAccountSelector(listOf(account("a"), account("b"))).pick(setOf("a", "b")))
    }

    @Test
    fun theOnlyAccountLeftIsPicked() {
        assertEquals("b", XAccountSelector(listOf(account("a"), account("b"))).pick(setOf("a"))?.id)
    }

    @Test
    fun spareAccountsComeAfterTheOthersAndBeforeLimitedOnes() {
        val accounts = listOf(account("on"), account("spare"))
        assertEquals(setOf("on"), picks(XAccountSelector(accounts, spare = { it.id == "spare" })))
        assertEquals(setOf("spare"), picks(XAccountSelector(accounts, rateLimited = { it.id == "on" }, spare = { it.id == "spare" })))
        assertEquals(setOf("on"), picks(XAccountSelector(accounts, rateLimited = { true }, spare = { it.id == "spare" })), "with all limited, the order holds")
    }

    @Test
    fun aDated429IsARefusalUntilItsResetAndASuccessLiftsIt() {
        val limits = XRateLimits()
        limits.flag("a", "/search", now + 60_000, refused = true)
        limits.flag("b", "/search", now + 60_000)
        assertTrue(limits.isRefused("a", "/search", now))
        assertFalse(limits.isRefused("b", "/search", now), "a guessed or counted limit is no refusal")
        assertFalse(limits.isRefused("a", "/search", now + 60_000))
        limits.record("a", "/search", { null }, now)
        assertFalse(limits.isRefused("a", "/search", now))
    }

    @Test
    fun healthyAccountsComeBeforeFlaggedOnes() {
        val selector = XAccountSelector(listOf(account("bad"), account("good")), flagged = { it.id == "bad" })
        assertEquals(setOf("good"), picks(selector))
    }

    @Test
    fun aNotFoundFlagLastsItsCooldownAndNoLonger() {
        val cooldown = XAccountPolicy.NOT_FOUND_COOLDOWN_MILLIS
        assertFalse(XAccountHealth(3, now - cooldown - 60_000).flagged(now), "an expired cooldown is healthy again")
        assertFalse(XAccountHealth(3, now - cooldown).flagged(now), "the flag lasts exactly the cooldown")
        assertTrue(XAccountHealth(3, now - cooldown + 60_000).flagged(now))
        assertFalse(XAccountHealth().flagged(now))
    }

    @Test
    fun flaggedAccountsAreStillAskedWhenNoneIsHealthy() {
        val selector = XAccountSelector(listOf(account("a"), account("b")), flagged = { true }, rateLimited = { true })
        assertTrue(selector.pick(emptySet())?.id in setOf("a", "b"))
    }

    @Test
    fun rateLimitedAccountsLeaveTheHealthyPool() {
        val selector = XAccountSelector(listOf(account("limited"), account("free")), rateLimited = { it.id == "limited" })
        assertEquals(setOf("free"), picks(selector))
        assertEquals("limited", XAccountSelector(listOf(account("limited")), rateLimited = { true }).pick(emptySet())?.id)
    }

    @Test
    fun theAccountWithTheMostRequestsLeftIsPicked() {
        val quota = mapOf("low" to 2, "high" to 140, "mid" to 50)
        val selector = XAccountSelector(listOf(account("low"), account("high"), account("mid")), remaining = { quota[it.id] })
        assertEquals(setOf("high"), picks(selector))
    }

    @Test
    fun anUnknownQuotaTiesWithTheBestInsteadOfBeatingIt() {
        val quota = mapOf("best" to 140, "low" to 3)
        val selector = XAccountSelector(listOf(account("best"), account("low"), account("unknown")), remaining = { quota[it.id] })
        assertEquals(setOf("best", "unknown"), picks(selector))
    }

    // aimdi176: an account answering 401/403 never reports a quota; counted as fresh it took every request.
    @Test
    fun anAccountThatNeverReportsAQuotaDoesNotTakeEveryRequest() {
        val quota = mapOf("a" to 120, "b" to 120)
        val selector = XAccountSelector(listOf(account("a"), account("b"), account("failing")), remaining = { quota[it.id] })
        val counts = (0 until 300).groupingBy { selector.pick(emptySet(), it)!!.id }.eachCount()
        assertTrue((counts["failing"] ?: 0) < 150)
        assertTrue(counts.keys.containsAll(listOf("a", "b")))
    }

    @Test
    fun aBurstGoesToTheAccountWithFewestRequestsInFlight() {
        val busy = mapOf("a" to 2, "b" to 0, "c" to 1)
        val quota = mapOf("a" to 140, "b" to 10, "c" to 140)
        val selector = XAccountSelector(listOf(account("a"), account("b"), account("c")), remaining = { quota[it.id] }, inFlight = { busy.getValue(it.id) })
        assertEquals(setOf("b"), picks(selector))
    }

    @Test
    fun equalAccountsShareTheLoad() {
        assertEquals(setOf("a", "b"), picks(XAccountSelector(listOf(account("a"), account("b")), remaining = { 30 })))
    }

    @Test
    fun aHealthyAccountBeatsAFlaggedOneWithMoreQuota() {
        val quota = mapOf("flagged" to 140, "ok" to 3)
        val selector = XAccountSelector(listOf(account("flagged"), account("ok")), flagged = { it.id == "flagged" }, remaining = { quota[it.id] })
        assertEquals(setOf("ok"), picks(selector))
    }

    @Test
    fun anExcludedAccountIsSkippedHoweverMuchItHasLeft() {
        val quota = mapOf("tried" to 140, "next" to 5)
        assertEquals("next", XAccountSelector(listOf(account("tried"), account("next")), remaining = { quota[it.id] }).pick(setOf("tried"))?.id)
    }

    // ---- rate limits ----

    private val search = "/SearchTimeline"
    private val detail = "/TweetDetail"
    private val reset = now + 15 * 60_000

    private fun quota(remaining: Int, resetAt: Long = reset): (String) -> String? =
        mapOf("x-rate-limit-remaining" to "$remaining", "x-rate-limit-reset" to "${resetAt / 1000}")::get

    @Test
    fun aLimitHoldsUntilItsResetAndIsPerEndpointAndPerAccount() {
        val limits = XRateLimits()
        assertFalse(limits.isLimited("a", search, now))
        limits.flag("a", search, reset)
        assertTrue(limits.isLimited("a", search, now))
        assertFalse(limits.isLimited("a", search, reset), "at the reset it is free again")
        assertFalse(limits.isLimited("a", detail, now), "X limits each endpoint on its own")
        assertFalse(limits.isLimited("b", search, now))
        limits.clear("a", search)
        assertFalse(limits.isLimited("a", search, now))
    }

    @Test
    fun theQuotaLeftCountsDownAsRequestsAreSent() {
        val limits = XRateLimits()
        assertNull(limits.remaining("a", search, now))
        limits.record("a", search, quota(10), now)
        limits.consume("a", search, now)
        assertEquals(9, limits.remaining("a", search, now))
        assertNull(limits.remaining("a", search, reset), "a new window is unknown again")
        assertFalse(limits.isLimited("a", search, now))
    }

    @Test
    fun aSpentQuotaSetsTheAccountAsideUntilTheReset() {
        val limits = XRateLimits()
        limits.record("a", search, quota(0), now)
        assertTrue(limits.isLimited("a", search, now))
        assertEquals(mapOf(search to reset / 1000 * 1000), limits.activeFor("a", now), "the wait an error card counts down")
        assertFalse(limits.isLimited("a", search, reset))
    }

    @Test
    fun requestsSentSpendTheQuotaOfTheirEndpointOnly() {
        val limits = XRateLimits()
        limits.record("a", search, quota(2), now)
        limits.consume("a", search, now)
        assertFalse(limits.isLimited("a", search, now))
        limits.consume("a", search, now)
        assertTrue(limits.isLimited("a", search, now))
        assertFalse(limits.isLimited("a", detail, now))
    }

    @Test
    fun anAnswerWrittenBeforeRequestsInFlightDoesNotHandTheirCreditsBack() {
        val limits = XRateLimits()
        limits.record("a", search, quota(2), now)
        limits.consume("a", search, now)
        limits.consume("a", search, now)
        limits.record("a", search, quota(1), now)
        assertTrue(limits.isLimited("a", search, now))
    }

    @Test
    fun aNewWindowReplacesTheCount() {
        val limits = XRateLimits()
        limits.record("a", search, quota(0), now)
        limits.record("a", search, quota(50, reset + 15 * 60_000), now)
        assertFalse(limits.isLimited("a", search, now))
    }

    @Test
    fun anUnknownQuotaIsNeverCountedDown() {
        val limits = XRateLimits()
        limits.consume("a", search, now)
        assertFalse(limits.isLimited("a", search, now))
    }

    @Test
    fun anAnswerWithoutQuotaLiftsAFlagAsASuccessAlwaysDid() {
        val limits = XRateLimits()
        limits.flag("a", search, reset)
        limits.record("a", search, { null }, now)
        assertFalse(limits.isLimited("a", search, now))
    }

    @Test
    fun malformedQuotaHeadersAreIgnored() {
        val limits = XRateLimits()
        limits.record("a", search, mapOf("x-rate-limit-remaining" to "x", "x-rate-limit-reset" to "1")::get, now)
        limits.record("a", detail, mapOf("x-rate-limit-remaining" to "0", "x-rate-limit-reset" to "-5")::get, now)
        assertFalse(limits.isLimited("a", search, now))
        assertFalse(limits.isLimited("a", detail, now))
    }

    // ---- requests in flight, and health ----

    @Test
    fun requestsInFlightAreCountedPerAccountAndEndpoint() {
        val load = XAccountLoad()
        load.begin("a", "/Search")
        load.begin("a", "/Search")
        load.begin("a", "/Detail")
        assertEquals(2, load.of("a", "/Search"))
        assertEquals(1, load.of("a", "/Detail"))
        assertEquals(0, load.of("b", "/Search"))
        repeat(3) { load.end("a", "/Search") }
        load.end("a", "/Detail")
        assertEquals(0, load.of("a", "/Search"), "an extra end never goes negative")
        assertEquals(0, load.of("a", "/Detail"))
    }

    @Test
    fun anAccountIsFlaggedOnlyAfterItsThirdRefusalInARow() {
        val first = XAccountHealth().afterRefusal(now)
        val second = first.afterRefusal(now)
        val third = second.afterRefusal(now)
        assertEquals(listOf(false, false, true), listOf(first, second, third).map { it.flagged(now) })
        assertEquals(3, third.consecutiveNotFound)
    }

    @Test
    fun theStoreInMemoryForgetsAHealthyAccount() {
        val store = XAccountHealthStore.inMemory()
        store.write("a", XAccountHealth(2))
        assertEquals(2, store.read("a").consecutiveNotFound)
        store.write("a", XAccountHealth())
        assertTrue(store.read("a").isClean)
    }
}
