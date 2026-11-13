package com.aimdi.xtanative.networks.immich

import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** A file read whole, with the status it came with: a post's photo or video on its way to Immich. */
class ImmichDownload(val code: Int, val bytes: ByteArray)

/**
 * [XHttp] and the two requests Immich needs that it cannot make yet: a file's
 * bytes, and a request whose body is bytes (the multipart upload, the PUT
 * that files assets in an album). Both belong in XHttp; [OkHttpImmichHttp]
 * makes them until they are there.
 */
interface ImmichHttp : XHttp {
    /** The file at [url], read whole, redirects followed. */
    suspend fun download(url: HttpUrl): ImmichDownload

    /** A [method] request whose [body] is bytes of [contentType]. */
    suspend fun send(method: String, url: HttpUrl, headers: Map<String, String>, body: ByteArray, contentType: String): XResponse
}

/**
 * [ImmichHttp] over OkHttp, on [client]'s connections. Files get five
 * minutes, as XTA gives an upload: a video over a home connection is slow.
 */
class OkHttpImmichHttp(client: OkHttpClient = OkHttpXHttp.defaultClient) : ImmichHttp, XHttp by OkHttpXHttp(client) {
    private val files = client.newBuilder().callTimeout(5, TimeUnit.MINUTES).build()

    override suspend fun download(url: HttpUrl): ImmichDownload = withContext(Dispatchers.IO) {
        files.newCall(Request.Builder().url(url).build()).execute().use { ImmichDownload(it.code, it.body?.bytes() ?: ByteArray(0)) }
    }

    override suspend fun send(method: String, url: HttpUrl, headers: Map<String, String>, body: ByteArray, contentType: String): XResponse =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).method(method, body.toRequestBody(contentType.toMediaType()))
                .apply { headers.forEach { (name, value) -> header(name, value) } }.build()
            files.newCall(request).execute().use { XResponse(it.code, it.body?.string().orEmpty(), it.headers.toMultimap()) }
        }
}

/** The file of a multipart upload: the form [field] it goes in, the [name] it is sent as, and its [bytes]. */
internal class FilePart(val field: String, val name: String, val bytes: ByteArray)

/**
 * A `multipart/form-data` body written the way XTA's http package writes
 * one: [fields] in order, then [file] as `application/octet-stream`.
 */
internal class Multipart(val fields: List<Pair<String, String>>, val file: FilePart, val boundary: String = "xta-boundary-${UUID.randomUUID()}") {
    val contentType: String get() = "multipart/form-data; boundary=$boundary"

    fun bytes(): ByteArray = ByteArrayOutputStream(file.bytes.size + 1024).apply {
        fields.forEach { (name, value) -> text("--$boundary\r\n${fieldHeader(name, value)}$value\r\n") }
        text("--$boundary\r\ncontent-type: application/octet-stream\r\n")
        text("content-disposition: form-data; name=\"${quoted(file.field)}\"; filename=\"${quoted(file.name)}\"\r\n\r\n")
        write(file.bytes)
        text("\r\n--$boundary--\r\n")
    }.toByteArray()

    private fun ByteArrayOutputStream.text(text: String) = write(text.toByteArray(Charsets.UTF_8))

    private fun fieldHeader(name: String, value: String): String {
        val plain = value.isNotEmpty() && value.all { it.code < 0x80 }
        val charset = if (plain) "" else "\r\ncontent-type: text/plain; charset=utf-8\r\ncontent-transfer-encoding: binary"
        return "content-disposition: form-data; name=\"${quoted(name)}\"$charset\r\n\r\n"
    }

    /** A name inside the header's quotes, as browsers write it: line breaks and quotes percent-encoded. */
    private fun quoted(value: String) = value.replace(Regex("\r\n|\r|\n"), "%0D%0A").replace("\"", "%22")
}
