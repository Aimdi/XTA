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

    @Test
    fun eachPageTakesItsOwnShapeOnceItsPictureIsIn() {
        val surface = ReaderSurface(3, 0, firstRatio = 0.75f) {}
        assertEquals(0.75f, surface.ratioOf(2), "a page not in yet takes the first page's shape")
        surface.measured(2, 1200, 600)
        surface.measured(1, 690, 13_800)
        assertEquals(2f, surface.ratioOf(2))
        assertEquals(0.05f, surface.ratioOf(1), "a long strip is drawn whole, not cut to a fixed shape")
        assertEquals(0.75f, surface.ratioOf(0))
        surface.measured(0, 0, 100)
        assertEquals(0.75f, surface.ratioOf(0), "a picture without a size changes nothing")
        assertEquals(1f, ReaderSurface(1, 0, firstRatio = Float.NaN) {}.ratioOf(0))
        assertEquals(0.02f, ReaderSurface(1, 0, firstRatio = 0.001f) {}.ratioOf(0))
    }
}
