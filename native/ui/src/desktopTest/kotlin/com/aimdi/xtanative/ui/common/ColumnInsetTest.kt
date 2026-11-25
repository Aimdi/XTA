package com.aimdi.xtanative.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals

/** Where a top bar's contents stand over a centred column ([ColumnTopAppBar]). */
class ColumnInsetTest {
    @Test
    fun aBarOverAColumnLeavesTheSameRoomOnEitherSideAsTheColumn() {
        // The wide window beside the sidebar: 1280 dp of screen over the 720 dp reading column.
        assertEquals(280f, ReadingColumn.inset(1280f, ReadingColumn.MAX_WIDTH))
        assertEquals(ReadingColumn.inset(1280f), ReadingColumn.inset(1280f, ReadingColumn.MAX_WIDTH), "the reading column's own inset")
    }

    @Test
    fun aNarrowScreenKeepsItsBarAsItWas() {
        assertEquals(0f, ReadingColumn.inset(400f, ReadingColumn.MAX_WIDTH), "a phone")
        assertEquals(0f, ReadingColumn.inset(720f, ReadingColumn.MAX_WIDTH), "exactly the column")
    }

    @Test
    fun noColumnOrNoBoundLeavesNoRoom() {
        assertEquals(0f, ReadingColumn.inset(1600f, Float.POSITIVE_INFINITY), "a plugin's own screen: the bar across the screen")
        assertEquals(0f, ReadingColumn.inset(Float.POSITIVE_INFINITY, 720f))
    }
}
