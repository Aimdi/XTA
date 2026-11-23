package com.aimdi.xtanative.ui.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun aHeldKeyMovesTheSameRowAlong() {
        // Each step is found by key in the list as the one before left it, as a key repeat finds it.
        var rows = listOf("a", "b", "c", "d")
        repeat(3) { rows = RowSteps.step(rows, "d", up = true)!!.let { (from, to) -> moved(rows, from, to) } }
        assertEquals(listOf("d", "a", "b", "c"), rows)
        assertNull(RowSteps.step(rows, "d", up = true), "at the top")
        assertEquals(0 to 1, RowSteps.step(rows, "d", up = false))
        assertNull(RowSteps.step(rows, "x", up = false), "a row no longer there")
    }

    @Test
    fun theMovedRowStaysTheOneUnderAStillPointer() {
        val rows = mutableListOf("a", "b", "c")
        val moves = RowKeyMoves { key, up -> RowSteps.step(rows, key, up)?.also { (from, to) -> rows.add(to, rows.removeAt(from)) } != null }
        val still = Offset(40f, 150f)
        moves.pointed("c", still)
        assertTrue(moves.moveHovered(up = true))
        assertEquals(listOf("a", "c", "b"), rows)
        // The window tells the rows that changed places under the still pointer: b is under it now.
        moves.left("c", still)
        moves.pointed("b", still)
        assertEquals("c", moves.hovered, "the moved row is still the one the keys move")
        assertTrue(moves.moveHovered(up = true))
        moves.pointed("b", Offset(40.2f, 150f))
        assertTrue(moves.moveHovered(up = false), "rounding is no move")
        assertEquals(listOf("a", "c", "b"), rows, "c went up twice and down once, never back and forth with b")
        moves.pointed("b", Offset(40f, 170f))
        assertEquals("b", moves.hovered, "a pointer that moves points again")
        moves.left("b", Offset(40f, 400f))
        assertNull(moves.hovered)
    }

    @Test
    fun withoutAMoveTheRowUnderThePointerIsTheOne() {
        val moves = RowKeyMoves { _, _ -> false }
        val still = Offset(10f, 10f)
        moves.pointed("a", still)
        assertFalse(moves.moveHovered(up = true), "at an end")
        moves.left("a", still)
        moves.pointed("b", still)
        assertEquals("b", moves.hovered, "nothing moved, so the window's own events are heard")
    }

    private fun moved(rows: List<String>, from: Int, to: Int) = rows.toMutableList().apply { add(to, removeAt(from)) }

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
