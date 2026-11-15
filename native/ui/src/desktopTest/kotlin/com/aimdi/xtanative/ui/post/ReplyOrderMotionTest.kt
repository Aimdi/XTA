package com.aimdi.xtanative.ui.post

import kotlin.test.Test
import kotlin.test.assertEquals

/** The reply order menu's placement and timing (the sort menu redesign). */
class ReplyOrderMotionTest {
    @Test
    fun theMenuHangsFromTheTriggersEndEdgeAndStaysInTheWindow() {
        // Left to right: the menu's right edge on the trigger's right edge.
        assertEquals(300f - 236f, ReplyOrderMotion.menuX(200f, 300f, 236f, 390f, 12f, rtl = false))
        // Right to left: its left edge on the trigger's left edge.
        assertEquals(40f, ReplyOrderMotion.menuX(40f, 140f, 236f, 390f, 12f, rtl = true))
        // Never past the window's margin.
        assertEquals(12f, ReplyOrderMotion.menuX(10f, 100f, 236f, 390f, 12f, rtl = false))
        assertEquals(390f - 236f - 12f, ReplyOrderMotion.menuX(300f, 380f, 236f, 390f, 12f, rtl = true))
    }

    @Test
    fun theMenuOpensBelowUnlessItWouldRunOffTheBottom() {
        assertEquals(104f, ReplyOrderMotion.menuY(56f, 100f, 152f, 800f, 4f, 12f))
        assertEquals(700f - 4f - 152f, ReplyOrderMotion.menuY(700f, 744f, 152f, 800f, 4f, 12f))
    }

    @Test
    fun rowsFollowEachOtherAndReducedMotionTakesNoTime() {
        assertEquals(listOf(30, 55, 80), (0..2).map(ReplyOrderMotion::rowDelay))
        assertEquals(0, ReplyOrderMotion.duration(ReplyOrderMotion.OPEN_MS, reduced = true))
        assertEquals(190, ReplyOrderMotion.duration(ReplyOrderMotion.OPEN_MS, reduced = false))
    }
}
