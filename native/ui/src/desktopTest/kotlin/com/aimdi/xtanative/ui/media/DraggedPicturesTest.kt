package com.aimdi.xtanative.ui.media

import java.awt.datatransfer.DataFlavor
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DraggedPicturesTest {
    @Test
    fun aPictureIsPutOutUnderItsSaveNameOnly() {
        val dir = Files.createTempDirectory("drag").toFile()
        try {
            val source = File(dir, "cached").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val out = File(dir, "out").apply { mkdirs() }
            val put = DraggedPictures.put(source, out, "../../alice-Fabc.jpg")
            assertEquals(File(out, "alice-Fabc.jpg"), put)
            assertTrue(put.readBytes().contentEquals(byteArrayOf(1, 2, 3)))
            assertEquals(listOf("alice-Fabc.jpg"), out.list()!!.toList())
            assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(put.toPath())))
            assertEquals("picture", DraggedPictures.put(source, out, "..").name)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun theStagingFolderAndTheAppsCacheFolderAreTheOwnersOnly() {
        val cache = Files.createTempDirectory("cache").toFile()
        try {
            val staging = DraggedPictures.staging(cache)
            assertEquals(File(cache, "xta-native/dragged-pictures"), staging)
            listOf(staging, staging.parentFile).forEach {
                assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(it.toPath())))
            }
        } finally {
            cache.deleteRecursively()
        }
    }

    @Test
    fun aDragHandsOverTheFileList() {
        val file = File("/tmp/x.jpg")
        val transfer = DraggedPictures.FileTransfer(file)
        assertTrue(transfer.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
        assertEquals(listOf(file), transfer.getTransferData(DataFlavor.javaFileListFlavor))
    }
}
