package com.aimdi.xtanative.ui.common

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class BulkPaceTest {
    @Test
    fun theGapIsTwoToThreeSecondsAndVaries() {
        val random = Random(7)
        val gaps = List(200) { BulkPace.gap(random) }
        assertTrue(gaps.all { it in 2_000L..3_000L })
        assertTrue(gaps.toSet().size > 50, "jittered, not a fixed beat")
    }
}
