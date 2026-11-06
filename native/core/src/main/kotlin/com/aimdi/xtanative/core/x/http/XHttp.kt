package com.aimdi.xtanative.core.x.http

import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
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

    /** Every value of a header sent more than once, such as Set-Cookie. */
    fun headers(name: String): List<String> =
        headers.entries.filter { it.key.equals(name, ignoreCase = true) }.flatMap { it.value }
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
 * connection pool. A GET that fails before an answer, or that X answers with
 * 502–504, is tried once more, as XTA's getXResponse does; not one that timed
 * out, nor when the read's [XRequestBudget] has no time left for it. Each call
 * waits no longer than what is left of that budget.
 */
class OkHttpXHttp(private val client: OkHttpClient = defaultClient) : XHttp {
    private val noRedirects = client.newBuilder().followRedirects(false).followSslRedirects(false).build()

    override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean): XResponse {
        val budget = coroutineContext[XRequestBudget]
        val caller = if (followRedirects) client else noRedirects
        val request = Request.Builder().url(url).apply { headers.forEach { (name, value) -> header(name, value) } }.build()
        try {
            val first = caller.send(request, budget)
            if (first.code !in 502..504 || !hasTimeToRetry(budget)) return first
        } catch (error: InterruptedIOException) {
            throw error
        } catch (error: IOException) {
            if (!hasTimeToRetry(budget)) throw error
        }
        delay(RETRY_DELAY_MILLIS)
        return caller.send(request, budget)
    }

    override suspend fun post(url: HttpUrl, headers: Map<String, String>): XResponse {
        val request = Request.Builder().url(url).post(ByteArray(0).toRequestBody(null))
            .apply { headers.forEach { (name, value) -> header(name, value) } }.build()
        return client.send(request, coroutineContext[XRequestBudget])
    }

    override suspend fun post(url: HttpUrl, headers: Map<String, String>, body: String, contentType: String): XResponse {
        val request = Request.Builder().url(url).post(body.toRequestBody(contentType.toMediaType()))
            .apply { headers.forEach { (name, value) -> header(name, value) } }.build()
        return client.send(request, coroutineContext[XRequestBudget])
    }

    private suspend fun OkHttpClient.send(request: Request, budget: XRequestBudget?): XResponse = withContext(Dispatchers.IO) {
        val call = newCall(request)
        if (budget != null) {
            val left = budget.remainingMillis
            if (left < 1) throw SocketTimeoutException("The request's time is spent")
            val timeout = call.timeout()
            val millis = if (timeout.timeoutNanos() == 0L) left else minOf(left, TimeUnit.NANOSECONDS.toMillis(timeout.timeoutNanos()))
            timeout.timeout(millis, TimeUnit.MILLISECONDS)
        }
        call.execute().use { it.toXResponse() }
    }

    private fun hasTimeToRetry(budget: XRequestBudget?) = budget == null || budget.remainingMillis > RETRY_DELAY_MILLIS

    private fun okhttp3.Response.toXResponse() = XResponse(code, body?.string().orEmpty(), headers.toMultimap())

    companion object {
        private const val RETRY_DELAY_MILLIS = 200L

        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
