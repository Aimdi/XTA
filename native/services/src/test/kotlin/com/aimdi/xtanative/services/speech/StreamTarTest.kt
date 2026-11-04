package com.aimdi.xtanative.services.speech

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream

class StreamTarTest {
    private val deep = "root/espeak-ng-data/" + "voices/".repeat(16) + "en"

    private fun folder(): File = Files.createTempDirectory("stream-tar").toFile()

    private fun compressed(compression: StreamTar.Compression, out: OutputStream): OutputStream = when (compression) {
        StreamTar.Compression.NONE -> out
        StreamTar.Compression.GZIP -> GZIPOutputStream(out)
        StreamTar.Compression.BZIP2 -> BZip2CompressorOutputStream(out)
        StreamTar.Compression.XZ -> XZOutputStream(out, LZMA2Options())
    }

    /** A tar of [files] (a name ending in / is a folder) and [links] (name to target), compressed so. */
    private fun archive(file: File, compression: StreamTar.Compression, files: List<Pair<String, String>>, links: Map<String, String> = emptyMap()): File {
        TarArchiveOutputStream(compressed(compression, file.outputStream())).use { tar ->
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            files.forEach { (name, text) ->
                val bytes = text.toByteArray()
                tar.putArchiveEntry(TarArchiveEntry(name).apply { if (!name.endsWith("/")) size = bytes.size.toLong() })
                tar.write(bytes)
                tar.closeArchiveEntry()
            }
            links.forEach { (name, target) ->
                tar.putArchiveEntry(TarArchiveEntry(name, TarConstants.LF_SYMLINK).apply { linkName = target })
                tar.closeArchiveEntry()
            }
        }
        return file
    }

    private fun entries(archive: File): List<String> {
        val seen = ArrayList<String>()
        StreamTar().read(archive) { entry ->
            val text = ByteArrayOutputStream().also(entry::writeTo).toString(Charsets.UTF_8)
            seen += when {
                entry.isSymbolicLink -> "${entry.name} -> link"
                entry.isDirectory -> entry.name
                else -> "${entry.name}=$text"
            }
        }
        return seen
    }

    @Test
    fun everyCompressionReadsTheSameEntriesInOrder() {
        val work = folder()
        val files = listOf("root/" to "", "root/model.onnx" to "model", "root/espeak-ng-data/" to "", "$deep/phontab" to "phon")
        StreamTar.Compression.entries.forEach { compression ->
            val read = entries(archive(File(work, "v-$compression"), compression, files))
            assertEquals(listOf("root/", "root/model.onnx=model", "root/espeak-ng-data/", "$deep/phontab=phon"), read, compression.name)
        }
    }

    @Test
    fun aCompressionIsNamedByTheArchivesFirstBytes() {
        assertEquals(StreamTar.Compression.GZIP, StreamTar.compressionOf(byteArrayOf(0x1f, 0x8b.toByte(), 8)))
        assertEquals(StreamTar.Compression.BZIP2, StreamTar.compressionOf("BZh9".toByteArray()))
        assertEquals(StreamTar.Compression.XZ, StreamTar.compressionOf(byteArrayOf(0xfd.toByte(), '7'.code.toByte(), 'z'.code.toByte(), 'X'.code.toByte(), 'Z'.code.toByte(), 0)))
        assertEquals(StreamTar.Compression.NONE, StreamTar.compressionOf("ro".toByteArray()), "too short to be any")
        assertEquals(StreamTar.Compression.NONE, StreamTar.compressionOf(ByteArray(0)))
    }

    @Test
    fun linksAreReportedAndNeverFollowed() {
        val work = folder()
        val outside = File(work, "outside.txt").apply { writeText("keep") }
        val archive = archive(File(work, "links.tar.xz"), StreamTar.Compression.XZ, listOf("root/" to "", "root/file.txt" to "file"), mapOf("root/escape" to outside.path))
        assertEquals(listOf("root/", "root/file.txt=file", "root/escape -> link"), entries(archive))
        assertEquals("keep", outside.readText())
    }

    @Test
    fun aVoiceInstallsThroughIt() {
        val work = folder()
        val archive = archive(
            File(work, "vits-test.tar.bz2"),
            StreamTar.Compression.BZIP2,
            listOf("vits-test/" to "", "vits-test/model.onnx" to "model", "vits-test/tokens.txt" to "tokens", "vits-test/espeak-ng-data/" to "", "vits-test/espeak-ng-data/phontab" to "phon"),
        )
        val voice = OfflineVoice(
            id = "test", name = "Test", locale = "en_US", url = "https://example.com", archiveBytes = 1, sha256 = null,
            licence = "CC0-1.0", kind = VoiceModelKind.VITS, archiveRoot = "vits-test", model = "model.onnx",
        )
        val root = File(work, "voices")
        VoiceArchives.install(archive, voice, root, StreamTar())
        val installed = VoiceArchives.voiceDirectory(root, voice)
        assertEquals("model", File(installed, "model.onnx").readText())
        assertEquals("phon", File(installed, "espeak-ng-data/phontab").readText())
        assertTrue(VoiceArchives.filesPresent(installed, voice))
    }

    @Test
    fun somethingThatIsNoArchiveInstallsNothing() {
        val work = folder()
        val voice = OfflineVoice("v", "V", "en_US", "https://e", 1, null, "MIT", VoiceModelKind.VITS, "v", "m.onnx")
        val root = File(work, "voices")
        listOf("broken.tar.bz2" to "BZh9 not bzip2 at all", "broken.tar" to "not an archive").forEach { (name, text) ->
            val archive = File(work, name).apply { writeText(text) }
            assertFailsWith<VoiceArchiveInvalid>(name) { VoiceArchives.install(archive, voice, root, StreamTar()) }
            assertFalse(VoiceArchives.voiceDirectory(root, voice).exists(), name)
        }
    }
}
