package com.aimdi.xtanative.ui.subscriptions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UngroupedSorterTest {
    @Test
    fun eachChoiceMovesToTheNextAccountAndTheRoundEnds() {
        var state = SorterState(listOf("a", "b", "c"))
        assertEquals("a", state.current)
        state = state.decide(listOf("g1"))
        state = state.decide(emptyList())
        assertEquals("c", state.current)
        assertEquals(2, state.position)
        state = state.decide(listOf("g1", "g2"))
        assertTrue(state.finished)
        assertNull(state.current)
        assertEquals(2, state.placedCount)
        assertEquals(listOf("b"), state.skipped)
        assertEquals(state, state.decide(listOf("g3")), "nothing is left to decide")
    }

    @Test
    fun undoBringsTheLastAccountBackFromTheOtherSide() {
        val state = SorterState(listOf("a", "b")).decide(listOf("g1")).undo()
        assertEquals("a", state.current)
        assertTrue(state.backward)
        assertFalse(state.decide(emptyList()).backward)
        assertEquals(SorterState(listOf("a")), SorterState(listOf("a")).undo())
    }

    @Test
    fun theSkippedGetARoundOfTheirOwn() {
        val end = SorterState(listOf("a", "b", "c")).decide(emptyList()).decide(listOf("g")).decide(emptyList())
        assertEquals(SorterState(listOf("a", "c")), end.skippedRound())
        assertTrue(end.withSaving(true).saving)
    }
}
