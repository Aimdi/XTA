package com.aimdi.xtanative.ui.saved

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A bookmark or heart drawn as tapped before the device has written it. */
class OptimisticTest {
    @Test
    fun aTapShowsTheOtherAtOnce() {
        val tapped = Optimistic().tapped(actual = false)
        assertTrue(tapped.shown(actual = false))
        assertFalse(Optimistic().tapped(actual = true).shown(actual = true))
    }

    @Test
    fun theKeptValueTakesOverOnceItAgrees() {
        val tapped = Optimistic().tapped(actual = false)
        assertEquals(tapped, tapped.kept(actual = false), "not written yet: still as tapped")
        assertNull(tapped.kept(actual = true).asked)
        assertTrue(tapped.kept(actual = true).shown(actual = true))
    }

    @Test
    fun aSecondTapBeforeTheWriteTurnsItBack() {
        val twice = Optimistic().tapped(actual = false).tapped(actual = false)
        assertFalse(twice.shown(actual = false))
        assertNull(twice.kept(actual = false).asked)
    }

    @Test
    fun untouchedItShowsWhatIsKept() {
        assertTrue(Optimistic().shown(actual = true))
        assertFalse(Optimistic().shown(actual = false))
    }
}
