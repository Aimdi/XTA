package com.aimdi.xtanative.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SidewaysWheelTest {
    @Test
    fun theWheelDownMovesTheRowOnAndUpMovesItBack() {
        assertEquals(64f, SidewaysWheel.delta(0f, 1f, notch = 64f, canBack = true, canForward = true))
        assertEquals(-128f, SidewaysWheel.delta(0f, -2f, notch = 64f, canBack = true, canForward = true))
    }

    @Test
    fun anEndTheRowIsAtLeavesTheTurnToWhatIsAround() {
        assertNull(SidewaysWheel.delta(0f, 1f, notch = 64f, canBack = true, canForward = false))
        assertNull(SidewaysWheel.delta(0f, -1f, notch = 64f, canBack = false, canForward = true))
    }

    @Test
    fun aSidewaysTurnIsTheRowsOwn() {
        assertNull(SidewaysWheel.delta(1f, 0f, notch = 64f, canBack = true, canForward = true))
        assertNull(SidewaysWheel.delta(1f, 0.5f, notch = 64f, canBack = true, canForward = true))
    }
}
