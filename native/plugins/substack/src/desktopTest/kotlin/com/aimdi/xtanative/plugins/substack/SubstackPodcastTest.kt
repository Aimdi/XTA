package com.aimdi.xtanative.plugins.substack

import com.aimdi.xtanative.store.StoreDir
import com.aimdi.xtanative.store.settings.SettingsStore
import com.aimdi.xtanative.store.settings.SubstackPrefs
import com.aimdi.xtanative.ui.media.AudioSessions
import com.aimdi.xtanative.ui.media.ContinuityPlayer
import com.aimdi.xtanative.ui.media.PlaybackFrame
import com.aimdi.xtanative.ui.media.PodcastCheckpoint
import com.aimdi.xtanative.ui.plugins.PluginSettings
import java.nio.file.Files
import java.util.Collections
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** XTA's podcast_continuity_test: the episode playing beyond its screen, where it was left, and its speed. */
class SubstackPodcastTest {
    private class FakePlayer : ContinuityPlayer {
        @Volatile override var frame = PlaybackFrame()
        private val ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
        override val changes = ticks
        val opens: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val commands: MutableList<String> = Collections.synchronizedList(mutableListOf())
        @Volatile var beforeOpen: (suspend (String) -> Unit)? = null
        @Volatile var beforeSeek: (suspend (Long) -> Unit)? = null
        @Volatile var honorSeek = true
        @Volatile var disposed = false

        override suspend fun open(url: String, startMs: Long) {
            opens += url
            commands += "open:$url"
            beforeOpen?.invoke(url)
            frame = PlaybackFrame(durationMs = DURATION)
            ticks.tryEmit(Unit)
        }

        override suspend fun play() {
            commands += "play"
            emit(playing = true)
        }

        override suspend fun pause() {
            commands += "pause"
            emit(playing = false)
        }

        override suspend fun seek(positionMs: Long) {
            commands += "seek:$positionMs"
            beforeSeek?.invoke(positionMs)
            if (honorSeek) emit(position = positionMs)
        }

        override suspend fun setVolume(value: Double) = Unit

        override suspend fun setRate(value: Double) {
            commands += "rate:$value"
        }

        override suspend fun stop() {
            commands += "stop"
            frame = frame.copy(playing = false)
        }

        override suspend fun dispose() {
            disposed = true
        }

