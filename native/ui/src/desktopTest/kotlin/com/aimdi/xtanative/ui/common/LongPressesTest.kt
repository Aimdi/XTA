package com.aimdi.xtanative.ui.common

import androidx.compose.ui.input.pointer.PointerType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A long press is timed for a touch and the mouse's primary button, never for its right button, whose press opened the menu already. */
class LongPressesTest {
    @Test
    fun theRightButtonHeldOpensNoSecondMenu() {
        assertFalse(LongPresses.counts(PointerType.Mouse, primaryPressed = false))
    }

    @Test
    fun aTouchAPenAndTheLeftButtonAreTimed() {
        assertTrue(LongPresses.counts(PointerType.Touch, primaryPressed = false))
        assertTrue(LongPresses.counts(PointerType.Stylus, primaryPressed = false))
        assertTrue(LongPresses.counts(PointerType.Mouse, primaryPressed = true))
    }
}
