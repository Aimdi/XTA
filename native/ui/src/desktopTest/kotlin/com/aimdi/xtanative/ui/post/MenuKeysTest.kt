package com.aimdi.xtanative.ui.post

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MenuKeysTest {
    @Test
    fun theArrowsStepThroughTheRows() {
        assertEquals(MenuMove.NEXT, MenuKeys.move(Key.DirectionDown, modified = false))
        assertEquals(MenuMove.PREVIOUS, MenuKeys.move(Key.DirectionUp, modified = false))
    }

    @Test
    fun homeEndAndThePageKeysGoToEitherEnd() {
        assertEquals(MenuMove.FIRST, MenuKeys.move(Key.MoveHome, modified = false))
        assertEquals(MenuMove.FIRST, MenuKeys.move(Key.PageUp, modified = false))
        assertEquals(MenuMove.LAST, MenuKeys.move(Key.MoveEnd, modified = false))
        assertEquals(MenuMove.LAST, MenuKeys.move(Key.PageDown, modified = false))
    }

    @Test
    fun otherKeysAndModifiedOnesGoOn() {
        assertNull(MenuKeys.move(Key.DirectionDown, modified = true), "Alt+↓ and the like are not a move")
        assertNull(MenuKeys.move(Key.Enter, modified = false), "Enter presses the focused row itself")
        assertNull(MenuKeys.move(Key.Spacebar, modified = false))
        assertNull(MenuKeys.move(Key.Escape, modified = false), "Esc closes the popup")
        assertNull(MenuKeys.move(Key.Tab, modified = false))
    }
}
