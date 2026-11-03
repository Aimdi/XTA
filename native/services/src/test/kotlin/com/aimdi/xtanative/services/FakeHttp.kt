package com.aimdi.xtanative.services

import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import okhttp3.HttpUrl

/** One request a test saw: what was asked, with what, and how. */
data class Asked(
    val method: String,
    val url: HttpUrl,
    val headers: Map<String, String>,
    val body: String? = null,
    val contentType: String? = null,
    val followRedirects: Boolean = true,
)

/** Stands in for the network: records each request and answers it with [answer]. */
class FakeHttp(private val answer: suspend (Asked) -> XResponse) : XHttp {
    val asked = ArrayList<Asked>()

    override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean): XResponse =
        Asked("GET", url, headers, followRedirects = followRedirects).also { asked += it }.let { answer(it) }

    override suspend fun post(url: HttpUrl, headers: Map<String, String>, body: String, contentType: String): XResponse =
        Asked("POST", url, headers, body, contentType).also { asked += it }.let { answer(it) }
}

fun response(code: Int, body: String = "", headers: Map<String, String> = emptyMap()) =
    XResponse(code, body, headers.mapValues { listOf(it.value) })
