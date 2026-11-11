package com.aimdi.xtanative.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** XTA's plugin_feed_skeleton_test: the bones stop pulsing when animations are switched off. */
class SkeletonMotionTest {
    @Test
    fun theBonesStopPulsingWhenAnimationsAreSwitchedOff() {
        assertTrue(SkeletonMotion.pulses(reducedMotion = false))
        assertFalse(SkeletonMotion.pulses(reducedMotion = true))
    }

    @Test
    fun aGridSkeletonsTilesFallInRowsWithAShortLastOne() {
        assertEquals(listOf(2, 2, 2, 2, 1), SkeletonGrid.rows(count = 9, columns = 2))
        assertEquals(listOf(3, 3, 3), SkeletonGrid.rows(count = 9, columns = 3))
        assertEquals(emptyList(), SkeletonGrid.rows(count = 0, columns = 2))
        assertEquals(emptyList(), SkeletonGrid.rows(count = 4, columns = 0))
    }
}
