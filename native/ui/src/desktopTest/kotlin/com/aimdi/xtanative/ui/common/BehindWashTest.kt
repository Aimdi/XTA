package com.aimdi.xtanative.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BehindWashTest {
    @Test
    fun aPressOutweighsAFocusWhichOutweighsThePointer() {
        val hover = BehindWash.alpha(hovered = true, focused = false, pressed = false)
        val focus = BehindWash.alpha(hovered = true, focused = true, pressed = false)
        val press = BehindWash.alpha(hovered = true, focused = true, pressed = true)
        assertTrue(hover > 0f)
        assertTrue(focus > hover)
        assertTrue(press > focus)
    }

    @Test
    fun aRowInNoStateIsNotWashed() {
        assertEquals(0f, BehindWash.alpha(hovered = false, focused = false, pressed = false))
    }

    @Test
    fun aPictureIsNotGreyedUnderThePointerButShowsAPressAndAFocus() {
        assertEquals(0f, BehindWash.alpha(hovered = true, focused = false, pressed = false, picture = true))
        assertTrue(BehindWash.alpha(hovered = true, focused = false, pressed = true, picture = true) > 0f)
        assertTrue(BehindWash.alpha(hovered = false, focused = true, pressed = false, picture = true) > 0f)
    }
}
