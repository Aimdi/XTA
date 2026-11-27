package com.aimdi.xtanative.ui.downloads

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class DroppedFilesTest {
    private fun files(list: List<File>) = object : Transferable {
        override fun getTransferDataFlavors() = arrayOf(DataFlavor.javaFileListFlavor)
        override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.javaFileListFlavor
        override fun getTransferData(flavor: DataFlavor): Any = list
    }

    @Test
    fun theNoteTakesTheDroppedPicturesAndVideosOnly() = runBlocking {
        val dir = Files.createTempDirectory("drop").toFile()
        try {
            val photo = File(dir, "a.JPG").apply { writeBytes(byteArrayOf(1)) }
            val video = File(dir, "b.mp4").apply { writeBytes(byteArrayOf(2)) }
            val text = File(dir, "c.txt").apply { writeText("x") }
            val folder = File(dir, "d.png").apply { mkdirs() }
            assertEquals(listOf(photo, video, folder), droppedFiles(files(listOf(photo, video, text, folder))))
            assertTrue(droppedFiles(StringSelection("https://x.com/a")).isEmpty())
            assertEquals("a.JPG", read(photo).name)
            assertEquals("b.mp4", readDropped(video)?.name)
            assertNull(readDropped(folder), "a folder named like a picture is passed over as it is read")
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun aDropIsTakenWithoutAskingTheDiskOnTheWindowsThread() {
        val asked = mutableListOf<String>()
        val onSleepingMount = object : File("/mnt/nas/holiday.jpg") {
            override fun isFile(): Boolean = true.also { asked += "isFile" }
            override fun exists(): Boolean = true.also { asked += "exists" }
            override fun length(): Long = 1L.also { asked += "length" }
        }
        assertEquals(listOf<File>(onSleepingMount), droppedFiles(files(listOf(onSleepingMount))))
        assertEquals(emptyList(), asked)
    }
}
