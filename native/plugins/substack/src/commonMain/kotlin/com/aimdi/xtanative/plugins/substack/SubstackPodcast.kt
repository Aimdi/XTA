package com.aimdi.xtanative.plugins.substack

import com.aimdi.xtanative.store.settings.SubstackPrefs
import com.aimdi.xtanative.ui.media.AudioSessionBinding
import com.aimdi.xtanative.ui.media.AudioSessions
import com.aimdi.xtanative.ui.media.ContinuityPlayer
import com.aimdi.xtanative.ui.media.PlaybackRestore
import com.aimdi.xtanative.ui.media.PodcastCheckpoint
import com.aimdi.xtanative.ui.media.PodcastPlaces
import com.aimdi.xtanative.ui.plugins.PluginSettings
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** What a podcast post's player row shows: the episode playing, how far, and at what speed. */
internal data class SubstackPlayback(
    val url: String? = null,
    val title: String = "",
    val playing: Boolean = false,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Double = 1.0,
)

/**
 * The podcast episode of a post, playing beyond the screen it was started
 * on, as XTA's PodcastStore: one player (Media3's on Android, libmpv's on
 * the desktop), the system's media session on Android, and where it was
 * left: the episode playing in `plugin.substack.podcast.v1`, each episode's
 * place in `podcast.places` and the speed in `podcast.speed`. A desktop
 * without libmpv has no player: its row opens the episode in the browser
 * instead.
 */
