package com.aimdi.xtanative.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TextTipsTest {
    @Test
    fun aTextThatFitsIsNotCut() {
        assertFalse(TextTips.isCut(overflowed = false, lineCount = 1) { false })
        assertFalse(TextTips.isCut(overflowed = false, lineCount = 3) { false })
    }

    @Test
    fun overflowingItsRoomCuts() {
        assertTrue(TextTips.isCut(overflowed = true, lineCount = 1) { false })
    }

    @Test
    fun anEllipsisOnAnyLineCuts() {
        assertTrue(TextTips.isCut(overflowed = false, lineCount = 2) { it == 1 })
        assertTrue(TextTips.isCut(overflowed = false, lineCount = 1) { it == 0 })
    }

    @Test
    fun noLinesAreNotCut() {
        assertFalse(TextTips.isCut(overflowed = false, lineCount = 0) { true })
    }

    @Test
    fun theTipIsTheWholeTextOnlyWhenCut() {
        assertEquals("A very long display name", TextTips.tip("A very long display name", cut = true))
        assertNull(TextTips.tip("A very long display name", cut = false))
    }

    @Test
    fun blankTextHasNoTip() {
        assertNull(TextTips.tip("   ", cut = true))
        assertNull(TextTips.tip("", cut = true))
    }

    @Test
    fun theTipLeavesOutSurroundingSpace() {
        assertEquals("@alice", TextTips.tip(" @alice\n", cut = true))
    }
}
