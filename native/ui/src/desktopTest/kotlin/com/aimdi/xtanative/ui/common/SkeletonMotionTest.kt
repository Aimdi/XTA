package com.aimdi.xtanative.ui.common

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** XTA's plugin_feed_skeleton_test: the bones stop pulsing when animations are switched off. */
class SkeletonMotionTest {
    @Test
    fun theBonesStopPulsingWhenAnimationsAreSwitchedOff() {
        assertTrue(SkeletonMotion.pulses(reducedMotion = false))
        assertFalse(SkeletonMotion.pulses(reducedMotion = true))
    }

}
