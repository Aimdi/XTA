package com.aimdi.xtanative.ui.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** "You're caught up" in a row of its own: from a thread's place to the list's row and back. */
class CaughtUpRowsTest {
    private val rows = CaughtUpRows(divider = 3, leading = 1)

    @Test
    fun withoutADividerTheRowsAreTheThreadsAfterTheNotices() {
        val plain = CaughtUpRows(divider = null, leading = 1)
        assertEquals(5 to 20, plain.rowOf(4, 20))
        assertEquals(4 to 20, plain.threadOf(5, 20, size = 300))
        assertEquals(0 to 7, plain.threadOf(0, 7, size = 40), "the notice is the first thread's place")
    }

    @Test
    fun threadsAboveTheDividerKeepTheirRows() {
        assertEquals(3 to 15, rows.rowOf(2, 15))
        assertEquals(2 to 15, rows.threadOf(3, 15, size = 300))
    }

    @Test
    fun threadsUnderTheDividerAreOneRowFurther() {
        assertEquals(6 to 10, rows.rowOf(4, 10))
        assertEquals(4 to 10, rows.threadOf(6, 10, size = 300))
    }

    @Test
    fun goingBackToTheLastReadThreadShowsTheDivider() {
        assertEquals(4 to 0, rows.rowOf(3, 0), "the divider's row")
        assertEquals(5 to 12, rows.rowOf(3, 12), "scrolled into the thread: its own row")
        assertEquals(3 to 12, rows.threadOf(5, 12, size = 300))
    }

    @Test
    fun theDividersOwnRowIsTheThreadUnderItScrolledLessThanNothing() {
        val kept = rows.threadOf(4, 10, size = 40)
        assertEquals(3 to -30, kept)
        assertEquals(4 to 0, rows.rowOf(kept.first, kept.second), "and it comes back at the divider")
    }

    @Test
    fun anOffsetLessThanNothingIsNoneAboveTheDivider() = assertEquals(3 to 0, rows.rowOf(2, -30))

    @Test
    fun aDividerPastTheThreadsIsNone() {
        assertNull(CaughtUpRows.at(5, count = 5))
        assertNull(CaughtUpRows.at(-1, count = 5))
        assertEquals(0, CaughtUpRows.at(0, count = 5))
        assertEquals(CaughtUpRows(null, 2), CaughtUpRows.of(5, count = 5, leading = 2))
    }
}
