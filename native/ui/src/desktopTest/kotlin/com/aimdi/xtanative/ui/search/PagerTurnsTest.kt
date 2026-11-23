package com.aimdi.xtanative.ui.search

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PagerTurnsTest {
    @Test
    fun aRestOnTheWayToATabPickedTwiceIsNotAPick() {
        val turns = PagerTurns()
        turns.started(3)
        // Ctrl+Tab again: the slide to 3 is cut short on 2 and a slide to 4 starts.
        turns.started(4)
        assertFalse(turns.picks(2), "the rest between the two slides")
        assertFalse(turns.picks(3))
        assertTrue(turns.picks(4))
    }

    @Test
    fun aSwipeOrASlideThatEndedPicksWhereThePagerRests() {
        val turns = PagerTurns()
        assertTrue(turns.picks(1), "a swipe")
        turns.started(2)
        turns.ended()
        assertTrue(turns.picks(0), "a drag that stopped the slide picks where it left the pager")
    }
}
