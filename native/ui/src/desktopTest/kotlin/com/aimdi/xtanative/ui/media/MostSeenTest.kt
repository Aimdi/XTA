package com.aimdi.xtanative.ui.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MostSeenTest {
    @Test
    fun theTileSeenMostPlays() {
        val seen = MostSeen<String>()
        seen.report("a", 0.7f)
        seen.report("b", 0.9f)
        assertEquals("b", seen.chosen.value)
        seen.report("b", 0.4f)
        assertEquals("a", seen.chosen.value)
    }

    @Test
    fun aTileUnderTheShareIsNeverChosen() {
        val seen = MostSeen<String>()
        seen.report("a", 0.59f)
        assertNull(seen.chosen.value)
        seen.report("a", 0.6f)
        assertEquals("a", seen.chosen.value)
        seen.report("a", 0f)
        assertNull(seen.chosen.value)
    }

    @Test
    fun aRowTallerThanTheScreenStillPlaysItsVideo() {
        // The row of a thread runs past the screen, but its video is whole on it: the video is what counts.
        val seen = MostSeen<String>()
        seen.report("video in a tall thread", 1f)
        assertEquals("video in a tall thread", seen.chosen.value)
    }

    @Test
    fun theChosenTileKeepsItsTurnOnATie() {
        val seen = MostSeen<String>()
        seen.report("a", 1f)
        seen.report("b", 1f)
        assertEquals("a", seen.chosen.value)
        seen.report("a", 0.8f)
        seen.report("a", 1f)
        assertEquals("b", seen.chosen.value, "b was seen more meanwhile, and keeps it on the tie")
    }

    @Test
    fun aForgottenTileGivesItsTurnToTheNext() {
        val seen = MostSeen<String>()
        seen.report("a", 1f)
        seen.report("b", 0.8f)
        seen.forget("a")
        assertEquals("b", seen.chosen.value)
        seen.forget("b")
        assertNull(seen.chosen.value)
    }
}
