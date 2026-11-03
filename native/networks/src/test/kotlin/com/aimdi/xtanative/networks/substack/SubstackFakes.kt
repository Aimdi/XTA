package com.aimdi.xtanative.networks.substack

import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random
import okhttp3.HttpUrl

/** One request the fake was asked. */
class Asked(val url: HttpUrl, val headers: Map<String, String>, val followRedirects: Boolean) {
    val hostPath: String get() = "${url.host}${url.encodedPath}"
}

/** Stands in for Substack's hosts: [answer] decides each reply, every request is kept in [asked]. */
class FakeHttp(private val answer: suspend (Asked) -> XResponse) : XHttp {
    val asked = CopyOnWriteArrayList<Asked>()

    val urls: List<HttpUrl> get() = asked.map { it.url }
    val hosts: List<String> get() = asked.map { it.url.host }

    override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean): XResponse {
        val request = Asked(url, headers, followRedirects)
        asked += request
        return answer(request)
    }
}

/** Virtual time: a sleep moves the clock at once and is kept, so every wait can be checked without waiting. */
class FakeClock(var now: Long = Instant.parse("2026-10-13T12:00:00Z").toEpochMilli()) : SubstackClock {
    val sleeps = CopyOnWriteArrayList<Long>()

    override fun now(): Long = now

    override suspend fun sleep(millis: Long) {
        sleeps += millis
        now += millis
    }
}

/** Jitter at its low end: every wait is exactly half its base. */
object LowJitter : Random() {
    override fun nextBits(bitCount: Int): Int = 0
}

fun answer(code: Int, body: String = "", headers: Map<String, String> = emptyMap()) =
    XResponse(code, body, headers.mapValues { listOf(it.value) })

fun ok(body: String) = answer(200, body)

fun fixture(name: String): String = FakeHttp::class.java.getResource("/substack/$name")!!.readText()

/** A client over [http] whose waits take no time. */
fun clientOf(http: FakeHttp, clock: FakeClock = FakeClock()) = SubstackClient(SubstackTransport(http, clock, LowJitter))

fun publication(subdomain: String, baseUrl: String = "https://$subdomain.substack.com", name: String = subdomain) =
    SubstackPublication(subdomain, baseUrl, name)
