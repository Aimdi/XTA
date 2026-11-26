package com.aimdi.xtanative.ui.common

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PressKeepsPlaceTest {
    private val window = PressKeepsPlace.PRESS_WINDOW_NANOS

    @Test
    fun aScrollRightAfterAMousePressIsThatPressesFocus() {
        assertTrue(PressKeepsPlace.followsPress(pressedAt = 1_000L, now = 1_000L))
        assertTrue(PressKeepsPlace.followsPress(pressedAt = 1_000L, now = 1_000L + 16_000_000L))
        assertTrue(PressKeepsPlace.followsPress(pressedAt = 1_000L, now = 1_000L + window))
    }

    @Test
    fun theKeysStillScrollWhenNoPressCameJustBefore() {
        assertFalse(PressKeepsPlace.followsPress(pressedAt = null, now = 5_000L))
        assertFalse(PressKeepsPlace.followsPress(pressedAt = 1_000L, now = 1_000L + window + 1))
    }

    @Test
    fun aPressAfterTheAskDoesNotCount() {
        assertFalse(PressKeepsPlace.followsPress(pressedAt = 2_000L, now = 1_000L))
    }
}
