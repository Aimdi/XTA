package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.threads.ThreadsMusic
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsPostFacts
import com.aimdi.xtanative.ui.media.ContinuityPlayer
import com.aimdi.xtanative.ui.media.PlaybackFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** A song's play button: one song at a time, the feed's video waiting, a preview that ends, and what a screen reader hears. */
class ThreadsSongsTest {
    private val song = ThreadsMusic("Made-up Song", "Made-up Band", audioUrl = "https://cdn.example/a.m4a", durationMs = 120_000, startMs = 30_000, lengthMs = 15_000)
    private val other = ThreadsMusic("Other", audioUrl = "https://cdn.example/b.m4a")

    @Test
    fun aPreviewPlaysFromItsStartForItsLengthAndNotPastTheSong() {
        assertEquals(SongWindow(30_000, 45_000), ThreadsSongRules.window(song))
        assertEquals(SongWindow(0, ThreadsSongRules.PREVIEW_MS), ThreadsSongRules.window(other), "30 seconds from the start where Meta says nothing")
        assertEquals(SongWindow(110_000, 120_000), ThreadsSongRules.window(song.copy(startMs = 110_000, lengthMs = null)), "never past the song's end")
        assertEquals(SongWindow(0, 15_000), ThreadsSongRules.window(song.copy(startMs = 500_000)), "a start past the end is the start")
        assertTrue(ThreadsSongRules.ended(45_000, ThreadsSongRules.window(song), completed = false))
        assertFalse(ThreadsSongRules.ended(44_000, ThreadsSongRules.window(song), completed = false))
        assertTrue(ThreadsSongRules.ended(0, ThreadsSongRules.window(song), completed = true), "the file's own end ends it")
    }

    @Test
    fun oneSongPlaysAtATimeAndTheFeedsVideoWaitsMeanwhile() {
        val a = ThreadsSongRules.pressed(SongTurn(), "a")
        assertTrue(a.plays("a"))
        assertTrue(ThreadsSongRules.holdsVideo(a))
        val b = ThreadsSongRules.pressed(a, "b")
        assertTrue(b.plays("b"))
        assertFalse(b.plays("a"), "starting a song stops the other")
        val paused = ThreadsSongRules.pressed(b, "b")
        assertEquals(SongTurn("b", playing = false), paused)
        assertFalse(ThreadsSongRules.holdsVideo(paused), "a paused song lets the video play")
        assertEquals(SongTurn("b", playing = true), ThreadsSongRules.pressed(paused, "b"), "pressed again, it plays on")
        assertEquals(b, ThreadsSongRules.paused(b, "a"), "a window left by another card changes nothing")
        assertEquals(SongTurn("b", playing = false), ThreadsSongRules.paused(b, "b"))
        assertEquals(SongTurn(), ThreadsSongRules.stopped(b, "b"), "a card off screen stops its song")
        assertEquals(b, ThreadsSongRules.stopped(b, "a"))
        val failed = ThreadsSongRules.failed(b, "b")
        assertTrue(failed.failed("b"))
        assertFalse(ThreadsSongRules.holdsVideo(failed))
        assertEquals(SongTurn("b", playing = true), ThreadsSongRules.pressed(failed, "b"), "pressed again after a failure, it tries again")
        assertFalse(ThreadsSongRules.holdsVideo(SongTurn()))
    }

    @Test
    fun theButtonIsThereOnlyWhereSomethingCanPlay() {
        assertTrue(ThreadsSongRules.offersPlay(song, hasPlayer = true))
        assertFalse(ThreadsSongRules.offersPlay(song, hasPlayer = false), "a desktop without libmpv shows no button")
        assertFalse(ThreadsSongRules.offersPlay(song.copy(audioUrl = null), hasPlayer = true), "a song without a file shows no button")
    }

    @Test
    fun aScreenReaderHearsWhatTheButtonDoes() {
        assertEquals("Play Made-up Song by Made-up Band", ThreadsSongRules.label(song, playing = false, "en"))
        assertEquals("Play Other", ThreadsSongRules.label(other, playing = false, "en"))
        assertEquals("Pause Made-up Song", ThreadsSongRules.label(song, playing = true, "en"))
        assertEquals("Made-up Song von Made-up Band abspielen", ThreadsSongRules.label(song, playing = false, "de"))
    }

