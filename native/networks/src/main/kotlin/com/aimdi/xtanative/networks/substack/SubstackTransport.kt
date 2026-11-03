package com.aimdi.xtanative.networks.substack

import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import java.net.SocketTimeoutException
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl

/** Time as the transport sees it, so tests can wait without waiting. */
interface SubstackClock {
    /** Now, in milliseconds since the epoch. */
    fun now(): Long

    suspend fun sleep(millis: Long)

    companion object {
        /** The wall clock, and real waits. */
        val SYSTEM: SubstackClock = object : SubstackClock {
            override fun now(): Long = System.currentTimeMillis()

            override suspend fun sleep(millis: Long) = delay(millis)
        }
    }
}

/**
 * The one road every Substack request takes, as XTA's substack_transport.dart.
 *
 * Every `*.substack.com` host answers from one service and is limited as one,
 * so they share a rate bucket; any other host is a bucket of its own. At most
 * [PER_BUCKET] requests are in flight in a bucket. A 429, or a 503 that says
 * `Retry-After`, blocks the whole bucket for the wait it asks for, or else for
 * a second doubling with each rate limit in a row, between half and all of it,
 * never more than five minutes; the next request waits that out instead of
 * asking again. A request that would wait more than ten seconds fails at once
 * with a 429 rather than keep a screen spinning, and a request is retried at
 * most [RETRIES] times. A 500, 502, 503 or 504 without `Retry-After` is asked
 * once more after half a second to a second.
 */
class SubstackTransport(
    private val http: XHttp,
    private val clock: SubstackClock = SubstackClock.SYSTEM,
    private val random: Random = Random.Default,
) {
    private class Bucket {
        val permits = Semaphore(PER_BUCKET)
        var blockedUntil: Long? = null
        var strikes = 0
    }

    private val buckets = ConcurrentHashMap<String, Bucket>()

    /** [url] as the transport allows it; a rate limit it cannot wait out is a 429 [com.aimdi.xtanative.networks.NetworkRequestException]. */
    suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean = true): XResponse {
        val bucket = buckets.computeIfAbsent(bucketOf(url.host)) { Bucket() }
        return bucket.permits.withPermit { send(url, headers, followRedirects, bucket) }
    }

    /** How long requests to [host] still wait out a rate limit, in milliseconds; 0 when they do not. */
    fun blockedFor(host: String): Long = buckets[bucketOf(host)]?.let { max(0, remaining(it)) } ?: 0

    private suspend fun send(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean, bucket: Bucket): XResponse {
        var attempt = 0
        while (true) {
            waitOut(url, bucket)
            val response = withTimeoutOrNull(REQUEST_TIMEOUT_MS) { http.get(url, headers, followRedirects) }
                ?: throw SocketTimeoutException("${url.host} did not answer in ${REQUEST_TIMEOUT_MS / 1000} s")
            val limit = limitOf(response, bucket)
            if (limit != null) {
                block(bucket, limit)
                if (attempt >= RETRIES) throw Substack.rateLimited(url.host, remaining(bucket))
            } else {
                synchronized(bucket) { bucket.strikes = 0 }
                if (attempt > 0 || response.code !in SERVER_ERRORS) return response
                clock.sleep(jittered(1_000))
            }
            attempt++
        }
    }

    private suspend fun waitOut(url: HttpUrl, bucket: Bucket) {
        val wait = remaining(bucket)
        if (wait <= 0) return
        if (wait > LONGEST_WAIT_MS) throw Substack.rateLimited(url.host, wait)
        clock.sleep(wait)
    }

    private fun remaining(bucket: Bucket): Long = synchronized(bucket) { bucket.blockedUntil }?.let { it - clock.now() } ?: 0

    /** The wait [response] asks for, or null when it is no rate limit. */
    private fun limitOf(response: XResponse, bucket: Bucket): Long? {
        val asked = retryAfter(response.header("retry-after"), clock.now())
        if (response.code != 429 && !(response.code == 503 && asked != null)) return null
        val strikes = synchronized(bucket) { ++bucket.strikes }
        return min(asked ?: jittered(1_000L shl min(strikes - 1, 16)), LONGEST_BLOCK_MS)
    }

    private fun block(bucket: Bucket, wait: Long) {
        val until = clock.now() + wait
        synchronized(bucket) {
            val current = bucket.blockedUntil
            if (current == null || until > current) bucket.blockedUntil = until
        }
    }

    /** Between half and all of [base], so readers limited together do not all come back at once. */
    private fun jittered(base: Long): Long = (base * (0.5 + random.nextDouble() * 0.5)).roundToLong()

    companion object {
        const val PER_BUCKET = 4
        const val RETRIES = 2
        const val REQUEST_TIMEOUT_MS = 20_000L
        const val LONGEST_WAIT_MS = 10_000L
        const val LONGEST_BLOCK_MS = 5 * 60_000L

        private val SERVER_ERRORS = setOf(500, 502, 503, 504)

        private val rfc850 = DateTimeFormatter.ofPattern("EEEE, dd-MMM-yy HH:mm:ss zzz", Locale.US)
        private val asctime = DateTimeFormatter.ofPattern("EEE MMM ppd HH:mm:ss yyyy", Locale.US)

        /** The bucket [host] is limited in: `substack.com` for all of Substack, else the host without `www.`. */
        fun bucketOf(host: String): String {
            val name = host.lowercase()
            if (name == "substack.com" || name.endsWith(".substack.com")) return "substack.com"
            return name.removePrefix("www.")
        }

        /** The wait a `Retry-After` header asks for, in milliseconds: seconds, or an HTTP date. */
        fun retryAfter(value: String?, now: Long): Long? {
            val raw = value?.trim().orEmpty()
            if (raw.isEmpty()) return null
            raw.toLongOrNull()?.let { return max(0, it) * 1000 }
            val at = httpDate(raw) ?: return null
            return max(0, at - now)
        }

        private fun httpDate(raw: String): Long? =
            runCatching { ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
                ?: runCatching { ZonedDateTime.parse(raw, rfc850).toInstant().toEpochMilli() }.getOrNull()
                ?: runCatching { LocalDateTime.parse(raw, asctime).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
    }
}
