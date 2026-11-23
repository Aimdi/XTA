package com.aimdi.xtanative.ui.home

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Alt+↑ and Alt+↓ on a reorderable list's row ([RowSteps]). */
class RowStepsTest {
    @Test
    fun aRowMovesOnePlaceAndStopsAtTheEnds() {
        assertEquals(1, RowSteps.target(2, up = true, within = 0..4))
        assertEquals(3, RowSteps.target(2, up = false, within = 0..4))
        assertNull(RowSteps.target(0, up = true, within = 0..4), "the first row goes no higher")
        assertNull(RowSteps.target(4, up = false, within = 0..4), "the last goes no lower")
        assertNull(RowSteps.target(0, up = false, within = 0..0), "a list of one")
        assertNull(RowSteps.target(0, up = false, within = IntRange.EMPTY))
    }

    @Test
    fun aRowStaysInItsPart() {
        assertNull(RowSteps.target(5, up = true, within = 5..7))
        assertEquals(6, RowSteps.target(5, up = false, within = 5..7))
        assertNull(RowSteps.target(-1, up = false, within = 0..3), "a row no longer there")
        assertNull(RowSteps.target(9, up = true, within = 0..3))
    }

    @Test
    fun onlyAltWithAnArrowMoves() {
        assertEquals(true, RowSteps.direction(Key.DirectionUp, alt = true, ctrl = false, shift = false, meta = false))
        assertEquals(false, RowSteps.direction(Key.DirectionDown, alt = true, ctrl = false, shift = false, meta = false))
        assertNull(RowSteps.direction(Key.DirectionUp, alt = false, ctrl = false, shift = false, meta = false))
        assertNull(RowSteps.direction(Key.DirectionUp, alt = true, ctrl = true, shift = false, meta = false))
        assertNull(RowSteps.direction(Key.DirectionDown, alt = true, ctrl = false, shift = true, meta = false))
        assertNull(RowSteps.direction(Key.DirectionDown, alt = true, ctrl = false, shift = false, meta = true))
        assertNull(RowSteps.direction(Key.DirectionLeft, alt = true, ctrl = false, shift = false, meta = false))
    }
}
