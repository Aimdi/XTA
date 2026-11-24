package com.aimdi.xtanative.ui.post

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.UnsupportedFlavorException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkDragsTest {
    @Test
    fun webAddressesAreDraggedAsTheyAre() {
        assertEquals("https://x.com/quax_tests/status/1", LinkDrags.addressOf("https://x.com/quax_tests/status/1"))
        assertEquals("https://example.org/a?b=1#c", LinkDrags.addressOf(" https://example.org/a?b=1#c "))
    }

    @Test
    fun mentionsAndTagsDragTheirPages() {
        assertEquals("https://x.com/quax_tests", LinkDrags.addressOf("@quax_tests"))
        assertEquals("https://x.com/hashtag/%C3%A4%20b", LinkDrags.addressOf("#ä b"))
    }

    @Test
    fun anAddressGoesOutInAscii() {
        assertEquals("https://example.org/%C3%A4", LinkDrags.addressOf("https://example.org/ä"))
    }

    @Test
    fun whatIsNoPageIsNotDragged() {
        assertNull(LinkDrags.addressOf(null))
        assertNull(LinkDrags.addressOf(""))
        assertNull(LinkDrags.addressOf("contract:0xabc"))
        assertNull(LinkDrags.addressOf("javascript:alert(1)"))
        assertNull(LinkDrags.addressOf("https://example.org/a b"))
    }

    @Test
    fun aUriListIsOneLineEndedWithCrLf() {
        assertEquals("https://x.com/a\r\n", LinkDrags.uriList("https://x.com/a"))
    }

    @Test
    fun aDraggedLinkIsAUriListAndPlainText() {
        val transfer = LinkTransfer("https://x.com/a/status/2")
        assertTrue(transfer.isDataFlavorSupported(LinkDragFlavors.uriList))
        assertTrue(transfer.isDataFlavorSupported(DataFlavor.stringFlavor))
        assertEquals("https://x.com/a/status/2\r\n", transfer.getTransferData(LinkDragFlavors.uriList))
        assertEquals("https://x.com/a/status/2", transfer.getTransferData(DataFlavor.stringFlavor))
        assertFailsWith<UnsupportedFlavorException> { transfer.getTransferData(DataFlavor.javaFileListFlavor) }
    }
}
