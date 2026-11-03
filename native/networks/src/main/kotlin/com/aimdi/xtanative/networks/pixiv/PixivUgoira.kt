package com.aimdi.xtanative.networks.pixiv

import com.aimdi.xtanative.core.json.J
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater
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
    fun framesIn(archive: ByteArray): List<PixivFrameBytes> {
        val files = PixivZip.read(archive) ?: return emptyList()
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

/** The files of a ZIP archive by name, read from its central directory. Pixiv packs ugoira frames stored or deflated. */
object PixivZip {
    private const val CENTRAL_ENTRY = 0x02014b50
    private const val LOCAL_ENTRY = 0x04034b50
    private const val END_OF_DIRECTORY = 0x06054b50

    /** Null for anything that is not a ZIP this can read. */
    fun read(bytes: ByteArray): Map<String, ByteArray>? = runCatching { entries(bytes) }.getOrNull()

    private fun entries(bytes: ByteArray): Map<String, ByteArray> {
        val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val end = (bytes.size - 22 downTo 0).first { data.getInt(it) == END_OF_DIRECTORY }
        var offset = data.u32(end + 16)
        return buildMap {
            repeat(data.u16(end + 10)) {
                check(data.getInt(offset) == CENTRAL_ENTRY) { "Broken ZIP directory" }
                val nameLength = data.u16(offset + 28)
                val name = String(bytes, offset + 46, nameLength, Charsets.UTF_8)
                put(name, entry(bytes, data, local = data.u32(offset + 42), method = data.u16(offset + 10), size = data.u32(offset + 20)))
                offset += 46 + nameLength + data.u16(offset + 30) + data.u16(offset + 32)
            }
        }
    }

    private fun entry(bytes: ByteArray, data: ByteBuffer, local: Int, method: Int, size: Int): ByteArray {
        check(data.getInt(local) == LOCAL_ENTRY) { "Broken ZIP entry" }
        val start = local + 30 + data.u16(local + 26) + data.u16(local + 28)
        val raw = bytes.copyOfRange(start, start + size)
        return when (method) {
            0 -> raw
            8 -> inflate(raw)
            else -> error("Unsupported ZIP method $method")
        }
    }

    private fun inflate(raw: ByteArray): ByteArray {
        val inflater = Inflater(true)
        try {
            // A raw stream wants one byte past its end, as Inflater's own documentation says.
            inflater.setInput(raw + 0.toByte())
            val out = ByteArrayOutputStream(raw.size * 2)
            val buffer = ByteArray(8192)
            while (!inflater.finished()) {
                val read = inflater.inflate(buffer)
                check(read > 0 || inflater.finished()) { "Broken ZIP entry" }
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        } finally {
            inflater.end()
        }
    }

    private fun ByteBuffer.u16(at: Int): Int = getShort(at).toInt() and 0xffff

    private fun ByteBuffer.u32(at: Int): Int = getInt(at)
}
