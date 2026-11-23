package com.aimdi.xtanative.ui.saved

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoteKeysTest {
    @Test
    fun ctrlSAndCtrlEnterSave() {
        assertTrue(NoteKeys.saves(s = true, enter = false, ctrl = true, other = false))
        assertTrue(NoteKeys.saves(s = false, enter = true, ctrl = true, other = false))
        // Enter alone types a new line; Ctrl+Shift+S and the rest are not the editor's.
        assertFalse(NoteKeys.saves(s = false, enter = true, ctrl = false, other = false))
        assertFalse(NoteKeys.saves(s = true, enter = false, ctrl = true, other = true))
        assertFalse(NoteKeys.saves(s = false, enter = false, ctrl = true, other = false))
    }
}
