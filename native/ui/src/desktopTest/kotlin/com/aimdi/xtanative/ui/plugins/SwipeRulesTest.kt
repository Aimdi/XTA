package com.aimdi.xtanative.ui.plugins

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SwipeRulesTest {
    @Test
    fun aSwipeFromTheEndAsksAndTheRowStays() {
        var asked = 0
        assertFalse(SwipeRules.settles(fromEnd = true) { asked++ }, "the row leaves with the follow, once confirmed, not by the swipe")
        assertEquals(1, asked)
    }

    @Test
    fun anyOtherSwipeAsksNothing() {
        var asked = 0
        assertFalse(SwipeRules.settles(fromEnd = false) { asked++ })
        assertEquals(0, asked)
    }
}
