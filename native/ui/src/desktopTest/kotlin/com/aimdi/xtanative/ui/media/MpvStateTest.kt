package com.aimdi.xtanative.ui.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MpvStateTest {
    private fun property(property: MpvProperty, value: Double) = MpvEvent(MpvEvent.PROPERTY_CHANGE, reply = property.id, value = value)

    private fun start(entry: Long) = MpvEvent(MpvEvent.START_FILE, entry = entry)

    private fun end(entry: Long, reason: Int, error: Int = 0) = MpvEvent(MpvEvent.END_FILE, entry = entry, endReason = reason, endError = error)

    private val restart = MpvEvent(MpvEvent.PLAYBACK_RESTART)

    /** A file playing: opened as source 1, mpv's entry 7, started, ready, unpaused, 3 s long. */
    private val playing = MpvState().opening(1, 0).opened(1, 7).after(start(7)).after(restart)
        .after(property(MpvProperty.PAUSE, 0.0)).after(property(MpvProperty.DURATION, 3.0)).after(property(MpvProperty.POSITION, 1.25))

    @Test
    fun aPlayingFileReadsAsMediaKitsState() {
        assertTrue(playing.ready)
        assertTrue(playing.playing)
        assertEquals(PlaybackFrame(positionMs = 1_250, durationMs = 3_000, playing = true), playing.frame)
    }

    @Test
    fun openingForgetsTheFileButKeepsThePlayersOwnSettings() {
        val loud = playing.after(property(MpvProperty.SPEED, 1.5)).after(property(MpvProperty.VOLUME, 40.0)).after(property(MpvProperty.MUTED, 1.0))
        val next = loud.opening(2, 12_000)
        assertEquals(MpvState(load = 2, positionMs = 12_000, paused = false, speed = 1.5, volume = 40.0, muted = true), next)
        assertFalse(next.playing, "nothing plays before mpv took the command")
    }

    @Test
    fun whatMpvSaysOfTheFileBeforeCountsOnlyOnceTheNewOneStarted() {
        val next = playing.after(property(MpvProperty.ENDED, 1.0)).opening(2, 0).opened(2, 8)
        val stale = next.after(property(MpvProperty.ENDED, 1.0)).after(property(MpvProperty.DURATION, 3.0)).after(end(7, MpvEvent.END_STOP)).after(start(7))
        assertFalse(stale.ended)
        assertEquals(0, stale.durationMs)
        assertFalse(stale.started)
        val started = stale.after(start(8)).after(property(MpvProperty.DURATION, 9.5))
        assertTrue(started.started)
        assertEquals(9_500, started.durationMs)
    }

    @Test
    fun thePlayersOwnStateCountsAtOnce() {
        val opening = MpvState().opening(1, 0).after(property(MpvProperty.PAUSE, 0.0)).after(property(MpvProperty.SPEED, 2.0))
        assertFalse(opening.paused)
        assertEquals(2.0, opening.speed)
    }

    @Test
    fun aRefusedOpenOrAnErrorOfItsFileFailsIt() {
        assertTrue(MpvState().opening(1, 0).opened(1, -13).failed)
        assertEquals(-13, MpvState().opening(1, 0).opened(1, -13).error)
        val failed = MpvState().opening(1, 0).opened(1, 7).after(start(7)).after(end(7, MpvEvent.END_ERROR, -13))
        assertTrue(failed.failed)
        assertFalse(failed.playing)
        assertTrue(failed.frame.failed)
        assertFalse(failed.after(restart).ready, "a failed file is never ready")
    }

    @Test
    fun anotherSourcesEndsAreNotThisOnes() {
        assertEquals(playing, playing.after(end(6, MpvEvent.END_ERROR, -13)))
        assertEquals(playing, playing.opened(2, 9), "an answer for a source replaced since")
        assertEquals(playing, playing.after(end(7, MpvEvent.END_STOP)), "a stop is the app's own doing")
    }

    @Test
    fun theEndIsCompletedAndNotPlaying() {
        val ended = playing.after(property(MpvProperty.ENDED, 1.0))
        assertTrue(ended.frame.completed)
        assertFalse(ended.frame.playing)
        assertTrue(playing.after(end(7, MpvEvent.END_EOF)).ended)
        assertFalse(ended.after(property(MpvProperty.ENDED, 0.0)).ended, "sought back from the end")
        assertFalse(ended.after(property(MpvProperty.ENDED, Double.NaN)).ended)
    }

    @Test
    fun aRestartBeforeTheFileStartedIsNotItsFirstFrame() {
        assertFalse(MpvState().opening(1, 0).opened(1, 7).after(restart).ready)
    }

    @Test
    fun valuesWithoutAValueKeepOrClearAsTheirMeaningSays() {
        val unknown = playing.after(property(MpvProperty.POSITION, Double.NaN)).after(property(MpvProperty.DURATION, Double.NaN))
        assertEquals(1_250, unknown.positionMs, "the place stays while mpv knows none")
        assertEquals(0, unknown.durationMs)
        val sized = playing.after(property(MpvProperty.WIDTH, 640.0)).after(property(MpvProperty.HEIGHT, 360.0))
        assertEquals(640 to 360, sized.width to sized.height)
        assertEquals(0, sized.after(property(MpvProperty.WIDTH, Double.NaN)).width)
        assertEquals(4_000, playing.after(property(MpvProperty.CACHED, 4.0)).cachedMs)
        assertEquals(0, MpvState.millis(-0.2))
    }

    @Test
    fun stoppingLeavesNothingOpen() {
        val stopped = playing.stopped()
        assertEquals(0, stopped.load)
        assertFalse(stopped.playing)
        assertEquals(PlaybackFrame(), stopped.frame)
    }

    @Test
    fun aTurnIsAQuarterTurnOrNone() {
        assertEquals(270, MpvState.rotationOf("270"))
        assertEquals(90, MpvState.rotationOf("-270"))
        assertEquals(0, MpvState.rotationOf("360"))
        assertEquals(0, MpvState.rotationOf("45"))
        assertEquals(0, MpvState.rotationOf(null))
        assertEquals(0, MpvState.rotationOf("left"))
        assertEquals(0, playing.opening(2, 0).rotation, "a new file is upright until mpv says otherwise")
    }

    @Test
    fun eachPropertyIsKnownByItsNumber() {
        MpvProperty.entries.forEach { assertEquals(it, MpvProperty.of(it.id)) }
        assertNull(MpvProperty.of(0))
        assertNull(MpvProperty.of(MpvProperty.entries.size + 1L))
        assertEquals(playing, playing.after(MpvEvent(MpvEvent.PROPERTY_CHANGE, reply = 99, value = 1.0)))
    }
}
