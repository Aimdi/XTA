package com.aimdi.xtanative.store.offline

import com.aimdi.xtanative.store.downloads.MediaHttp
import com.aimdi.xtanative.store.downloads.isWebUrl
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * The picture type of [bytes], read from their first bytes, for raster
 * pictures alone (PNG, JPEG, GIF, WebP, AVIF): only these are put into a
 * kept article, never SVG, which can carry script.
 */
fun offlineImageMime(bytes: ByteArray): String? {
    fun at(start: Int, vararg expected: Int) =
        bytes.size >= start + expected.size && expected.indices.all { bytes[start + it] == expected[it].toByte() }
    return when {
        at(0, 137, 80, 78, 71, 13, 10, 26, 10) -> "image/png"
        at(0, 255, 216, 255) -> "image/jpeg"
        at(0, 71, 73, 70, 56, 55, 97) || at(0, 71, 73, 70, 56, 57, 97) -> "image/gif"
        at(0, 82, 73, 70, 70) && at(8, 87, 69, 66, 80) -> "image/webp"
        at(4, 102, 116, 121, 112) && (at(8, 97, 118, 105, 102) || at(8, 97, 118, 105, 115)) -> "image/avif"
        else -> null
    }
}

/** The type of a kept video (MP4 or WebM) or picture, from its first bytes; null for anything else. */
fun offlineMediaMime(bytes: ByteArray, video: Boolean): String? = when {
    !video -> offlineImageMime(bytes)
    bytes.size >= 12 && String(bytes, 4, 4, Charsets.ISO_8859_1) == "ftyp" -> "video/mp4"
    bytes.size >= 4 && bytes[0] == 26.toByte() && bytes[1] == 69.toByte() && bytes[2] == 223.toByte() && bytes[3] == 163.toByte() -> "video/webm"
    else -> null
}

/**
 * Fetches [source] into [target], refusing more than [limit] bytes, an
 * answer shorter than it announced, and a file whose first bytes are not the
 * picture or video it claims to be. Stopped between chunks once [cancelled];
 * whatever stops it leaves no file behind.
 */
suspend fun retainOfflineMedia(http: MediaHttp, source: OfflineMediaSource, target: File, limit: Long, cancelled: () -> Boolean): OfflineFile {
    if (!isWebUrl(source.url) || limit <= 0) throw IllegalArgumentException("Unsupported offline media")
    val response = withTimeout(OFFLINE_REQUEST_TIMEOUT_MS) { http.get(source.url, emptyMap()) }
    return response.use {
        val length = response.contentLength
        if (response.code != 200 || (length != null && length > limit)) throw IOException("Offline media unavailable or too large")
        withContext(Dispatchers.IO) {
            try {
                val (bytes, header) = copyLimited(response.body, target, limit, cancelled)
                val mime = offlineMediaMime(header, source.video)
                if (bytes == 0L || (length != null && length != bytes) || mime == null || cancelled()) {
                    throw IOException("Incomplete or unsupported offline media")
                }
                OfflineFile(target.name, source.url, mime, bytes)
            } catch (e: Exception) {
                target.delete()
                throw e
            }
        }
    }
}

private const val OFFLINE_REQUEST_TIMEOUT_MS = 20_000L

/** Copies [input] to [target], every chunk written through; the count and the first 32 bytes. */
private fun copyLimited(input: java.io.InputStream, target: File, limit: Long, cancelled: () -> Boolean): Pair<Long, ByteArray> {
    var bytes = 0L
    val header = java.io.ByteArrayOutputStream()
    FileOutputStream(target).use { output ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (cancelled() || bytes + read > limit) throw IOException("Offline transfer stopped")
            bytes += read
            if (header.size() < 32) header.write(buffer, 0, minOf(read, 32 - header.size()))
            output.write(buffer, 0, read)
            output.flush()
        }
    }
    return bytes to header.toByteArray()
}
