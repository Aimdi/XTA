package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.threads.ThreadsMusic
import com.aimdi.xtanative.ui.media.ContinuityPlayer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Which post's song is heard: at most one, [key] (the post's id), playing or
 * paused; [failed] the last one whose file would not play.
 */
internal data class SongTurn(val key: String? = null, val playing: Boolean = false, val failed: String? = null) {
    fun plays(key: String): Boolean = this.key == key && playing

    fun failed(key: String): Boolean = failed == key
}

/** Where a song's preview starts and stops, in its file. */
internal data class SongWindow(val startMs: Long, val endMs: Long)

/**
 * The rules of a Threads song's play button, plain so they are checked
 * without a player: one song at a time, the feed's video waiting while one
 * is heard, a preview from Meta's start for its length that stops at its
 * end (never looping), and a song that leaves the screen stopped.
 */
internal object ThreadsSongRules {
    /** How long a preview lasts when Meta does not say: Instagram's and Threads' clips are 30 seconds at most. */
    const val PREVIEW_MS = 30_000L

    /** The preview of [music]: from its start for its length (or [PREVIEW_MS]), not past the song's end. */
    fun window(music: ThreadsMusic): SongWindow {
        val duration = music.durationMs?.takeIf { it > 0 }
        val start = music.startMs.takeIf { it >= 0 && (duration == null || it < duration) } ?: 0
        val length = music.lengthMs?.takeIf { it > 0 } ?: PREVIEW_MS
        val end = (start + length).let { if (duration != null) it.coerceAtMost(duration) else it }
        return SongWindow(start, end)
    }

    /** The play button of [key] pressed: its song pauses if it plays, else it plays and any other song stops. */
    fun pressed(turn: SongTurn, key: String): SongTurn = when {
        turn.plays(key) -> turn.copy(playing = false)
        else -> SongTurn(key = key, playing = true)
    }

    /** [key]'s song paused from outside its button (the window left); another song is left alone. */
    fun paused(turn: SongTurn, key: String): SongTurn = if (turn.key == key) turn.copy(playing = false) else turn

    /** [key]'s card left the screen, or its preview ended: its song stops, and the next press starts it again. */
    fun stopped(turn: SongTurn, key: String): SongTurn = if (turn.key == key) SongTurn(failed = turn.failed) else turn

    /** [key]'s file would not play: it stops, and its card says so. */
    fun failed(turn: SongTurn, key: String): SongTurn = if (turn.key == key) SongTurn(failed = key) else turn

    /** Whether the preview is over at [positionMs]: its end reached, or the file's. */
    fun ended(positionMs: Long, window: SongWindow, completed: Boolean): Boolean = completed || positionMs >= window.endMs

    /** Whether the feed's video waits: while a song is heard, nothing else plays. */
    fun holdsVideo(turn: SongTurn): Boolean = turn.key != null && turn.playing

    /** Whether [music]'s card offers a play button: with a file, and a player on this platform; never a button that does nothing. */
    fun offersPlay(music: ThreadsMusic, hasPlayer: Boolean): Boolean = hasPlayer && music.playable

    /**
     * What a screen reader says for the button, in [language]: "Play Title
     * by Artist", "Play Title" where no artist is named, "Pause Title"
     * while it plays.
     */
    fun label(music: ThreadsMusic, playing: Boolean, language: String): String = when {
        playing -> Strings.song_pause.get(language, music.title)
        music.artist == null -> Strings.song_play_title.get(language, music.title)
        else -> Strings.song_play.get(language, music.title, music.artist)
    }
}

/**
 * The Threads plugin's one song player, which every song card asks
 * ([ThreadsSongRules]): a song pressed plays from its preview's start
 * through the app's player for a source kept across screens (`ui.media`'s
 * [ContinuityPlayer]: Media3 on Android, libmpv on the desktop), and stops
 * at the preview's end; another pressed takes its place. The player is made
 * on the first press, so nothing loads before the reader asks, and released
 * when the song stops. [main] is where a player is made (Media3 wants its
 * thread with a looper).
 */
internal class ThreadsSongs(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val main: CoroutineDispatcher = Dispatchers.Main,
) {
    private val turns = MutableStateFlow(SongTurn())
    val turn: StateFlow<SongTurn> = turns.asStateFlow()

    private val lock = Mutex()
    private var player: ContinuityPlayer? = null
    private var opened: String? = null
    private var watch: Job? = null
    private var songs: Map<String, Pair<ThreadsMusic, () -> ContinuityPlayer>> = emptyMap()

    /** [key]'s play button: [music] plays (or pauses), through a player [players] makes. */
    fun press(key: String, music: ThreadsMusic, players: () -> ContinuityPlayer) {
        if (!music.playable) return
        songs = mapOf(key to (music to players))
        change { ThreadsSongRules.pressed(it, key) }
    }

    fun pause(key: String) = change { ThreadsSongRules.paused(it, key) }

    fun stop(key: String) = change { ThreadsSongRules.stopped(it, key) }

    /** Everything stopped and the player released, as the plugin closes. */
    fun close() {
        turns.value = SongTurn()
        scope.launch { lock.withLock { release() } }.invokeOnCompletion { scope.cancel() }
    }

    private fun change(rule: (SongTurn) -> SongTurn) {
        val before = turns.value
        turns.update(rule)
        if (turns.value != before) scope.launch { lock.withLock { follow() } }
    }

    /** Brings the player to where [turn] stands: the song open or released, playing or paused. */
    private suspend fun follow() {
        val now = turns.value
        val key = now.key
        val song = key?.let { songs[it] }
        if (key == null || song == null) return release()
        val (music, players) = song
        val url = music.audioUrl ?: return release()
        if (opened != key) {
            release()
            val made = runCatching { withContext(main) { players() } }.getOrNull() ?: return fail(key)
            player = made
            opened = key
            val window = ThreadsSongRules.window(music)
            if (runCatching { made.open(url, window.startMs) }.isFailure) return fail(key)
            watch = scope.launch { watch(made, key, window) }
        }
        val current = player ?: return
        runCatching { if (turns.value.plays(key)) current.play() else current.pause() }
    }

    private suspend fun watch(player: ContinuityPlayer, key: String, window: SongWindow) {
        player.changes.collect {
            val frame = player.frame
            when {
                frame.failed -> change { ThreadsSongRules.failed(it, key) }
                ThreadsSongRules.ended(frame.positionMs, window, frame.completed) -> change { ThreadsSongRules.stopped(it, key) }
            }
        }
    }

    private suspend fun fail(key: String) {
        turns.update { ThreadsSongRules.failed(it, key) }
        release()
    }

    private suspend fun release() {
        watch?.cancel()
        watch = null
        val gone = player
        player = null
        opened = null
        if (gone != null) runCatching {
            gone.stop()
            gone.dispose()
        }
    }
}
