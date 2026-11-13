package com.aimdi.xtanative.store.downloads

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** One answer to a media request, its body read as it arrives. Closing it ends the transfer, from any thread. */
class MediaResponse(
    val code: Int,
    headers: Map<String, String>,
    /** The length the host announced, if it did. */
    val contentLength: Long?,
    val body: InputStream,
    private val onClose: () -> Unit = {},
) : Closeable {
    private val headers = headers.mapKeys { it.key.lowercase() }

    fun header(name: String): String? = headers[name.lowercase()]

    fun hasHeader(name: String): Boolean = name.lowercase() in headers

    override fun close() {
        runCatching { body.close() }
        onClose()
    }
}

/**
 * The media requests downloads and the offline library make: pictures,
 * videos and files, streamed instead of read whole. An interface, so tests
 * can stand in for the hosts.
 */
fun interface MediaHttp {
    /** A GET of [url] with [headers]; the caller reads the body and closes the answer. */
    suspend fun get(url: String, headers: Map<String, String>): MediaResponse
}

/** [MediaHttp] over OkHttp. A long video may take minutes, so only connecting and each read are timed. */
class OkHttpMediaHttp(private val client: OkHttpClient = defaultClient) : MediaHttp {
    override suspend fun get(url: String, headers: Map<String, String>): MediaResponse = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(url).apply { headers.forEach { (name, value) -> header(name, value) } }.build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = continuation.resumeWithException(e)

            override fun onResponse(call: Call, response: Response) {
                val body = response.body
                if (body == null) {
                    response.close()
                    continuation.resumeWithException(IOException("No body for $url"))
                    return
                }
                val answer = MediaResponse(
                    code = response.code,
                    headers = response.headers.associate { it.first to it.second },
                    contentLength = body.contentLength().takeIf { it >= 0 },
                    body = body.byteStream(),
                    onClose = response::close,
                )
                continuation.resume(answer) { _, _, _ -> answer.close() }
            }
        })
    }

    companion object {
        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
    }
}
