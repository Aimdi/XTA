package com.aimdi.xtanative.ui.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class PlaybackTest {
    /** A player whose answers the test sets: it learns its duration [readyAfterMs] after opening, and seeks as told. */
    private class Player(private val readyAfterMs: Long = 20, private val seeks: Boolean = true) : ContinuityPlayer {
        @Volatile
        override var frame = PlaybackFrame()
        override val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
        val sought = ArrayList<Long>()
        var durationMs = 60_000L
        var failsToOpen = false

        override suspend fun open(url: String, startMs: Long) {
            CoroutineScope(Dispatchers.Default).launch {
                delay(readyAfterMs)
                frame = if (failsToOpen) frame.copy(failed = true) else frame.copy(durationMs = durationMs, positionMs = startMs)
                changes.emit(Unit)
            }
        }

        override suspend fun seek(positionMs: Long) {
            sought += positionMs
            if (seeks) {
                frame = frame.copy(positionMs = positionMs)
                changes.emit(Unit)
            }
        }

        override suspend fun play() = Unit
        override suspend fun pause() = Unit
        override suspend fun setVolume(value: Double) = Unit
        override suspend fun setRate(value: Double) = Unit
        override suspend fun stop() = Unit
        override suspend fun dispose() = Unit
    }

    @Test
    fun theResumePlaceStopsShortOfTheEnd() {
        assertEquals(0, PlaybackRestore.resumeTarget(0, 60_000))
        assertEquals(0, PlaybackRestore.resumeTarget(5_000, 0))
        assertEquals(30_000, PlaybackRestore.resumeTarget(30_000, 60_000))
        assertEquals(59_500, PlaybackRestore.resumeTarget(60_000, 60_000))
        assertEquals(0, PlaybackRestore.resumeTarget(300, 400))
    }

    @Test
    fun aRestoredSourceIsSoughtOnceItKnowsItsLength(): Unit = runBlocking {
        val player = Player()
        player.open("https://example.com/a.mp3")
        assertTrue(PlaybackRestore.restore(player, 90_000))
        assertEquals(listOf(59_500L), player.sought)
    }

    @Test
    fun aSourceAlreadyAtItsPlaceIsNotSought(): Unit = runBlocking {
        val player = Player()
        player.open("https://example.com/a.mp3", startMs = 30_000)
        assertTrue(PlaybackRestore.restore(player, 30_400))
        assertTrue(player.sought.isEmpty())
    }

    @Test
    fun aFailedReplacedOrStuckSourceIsNotRestored(): Unit = runBlocking {
        val failing = Player().apply { failsToOpen = true }
        failing.open("u")
        assertFalse(PlaybackRestore.restore(failing, 10_000))
        assertFalse(PlaybackRestore.restore(Player(), 10_000, isCurrent = { false }))
        val stuck = Player(seeks = false)
        stuck.open("u")
        assertFalse(PlaybackRestore.restore(stuck, 10_000, seekTimeoutMs = 100))
        val silent = Player(readyAfterMs = 10_000)
        silent.open("u")
        assertFalse(PlaybackRestore.restore(silent, 10_000, readyTimeoutMs = 100))
        assertTrue(PlaybackRestore.restore(Player(), 0))
    }

    @Test
    fun aCheckpointRoundTripsAndRefusesWhatIsOutOfBounds() {
        val checkpoint = PodcastCheckpoint("https://example.com/ep.mp3", "Episode", 61_000, 3_600_000)
        assertEquals(checkpoint, PodcastCheckpoint.decode(checkpoint.encode()))
        assertNull(PodcastCheckpoint.decode(null))
        assertNull(PodcastCheckpoint.decode("{"))
        assertNull(PodcastCheckpoint.decode("""{"version":2,"url":"https://e.com/a","title":"t","position":1,"duration":2}"""))
        assertNull(PodcastCheckpoint.decode("""{"version":1,"url":"ftp://e.com/a","title":"t","position":1,"duration":2}"""))
        assertNull(PodcastCheckpoint.decode("""{"version":1,"url":"https://e.com/a","title":"t","position":5,"duration":5}"""))
        assertEquals(0, PodcastCheckpoint.decode("""{"version":1,"url":"https://e.com/a","title":"t","position":0,"duration":0}""")?.positionMs)
        val long = PodcastCheckpoint("https://e.com/a", "t".repeat(600), 1, 2)
        assertEquals(512, PodcastCheckpoint.decode(long.encode())?.title?.length)
    }

    @Test
    fun placesKeepTheNewestFirstAndAtMostFifty() {
        var places = PodcastPlaces.EMPTY
        repeat(60) { places = places.leftAt("https://e.com/$it", (it + 1) * 1_000L, 100_000) }
        assertEquals(PodcastPlaces.LIMIT, places.size)
        assertEquals("https://e.com/59", places.urls.first())
        assertNull(places.of("https://e.com/0"))
        places = places.leftAt("https://e.com/30", 5_000, 100_000)
        assertEquals("https://e.com/30", places.urls.first())
        assertEquals(PodcastPlace(5_000, 100_000), places.of("https://e.com/30"))
        assertNull(places.leftAt("https://e.com/30", 0, 100_000).of("https://e.com/30"))
        assertEquals(places, PodcastPlaces.decode(places.encode()))
        val damaged = """[{"url":"https://e.com/a","position":10,"duration":20},{"url":"nope","position":1},{"url":"https://e.com/a","position":3,"duration":9}]"""
        val decoded = PodcastPlaces.decode(damaged)
        assertEquals(1, decoded.size)
        assertEquals(PodcastPlace(10, 20), decoded.of("https://e.com/a"))
        assertEquals(PodcastPlaces.EMPTY, PodcastPlaces.decode("{\"not\":\"a list\"}"))
    }
}
