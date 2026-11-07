package com.aimdi.xtanative.plugins.pixiv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The reader's page and the moves the bar, the sheets and the overview make on it. */
class PixivReaderSurfaceTest {
    @Test
    fun aStartIsHeldInsideTheWork() {
        assertEquals(4, ReaderSurface(5, 99) {}.page)
        assertEquals(0, ReaderSurface(5, -3) {}.page)
        assertEquals(0, ReaderSurface(0, 3) {}.page)
    }

    @Test
    fun aShownPageIsAJumpNotAMove() {
        val surface = ReaderSurface(5, 1) {}
        surface.showPage(3)
        assertEquals(1, surface.page)
        assertEquals(3, surface.jump)
        surface.showPage(99)
        assertEquals(4, surface.jump)
        assertEquals(1, surface.page)
    }

    @Test
    fun aLoadedPictureIsRememberedOnce() {
        val surface = ReaderSurface(2, 0) {}
        surface.shown("a")
        surface.shown("a")
        surface.shown("b")
        assertEquals(setOf("a", "b"), surface.loaded)
    }

    @Test
    fun theOverviewIsAskedForWhicheverWayItOpened() {
        val surface = ReaderSurface(2, 0) {}
        surface.openOverview(true)
        assertEquals(true, surface.overviewRequest)
        surface.openOverview(false)
        assertEquals(false, surface.overviewRequest)
    }

    @Test
    fun changingDirectionFlips() {
        var flipped = false
        ReaderSurface(2, 0) { flipped = true }.changeDirection()
        assertTrue(flipped)
    }
}
