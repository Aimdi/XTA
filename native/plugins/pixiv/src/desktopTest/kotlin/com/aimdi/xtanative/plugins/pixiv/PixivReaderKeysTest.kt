package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The reader's keys on the desktop, and what they do to its page and zoom. */
class PixivReaderKeysTest {
    private fun of(key: Key, shift: Boolean = false, ctrl: Boolean = false, alt: Boolean = false, rightToLeft: Boolean = false) =
        ReaderKeys.of(key, shift, ctrl, alt, rightToLeft)

    @Test
    fun arrowsAndPageKeysTurnThePageAndArrowsFollowTheReadingDirection() {
        assertEquals(ReaderKey.PREVIOUS, of(Key.DirectionLeft))
        assertEquals(ReaderKey.NEXT, of(Key.DirectionRight))
        assertEquals(ReaderKey.NEXT, of(Key.DirectionLeft, rightToLeft = true))
        assertEquals(ReaderKey.PREVIOUS, of(Key.DirectionRight, rightToLeft = true))
        assertEquals(ReaderKey.PREVIOUS, of(Key.PageUp, rightToLeft = true), "Page Up is always the page before")
        assertEquals(ReaderKey.NEXT, of(Key.PageDown))
        assertEquals(ReaderKey.FIRST, of(Key.MoveHome))
        assertEquals(ReaderKey.LAST, of(Key.MoveEnd))
    }

    @Test
    fun zoomSaveAndQualityKeys() {
        assertEquals(ReaderKey.ZOOM_IN, of(Key.Equals, shift = true), "+ is Shift and = on many keyboards")
        assertEquals(ReaderKey.ZOOM_IN, of(Key.Plus))
        assertEquals(ReaderKey.ZOOM_IN, of(Key.NumPadAdd))
        assertEquals(ReaderKey.ZOOM_OUT, of(Key.Minus))
        assertEquals(ReaderKey.ZOOM_RESET, of(Key.Zero))
        assertEquals(ReaderKey.DOWNLOAD, of(Key.D))
        assertEquals(ReaderKey.HD, of(Key.H))
        assertEquals(ReaderKey.SCREEN_DOWN, of(Key.Spacebar))
        assertEquals(ReaderKey.SCREEN_UP, of(Key.Spacebar, shift = true))
    }

    @Test
    fun theAppsOwnCombinationsAreLeftToIt() {
        assertEquals(ReaderKey.NONE, of(Key.Equals, ctrl = true), "Ctrl and + is the app's text size")
        assertEquals(ReaderKey.NONE, of(Key.DirectionLeft, alt = true), "Alt and ← goes back")
        assertEquals(ReaderKey.NONE, of(Key.H, ctrl = true), "Ctrl+H is History")
        assertEquals(ReaderKey.NONE, of(Key.D, shift = true))
        assertEquals(ReaderKey.NONE, of(Key.Escape), "Esc is the app's, which closes the reader")
        assertEquals(ReaderKey.NONE, of(Key.J))
    }

    @Test
    fun pagesTurnInsideTheWorkAndAKeyPastAnEndIsStillTheReaders() {
        val surface = ReaderSurface(3, 1) {}
        assertEquals(true, surface.key(ReaderKey.NEXT))
        assertEquals(2, surface.jump)
        surface.jump = null
        surface.page = 2
        assertEquals(true, surface.key(ReaderKey.NEXT))
        assertNull(surface.jump, "no page after the last")
        surface.key(ReaderKey.FIRST)
        assertEquals(0, surface.jump)
        assertNull(ReaderKeys.target(ReaderKey.LAST, 2, 3))
        assertNull(ReaderKeys.target(ReaderKey.ZOOM_IN, 0, 3))
    }

    @Test
    fun otherKeysGoToTheWayOfReadingShown() {
        val surface = ReaderSurface(3, 0) {}
        assertEquals(false, surface.key(ReaderKey.ZOOM_IN), "nothing shown takes it")
        val asked = mutableListOf<ReaderKey>()
        surface.modeKeys = { asked += it; true }
        assertEquals(true, surface.key(ReaderKey.ZOOM_IN))
        assertEquals(listOf(ReaderKey.ZOOM_IN), asked)
        assertEquals(1, zoomStep(ReaderKey.ZOOM_IN))
        assertEquals(0, zoomStep(ReaderKey.ZOOM_RESET))
        assertNull(zoomStep(ReaderKey.LINE_DOWN))
    }

    @Test
    fun zoomKeysStepBetweenThePagesOwnSizeAndTheMost() {
        assertEquals(1.25f, steppedScale(1f, 1, 4f))
        assertEquals(4f, steppedScale(3.5f, 1, 4f))
        assertEquals(1f, steppedScale(1.2f, -1, 4f), "a step out near its own size lands on it")
        assertEquals(1f, steppedScale(3f, 0, 4f))
        val column = ColumnZoom()
        column.step(1, IntSize(100, 100))
        assertEquals(1.25f, column.scale)
        column.step(0, IntSize(100, 100))
        assertEquals(1f, column.scale)
        val page = PageZoom()
        page.step(1)
        page.offset = Offset(10f, 0f)
        page.step(1)
        assertEquals(Offset(12.5f, 0f), page.offset, "the spot zoomed into stays in view")
        page.step(0)
        assertEquals(Offset.Zero, page.offset)
    }
}
