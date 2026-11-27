package com.aimdi.xtanative.ui.common

import com.aimdi.xtanative.ui.home.DesktopActions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EscapeClearsTest {
    @Test
    fun theFirstEscEmptiesAFieldWithWordsAndTheNextLeavesIt() {
        val actions = DesktopActions()
        var text = "flutter"
        val field = Any()
        actions.addFieldEscape(field) { EscapeClears.escape(focused = true, text = text) { text = "" } }
        assertTrue(actions.escapeInField(), "the first Esc empties the field and keeps the keyboard there")
        assertEquals("", text)
        assertFalse(actions.escapeInField(), "the next, with nothing to empty, leaves the field")
    }

    @Test
    fun aFieldWithoutTheKeyboardIsLeftAlone() {
        var text = "flutter"
        assertFalse(EscapeClears.escape(focused = false, text = text) { text = "" })
        assertEquals("flutter", text)
    }

    @Test
    fun anEmptyFieldHasNothingToEmpty() {
        var cleared = false
        assertFalse(EscapeClears.escape(focused = true, text = "") { cleared = true })
        assertFalse(cleared)
        assertTrue(EscapeClears.clears(" "))
    }
}
