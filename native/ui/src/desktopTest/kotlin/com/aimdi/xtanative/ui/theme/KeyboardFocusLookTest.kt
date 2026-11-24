package com.aimdi.xtanative.ui.theme

import androidx.compose.material3.ExperimentalMaterial3Api
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalMaterial3Api::class)
class KeyboardFocusLookTest {
    private val look = KeyboardFocusLook

    @Test
    fun aKeyPressedTurnsToTheKeyboardAndAMousePressBackToThePointer() {
        assertEquals(true, look.modeAfter(false, KeyEvent.KEY_PRESSED, KeyEvent.VK_TAB))
        assertEquals(true, look.modeAfter(false, KeyEvent.KEY_PRESSED, KeyEvent.VK_A))
        assertEquals(false, look.modeAfter(true, MouseEvent.MOUSE_PRESSED, null))
    }

    @Test
    fun aModifierOnItsOwnLeavesTheModeAsItWas() {
        listOf(KeyEvent.VK_SHIFT, KeyEvent.VK_CONTROL, KeyEvent.VK_ALT, KeyEvent.VK_META).forEach {
            assertNull(look.modeAfter(false, KeyEvent.KEY_PRESSED, it), "a Ctrl+click is still a click")
        }
    }

    @Test
    fun releasesMovesAndAModeAlreadyRightChangeNothing() {
        assertNull(look.modeAfter(false, KeyEvent.KEY_RELEASED, KeyEvent.VK_TAB))
        assertNull(look.modeAfter(false, MouseEvent.MOUSE_RELEASED, null))
        assertNull(look.modeAfter(false, MouseEvent.MOUSE_MOVED, null))
        assertNull(look.modeAfter(true, KeyEvent.KEY_PRESSED, KeyEvent.VK_TAB))
        assertNull(look.modeAfter(false, MouseEvent.MOUSE_PRESSED, null))
    }

    @Test
    fun onlyTheKeyboardsFocusLightsStrongly() {
        val keys = DesktopRipples.rippleAlpha!!.focusedAlpha
        val mouse = MouseRipples.rippleAlpha!!.focusedAlpha
        assertEquals(0.1f, mouse, "a clicked button's focus as faint as Material's own")
        kotlin.test.assertTrue(keys > mouse)
    }
}
