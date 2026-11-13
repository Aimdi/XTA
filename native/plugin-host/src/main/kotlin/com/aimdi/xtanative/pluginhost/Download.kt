package com.aimdi.xtanative.pluginhost

import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

/** Where plugin files come from: [OkHttpArtifactSource] in the app; tests answer from memory. */
fun interface ArtifactSource {
    /** A request for [url]'s bytes from [offset] on (0: the whole file); nothing is sent before [ArtifactRequest.open]. */
    fun request(url: String, offset: Long): ArtifactRequest
}

/** One transfer. [cancel] may come from any thread, and makes a blocked [open] or read give up. */
interface ArtifactRequest {
    /** Sends the request and waits for the answer to begin. */
    fun open(): ArtifactResponse

    fun cancel()
}

/**
 * An answer: its HTTP [code], and [body] holding the file's bytes from
 * [offset] on. A 200 always starts at 0; a 206 where its Content-Range says,
 * -1 when it does not say.
 */
class ArtifactResponse(val code: Int, val offset: Long, val body: InputStream) : Closeable {
    override fun close() = body.close()
}

/**
 * Plugin files over OkHttp, streamed as bytes ([OkHttpXHttp] reads answers
 * whole, as text). Each request asks for the file as it is, without gzip, so
 * an offset means the same bytes on every attempt, and from an offset with a
 * Range header; a server that ignores it sends the whole file again.
 */
class OkHttpArtifactSource(client: OkHttpClient = OkHttpXHttp.defaultClient) : ArtifactSource {
    // A plugin can take longer to arrive than the shared client's limit on a whole call; reads still time out.
    private val client = client.newBuilder().callTimeout(0, TimeUnit.MILLISECONDS).build()

    override fun request(url: String, offset: Long): ArtifactRequest {
        val request = Request.Builder().url(url).header("Accept-Encoding", "identity")
            .apply { if (offset > 0) header("Range", "bytes=$offset-") }
            .build()
        return OkHttpArtifactRequest(client.newCall(request))
    }

    private class OkHttpArtifactRequest(private val call: Call) : ArtifactRequest {
        override fun open(): ArtifactResponse {
            val response = call.execute()
            val body = response.body?.byteStream() ?: ByteArrayInputStream(ByteArray(0))
            return ArtifactResponse(response.code, rangeStart(response.code, response.header("Content-Range")), body)
        }

        override fun cancel() = call.cancel()
    }

    internal companion object {
        private val contentRange = Regex("""^bytes\s+(\d+)-""")

        /** Where the bytes of an answer with [code] start, from its Content-Range ("bytes 100-199/200") for a 206. */
        fun rangeStart(code: Int, header: String?): Long = when (code) {
            206 -> header?.trim()?.let { contentRange.find(it) }?.groupValues?.get(1)?.toLongOrNull() ?: -1
            else -> 0
        }
    }
}

/** How a download ended. */
sealed interface Downloaded {
    /** The whole file is there, every byte fetched by this attempt. */
    data object Whole : Downloaded

    /** The whole file is there, some of it kept from an earlier attempt. */
    data object Resumed : Downloaded

    /** It did not arrive, for [reason]; what did arrive stays when it can be resumed from. */
    data class Failed(val reason: PluginFailure) : Downloaded
}

/**
 * Fetches a plugin's file into a partial file beside its final place,
 * streaming its bytes and reporting progress. What an earlier attempt left
 * there is resumed from when the server allows it (HTTP ranges), and fetched
 * again from the first byte when not. Cancelling the caller stops the
 * transfer at once and keeps what arrived, for the next attempt.
 */
class PluginDownloader(private val source: ArtifactSource) {
    /** Downloads [artifact] into [part]; [onProgress] hears the bytes there so far and the file's size. */
    suspend fun download(artifact: PluginArtifact, part: File, onProgress: (bytes: Long, total: Long) -> Unit): Downloaded {
        val kept = withContext(Dispatchers.IO) { keptIn(part, artifact.size) }
        if (kept == artifact.size) return Downloaded.Resumed
        return transfer(artifact, part, kept, onProgress)
            ?: transfer(artifact, part, 0, onProgress)
            ?: Downloaded.Failed(PluginFailure.DownloadFailed(206, "the server sent another part of the file than asked"))
    }

    /** One request from [offset]; null when the server could not resume there and the file should be asked for whole. */
    private suspend fun transfer(artifact: PluginArtifact, part: File, offset: Long, onProgress: (Long, Long) -> Unit): Downloaded? =
        try {
            val request = source.request(artifact.url, offset)
            blocking(request::cancel) { job ->
                request.open().use { response -> receive(response, artifact, part, offset, job, onProgress) }
            }
        } catch (e: IOException) {
            Downloaded.Failed(PluginFailure.DownloadFailed(null, e.message))
        } catch (e: IllegalArgumentException) {
            Downloaded.Failed(PluginFailure.DownloadFailed(null, e.message))
        }

    private fun receive(response: ArtifactResponse, artifact: PluginArtifact, part: File, offset: Long, job: Job, onProgress: (Long, Long) -> Unit): Downloaded? =
        when {
            // Asked past the file's end: what was kept is no start of it.
            response.code == 416 && offset > 0 -> null.also { part.delete() }
            response.code == 206 && response.offset != offset -> null.also { part.delete() }
            response.code == 206 -> copy(response.body, part, offset, artifact.size, job, onProgress)
            response.code in 200..299 -> copy(response.body, part, 0, artifact.size, job, onProgress)
            else -> Downloaded.Failed(PluginFailure.DownloadFailed(response.code, null))
        }

    private fun copy(input: InputStream, part: File, from: Long, total: Long, job: Job, onProgress: (Long, Long) -> Unit): Downloaded {
        part.parentFile?.mkdirs()
        val bytes = FileOutputStream(part, from > 0).use { out -> pump(input, out, from, total, job, onProgress) }
        return when {
            bytes > total -> Downloaded.Failed(PluginFailure.WrongSize(total, bytes)).also { part.delete() }
            bytes < total -> Downloaded.Failed(PluginFailure.DownloadFailed(null, "the transfer ended after $bytes of $total bytes"))
            from > 0 -> Downloaded.Resumed
            else -> Downloaded.Whole
        }
    }

    /** Copies [input] to [out] until it ends or brings more than [total]; the bytes the file then holds. */
    private fun pump(input: InputStream, out: FileOutputStream, from: Long, total: Long, job: Job, onProgress: (Long, Long) -> Unit): Long {
        val buffer = ByteArray(BUFFER)
        var bytes = from
        onProgress(bytes, total)
        while (bytes <= total) {
            job.ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            bytes += read
            onProgress(minOf(bytes, total), total)
        }
        return bytes
    }

    private companion object {
        const val BUFFER = 64 * 1024

        /** The bytes an earlier attempt left in [part]; more than the file holds means another file's, and they go. */
        fun keptIn(part: File, size: Long): Long = part.length().let { if (it > size) 0L.also { part.delete() } else it }
    }
}

/**
 * Runs [block], which blocks, on the IO dispatcher, handing it its job to
 * check between steps. Cancelling the caller calls [abort], which makes a
 * blocked call give up, and ends in a CancellationException, whatever [block]
 * throws then.
 */
internal suspend fun <T> blocking(abort: () -> Unit, block: (Job) -> T): T = withContext(Dispatchers.IO) {
    val done = AtomicBoolean(false)
    val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } finally {
            if (!done.get()) abort()
        }
    }
    try {
        block(coroutineContext.job)
    } catch (e: IOException) {
        ensureActive()
        throw e
    } finally {
        done.set(true)
        watcher.cancel()
    }
}