        fun emit(position: Long = frame.positionMs, playing: Boolean = frame.playing, completed: Boolean = false) {
            frame = frame.copy(positionMs = position, playing = playing, completed = completed)
            ticks.tryEmit(Unit)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val app = SettingsStore(StoreDir(Files.createTempDirectory("substack-podcast").toFile())).also { runBlocking { it.load() } }
    private val settings = PluginSettings("substack", app.state) { change -> app.update(change) }

    @AfterTest
    fun end() = scope.cancel()

    private fun checkpoint() = PodcastCheckpoint(URL, "Episode one", POSITION, DURATION).encode()

    private fun stored(): PodcastCheckpoint? = PodcastCheckpoint.decode(app.state.value?.get(SubstackPrefs.podcast))

    private suspend fun podcast(player: FakePlayer, made: () -> Unit = {}): SubstackPodcast =
        SubstackPodcast(settings, scope).also { it.attach({ made(); player }, AudioSessions.None) }

    private suspend fun until(what: String = "the condition", check: () -> Boolean) {
        withTimeout(5_000) { while (!check()) delay(5) }
        assertTrue(check(), what)
    }

    @Test
    fun aColdRestoreShowsThePausedPlaceWithoutMakingAPlayer(): Unit = runBlocking {
        settings.update { it.with(SubstackPrefs.podcast, checkpoint()) }
        var made = 0
        val player = FakePlayer()
        val podcast = podcast(player) { made++ }
        until { podcast.state.value.url == URL }
        assertEquals(POSITION, podcast.state.value.positionMs)
        assertFalse(podcast.state.value.playing)
        assertEquals(0, made)
        podcast.toggle(URL, "Episode one")
        until { podcast.state.value.playing }
        assertEquals(1, made)
        assertEquals(listOf(URL), player.opens)
        assertEquals(POSITION, podcast.state.value.positionMs)
    }

    @Test
    fun closingKeepsTheEpisodeToComeBackPaused(): Unit = runBlocking {
        val player = FakePlayer()
        val podcast = podcast(player)
        podcast.toggle(URL, "Episode one")
        until { podcast.state.value.playing }
        player.emit(position = POSITION)
        until { podcast.state.value.positionMs == POSITION }
        podcast.close()
        assertEquals(POSITION, stored()?.positionMs)
        val restored = podcast(FakePlayer())
        until { restored.state.value.url == URL }
        assertEquals(POSITION, restored.state.value.positionMs)
        assertFalse(restored.state.value.playing)
    }

    @Test
    fun continuousPlaybackIsWrittenEveryFewSeconds(): Unit = runBlocking {
        val player = FakePlayer()
        val podcast = podcast(player)
        podcast.toggle(URL, "Episode one")
        until { podcast.state.value.playing }
        for (second in 1..5) {
            player.emit(position = second * 1_000L)
            delay(500)
        }
        assertTrue((stored()?.positionMs ?: 0) > 0)
    }

    @Test
    fun aFinishedEpisodeClearsItsPlaceAndDoesNotComeBack(): Unit = runBlocking {
        val player = FakePlayer()
        val podcast = podcast(player)
        podcast.toggle(URL, "Episode one")
        until { podcast.state.value.playing }
        player.emit(position = DURATION, completed = true)
        until { podcast.state.value.url == null }
        podcast.close()
        assertNull(stored())
        val restored = podcast(FakePlayer())
        delay(100)
        assertNull(restored.state.value.url)
    }

    @Test
    fun anotherEpisodeAskedForMeanwhileGetsNoSeekOrPlayOfTheFirst(): Unit = runBlocking {
        settings.update { it.with(SubstackPrefs.podcast, checkpoint()) }
        val player = FakePlayer()
        val gate = CompletableDeferred<Unit>()
        player.beforeOpen = { if (it == URL) gate.await() }
        val podcast = podcast(player)
        until { podcast.state.value.url == URL }
        podcast.toggle(URL, "Episode one")
        until { player.opens.isNotEmpty() }
        podcast.toggle(NEXT, "Episode two")
        player.emit(position = 17 * 60_000L, completed = true)
        gate.complete(Unit)
        until { podcast.state.value.playing }
        assertEquals(listOf(URL, NEXT), player.opens)
        assertTrue(player.commands.none { it.startsWith("seek:") }, player.commands.toString())
        assertEquals(NEXT, podcast.state.value.url)
        assertEquals(0, podcast.state.value.positionMs)
        assertEquals(1, player.commands.count { it == "play" })
    }

    @Test
    fun aStopDuringTheOpenCancelsTheResumeAndClearsTheCheckpoint(): Unit = runBlocking {
        settings.update { it.with(SubstackPrefs.podcast, checkpoint()) }
        val player = FakePlayer()
        val gate = CompletableDeferred<Unit>()
        player.beforeOpen = { gate.await() }
        val podcast = podcast(player)
        until { podcast.state.value.url == URL }
        podcast.toggle(URL, "Episode one")
        until { player.opens.isNotEmpty() }
        podcast.stop()
        assertNull(podcast.state.value.url, "the row is cleared at once, before the open answers")
        gate.complete(Unit)
        until { app.state.value?.get(SubstackPrefs.podcast) == "" }
        delay(100)
        assertNull(podcast.state.value.url)
        assertFalse("play" in player.commands)
        assertNull(stored())
        podcast.close()
        assertNull(stored())
    }

    @Test
    fun aLateWriteOfThePlaceCannotReplaceTheStop(): Unit = runBlocking {
        val player = FakePlayer()
        val podcast = podcast(player)
        podcast.toggle(URL, "Episode one")
        until { podcast.state.value.playing }
        player.emit(position = POSITION)
        podcast.stop()
        delay(2_500)
        assertNull(stored())
    }

    @Test
    fun aFailedLoadKeepsTheCheckpointAndCanBeTriedAgain(): Unit = runBlocking {
        settings.update { it.with(SubstackPrefs.podcast, checkpoint()) }
        val player = FakePlayer().apply { beforeOpen = { error("network") } }
        val podcast = podcast(player)
        until { podcast.state.value.url == URL }
        podcast.toggle(URL, "Episode one")
        until { podcast.state.value.failed }
        assertEquals(POSITION, podcast.state.value.positionMs)
        assertFalse(podcast.state.value.playing)
        assertEquals(POSITION, stored()?.positionMs)
        player.beforeOpen = null
        podcast.toggle(URL, "Episode one")
        until { podcast.state.value.playing }
        assertFalse(podcast.state.value.failed)
    }

    @Test
    fun aRestoredSliderMovesWithoutStartingThePlayer(): Unit = runBlocking {
        settings.update { it.with(SubstackPrefs.podcast, checkpoint()) }
        val player = FakePlayer()
        val podcast = podcast(player)
        until { podcast.state.value.url == URL }
        podcast.seek(12 * 60_000L)
        until { podcast.state.value.positionMs == 12 * 60_000L }
        assertTrue(player.commands.isEmpty())
        podcast.toggle(URL, "Episode one")
        until { podcast.state.value.playing }
        assertTrue("seek:${12 * 60_000L}" in player.commands)
    }

    @Test
    fun anEpisodeThatCannotBePutBackStaysPausedWithItsCheckpoint(): Unit = runBlocking {
        settings.update { it.with(SubstackPrefs.podcast, checkpoint()) }
        val player = FakePlayer().apply { honorSeek = false }
        val podcast = podcast(player)
        until { podcast.state.value.url == URL }
        podcast.toggle(URL, "Episode one")
        until { podcast.state.value.failed }
        assertEquals(POSITION, podcast.state.value.positionMs)
        assertEquals(1, player.opens.size)
        assertFalse("play" in player.commands)
        podcast.close()
        assertEquals(POSITION, stored()?.positionMs)
    }

    @Test
    fun aStalledResumeSeekKeepsTheCheckpointAndALaterTryPlaysFromThePlace(): Unit = runBlocking {
        settings.update { it.with(SubstackPrefs.podcast, checkpoint()) }
        val gate = CompletableDeferred<Unit>()
        val player = FakePlayer().apply { beforeSeek = { gate.await() } }
        val podcast = podcast(player)
        until { podcast.state.value.url == URL }
        podcast.toggle(URL, "Episode one")
        until { podcast.state.value.failed }
        assertEquals(POSITION, podcast.state.value.positionMs)
        assertFalse("play" in player.commands)
        gate.complete(Unit)
        podcast.toggle(URL, "Episode one")
        until { podcast.state.value.playing }
        assertEquals(POSITION, podcast.state.value.positionMs)
    }

    @Test
    fun aStalledOpenDoesNotHoldUpTheStop(): Unit = runBlocking {
        settings.update { it.with(SubstackPrefs.podcast, checkpoint()) }
        val gate = CompletableDeferred<Unit>()
        val player = FakePlayer().apply { beforeOpen = { gate.await() } }
        val podcast = podcast(player)
        until { podcast.state.value.url == URL }
        podcast.toggle(URL, "Episode one")
        until { player.opens.isNotEmpty() }
        podcast.stop()
        assertNull(podcast.state.value.url)
        gate.complete(Unit)
        until { app.state.value?.get(SubstackPrefs.podcast) == "" }
        assertFalse("play" in player.commands)
    }

    @Test
    fun aChosenSpeedAppliesNowSurvivesARestartAndOpensTheNextEpisode(): Unit = runBlocking {
        val player = FakePlayer()
        val podcast = podcast(player)
        podcast.toggle(URL, "Episode one")
        until { podcast.state.value.playing }
        podcast.setSpeed(1.5)
        until { player.commands.lastOrNull() == "rate:1.5" }
        assertEquals(1.5, podcast.state.value.speed)
        podcast.setSpeed(3.0)
        assertEquals(1.5, podcast.state.value.speed, "only the offered speeds")
        podcast.toggle(NEXT, "Episode two")
        until { podcast.state.value.url == NEXT && podcast.state.value.playing }
        val opened = player.commands.lastIndexOf("open:$NEXT")
        val after = player.commands.drop(opened)
        assertTrue(after.indexOf("rate:1.5") in 1 until after.indexOf("play"), after.toString())
        podcast.close()
        val again = podcast(FakePlayer())
        until { again.state.value.speed == 1.5 }
    }

    @Test
    fun aStoredSpeedThatIsNotOfferedReadsAsNormal(): Unit = runBlocking {
        app.update { it.with(SubstackPrefs.podcastSpeed.key, com.aimdi.xtanative.store.settings.PrefValue.Real(7.0)) }
        val podcast = podcast(FakePlayer())
        delay(100)
        assertEquals(1.0, podcast.state.value.speed)
    }

    @Test
    fun eachEpisodeResumesWhereItWasLeftAndAFinishedOneStartsOver(): Unit = runBlocking {
        val player = FakePlayer()
        val podcast = podcast(player)
        podcast.toggle(URL, "Episode one")
        until { podcast.state.value.playing }
        player.emit(position = POSITION, playing = true)
        until { podcast.state.value.positionMs == POSITION }
        podcast.toggle(NEXT, "Episode two")
        until { podcast.state.value.url == NEXT && podcast.state.value.playing }
        assertTrue(player.commands.none { it.startsWith("seek:") }, "a new episode starts at the top")
        player.emit(position = 3 * 60_000L, playing = true)
        until { podcast.state.value.positionMs == 3 * 60_000L }
        podcast.toggle(URL, "Episode one")
        until { podcast.state.value.url == URL && podcast.state.value.playing }
        assertTrue("seek:$POSITION" in player.commands, "the first one picks up where it was")
        podcast.close()

        val again = podcast(FakePlayer())
        until { again.state.value.url == URL }
        again.toggle(NEXT, "Episode two")
        until { again.state.value.url == NEXT && again.state.value.playing }
        assertEquals(3 * 60_000L, again.state.value.positionMs, "places survive a restart")
        again.stop(finished = true)
        until { app.state.value?.get(SubstackPrefs.podcast) == "" }
        again.toggle(NEXT, "Episode two")
        until { again.state.value.url == NEXT && again.state.value.playing }
        assertEquals(0, again.state.value.positionMs, "a finished episode forgets its place")
    }

    @Test
    fun normalSpeedIsSetOnOpenTooSoAKeptRateIsNotCarriedOver(): Unit = runBlocking {
        val player = FakePlayer()
        val podcast = podcast(player)
        podcast.toggle(URL, "Episode one")
        until { podcast.state.value.playing }
        podcast.setSpeed(1.5)
        podcast.stop()
        podcast.setSpeed(1.0)
        until { podcast.state.value.speed == 1.0 }
        podcast.toggle(NEXT, "Episode two")
        until { podcast.state.value.url == NEXT && podcast.state.value.playing }
        assertTrue("rate:1.0" in player.commands.drop(player.commands.lastIndexOf("open:$NEXT")))
    }

    @Test
    fun damagedOversizedUnsafeAndFinishedRecordsAreIgnored() {
        val base = """"url":"$URL","title":"Episode one","duration":$DURATION"""
        listOf(
            "{broken",
            "x".repeat(16_385),
            "[]",
            """{"version":5,"position":$POSITION,$base}""",
            """{"version":1,"position":$POSITION,"url":"file:///secret.mp3","title":"t","duration":$DURATION}""",
            """{"version":1,"position":-1,$base}""",
            """{"version":1,"position":$DURATION,$base}""",
            """{"version":1,"position":$POSITION,"url":"$URL","title":"t","duration":${8L * 24 * 3_600_000}}""",
            """{"version":1,"position":$POSITION,"url":"$URL","title":"${"x".repeat(513)}","duration":$DURATION}""",
        ).forEach { assertNull(PodcastCheckpoint.decode(it), it.take(80)) }
        assertEquals("Episode one", PodcastCheckpoint.decode(checkpoint())?.title)
    }

    private companion object {
        const val URL = "https://publication.example/episode.mp3"
        const val NEXT = "https://publication.example/episode-2.mp3"
        const val POSITION = (8 * 60 + 12) * 1_000L
        const val DURATION = 30 * 60_000L
    }
}
