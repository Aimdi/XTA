package com.aimdi.xtanative.ui.subscriptions

import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals

/** A group tile's unread dot sits on its face's rim, not at the cover's corner or the tile's edge. */
class CoverDotTest {
    @Test
    fun theDotIsOnTheFacesRimAt45Degrees() {
        val extent = 74f
        val centre = CoverDot.centre(extent)
        // From the face's centre to the dot's: the face's radius.
        val fromFace = hypot(extent / 2 - centre, extent / 2 - centre)
        assertEquals(extent * CoverDot.FACE / 2, fromFace, 0.01f)
    }

    @Test
    fun itScalesWithTheCover() {
        assertEquals(CoverDot.centre(40f) * 2, CoverDot.centre(80f), 0.001f)
    }
}
