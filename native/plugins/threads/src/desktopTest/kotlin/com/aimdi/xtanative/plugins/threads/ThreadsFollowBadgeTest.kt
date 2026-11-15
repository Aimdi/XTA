package com.aimdi.xtanative.plugins.threads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The + on a Threads card's face: when it shows, and where, as X's badge. */
class ThreadsFollowBadgeTest {
    @Test
    fun theBadgeShowsOnlyForAnAuthorNotFollowedOnceTheFollowsAreKnown() {
        assertTrue(ThreadsFollowBadgeGeometry.shown(followsKnown = true, followed = false))
        assertFalse(ThreadsFollowBadgeGeometry.shown(followsKnown = true, followed = true))
        assertFalse(ThreadsFollowBadgeGeometry.shown(followsKnown = false, followed = false), "no + flashing on every card while the follows load")
    }

    @Test
    fun aFortyDpFaceGetsXsBadgeOnItsRim() {
        assertEquals(16.8f, ThreadsFollowBadgeGeometry.disc(40f), 0.01f)
        assertEquals(1.5f, ThreadsFollowBadgeGeometry.ring(16.8f))
        assertEquals(20f, ThreadsFollowBadgeGeometry.disc(80f), "never larger than 20 dp")
        assertEquals(34.14f, ThreadsFollowBadgeGeometry.center(40f), 0.01f)
    }
}
