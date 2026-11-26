package com.aimdi.xtanative.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The one clock relative times move on by: due at the whole minute, running only while a time is shown. */
class MinuteClockTest {
    @Test
    fun theNextTickIsAtTheWholeMinute() {
        assertEquals(60_000L, MinuteTicks.untilNextMinute(120_000L))
        assertEquals(30_000L, MinuteTicks.untilNextMinute(90_000L))
        assertEquals(1_000L, MinuteTicks.untilNextMinute(119_800L))
    }

    @Test
    fun itRunsWhileOneReadsIt() {
        assertFalse(MinuteClock.running())
        MinuteClock.join()
        MinuteClock.join()
        assertTrue(MinuteClock.running())
        MinuteClock.leave()
        assertTrue(MinuteClock.running())
        MinuteClock.leave()
        assertFalse(MinuteClock.running())
        MinuteClock.leave()
        assertFalse(MinuteClock.running())
    }
}
