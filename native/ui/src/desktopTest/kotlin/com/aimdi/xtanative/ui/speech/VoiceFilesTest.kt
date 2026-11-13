package com.aimdi.xtanative.ui.speech

import com.aimdi.xtanative.services.speech.VoiceDownloadCancelled
import com.aimdi.xtanative.store.downloads.MediaHttp
import com.aimdi.xtanative.store.downloads.MediaResponse
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
}
