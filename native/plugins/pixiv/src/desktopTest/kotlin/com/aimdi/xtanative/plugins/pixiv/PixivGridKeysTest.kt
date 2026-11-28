package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A works grid's keys on the desktop, and which work each marks. */
class PixivGridKeysTest {
    private fun of(key: Key, shift: Boolean = false, ctrl: Boolean = false, alt: Boolean = false) = GridKeys.of(key, shift, ctrl, alt)

    @Test
    fun theListKeysOfTheAppMoveTheMark() {
        assertEquals(GridKey.NEXT, of(Key.J))
        assertEquals(GridKey.PREVIOUS, of(Key.K))
        assertEquals(GridKey.NEXT, of(Key.DirectionRight))
        assertEquals(GridKey.PREVIOUS, of(Key.DirectionLeft))
        assertEquals(GridKey.DOWN, of(Key.DirectionDown))
        assertEquals(GridKey.UP, of(Key.DirectionUp))
        assertEquals(GridKey.SCREEN_DOWN, of(Key.PageDown))
        assertEquals(GridKey.SCREEN_UP, of(Key.PageUp))
        assertEquals(GridKey.SCREEN_DOWN, of(Key.Spacebar))
        assertEquals(GridKey.SCREEN_UP, of(Key.Spacebar, shift = true))
        assertEquals(GridKey.FIRST, of(Key.MoveHome))
        assertEquals(GridKey.LAST, of(Key.MoveEnd))
        assertEquals(GridKey.OPEN, of(Key.Enter))
        assertEquals(GridKey.OPEN, of(Key.O))
        assertEquals(GridKey.MENU, of(Key.Menu))
        assertEquals(GridKey.MENU, of(Key.F10, shift = true))
    }

    @Test
    fun theAppsCombinationsAreLeftToIt() {
        assertEquals(GridKey.NONE, of(Key.J, ctrl = true))
        assertEquals(GridKey.NONE, of(Key.DirectionLeft, alt = true), "Alt+← goes back")
        assertEquals(GridKey.NONE, of(Key.MoveHome, alt = true), "Alt+Home goes home")
        assertEquals(GridKey.NONE, of(Key.J, shift = true))
        assertEquals(GridKey.NONE, of(Key.Escape))
        assertEquals(GridKey.NONE, of(Key.F10))
    }

    @Test
    fun theFirstKeyMarksTheFirstWorkOnScreen() {
        assertEquals(6, GridKeys.target(GridKey.NEXT, null, 40, 4, 12, firstShown = 6))
        assertEquals(6, GridKeys.target(GridKey.UP, null, 40, 4, 12, firstShown = 6))
        assertEquals(18, GridKeys.target(GridKey.SCREEN_DOWN, null, 40, 4, 12, firstShown = 6))
    }

    @Test
    fun rowsScreensAndEndsStayInsideTheList() {
        assertEquals(5, GridKeys.target(GridKey.NEXT, 4, 10, 4, 8, 0))
        assertEquals(8, GridKeys.target(GridKey.DOWN, 4, 10, 4, 8, 0))
        assertEquals(9, GridKeys.target(GridKey.DOWN, 7, 10, 4, 8, 0), "a row down past the end marks the last")
        assertEquals(0, GridKeys.target(GridKey.UP, 2, 10, 4, 8, 0))
        assertEquals(9, GridKeys.target(GridKey.SCREEN_DOWN, 4, 10, 4, 8, 0))
        assertEquals(0, GridKeys.target(GridKey.FIRST, 4, 10, 4, 8, 0))
        assertEquals(9, GridKeys.target(GridKey.LAST, 4, 10, 4, 8, 0))
        assertNull(GridKeys.target(GridKey.PREVIOUS, 0, 10, 4, 8, 0), "nothing before the first")
        assertNull(GridKeys.target(GridKey.NEXT, null, 0, 4, 8, 0), "nothing in an empty grid")
        assertNull(GridKeys.target(GridKey.OPEN, 3, 10, 4, 8, 0))
    }

    @Test
    fun aMarkedWorkIsScrolledJustIntoView() {
        assertEquals(0, Reveal.by(100, 300, 0, 800))
        assertEquals(-40, Reveal.by(-40, 160, 0, 800), "a work cut at the top comes down")
        assertEquals(100, Reveal.by(700, 900, 0, 800), "a work cut at the foot comes up")
        assertEquals(300, Reveal.by(300, 1400, 0, 800), "one taller than the view shows its top")
    }

    @Test
    fun worksComeAfterTheHeaderAndBeforeTheFooter() {
        assertEquals(3, GridPlaces.firstWorkItem(total = 24, works = 20))
        assertEquals(0, GridPlaces.firstWorkItem(total = 0, works = 0))
    }
}
