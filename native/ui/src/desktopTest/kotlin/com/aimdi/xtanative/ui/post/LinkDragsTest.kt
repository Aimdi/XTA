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

    @Test
    fun aLinkDraggedOutAndDroppedBackOnTheWindowIsNotTaken() {
        val transfer = LinkTransfer("https://example.org/a")
        assertTrue(com.aimdi.xtanative.ui.links.DraggedText.fromApp(transfer))
        kotlin.test.assertFalse(com.aimdi.xtanative.ui.links.DraggedText.offered(transfer), "the frame does not start taking it")
        assertNull(com.aimdi.xtanative.ui.links.DraggedText.read(transfer), "nor opens it on a drop")
    }

    @Test
    fun theWindowHearsThePointerMoveWhenADragEndsOverIt() {
        assertEquals(java.awt.event.MouseEvent.MOUSE_MOVED, DragHover.eventAt(100, 50, 0, 0))
        assertEquals(java.awt.event.MouseEvent.MOUSE_MOVED, DragHover.eventAt(100, 50, 99, 49))
    }

    @Test
    fun theWindowHearsThePointerLeaveWhenADragEndsOutside() {
        assertEquals(java.awt.event.MouseEvent.MOUSE_EXITED, DragHover.eventAt(100, 50, 100, 10))
        assertEquals(java.awt.event.MouseEvent.MOUSE_EXITED, DragHover.eventAt(100, 50, 10, -1))
        assertEquals(java.awt.event.MouseEvent.MOUSE_EXITED, DragHover.eventAt(0, 0, 0, 0))
    }

    @Test
    fun theWindowDraggedFromAnswersNotTheFirstOneOpenedUnderThePointer() {
        assertEquals("post", DragHover.windowUnder("post", listOf("main", "post")), "a post window over the main one")
        assertEquals("main", DragHover.windowUnder("main", listOf("main", "post")))
        assertEquals("main", DragHover.windowUnder("post", listOf("main")), "the active one not under the pointer")
        assertEquals("main", DragHover.windowUnder(null, listOf("main")), "another program in use")
        assertNull(DragHover.windowUnder("post", emptyList<String>()))
    }

    @Test
    fun aPostsCardHandsOutItsAddressAndThePostForSaved() {
        val post = com.aimdi.xtanative.core.x.model.XPost("7", com.aimdi.xtanative.core.x.model.XUser("1", "jane", "Jane"), java.time.Instant.EPOCH, "hi")
        val address = "https://x.com/jane/status/7"
        DraggedPosts.started(address, post)
        val transfer = LinkTransfer(address, carriesPost = true)
        assertEquals(address, transfer.getTransferData(DataFlavor.stringFlavor))
        assertEquals(address + "\r\n", transfer.getTransferData(LinkDragFlavors.uriList))
        assertEquals(post, PostDragFlavor.postIn(transfer))
        assertNull(PostDragFlavor.postIn(LinkTransfer(address)), "a link of the post's text carries no post")
        assertNull(PostDragFlavor.postIn(LinkTransfer("https://x.com/jane/status/8", carriesPost = true)), "another drag's address")
        assertNull(DraggedPosts.post(null))
    }
}
