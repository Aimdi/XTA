package com.aimdi.xtanative.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BehindWashTest {
    @Test
    fun aPressOutweighsThePointer() {
        val hover = BehindWash.alpha(hovered = true, pressed = false)
        val press = BehindWash.alpha(hovered = true, pressed = true)
        assertTrue(hover > 0f)
        assertTrue(press > hover)
    }

    @Test
    fun aRowInNoStateIsNotWashed() {
        assertEquals(0f, BehindWash.alpha(hovered = false, pressed = false))
    }

    @Test
    fun aPictureIsNotGreyedUnderThePointerButShowsAPress() {
        assertEquals(0f, BehindWash.alpha(hovered = true, pressed = false, picture = true))
        assertTrue(BehindWash.alpha(hovered = true, pressed = true, picture = true) > 0f)
    }

    @Test
    fun aPostsRingIsInsetAsARowsIs() {
        assertEquals(RowHighlight.block(600f, 180f), BehindWash.ring(600f, 180f, picture = false))
        val ring = BehindWash.ring(600f, 180f, picture = false)
        assertTrue(ring.left > 0f && ring.top > 0f)
    }

    @Test
    fun aPicturesRingFollowsItsOwnEdgeAndCorners() {
        assertEquals(RowHighlight.Block(0f, 0f, 500f, 280f, BehindWash.PICTURE_RADIUS), BehindWash.ring(500f, 280f, picture = true))
        assertEquals(10f, BehindWash.ring(20f, 20f, picture = true).radius)
    }
}
