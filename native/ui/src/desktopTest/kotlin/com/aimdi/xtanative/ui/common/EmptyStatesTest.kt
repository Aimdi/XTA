package com.aimdi.xtanative.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals

/** Where an empty state stands: about a third of the way down the room it has. */
class EmptyStatesTest {
    @Test
    fun itStandsAboutAThirdOfTheWayDown() {
        assertEquals(228f, EmptyStates.top(900f), "a 900dp window: its third, less the icon and title above it")
        assertEquals(168f, EmptyStates.top(720f))
    }

    @Test
    fun aShortOrUnboundedRoomStillLeavesALittleOverIt() {
        assertEquals(24f, EmptyStates.top(200f))
        assertEquals(24f, EmptyStates.top(Float.POSITIVE_INFINITY))
    }
}
