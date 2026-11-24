package com.aimdi.xtanative.ui.post

import kotlin.test.Test
import kotlin.test.assertEquals

class PostPressesTest {
    @Test
    fun aMiddleClickOpensAWindowUnlessALinkTookIt() {
        assertEquals(PostPress.WINDOW, PostPresses.of(primary = false, middle = true, ctrl = false, taken = false))
        assertEquals(PostPress.WINDOW, PostPresses.of(primary = false, middle = true, ctrl = true, taken = false))
        assertEquals(PostPress.TAP, PostPresses.of(primary = false, middle = true, ctrl = false, taken = true), "a link's middle click opens the browser")
    }

    @Test
    fun aCtrlClickOpensTheTapInAWindow() {
        assertEquals(PostPress.TAP_IN_WINDOW, PostPresses.of(primary = true, middle = false, ctrl = true, taken = false))
        assertEquals(PostPress.TAP, PostPresses.of(primary = true, middle = false, ctrl = false, taken = false))
        assertEquals(PostPress.TAP, PostPresses.of(primary = false, middle = false, ctrl = true, taken = false), "a right-click with Ctrl is the menu's")
    }

    @Test
    fun aCtrlClickOpensOneWindowAndEnterAfterItOpensThePostHere() {
        val presses = CardPresses()
        presses.last = PostPress.TAP_IN_WINDOW
        assertEquals(PostPress.TAP_IN_WINDOW, presses.take(), "the Ctrl+click's own tap")
        assertEquals(PostPress.TAP, presses.take(), "Enter on the card it left focused")
    }
}
