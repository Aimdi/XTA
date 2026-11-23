package com.aimdi.xtanative.ui.downloads

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ClipboardPictureTest {
    private fun picture(withText: Boolean) = object : Transferable {
        override fun getTransferDataFlavors() = if (withText) arrayOf(DataFlavor.imageFlavor, DataFlavor.stringFlavor) else arrayOf(DataFlavor.imageFlavor)
        override fun isDataFlavorSupported(flavor: DataFlavor) = flavor in transferDataFlavors
        override fun getTransferData(flavor: DataFlavor): Any = if (flavor == DataFlavor.imageFlavor) BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB) else "text"
    }

    @Test
    fun aCopiedScreenshotBecomesAPng() {
        val file = assertNotNull(ClipboardPicture.pictureOf(picture(withText = false)))
        assertEquals("pasted.png", file.name)
        assertEquals("image/png", file.mime)
        // PNG's signature.
        assertEquals(listOf(0x89, 0x50, 0x4E, 0x47), file.bytes.take(4).map { it.toInt() and 0xFF })
    }

    @Test
    fun textIsTheFieldsToPaste() {
        assertNull(ClipboardPicture.pictureOf(picture(withText = true)))
        assertNull(ClipboardPicture.pictureOf(StringSelection("hello")))
        assertNull(ClipboardPicture.pictureOf(null))
    }
}
