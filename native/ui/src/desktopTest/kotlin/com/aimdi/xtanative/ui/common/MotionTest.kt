package com.aimdi.xtanative.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * home_timeline_nested_scroll_test.dart's scrollToTop. An unattached
 * [androidx.compose.foundation.lazy.LazyListState] never finishes
 * `scrollToItem`, so the test checks the choice, which is what the list uses.
 */
class MotionTest {
    @Test
    fun withMotionDisabledAListJumpsToItsTopAtOnce() {
        assertEquals(TopJump.INSTANT, topJump(reduced = true))
        assertEquals(TopJump.ANIMATED, topJump(reduced = false))
    }
}
