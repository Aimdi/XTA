package com.aimdi.xtanative.ui.common

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecondaryPressesTest {
    @Test
    fun theInnermostMenuTakesARightClickAndTheOnesAroundItLeaveIt() {
        // Main pass: the save button's menu sees the press first, the post's after it.
        assertTrue(SecondaryPresses.take(1000L), "the innermost")
        assertFalse(SecondaryPresses.take(1000L), "the post around it")
        assertTrue(SecondaryPresses.take(1500L), "the next right-click is free again")
    }
}
