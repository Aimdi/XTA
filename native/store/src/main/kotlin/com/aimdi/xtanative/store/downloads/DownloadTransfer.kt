package com.aimdi.xtanative.store.downloads

import com.aimdi.xtanative.core.json.J
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A media host answered a download with [code], or with a body that cannot be what was asked for. */
class DownloadHttpException(message: String, val code: Int? = null) : IOException(message)

/**
 * Hands a fetched file to where it goes: the platform's part (Android's
 * MediaStore, a picked folder or the save dialog; the desktop's Downloads
 * folder). The answer is where it was saved, or null when the reader backed
 * out of the dialog.
 */
fun interface SaveStaged {
    suspend fun save(entry: DownloadEntry, file: File, cancellation: DownloadCancellation): String?
}

/**
 * One download from start to saved, as [DownloadQueue] runs it: the file is
 * fetched into [staging] first (resuming an interrupted one where the host
 * allows it), and only a whole one is handed to [save]. A transfer that
 * failed keeps its partial file for the next attempt, unless it was cancelled
 * or cannot be resumed.
 */
class DownloadTransfer(
    private val http: MediaHttp,
    /** The folder partial files wait in, `<id>.part` beside `<id>.part.json`. */
    private val staging: File,
    private val save: SaveStaged,
    /** Whether files can be saved into shared folders without asking (not on Android 9 and older). */
    private val sharedStorageAvailable: suspend () -> Boolean = { true },
) : DownloadRunner {
    override suspend fun run(
        entry: DownloadEntry,
        cancellation: DownloadCancellation,
        progress: (received: Long, total: Long?) -> Unit,
        phase: (DownloadStatus) -> Unit,
    ): String? {
        cancellation.check()
        val file = partFile(staging, entry)
        val metadata = File(file.path + ".json")
        var finished = false
        try {
            withContext(Dispatchers.IO) { staging.mkdirs() }
            cancellable(cancellation) { receive(entry.url, file, metadata, cancellation, progress) }
            cancellation.check()
            val target = target(entry)
            cancellation.check()
            val destination = place(target, file, cancellation, phase)
            cancellation.check()
            finished = true
            return destination
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                if (finished || cancellation.cancelled || validator(entry.url, file, metadata) == null) discard(file, metadata)
            }
        }
    }

    /** Without shared folders a background download asks where to go instead. */
    private suspend fun target(entry: DownloadEntry): DownloadEntry =
        if (entry.treeUri == null && entry.background && !sharedStorageAvailable()) entry.copy(background = false) else entry

    /**
     * Hands the file to [save]. Without a folder and outside the background
     * that opens the system's save dialog, which takes one request at a time,
     * so downloads running side by side wait their turn for it.
     */
    private suspend fun place(entry: DownloadEntry, file: File, cancellation: DownloadCancellation, phase: (DownloadStatus) -> Unit): String? {
        val dialog = entry.treeUri == null && !entry.background
        if (!dialog) {
            phase(DownloadStatus.SAVING)
            return withContext(Dispatchers.IO) { save.save(entry, file, cancellation) }
        }
        cancellable(cancellation) { dialogTurn.lock() }
        try {
            cancellation.check()
            phase(DownloadStatus.CHOOSING_LOCATION)
            return withContext(Dispatchers.IO) { save.save(entry, file, cancellation) }
        } finally {
            dialogTurn.unlock()
        }
    }

    /** Fetches [url] into [file], resuming the bytes already there when the host vouches they are the same file. */
    private suspend fun receive(url: String, file: File, metadata: File, cancellation: DownloadCancellation, progress: (Long, Long?) -> Unit) {
        var validator = validator(url, file, metadata)
        var offset = if (validator == null) 0 else file.length()
        if (offset == 0L) discard(file, metadata)
        repeat(2) {
            cancellation.check()
            val headers = downloadRequestHeaders(url) + ("accept-encoding" to "identity") +
                (if (offset > 0) mapOf("range" to "bytes=$offset-", "if-range" to validator.orEmpty()) else emptyMap())
            val response = http.get(url, headers)
            cancellation.onCancel(response::close)
            response.use {
                cancellation.check()
                if (response.code == 416 && offset > 0) {
                    discard(file, metadata)
                    offset = 0
                    validator = null
                    return@repeat
                }
                val window = window(response, offset, validator)
                if (window == null) {
                    discard(file, metadata)
                    throw DownloadHttpException("Invalid resumed download")
                }
                if (window.start == 0L) offset = 0
                val encoding = response.header("content-encoding")
                if (encoding != null && encoding != "identity") {
                    discard(file, metadata)
                    throw DownloadHttpException("Unexpected download encoding")
                }
                writeMetadata(metadata, url, strongEtag(response.header("etag")) ?: validator.takeIf { offset > 0 })
                copy(response, file, offset, window, cancellation, progress)
                return
            }
        }
        throw DownloadHttpException("Unable to resume download")
    }

    /** What part of the file an answer carries: from [start], up to [end] (exclusive) when it says, of [total]. */
    private data class Window(val start: Long, val end: Long?, val total: Long?)

    /**
     * The part [response] carries: all of it for a 200 (the range was ignored,
     * or If-Range found a changed file), the part asked for in a valid 206; null
     * for a 206 whose Content-Range or ETag does not match what was asked.
     */
    private fun window(response: MediaResponse, offset: Long, validator: String?): Window? {
        when (response.code) {
            200 -> return Window(0, null, response.contentLength)
            206 -> Unit
            else -> throw DownloadHttpException("HTTP ${response.code}", response.code)
        }
        val range = Regex("^bytes (\\d+)-(\\d+)/(\\d+)$").find(response.header("content-range").orEmpty()) ?: return null
        val (first, last, size) = range.destructured.toList().map { it.toLong() }
        val length = response.contentLength
        val etagChanged = offset > 0 && response.hasHeader("etag") && strongEtag(response.header("etag")) != validator
        if (first != offset || last < offset || last >= size || (length != null && length != last - offset + 1) || etagChanged) return null
        return Window(offset, last + 1, size)
    }

    private suspend fun copy(response: MediaResponse, file: File, offset: Long, window: Window, cancellation: DownloadCancellation, progress: (Long, Long?) -> Unit) =
        withContext(Dispatchers.IO) {
            val limit = window.end ?: window.total
            var received = offset
            FileOutputStream(file, offset > 0).use { output ->
                progress(received, window.total)
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = response.body.read(buffer)
                    if (read < 0) break
                    cancellation.check()
                    if (limit != null && received + read > limit) throw DownloadHttpException("Download exceeds declared length")
                    output.write(buffer, 0, read)
                    received += read
                    progress(received, window.total)
                }
                cancellation.check()
                if (window.total != null && window.total != received) throw DownloadHttpException("Incomplete download")
                output.fd.sync()
            }
        }

    private fun writeMetadata(metadata: File, url: String, etag: String?) {
        val pending = File(metadata.path + ".tmp")
        pending.writeText(buildJsonObject { put("url", url); put("etag", etag) }.toString())
        if (!pending.renameTo(metadata)) {
            metadata.delete()
            pending.renameTo(metadata)
        }
    }

    companion object {
        /** The save dialog takes one request at a time, whichever download asks. */
        private val dialogTurn = Mutex()

        private const val STALE_MS = 7L * 24 * 60 * 60 * 1000

        /** Partial files of downloads interrupted a week ago or more: what [DownloadQueue]'s cleanup clears. */
        suspend fun clearInterruptedFiles(staging: File, now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
            staging.listFiles()?.filter { it.isFile && it.lastModified() < now - STALE_MS }?.forEach { it.delete() }
            Unit
        }

        /** Removes what an earlier attempt of [entry] left: what [DownloadQueue]'s discard does. */
        suspend fun discardEntry(staging: File, entry: DownloadEntry) = withContext(Dispatchers.IO) {
            val file = partFile(staging, entry)
            discard(file, File(file.path + ".json"))
        }

        private fun partFile(staging: File, entry: DownloadEntry) = File(staging, "${safeDownloadName(entry.id)}.part")

        private fun discard(file: File, metadata: File) {
            listOf(file, metadata, File(metadata.path + ".tmp")).forEach { it.delete() }
        }

        /** Only a strong ETag can vouch that kept bytes and new ones are of the same file. */
        internal fun strongEtag(value: String?): String? = value?.takeIf { Regex("^\"[^\"\\r\\n]*\"$").matches(it) }

        /** The ETag a partial [file] of [url] was fetched under, when it can be resumed. */
        private fun validator(url: String, file: File, metadata: File): String? = runCatching {
            val data = J.parse(metadata.readText())
            if (data["url"].string == url && file.isFile) strongEtag(data["etag"].string) else null
        }.getOrNull()

        /**
         * Runs [work] so that a cancel stops it at once, even while it waits on
         * the network, and throws [DownloadCancelled] for it.
         */
        internal suspend fun <T> cancellable(cancellation: DownloadCancellation, work: suspend () -> T): T = supervisorScope {
            val job = async(Dispatchers.IO) { work() }
            cancellation.onCancel { job.cancel() }
            try {
                job.await()
            } catch (e: Throwable) {
                if (cancellation.cancelled) throw DownloadCancelled() else throw e
            }
        }
    }
}
