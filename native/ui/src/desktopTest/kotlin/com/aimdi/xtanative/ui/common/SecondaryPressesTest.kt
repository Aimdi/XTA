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

    @Test
    fun aRightClickOffWhatAMenuAcceptsIsLeftToTheOneAroundIt() {
        // A cashtag's long press over a post's text: a right-click beside the tag is the post's.
        assertFalse(SecondaryPresses.opens(right = true, accepts = { false }, uptime = 2000L), "off the tag")
        assertTrue(SecondaryPresses.opens(right = true, accepts = { true }, uptime = 2000L), "the post around it")
        assertFalse(SecondaryPresses.opens(right = true, accepts = { true }, uptime = 2000L), "taken once")
    }

    @Test
    fun onlyARightClickOpensAndOtherPressesAreNotTestedForWhereTheyLand() {
        var asked = false
        assertFalse(SecondaryPresses.opens(right = false, accepts = { asked = true; true }, uptime = 3000L))
        assertFalse(asked, "a left click or a move does not lay out the text")
        assertTrue(SecondaryPresses.opens(right = true, accepts = { true }, uptime = 3000L), "a left press leaves the time free")
    }
}
