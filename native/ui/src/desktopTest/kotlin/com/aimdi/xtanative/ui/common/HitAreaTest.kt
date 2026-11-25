package com.aimdi.xtanative.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals

/** How far a small button's hit area reaches past its glyph. */
class HitAreaTest {
    @Test
    fun aSmallGlyphGrowsToTheMinimumOnEachSide() {
        assertEquals(23, HitAreas.spare(size = 50, min = 96), "a 25 dp row at 2x grows to 48 dp")
        assertEquals(11, HitAreas.spare(size = 25, min = 48))
    }

    @Test
    fun aLargeOneStaysAsItIs() {
        assertEquals(0, HitAreas.spare(size = 48, min = 48))
        assertEquals(0, HitAreas.spare(size = 120, min = 48))
    }
}
