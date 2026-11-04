package com.aimdi.xtanative.ui.speech

import com.aimdi.xtanative.services.speech.OfflineVoice
import com.aimdi.xtanative.services.speech.VoiceArchiveInvalid
import com.aimdi.xtanative.services.speech.VoiceArchives
import com.aimdi.xtanative.services.speech.VoiceDownloadCancelled
import com.aimdi.xtanative.services.speech.VoiceModelKind
import com.aimdi.xtanative.store.downloads.MediaHttp
import com.aimdi.xtanative.store.downloads.MediaResponse
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class VoiceFilesTest {
    private fun folder(): File = Files.createTempDirectory("voice-files").toFile()

    private fun http(code: Int, body: ByteArray, length: Long? = body.size.toLong()) = MediaHttp { _, _ ->
        MediaResponse(code, emptyMap(), length, ByteArrayInputStream(body))
    }

    @Test
    fun aDownloadLandsInAFileOfItsOwnAndReportsItsBytes(): Unit = runBlocking {
        val staging = folder()
        val body = ByteArray(200_000) { (it % 251).toByte() }
        val seen = ArrayList<Pair<Long, Long?>>()
        val file = MediaVoiceTransfer(http(200, body), staging).download("https://example.com/v.tar.bz2", { r, t -> seen += r to t }) { false }
        assertTrue(file.readBytes().contentEquals(body))
        assertEquals(staging, file.parentFile)
        assertEquals(0L to 200_000L, seen.first())
        assertEquals(200_000L to 200_000L, seen.last())
    }

    @Test
    fun aCancelledOrBrokenDownloadLeavesNothingBehind(): Unit = runBlocking {
        val staging = folder()
        assertFailsWith<VoiceDownloadCancelled> {
            MediaVoiceTransfer(http(200, ByteArray(10)), staging).download("https://example.com/v", { _, _ -> }) { true }
        }
        assertFailsWith<IOException> {
            MediaVoiceTransfer(http(404, ByteArray(0)), staging).download("https://example.com/v", { _, _ -> }) { false }
        }
        assertFailsWith<IOException> {
            MediaVoiceTransfer(http(200, ByteArray(10), length = 20), staging).download("https://example.com/v", { _, _ -> }) { false }
        }
        assertEquals(0, staging.listFiles()!!.size)
    }

    private fun tar(folder: File, archive: File, vararg flags: String) {
        val process = ProcessBuilder(listOf("tar", *flags, "-cf", archive.path, "-C", folder.path, ".")).redirectErrorStream(true).start()
        process.inputStream.readBytes()
        assertEquals(0, process.waitFor())
    }

    private fun hasTar(): Boolean = runCatching { ProcessBuilder("tar", "--version").start().waitFor() == 0 }.getOrDefault(false)

    @Test
    fun aVoiceArchiveUnpacksThroughTheSystemsTar() {
        if (!hasTar()) return
        val work = folder()
        val content = File(work, "content/vits-test").apply { mkdirs() }
        File(content, "model.onnx").writeText("model")
        File(content, "tokens.txt").writeText("tokens")
        File(content, "espeak-ng-data").mkdirs()
        File(content, "espeak-ng-data/phontab").writeText("phon")
        val archive = File(work, "vits-test.tar.bz2")
        tar(File(work, "content"), archive, "-j")
        val voice = OfflineVoice(
            id = "test", name = "Test", locale = "en_US", url = "https://example.com", archiveBytes = 1, sha256 = null,
            licence = "CC0-1.0", kind = VoiceModelKind.VITS, archiveRoot = "vits-test", model = "model.onnx",
        )
        val root = File(work, "voices")
        VoiceArchives.install(archive, voice, root, SystemTar())
        val installed = VoiceArchives.voiceDirectory(root, voice)
        assertEquals("model", File(installed, "model.onnx").readText())
        assertTrue(VoiceArchives.filesPresent(installed, voice))
        assertFalse(File(work, "vits-test.tar.bz2.unpacked").exists())
    }

    @Test
    fun linksAreReportedAndNeverFollowed() {
        if (!hasTar()) return
        val work = folder()
        val outside = File(work, "outside").apply { mkdirs() }
        File(outside, "keep.txt").writeText("keep")
        val content = File(work, "content/root").apply { mkdirs() }
        File(content, "file.txt").writeText("file")
        Files.createSymbolicLink(File(content, "escape").toPath(), outside.toPath())
        val archive = File(work, "links.tar.xz")
        tar(File(work, "content"), archive, "-J")
        val seen = ArrayList<String>()
        SystemTar().read(archive) { entry -> seen += entry.name + if (entry.isSymbolicLink) " -> link" else if (entry.isDirectory) "/" else "" }
        assertTrue("root/escape -> link" in seen, seen.toString())
        assertTrue("root/file.txt" in seen, seen.toString())
        assertTrue(seen.indexOf("root/") < seen.indexOf("root/file.txt"), seen.toString())
        assertEquals("keep", File(outside, "keep.txt").readText())
        assertFalse(File(work, "links.tar.xz.unpacked").exists())
    }

    @Test
    fun somethingThatIsNoArchiveFails() {
        if (!hasTar()) return
        val work = folder()
        val archive = File(work, "broken.tar.bz2").apply { writeText("not an archive") }
        assertFailsWith<IOException> { SystemTar().read(archive) { } }
        val voice = OfflineVoice("v", "V", "en_US", "https://e", 1, null, "MIT", VoiceModelKind.VITS, "v", "m.onnx")
        assertFailsWith<VoiceArchiveInvalid> { VoiceArchives.install(archive, voice, File(work, "voices"), SystemTar()) }
        assertFalse(File(work, "broken.tar.bz2.unpacked").exists())
    }
}
