package com.aimdi.xtanative.ui.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InlineTurnsTest {
    private val turns = InlineTurns<String, String>()
    private val holder get() = turns.holder.value?.key

    @Test
    fun aVideoTakesThePlayerAndAGifGetsItBackAfterward() {
        turns.want("gif", "loop.mp4")
        assertEquals("gif", holder)
        turns.ask("video", "clip.mp4")
        assertEquals(Turn("video", "clip.mp4"), turns.holder.value)
        turns.drop("video")
        assertEquals("gif", holder)
        turns.drop("gif")
        assertNull(holder)
    }

    @Test
    fun theVideoAskedLastWinsAndTheOneBeforeWaitsToBeAskedAgain() {
        turns.ask("first", "a.mp4")
        turns.ask("second", "b.mp4")
        assertEquals("second", holder)
        turns.drop("second")
        assertNull(holder)
        turns.ask("first", "a.mp4")
        assertEquals("first", holder)
    }

    @Test
    fun aGifNeverTakesItFromAVideoAndTheLatestGifPlays() {
        turns.ask("video", "clip.mp4")
        turns.want("gif one", "1.mp4")
        turns.want("gif two", "2.mp4")
        assertEquals("video", holder)
        turns.drop("video")
        assertEquals("gif two", holder)
        turns.want("gif one", "1.mp4")
        assertEquals("gif one", holder)
    }

    @Test
    fun whileFullScreenTheHolderKeepsItAndItsDropWaits() {
        turns.ask("video", "clip.mp4")
        turns.pin("other")
        assertFalse(turns.pinned)
        turns.pin("video")
        assertTrue(turns.pinned)
        turns.ask("next", "next.mp4")
        turns.want("gif", "loop.mp4")
        turns.drop("video")
        assertEquals("video", holder)
        turns.unpin("video")
        assertFalse(turns.pinned)
        assertEquals("gif", holder)
    }

    @Test
    fun aKeptPlayerGoesOnceNeitherItsPlaceNorTheFullScreenShowsIt() {
        var released = 0
        val inPlace = KeptPlayer { released++ }
        inPlace.left()
        inPlace.left()
        assertEquals(1, released)
        val fullscreen = KeptPlayer { released++ }
        fullscreen.enteredFullscreen()
        fullscreen.left()
        assertEquals(1, released)
        fullscreen.leftFullscreen()
        assertEquals(2, released)
        val back = KeptPlayer { released++ }
        back.enteredFullscreen()
        back.leftFullscreen()
        assertEquals(2, released)
    }

    @Test
    fun oneVideoStartingPausesTheOneBefore() {
        val paused = mutableListOf<String>()
        OneVideo.started("a") { paused += "a" }
        OneVideo.started("a") { paused += "a" }
        assertEquals(emptyList(), paused)
        OneVideo.started("b") { paused += "b" }
        assertEquals(listOf("a"), paused)
        OneVideo.stopped("a")
        OneVideo.stopped("b")
        OneVideo.started("c") { paused += "c" }
        assertEquals(listOf("a"), paused)
        OneVideo.stopped("c")
    }
}
