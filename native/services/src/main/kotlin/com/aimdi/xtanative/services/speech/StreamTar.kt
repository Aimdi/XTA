package com.aimdi.xtanative.services.speech

import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.tukaani.xz.XZInputStream

/**
 * Reads a downloaded archive in the app itself, as XTA's archive package
 * does: a tar, bare or compressed with gzip, bzip2 (a voice) or xz (the
 * engine), as its first bytes say. A system's tar cannot be counted on for
 * them: Android's toybox has neither bzip2 nor xz. Entries come in the
 * archive's order; a link is reported as such, never followed or written.
 * Throws on a damaged archive.
 */
class StreamTar : ArchiveReader {
    override fun read(archive: File, each: (ArchiveEntry) -> Unit) {
        BufferedInputStream(archive.inputStream()).use { raw ->
            TarArchiveInputStream(decompressed(raw)).use { tar ->
                while (true) {
                    val entry = tar.nextEntry ?: break
                    each(StreamEntry(entry, tar))
                }
            }
        }
    }

    private class StreamEntry(entry: TarArchiveEntry, private val tar: TarArchiveInputStream) : ArchiveEntry {
        override val name: String = entry.name
        override val isSymbolicLink: Boolean = entry.isSymbolicLink || entry.isLink
        override val isDirectory: Boolean = !isSymbolicLink && entry.isDirectory

        override fun writeTo(out: OutputStream) {
            tar.copyTo(out)
        }
    }

    /** How an archive is compressed, by its first bytes. */
    enum class Compression { NONE, GZIP, BZIP2, XZ }

    companion object {
        private val XZ_MAGIC = byteArrayOf(0xfd.toByte(), '7'.code.toByte(), 'z'.code.toByte(), 'X'.code.toByte(), 'Z'.code.toByte(), 0)

        /** What [head], an archive's first bytes, says it is compressed with. */
        fun compressionOf(head: ByteArray): Compression = when {
            head.size >= 2 && head[0] == 0x1f.toByte() && head[1] == 0x8b.toByte() -> Compression.GZIP
            head.size >= 3 && head[0] == 'B'.code.toByte() && head[1] == 'Z'.code.toByte() && head[2] == 'h'.code.toByte() -> Compression.BZIP2
            head.size >= XZ_MAGIC.size && head.copyOf(XZ_MAGIC.size).contentEquals(XZ_MAGIC) -> Compression.XZ
            else -> Compression.NONE
        }

        private fun decompressed(raw: BufferedInputStream): InputStream = when (compressionOf(peek(raw, XZ_MAGIC.size))) {
            Compression.GZIP -> GZIPInputStream(raw)
            Compression.BZIP2 -> BZip2CompressorInputStream(raw, true)
            Compression.XZ -> XZInputStream(raw)
            Compression.NONE -> raw
        }

        /** Up to [count] of [raw]'s first bytes, left to be read again. */
        private fun peek(raw: BufferedInputStream, count: Int): ByteArray {
            raw.mark(count)
            val head = ByteArray(count)
            var read = 0
            while (read < count) {
                val got = raw.read(head, read, count - read)
                if (got < 0) break
                read += got
            }
            raw.reset()
            return head.copyOf(read)
        }
    }
}
