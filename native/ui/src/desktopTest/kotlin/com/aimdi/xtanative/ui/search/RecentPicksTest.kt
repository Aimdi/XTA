package com.aimdi.xtanative.ui.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecentPicksTest {
    @Test
    fun downGoesFromTheFieldToTheFirstAndOnToTheLastWhereItStops() {
        assertEquals(0, RecentPicks.moved(null, 3, 1))
        assertEquals(1, RecentPicks.moved(0, 3, 1))
        assertEquals(2, RecentPicks.moved(1, 3, 1))
        assertEquals(2, RecentPicks.moved(2, 3, 1), "no wrap at the end of the row")
    }

    @Test
    fun upGoesBackAndFromTheFirstToTheTypedText() {
        assertEquals(1, RecentPicks.moved(2, 3, -1))
        assertNull(RecentPicks.moved(0, 3, -1), "back to what is typed")
        assertNull(RecentPicks.moved(null, 3, -1), "no wrap to the last either")
    }

    @Test
    fun nothingIsHighlightedWithoutSearchesAndOneTakenAwayMovesOnFromTheLastLeft() {
        assertNull(RecentPicks.moved(null, 0, 1))
        assertNull(RecentPicks.moved(4, 0, -1))
        assertEquals(1, RecentPicks.moved(5, 2, 1), "the list shrank under the highlight")
        assertEquals(0, RecentPicks.moved(5, 2, -1))
    }

    @Test
    fun upWithNothingHighlightedIsLeftToTheField() {
        assertTrue(RecentPicks.takes(null, 3, 1))
        assertFalse(RecentPicks.takes(null, 3, -1), "the caret goes to the start, as before")
        assertTrue(RecentPicks.takes(1, 3, -1))
        assertTrue(RecentPicks.takes(2, 3, 1), "↓ at the last stays with the list")
        assertFalse(RecentPicks.takes(null, 0, 1), "no searches shown: the field's own ↓")
    }

    @Test
    fun theHighlightOnlyCountsWhileItsSearchIsShown() {
        val pick = RecentPick()
        pick.index = 2
        assertEquals(2, pick.of(listOf("a", "b", "c")))
        assertNull(pick.of(listOf("a", "b")))
        assertNull(pick.of(emptyList()), "hidden once something is searched")
    }
}
