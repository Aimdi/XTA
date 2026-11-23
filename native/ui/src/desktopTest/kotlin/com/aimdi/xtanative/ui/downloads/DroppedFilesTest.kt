package com.aimdi.xtanative.ui.downloads

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DroppedFilesTest {
    private fun files(list: List<File>) = object : Transferable {
        override fun getTransferDataFlavors() = arrayOf(DataFlavor.javaFileListFlavor)
        override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.javaFileListFlavor
        override fun getTransferData(flavor: DataFlavor): Any = list
    }

    @Test
    fun theNoteTakesTheDroppedPicturesAndVideosOnly() {
        val dir = Files.createTempDirectory("drop").toFile()
        try {
            val photo = File(dir, "a.JPG").apply { writeBytes(byteArrayOf(1)) }
            val video = File(dir, "b.mp4").apply { writeBytes(byteArrayOf(2)) }
            val text = File(dir, "c.txt").apply { writeText("x") }
            val folder = File(dir, "d.png").apply { mkdirs() }
            assertEquals(listOf(photo, video), droppedFiles(files(listOf(photo, video, text, folder))))
            assertTrue(droppedFiles(StringSelection("https://x.com/a")).isEmpty())
            assertEquals("a.JPG", read(photo).name)
        } finally {
            dir.deleteRecursively()
        }
    }
}
