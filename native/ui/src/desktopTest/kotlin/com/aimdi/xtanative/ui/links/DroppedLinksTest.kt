package com.aimdi.xtanative.ui.links

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DroppedLinksTest {
    @Test
    fun aDroppedWebAddressIsTaken() {
        assertEquals("https://x.com/flutter/status/1", DroppedLinks.linkIn("https://x.com/flutter/status/1"))
        assertEquals("https://x.com/a", DroppedLinks.linkIn("\n  https://x.com/a  \nsecond line"))
        assertEquals("HTTP://example.org/x", DroppedLinks.linkIn("HTTP://example.org/x"))
    }

    @Test
    fun otherTextIsLeftAlone() {
        assertNull(DroppedLinks.linkIn(null))
        assertNull(DroppedLinks.linkIn(""))
        assertNull(DroppedLinks.linkIn("look at https://x.com/a"))
        assertNull(DroppedLinks.linkIn("file:///home/me/a.jpg"))
        assertNull(DroppedLinks.linkIn("javascript:alert(1)"))
        assertNull(DroppedLinks.linkIn("https://"))
    }

    @Test
    fun aBrowsersLinkComesAsAnAddressList() {
        assertEquals("https://x.com/a", DroppedLinks.linkIn("# dragged from a browser\r\nhttps://x.com/a\r\n"))
        val browser = object : java.awt.datatransfer.Transferable {
            val uriList = java.awt.datatransfer.DataFlavor("text/uri-list;class=java.lang.String")
            override fun getTransferDataFlavors() = arrayOf(uriList, java.awt.datatransfer.DataFlavor.stringFlavor)
            override fun isDataFlavorSupported(flavor: java.awt.datatransfer.DataFlavor) = flavor in transferDataFlavors
            override fun getTransferData(flavor: java.awt.datatransfer.DataFlavor): Any = if (flavor == uriList) "https://x.com/list\r\n" else "plain text"
        }
        assertEquals("https://x.com/list\r\n", DraggedText.read(browser))
        val files = object : java.awt.datatransfer.Transferable {
            override fun getTransferDataFlavors() = arrayOf(java.awt.datatransfer.DataFlavor.javaFileListFlavor)
            override fun isDataFlavorSupported(flavor: java.awt.datatransfer.DataFlavor) = flavor == java.awt.datatransfer.DataFlavor.javaFileListFlavor
            override fun getTransferData(flavor: java.awt.datatransfer.DataFlavor): Any = emptyList<java.io.File>()
        }
        kotlin.test.assertFalse(DraggedText.offered(files))
    }
}
