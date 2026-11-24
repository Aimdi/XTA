package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** A sheet's list moved by the reading keys ([SheetKeys], [DesktopKeys.sheetScroll]). */
class SheetKeysTest {
    private fun scroll(key: DeskKey, shift: Boolean = false, ctrl: Boolean = false, alt: Boolean = false) = DesktopKeys.sheetScroll(key, shift, ctrl, alt)

    @Test
    fun theReadingKeysMoveTheSheetsList() {
        assertEquals(DeskAction.LINE_UP, scroll(DeskKey.UP))
        assertEquals(DeskAction.LINE_DOWN, scroll(DeskKey.DOWN))
        assertEquals(DeskAction.SCREEN_UP, scroll(DeskKey.PAGE_UP))
        assertEquals(DeskAction.SCREEN_DOWN, scroll(DeskKey.PAGE_DOWN))
        assertEquals(DeskAction.SCREEN_DOWN, scroll(DeskKey.SPACE))
        assertEquals(DeskAction.SCREEN_UP, scroll(DeskKey.SPACE, shift = true))
        assertEquals(DeskAction.TO_TOP, scroll(DeskKey.HOME))
        assertEquals(DeskAction.TO_END, scroll(DeskKey.END))
    }

    @Test
    fun modifiedKeysAndOthersAreLeftAlone() {
        assertEquals(DeskAction.NONE, scroll(DeskKey.DOWN, ctrl = true))
        assertEquals(DeskAction.NONE, scroll(DeskKey.DOWN, alt = true), "Alt+↓ moves a row")
        assertEquals(DeskAction.NONE, scroll(DeskKey.HOME, shift = true))
        assertEquals(DeskAction.NONE, scroll(DeskKey.ESCAPE), "Esc closes the sheet")
        assertEquals(DeskAction.NONE, scroll(DeskKey.ENTER))
        assertEquals(DeskAction.NONE, scroll(DeskKey.J))
    }

    @Test
    fun theListPointedAtComesBeforeTheFirst() {
        val keys = SheetKeys()
        assertNull(keys.list())
        val first = LazyListState()
        val second = ScrollState(0)
        keys.add(first)
        keys.add(second)
        assertSame(first, keys.list())
        keys.point(second)
        assertSame(second, keys.list())
        keys.remove(second)
        assertSame(first, keys.list())
    }
}
