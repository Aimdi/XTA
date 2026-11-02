package com.aimdi.xtanative.core.x.http

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** One HTTP answer, read whole: X's pages and JSON are small enough. */
class XResponse(val code: Int, val body: String, private val headers: Map<String, List<String>> = emptyMap()) {
    val isSuccessful: Boolean get() = code in 200..299

    fun header(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()
}

/** The requests XTA Native makes. An interface, so tests can stand in for x.com. */
fun interface XHttp {
    suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean): XResponse

    /** An empty POST: X hands out guest tokens no other way. */
    suspend fun post(url: HttpUrl, headers: Map<String, String>): XResponse =
        throw UnsupportedOperationException("POST $url")

    /** A POST carrying [body] as [contentType]: some networks read through POST (GraphQL forms). */
    suspend fun post(url: HttpUrl, headers: Map<String, String>, body: String, contentType: String): XResponse =
        throw UnsupportedOperationException("POST $url")
}

/**
 * [XHttp] over one shared OkHttp client, so every request reuses the
 * connection pool. A request that fails before an answer, or that X answers
 * with 502–504, is tried once more, as XTA's getXResponse does.
 */
class OkHttpXHttp(private val client: OkHttpClient = defaultClient) : XHttp {
    private val noRedirects = client.newBuilder().followRedirects(false).followSslRedirects(false).build()

    override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean): XResponse =
        withContext(Dispatchers.IO) {
            val caller = if (followRedirects) client else noRedirects
            val request = Request.Builder().url(url).apply { headers.forEach { (name, value) -> header(name, value) } }.build()
            try {
                val first = caller.newCall(request).execute().use { it.toXResponse() }
                if (first.code !in 502..504) return@withContext first
            } catch (_: IOException) {
                // tried again below
            }
            delay(200)
            caller.newCall(request).execute().use { it.toXResponse() }
        }

    override suspend fun post(url: HttpUrl, headers: Map<String, String>): XResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).post(ByteArray(0).toRequestBody(null))
            .apply { headers.forEach { (name, value) -> header(name, value) } }.build()
        client.newCall(request).execute().use { it.toXResponse() }
    }

    override suspend fun post(url: HttpUrl, headers: Map<String, String>, body: String, contentType: String): XResponse =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).post(body.toRequestBody(contentType.toMediaType()))
                .apply { headers.forEach { (name, value) -> header(name, value) } }.build()
            client.newCall(request).execute().use { it.toXResponse() }
        }

    private fun okhttp3.Response.toXResponse() = XResponse(code, body?.string().orEmpty(), headers.toMultimap())

    companion object {
        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
