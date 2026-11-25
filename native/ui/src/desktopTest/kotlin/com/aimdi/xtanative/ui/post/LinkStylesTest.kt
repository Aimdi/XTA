package com.aimdi.xtanative.ui.post

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A post's links read as links at rest, and show the pointer, the keys and a press where they land. */
class LinkStylesTest {
    private val blue = Color(0xFF1D9BF0)
    private val styles = linkStyles(blue)

    @Test
    fun atRestALinkIsOnlyColoured() {
        assertEquals(blue, styles.style?.color)
        assertNull(styles.style?.textDecoration)
    }

    @Test
    fun thePointerAndTheKeysUnderlineIt() {
        assertEquals(TextDecoration.Underline, styles.hoveredStyle?.textDecoration)
        assertEquals(TextDecoration.Underline, styles.focusedStyle?.textDecoration)
        assertEquals(blue, styles.hoveredStyle?.color)
    }

    @Test
    fun aPressWashesItInItsColour() {
        assertEquals(blue.copy(alpha = LINK_PRESSED_ALPHA), styles.pressedStyle?.background)
        assertEquals(TextDecoration.Underline, styles.pressedStyle?.textDecoration)
    }
}
