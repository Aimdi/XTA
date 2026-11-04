package com.aimdi.xtanative.networks.substack

import com.aimdi.xtanative.networks.NetworkRequestException
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import okhttp3.HttpUrl.Companion.toHttpUrl

/** XTA's test/substack_transport_test.dart: the limiter, the backoff and the retries, on virtual time. */
class SubstackTransportTest {
    private val feed = "https://fieldnotes.substack.com/api/v1/posts".toHttpUrl()
    private val other = "https://harbour.substack.com/api/v1/posts".toHttpUrl()
    private val custom = "https://www.example.com/api/v1/posts".toHttpUrl()

    private class Run(val http: FakeHttp, val clock: FakeClock, val transport: SubstackTransport)

    private fun run(respond: suspend (Asked, Int) -> com.aimdi.xtanative.core.x.http.XResponse): Run {
        val count = AtomicInteger()
        val http = FakeHttp { respond(it, count.getAndIncrement()) }
        val clock = FakeClock()
        return Run(http, clock, SubstackTransport(http, clock, LowJitter))
    }

    @Test
    fun substackHostsShareOneRateBucketAndOtherHostsHaveTheirOwn() {
        assertEquals("substack.com", SubstackTransport.bucketOf("fieldnotes.substack.com"))
        assertEquals("substack.com", SubstackTransport.bucketOf("SUBSTACK.COM"))
        assertEquals("substack.com", SubstackTransport.bucketOf("open.substack.com"))
        assertEquals("example.com", SubstackTransport.bucketOf("www.example.com"))
        assertEquals("newsletter.example.com", SubstackTransport.bucketOf("newsletter.example.com"))
    }

    @Test
    fun retryAfterIsReadAsSecondsOrAsAnHttpDateAndNothingElse() {
        val now = Instant.parse("2026-10-13T12:00:00Z")
        fun date(at: Instant) = DateTimeFormatter.RFC_1123_DATE_TIME.format(at.atOffset(ZoneOffset.UTC))
        assertEquals(3_000, SubstackTransport.retryAfter("3", now.toEpochMilli()))
        assertEquals(7_000, SubstackTransport.retryAfter(date(now.plusSeconds(7)), now.toEpochMilli()))
        assertEquals(0, SubstackTransport.retryAfter(date(now.minusSeconds(60)), now.toEpochMilli()))
        assertEquals(5_000, SubstackTransport.retryAfter("Tue, 13 Oct 2026 12:00:05 GMT", now.toEpochMilli()))
        assertNull(SubstackTransport.retryAfter("soon", now.toEpochMilli()))
        assertNull(SubstackTransport.retryAfter(null, now.toEpochMilli()))
    }

    @Test
    fun a429BacksOffWithGrowingJitteredWaitsThenReportsTheRateLimit() = runBlocking {
        val run = run { _, _ -> answer(429) }
        val failure = assertFailsWith<NetworkRequestException> { run.transport.get(feed, emptyMap()) }
        assertEquals(429, failure.code)
        assertEquals(Substack.NETWORK, failure.network)
        assertTrue(failure.isSubstackRateLimit)
        assertEquals(2_000, run.transport.blockedFor(feed.host), "the third strike blocks for 4 s at the low end of its jitter")
        assertEquals(SubstackTransport.RETRIES + 1, run.http.asked.size)
        assertEquals(listOf(500L, 1_000L), run.clock.sleeps)
    }

    @Test
    fun aRetryAfterAnswerIsWaitedOutAndTheNextAnswerIsRead() = runBlocking {
        val run = run { _, index -> if (index == 0) answer(429, headers = mapOf("Retry-After" to "3")) else ok("[]") }
        assertEquals(200, run.transport.get(feed, emptyMap()).code)
        assertEquals(listOf(3_000L), run.clock.sleeps)
    }

    @Test
    fun aLongWaitFailsAtOnceAndHoldsBackEveryHostInTheBucket() = runBlocking {
        val run = run { asked, _ -> if (asked.url.host == custom.host) ok("[]") else answer(429, headers = mapOf("retry-after" to "120")) }
        assertFailsWith<NetworkRequestException> { run.transport.get(feed, emptyMap()) }
        assertFailsWith<NetworkRequestException> { run.transport.get(other, emptyMap()) }
        assertEquals(listOf(feed), run.http.urls, "the second Substack host must not be asked while the bucket waits")

        assertEquals(200, run.transport.get(custom, emptyMap()).code)
        run.clock.now += 3 * 60_000
        runCatching { run.transport.get(other, emptyMap()) }
        assertEquals(listOf(feed, custom, other), run.http.urls)
    }

    @Test
    fun aServerErrorIsRetriedOnceAfterAShortJitteredPause() = runBlocking {
        val recovering = run { _, index -> if (index == 0) answer(503) else ok("[]") }
        assertEquals(200, recovering.transport.get(feed, emptyMap()).code)
        assertEquals(listOf(500L), recovering.clock.sleeps)

        val down = run { _, _ -> answer(502) }
        assertEquals(502, down.transport.get(feed, emptyMap()).code)
        assertEquals(2, down.http.asked.size)
    }

    @Test
    fun aSuccessClearsTheStrikesSoTheNextRateLimitStartsSmallAgain() = runBlocking {
        val run = run { _, index -> if (index == 1) ok("[]") else answer(429) }
        run.transport.get(feed, emptyMap())
        runCatching { run.transport.get(feed, emptyMap()) }
        assertEquals(listOf(500L, 500L), run.clock.sleeps.take(2))
    }

    @Test
    fun noMoreThanFourRequestsAreInFlightPerBucket() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val active = AtomicInteger()
        var most = 0
        val http = FakeHttp {
            most = maxOf(most, active.incrementAndGet())
            gate.await()
            active.decrementAndGet()
            ok("[]")
        }
        val transport = SubstackTransport(http, FakeClock())
        val requests = (0 until 6).map { "https://pub$it.substack.com/api/v1/posts".toHttpUrl() } + custom
        val pending = requests.map { async { transport.get(it, emptyMap()) } }
        repeat(10) { yield() }
        assertEquals(SubstackTransport.PER_BUCKET + 1, active.get(), "four Substack requests and the custom domain")
        gate.complete(Unit)
        pending.awaitAll()
        assertEquals(SubstackTransport.PER_BUCKET + 1, most)
    }

    @Test
    fun theRequestCarriesTheHeadersAndFollowsRedirectsUnlessToldNot() = runBlocking {
        val run = run { _, _ -> ok("[]") }
        run.transport.get(feed, mapOf("Accept" to "application/json"))
        run.transport.get(feed, emptyMap(), followRedirects = false)
        assertEquals("application/json", run.http.asked[0].headers["Accept"])
        assertEquals(listOf(true, false), run.http.asked.map { it.followRedirects })
    }
}
