package com.aimdi.xtanative.networks.immich

import com.aimdi.xtanative.core.x.http.XResponse
import okhttp3.HttpUrl

/** One request a client made: what it asked, with what headers and body. */
internal class Sent(val method: String, val url: HttpUrl, val headers: Map<String, String>, val body: ByteArray?, val contentType: String?) {
    val text: String get() = body?.toString(Charsets.ISO_8859_1).orEmpty()
}

/**
 * Stands in for the user's Immich, answering each request with [answer], and
 * for X's media servers, handing out [files]. Notes everything it was asked.
 */
internal class FakeImmich(private val answer: (Sent) -> XResponse = { json("{}") }) : ImmichHttp {
    val sent = mutableListOf<Sent>()
    val downloaded = mutableListOf<HttpUrl>()
    var files: (HttpUrl) -> ImmichDownload = { ImmichDownload(200, byteArrayOf(1, 2, 3)) }

    val paths: List<String> get() = sent.map { it.url.encodedPath }

    override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean) = reply(Sent("GET", url, headers, null, null))

    override suspend fun post(url: HttpUrl, headers: Map<String, String>, body: String, contentType: String) =
        reply(Sent("POST", url, headers, body.toByteArray(), contentType))

    override suspend fun send(method: String, url: HttpUrl, headers: Map<String, String>, body: ByteArray, contentType: String) =
        reply(Sent(method, url, headers, body, contentType))

    override suspend fun download(url: HttpUrl): ImmichDownload = files(url).also { downloaded += url }

    private fun reply(request: Sent): XResponse {
        sent += request
        return answer(request)
    }
}

internal fun fixture(name: String): String = FakeImmich::class.java.getResource("/immich/$name")!!.readText()

/** An answer of JSON, typed as such. */
internal fun json(body: String, code: Int = 200) = XResponse(code, body, mapOf("content-type" to listOf("application/json; charset=utf-8")))

internal fun server(address: String = "http://immich.local:2283", key: String = "key") = ImmichServer.of(address, key)!!
