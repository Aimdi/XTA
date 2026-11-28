package com.aimdi.xtanative.networks.pixiv

import com.aimdi.xtanative.core.json.J
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** One frame of an animated work: its file in the archive, and how long it stays up. */
data class PixivUgoiraFrame(val file: String, val delay: Duration)

/** One frame's encoded picture, and how long it stays up. */
class PixivFrameBytes(val bytes: ByteArray, val delay: Duration)

/**
 * An animated work (ugoira), from `/v1/ugoira/metadata`: the ZIP holding its
 * frames, and each frame's delay. The archive comes from Pixiv's image server,
 * so it is fetched with [PixivImages.HEADERS] from [archiveUrl].
 */
data class PixivUgoira(val zipUrl: String, val frames: List<PixivUgoiraFrame>) {
    /** The archive as served by the image server the reader picked. */
    fun archiveUrl(imageHost: String = PixivImages.HOST): String = PixivImages.url(zipUrl, imageHost)

    /** The frames of [archive] in this metadata's order, each with its delay; frames the archive lacks are skipped. */
    fun framesIn(archive: ByteArray): List<PixivFrameBytes> = runCatching { framesFrom(ByteArrayInputStream(archive)) }.getOrDefault(emptyList())

    /**
     * The frames of the archive streaming in from [input], as [framesIn] gives them, the archive
     * read entry by entry and only this metadata's frames kept. Throws [IOException] for an archive
     * past [maxBytes] or one that will not read; [check] runs between reads.
     */
    fun framesFrom(input: InputStream, maxBytes: Long = Long.MAX_VALUE, check: () -> Unit = {}): List<PixivFrameBytes> {
        val names = frames.mapTo(HashSet()) { it.file }
        val files = PixivZip.stream(input, wanted = { it in names }, maxBytes = maxBytes, check = check) ?: return emptyList()
        return frames.mapNotNull { frame -> files[frame.file]?.let { PixivFrameBytes(it, frame.delay) } }
    }

    companion object {
        /** Null when the metadata carries no archive or no playable frame. A delay is kept between 16 ms and 10 s. */
        fun parse(json: J): PixivUgoira? {
            val meta = json["ugoira_metadata"]
            val zip = meta["zip_urls"]["medium"].string?.ifEmpty { null } ?: return null
            val frames = meta["frames"].list.mapNotNull { frame ->
                frame["file"].string?.ifEmpty { null }?.let { file ->
                    PixivUgoiraFrame(file, (frame["delay"].long ?: 100).coerceIn(16, 10_000).milliseconds)
                }
            }
            return frames.takeIf { it.isNotEmpty() }?.let { PixivUgoira(zip, it) }
        }
    }
}

/**
 * The files of a ZIP archive by name, read entry by entry as the archive
 * streams in, so the archive itself is never held whole beside its files.
 * Pixiv packs ugoira frames stored (sizes in each entry's header) or deflated.
 */
object PixivZip {
    /** Null for anything that is not a ZIP this can read. */
    fun read(bytes: ByteArray): Map<String, ByteArray>? = runCatching { stream(ByteArrayInputStream(bytes)) }.getOrNull()

    /**
     * The files of [input] that [wanted] takes (every file when null), the
     * others skipped. More than [maxBytes] read from [input], or kept, is an
     * [IOException]; [check] runs between reads, so a cancelled read stops on its way.
     * Null when [input] holds no ZIP entry at all.
     */
    fun stream(input: InputStream, wanted: ((String) -> Boolean)? = null, maxBytes: Long = Long.MAX_VALUE, check: () -> Unit = {}): Map<String, ByteArray>? {
        val counted = CappedInput(input, maxBytes)
        val files = LinkedHashMap<String, ByteArray>()
        var kept = 0L
        ZipInputStream(counted).use { zip ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                check()
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory || (wanted != null && !wanted(entry.name))) continue
                val out = ByteArrayOutputStream(entry.size.takeIf { it in 0..maxBytes }?.toInt() ?: 8192)
                while (true) {
                    check()
                    val read = zip.read(buffer)
                    if (read < 0) break
                    kept += read
                    if (kept > maxBytes) throw IOException("ugoira archive too large")
                    out.write(buffer, 0, read)
                }
                files[entry.name] = out.toByteArray()
            }
        }
        return files.takeIf { it.isNotEmpty() }
    }

    /** [input] that refuses to give more than [max] bytes. */
    private class CappedInput(input: InputStream, private val max: Long) : FilterInputStream(input) {
        private var count = 0L

        override fun read(): Int = super.read().also { if (it >= 0) counted(1) }

        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) counted(it) }

        override fun skip(n: Long): Long = super.skip(n).also { if (it > 0) counted(it) }

        private fun counted(read: Number) {
            count += read.toLong()
            if (count > max) throw IOException("ugoira archive too large")
        }
    }
}
