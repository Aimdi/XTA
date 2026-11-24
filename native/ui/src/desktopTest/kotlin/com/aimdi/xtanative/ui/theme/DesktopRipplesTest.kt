package com.aimdi.xtanative.ui.theme

import androidx.compose.material3.ExperimentalMaterial3Api
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalMaterial3Api::class)
class DesktopRipplesTest {
    @Test
    fun theKeyboardsFocusShowsWellAboveThePointersHoverAndAPress() {
        val alpha = assertNotNull(DesktopRipples.rippleAlpha)
        assertTrue(alpha.focusedAlpha >= 3 * alpha.hoveredAlpha, "focus three times the hover, at least")
        assertTrue(alpha.focusedAlpha > alpha.pressedAlpha && alpha.focusedAlpha > alpha.draggedAlpha)
        assertTrue(alpha.focusedAlpha < 0.5f, "the button's label still reads under it")
    }
}
