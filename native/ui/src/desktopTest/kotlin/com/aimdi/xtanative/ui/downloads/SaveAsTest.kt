package com.aimdi.xtanative.ui.downloads

import com.aimdi.xtanative.store.downloads.DownloadCancellation
import com.aimdi.xtanative.store.downloads.DownloadCancelled
import com.aimdi.xtanative.ui.plugins.writtenText
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** Save as over a file already there: it goes only once the new one is whole. */
class SaveAsTest {
    @Test
    fun aCancelledSaveOverAnExistingFileLeavesTheReadersFile() {
        val dir = Files.createTempDirectory("saveas").toFile()
        try {
            val download = File(dir, "download.part").apply { writeBytes(ByteArray(200_000) { 7 }) }
            val target = File(dir, "photo.jpg").apply { writeText("the reader's photo") }
            val cancelled = DownloadCancellation().apply { cancel() }
            assertFailsWith<DownloadCancelled> { writeSaved(download, target, cancelled) }
            assertEquals("the reader's photo", target.readText())
            assertEquals(setOf("download.part", "photo.jpg"), dir.list()!!.toSet())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun aWholeSaveReplacesTheFileAndATextIsWrittenOverOne() = runBlocking {
        val dir = Files.createTempDirectory("saveas").toFile()
        try {
            val download = File(dir, "download.part").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val target = File(dir, "photo.jpg").apply { writeText("old") }
            assertEquals(target.toURI().toString(), writeSaved(download, target, DownloadCancellation()))
            assertEquals(listOf<Byte>(1, 2, 3), target.readBytes().toList())
            val opml = File(dir, "feeds.opml").apply { writeText("old") }
            assertTrue(writtenText(opml, "<opml/>"))
            assertEquals("<opml/>", opml.readText())
        } finally {
            dir.deleteRecursively()
        }
    }
}
