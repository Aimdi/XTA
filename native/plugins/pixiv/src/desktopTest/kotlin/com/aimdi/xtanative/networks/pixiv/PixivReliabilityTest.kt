package com.aimdi.xtanative.networks.pixiv

import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.NetworkRequestException
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/** Pixiv's rate limit and burst guard: no request goes into a limit Pixiv named, and no more than three at once. */
class PixivReliabilityTest {
    private val limited = """{"error": {"message": "Rate Limit"}}"""

    private class Clock(var now: Long = NOW)

    private fun client(http: FakePixivHttp, clock: Clock) = PixivClient(http, signedIn(), clock = { clock.now })

    @Test
    fun aRateLimitHoldsEveryRequestBackForTheWaitPixivNamed(): Unit = runBlocking {
        val clock = Clock()
        var limiting = true
        val http = FakePixivHttp { if (limiting) XResponse(429, limited, mapOf("Retry-After" to listOf("30"))) else answer(illustsJson()) }
        val client = client(http, clock)

        val first = assertFailsWith<NetworkRequestException> { client.following() }
        assertEquals(429, first.code)
        assertEquals(Instant.ofEpochMilli(NOW + 30_000), first.retryAt)
        limiting = false

        clock.now = NOW + 29_000
        val held = assertFailsWith<NetworkRequestException> { client.recommended() }
        assertEquals(429, held.code, "still limited: the reader sees the same rate limit")
        assertEquals(Instant.ofEpochMilli(NOW + 30_000), held.retryAt)
        assertEquals(1, http.reads.size, "nothing was asked inside the wait")

        clock.now = NOW + 30_001
        client.following()
        assertEquals(2, http.reads.size, "once the wait is over Pixiv is asked again")
    }

    @Test
    fun pixivsRateLimitSaidWithA403AlsoHoldsRequestsBack(): Unit = runBlocking {
        val clock = Clock()
        val http = FakePixivHttp { answer(limited, 403) }
        val client = client(http, clock)
        assertFailsWith<NetworkRequestException> { client.following() }
        assertFailsWith<NetworkRequestException> { client.following() }
        assertEquals(1, http.reads.size)
        assertTrue(http.tokenCalls.isEmpty(), "a rate limit is no reason to refresh the token")
    }

    @Test
    fun theWaitIsWhatPixivSaysKeptBetweenTenSecondsAndHalfAnHour() {
        assertEquals(60_000, pixivRetryAfterMillis(null, NOW), "no Retry-After: a minute")
        assertEquals(60_000, pixivRetryAfterMillis("soon", NOW), "an unreadable one: a minute")
        assertEquals(10_000, pixivRetryAfterMillis("0", NOW))
        assertEquals(45_000, pixivRetryAfterMillis(" 45 ", NOW))
        assertEquals(30 * 60_000L, pixivRetryAfterMillis("86400", NOW))
        // NOW is 2026-08-06T01:02:03Z; two minutes later as an HTTP date.
        assertEquals(120_000, pixivRetryAfterMillis("Thu, 06 Aug 2026 01:04:03 GMT", NOW))
    }

    @Test
    fun atMostThreeRequestsAreOnTheirWayAtOnce(): Unit = runBlocking {
        val now = AtomicInteger()
        val most = AtomicInteger()
        val http = FakePixivHttp {
            most.accumulateAndGet(now.incrementAndGet(), ::maxOf)
            delay(40)
            now.decrementAndGet()
            answer(illustsJson())
        }
        val client = client(http, Clock())
        (1..10).map { async(kotlinx.coroutines.Dispatchers.Default) { client.following() } }.awaitAll()
        assertEquals(10, http.reads.size)
        assertEquals(MAX_IN_FLIGHT, most.get())
    }
}
