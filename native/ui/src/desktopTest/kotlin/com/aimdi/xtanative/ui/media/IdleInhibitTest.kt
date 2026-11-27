package com.aimdi.xtanative.ui.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The screen kept on while a video is heard playing in a window that shows, and let go otherwise. */
class IdleInhibitTest {
    private class FakeBus(var answers: Boolean = true) : InhibitBus {
        val calls = mutableListOf<String>()
        var alive = true
        private var next = 0

        override fun inhibit(reason: String): HeldInhibit? {
            calls += "inhibit"
            return if (answers) HeldInhibit.ScreenSaver(++next) else null
        }

        override fun release(held: HeldInhibit) {
            calls += "release ${(held as HeldInhibit.ScreenSaver).cookie}"
        }

        override fun holds(held: HeldInhibit): Boolean = alive
    }

    private val bus = FakeBus()
    private var shown = true
    private val inhibitor = IdleInhibitor(bus, { shown })
    private val video = Any()
    private val other = Any()

    @Test
    fun aVideoPlayingKeepsTheScreenOnUntilItPauses() {
        inhibitor.playing(video, true)
        assertTrue(inhibitor.holding)
        inhibitor.playing(video, true)
        assertEquals(listOf("inhibit"), bus.calls, "asked once")
        inhibitor.playing(video, false)
        assertFalse(inhibitor.holding)
        assertEquals(listOf("inhibit", "release 1"), bus.calls)
    }

    @Test
    fun theScreenSleepsOnlyOnceNoVideoPlays() {
        inhibitor.playing(video, true)
        inhibitor.playing(other, true)
        inhibitor.playing(video, false)
        assertTrue(inhibitor.holding, "the other plays on")
        inhibitor.playing(other, false)
        assertEquals(listOf("inhibit", "release 1"), bus.calls)
    }

    @Test
    fun aHiddenWindowLetsTheScreenSleepAndShowingItAgainKeepsItOn() {
        inhibitor.playing(video, true)
        shown = false
        inhibitor.check()
        assertFalse(inhibitor.holding)
        shown = true
        inhibitor.check()
        assertTrue(inhibitor.holding)
        assertEquals(listOf("inhibit", "release 1", "inhibit"), bus.calls)
    }

    @Test
    fun nothingPlayingNeverAsksAndAHiddenWindowNeitherAsks() {
        inhibitor.check()
        shown = false
        inhibitor.playing(video, true)
        assertEquals(emptyList(), bus.calls)
        assertFalse(inhibitor.holding)
    }

    @Test
    fun aBusThatWouldNotIsAskedAgainLaterAndAGoneConnectionHoldsNothing() {
        bus.answers = false
        inhibitor.playing(video, true)
        assertFalse(inhibitor.holding)
        bus.answers = true
        inhibitor.check()
        assertTrue(inhibitor.holding)
        // The bus went away: what it held went with it, so nothing is released; asked again on a new connection.
        bus.alive = false
        inhibitor.check()
        bus.alive = true
        assertEquals(listOf("inhibit", "inhibit", "inhibit"), bus.calls)
        assertTrue(inhibitor.holding)
    }

    @Test
    fun onlyAHeardVideoPlayingKeepsTheScreenOn() {
        assertTrue(IdleInhibitRules.keepsAwake(heard = true, playing = true))
        assertFalse(IdleInhibitRules.keepsAwake(heard = true, playing = false), "paused")
        assertFalse(IdleInhibitRules.keepsAwake(heard = false, playing = true), "muted, or a GIF")
        assertEquals(InhibitStep.TAKE, IdleInhibitRules.step(wanted = true, holding = false))
        assertEquals(InhibitStep.LET_GO, IdleInhibitRules.step(wanted = false, holding = true))
        assertEquals(InhibitStep.KEEP, IdleInhibitRules.step(wanted = true, holding = true))
        assertEquals(InhibitStep.KEEP, IdleInhibitRules.step(wanted = false, holding = false))
    }
}