    @Test
    fun thePlayerIsMadeOnTheFirstPressOneAtATimeAndReleasedAtThePreviewsEnd(): Unit = runBlocking {
        val made = mutableListOf<FakePlayer>()
        val songs = ThreadsSongs(CoroutineScope(SupervisorJob() + Dispatchers.Default), Dispatchers.Default)
        val players = { FakePlayer().also { made += it } }
        assertTrue(made.isEmpty(), "nothing loads before play is pressed")

        songs.press("a", song, players)
        until { made.singleOrNull()?.playing == true }
        val first = made.single()
        assertEquals("https://cdn.example/a.m4a" to 30_000L, first.opened)

        songs.press("b", other, players)
        until { made.size == 2 && made[1].playing }
        assertTrue(first.disposed, "the first song's player is let go")
        assertTrue(songs.turn.value.plays("b"))

        songs.press("b", other, players)
        until { !made[1].playing }
        assertEquals(SongTurn("b", playing = false), songs.turn.value)

        songs.press("b", other, players)
        until { made[1].playing }
        made[1].reach(ThreadsSongRules.PREVIEW_MS)
        until { made[1].disposed }
        assertEquals(SongTurn(), songs.turn.value, "the preview ends and does not loop")
        assertEquals(2, made.size)

        songs.press("a", song.copy(audioUrl = null), players)
        assertNull(songs.turn.value.key, "a song without a file is not played")

        songs.press("a", song, players)
        until { made.size == 3 && made[2].playing }
        made[2].fail()
        until { made[2].disposed }
        assertTrue(songs.turn.value.failed("a"))

        songs.press("a", song, players)
        until { made.size == 4 && made[3].playing }
        songs.stop("a")
        until { made[3].disposed }
        assertEquals(SongTurn(), songs.turn.value)
    }

    @Test
    fun aKeptPostKeepsItsSongWholeAndDropsWhatIsNotHttps() {
        val full = song.copy(imageUrl = "https://cdn.example/s.jpg", largeImageUrl = "https://cdn.example/l.jpg", explicit = true, pageUrl = "https://music.example/song")
        val post = ThreadsPost(id = "1", handle = "a", authorName = "A", text = "t", facts = ThreadsPostFacts(music = full))
        assertEquals(post, ThreadsPostJson.list(ThreadsPostJson.listText(listOf(post))).single())
        val bare = post.copy(facts = ThreadsPostFacts(music = ThreadsMusic("Only a title")))
        assertEquals(bare, ThreadsPostJson.list(ThreadsPostJson.listText(listOf(bare))).single())
        val older = ThreadsFactsJson.facts(com.aimdi.xtanative.core.json.J.parse("""{"music":{"title":"T","audioUrl":"http://x/a.m4a","startMs":-4,"imageUrl":"https://x/i.jpg"}}"""))
        assertEquals(ThreadsMusic("T", imageUrl = "https://x/i.jpg"), older.music, "a kept song of an older reader, or a damaged one, reads as much as it can")
    }

    private suspend fun until(check: () -> Boolean) = withTimeout(5_000) { while (!check()) delay(5) }

    private class FakePlayer : ContinuityPlayer {
        private val ticks = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 16)
        override var frame = PlaybackFrame()
        override val changes: Flow<Unit> = ticks
        var opened: Pair<String, Long>? = null
        val playing get() = frame.playing
        var disposed = false

        fun reach(positionMs: Long) {
            frame = frame.copy(positionMs = positionMs)
            ticks.tryEmit(Unit)
        }

        fun fail() {
            frame = frame.copy(failed = true)
            ticks.tryEmit(Unit)
        }

        override suspend fun open(url: String, startMs: Long) {
            opened = url to startMs
            frame = frame.copy(positionMs = startMs)
        }

        override suspend fun play() {
            frame = frame.copy(playing = true)
        }

        override suspend fun pause() {
            frame = frame.copy(playing = false)
        }

        override suspend fun seek(positionMs: Long) = reach(positionMs)

        override suspend fun setVolume(value: Double) = Unit

        override suspend fun setRate(value: Double) = Unit

        override suspend fun stop() {
            frame = frame.copy(playing = false)
        }

        override suspend fun dispose() {
            disposed = true
        }
    }
}