internal class SubstackPodcast(private val settings: PluginSettings, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(SubstackPlayback())
    val state: StateFlow<SubstackPlayback> = mutable.asStateFlow()

    private val commands = Mutex()
    private var makePlayer: (() -> ContinuityPlayer)? = null
    private var sessions: AudioSessions = AudioSessions.None
    private var player: ContinuityPlayer? = null
    private var frames: Job? = null
    private var loadedUrl: String? = null
    private var places = PodcastPlaces.EMPTY
    private var writtenAt = 0L
    private var restored = false

    /** Whether this platform plays episodes here. */
    val canPlay: Boolean get() = makePlayer != null

    /** The platform's way to make a player and its media session, given by the first screen that shows a player row. */
    fun attach(players: (() -> ContinuityPlayer)?, sessions: AudioSessions) {
        if (makePlayer == null) makePlayer = players
        this.sessions = sessions
        if (!restored) {
            restored = true
            scope.launch { restore() }
        }
    }

    private suspend fun restore() {
        val all = settings.loaded()
        places = PodcastPlaces.decode(all[SubstackPrefs.podcastPlaces])
        val speed = all[SubstackPrefs.podcastSpeed]
        val checkpoint = PodcastCheckpoint.decode(all[SubstackPrefs.podcast])
        mutable.update {
            if (checkpoint == null || it.url != null) it.copy(speed = speed)
            else SubstackPlayback(checkpoint.url, checkpoint.title, positionMs = checkpoint.positionMs, durationMs = checkpoint.durationMs, speed = speed)
        }
    }

    /** Plays or pauses [url]; another episode is left where it is, and [url] resumes where it was left. */
    fun toggle(url: String, title: String) {
        if (!PodcastCheckpoint.validUrl(url)) return
        scope.launch {
            commands.withLock {
                val now = state.value
                if (now.url == url && loadedUrl == url) control(!now.playing) else open(url, title, now)
            }
        }
    }

    fun seek(positionMs: Long) {
        scope.launch {
            commands.withLock {
                val now = state.value
                if (loadedUrl == null || loadedUrl != now.url) return@withLock
                val target = PlaybackRestore.resumeTarget(positionMs, now.durationMs).coerceAtLeast(0)
                if (attempt { player?.seek(target) }) mutable.update { it.copy(positionMs = target) } else failed()
                checkpoint(force = true)
            }
        }
    }

    fun setSpeed(speed: Double) {
        if (speed !in SPEEDS) return
        mutable.update { it.copy(speed = speed) }
        scope.launch {
            settings.set(SubstackPrefs.podcastSpeed, speed)
            commands.withLock { if (loadedUrl != null) attempt { player?.setRate(speed) } }
        }
    }

    /** Stops the episode; a finished one forgets its place, another keeps it. */
    fun stop(finished: Boolean = false) {
        scope.launch { commands.withLock { stopNow(finished) } }
    }

    /** The player is let go: the plugin stops. */
    suspend fun close() {
        commands.withLock {
            checkpoint(force = true)
            frames?.cancel()
            player?.let { attempt { it.dispose() } }
            player = null
            sessions.clear()
        }
    }

    private suspend fun open(url: String, title: String, before: SubstackPlayback) {
        val make = makePlayer ?: return
        before.url?.takeIf { it != url && before.positionMs > 0 }?.let { places = places.leftAt(it, before.positionMs, before.durationMs) }
        val start = if (before.url == url) before.positionMs else places.of(url)?.positionMs ?: 0
        val duration = if (before.url == url) before.durationMs else places.of(url)?.durationMs ?: 0
        mutable.value = SubstackPlayback(url, title, loading = true, positionMs = start, durationMs = duration, speed = before.speed)
        val player = player ?: make().also { listen(it) }.also { player = it }
        loadedUrl = null
        val opened = attempt { player.open(url) } && PlaybackRestore.restore(player, start, isCurrent = { state.value.url == url })
        if (!opened || state.value.url != url) return failed()
        loadedUrl = url
        mutable.update { it.copy(loading = false, failed = false) }
        sessions.bind(title, binding = AudioSessionBinding(onPlay = { toggleIf(true) }, onPause = { toggleIf(false) }, onStop = { stop() }, onSeek = ::seek))
        attempt { player.setRate(before.speed) }
        control(play = true)
    }

    private fun toggleIf(play: Boolean) {
        scope.launch { commands.withLock { if (loadedUrl != null) control(play) } }
    }

    private suspend fun control(play: Boolean) {
        val done = attempt { if (play) player?.play() else player?.pause() }
        if (!done) failed()
        checkpoint(force = !play)
    }

    private suspend fun stopNow(finished: Boolean) {
        val now = state.value
        now.url?.let { url -> places = if (finished || now.positionMs <= 0) places.without(url) else places.leftAt(url, now.positionMs, now.durationMs) }
        loadedUrl = null
        player?.let { attempt { it.stop() } }
        mutable.value = SubstackPlayback(speed = now.speed)
        sessions.clear()
        keep(checkpoint = "")
    }

    private fun listen(player: ContinuityPlayer) {
        frames?.cancel()
        frames = scope.launch { player.changes.collect { received(player) } }
    }

    private suspend fun received(player: ContinuityPlayer) {
        val frame = player.frame
        if (loadedUrl == null || loadedUrl != state.value.url) return
        when {
            frame.failed -> commands.withLock { failed() }
            frame.completed -> commands.withLock { stopNow(finished = true) }
            else -> {
                val before = state.value
                mutable.update { it.copy(playing = frame.playing, positionMs = frame.positionMs, durationMs = frame.durationMs.takeIf { d -> d > 0 } ?: it.durationMs) }
                sessions.update(frame.playing, frame.positionMs, frame.durationMs.takeIf { it > 0 })
                checkpoint(force = before.playing && !frame.playing)
            }
        }
    }

    private suspend fun failed() {
        loadedUrl = null
        mutable.update { it.copy(loading = false, playing = false, failed = true) }
        sessions.clear()
        checkpoint(force = true)
    }

    /** Where the episode stands, written at most every few seconds while it plays, and at once when [force]d. */
    private suspend fun checkpoint(force: Boolean) {
        val now = state.value
        val url = now.url ?: return
        val clock = System.currentTimeMillis()
        if (!force && abs(clock - writtenAt) < CHECKPOINT_MS) return
        writtenAt = clock
        val checkpoint = PodcastCheckpoint(url, now.title, now.positionMs, now.durationMs)
        if (now.positionMs > 0) places = places.leftAt(url, now.positionMs, now.durationMs)
        attempt { keep(checkpoint.encode()) }
    }

    /** The episode playing, [checkpoint], and each episode's place, written together. */
    private suspend fun keep(checkpoint: String) {
        val kept = places.encode()
        settings.update { it.with(SubstackPrefs.podcast, checkpoint).with(SubstackPrefs.podcastPlaces, kept) }
    }

    /** [block] with a time limit; false when it failed or did not answer in time. */
    private suspend fun attempt(block: suspend () -> Unit): Boolean = try {
        withTimeoutOrNull(COMMAND_MS) { block() } != null
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    companion object {
        val SPEEDS = listOf(0.8, 1.0, 1.25, 1.5, 1.75, 2.0)
        private const val COMMAND_MS = 8_000L
        private const val CHECKPOINT_MS = 10_000L

        /** A position as the row shows it: m:ss, or h:mm:ss past an hour. */
        fun clock(ms: Long): String {
            val seconds = (ms / 1000).coerceAtLeast(0)
            val minutes = (seconds / 60) % 60
            val hours = seconds / 3600
            return if (hours > 0) String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds % 60)
            else String.format(Locale.ROOT, "%02d:%02d", minutes, seconds % 60)
        }
    }
}
