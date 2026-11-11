package com.aimdi.xtanative.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkeletonGridTest {
    @Test
    fun theTilesFillWholeRowsAndTheLastKeepsWhatIsLeft() {
        assertEquals(3, SkeletonGrid.rows(9, 3))
        assertEquals(4, SkeletonGrid.rows(10, 3))
        assertEquals(0, SkeletonGrid.rows(0, 3))
        assertTrue(SkeletonGrid.drawn(3, 0, 10, 3))
        assertFalse(SkeletonGrid.drawn(3, 1, 10, 3), "the last row's other places stay empty")
    }
}
