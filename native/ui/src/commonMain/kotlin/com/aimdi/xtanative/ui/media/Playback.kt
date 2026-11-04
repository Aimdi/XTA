package com.aimdi.xtanative.ui.media

import kotlin.math.abs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Where a player stands at one moment. [volume] is 0 to 100; [rate] 1 is normal speed. */
data class PlaybackFrame(
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val playing: Boolean = false,
    val completed: Boolean = false,
    val failed: Boolean = false,
    val volume: Double = 100.0,
    val rate: Double = 1.0,
)

/**
 * The small port of a player that keeps one source going across screens and
 * restarts, a podcast episode's or a video's (continuity_player.dart): the
 * policies around it are checked without a real player. [changes] ticks
 * whenever [frame] may differ.
 */
interface ContinuityPlayer {
    val frame: PlaybackFrame
    val changes: Flow<Unit>

    suspend fun open(url: String, startMs: Long = 0)

    suspend fun play()

    suspend fun pause()

    suspend fun seek(positionMs: Long)

    suspend fun setVolume(value: Double)

    suspend fun setRate(value: Double)

    suspend fun stop()

    suspend fun dispose()
}

/** Putting a player back at a place once its source is open (playback_restore.dart). */
object PlaybackRestore {
    /**
     * Where to resume at [positionMs] of [durationMs]: half a second before
     * the end at most, so a finished one does not open at its last frame;
     * the start when either is unknown.
     */
    fun resumeTarget(positionMs: Long, durationMs: Long): Long {
        if (positionMs <= 0 || durationMs <= 0) return 0
        val ceiling = durationMs - 500
        if (ceiling <= 0) return 0
        return positionMs.coerceAtMost(ceiling)
    }

    /**
     * Moves [player], whose source has just opened, to [positionMs] once it
     * knows its duration, and answers whether it got there. An open may take
     * the start place, but the player says it is ready later, so the place
     * is checked, and sought only when it is not already reached. False when
     * the source failed, the wait ran out or [isCurrent] says this source was
     * replaced meanwhile.
     */
    suspend fun restore(
        player: ContinuityPlayer,
        positionMs: Long,
        isCurrent: () -> Boolean = { true },
        readyTimeoutMs: Long = 5_000,
        seekTimeoutMs: Long = 2_000,
    ): Boolean {
        if (!isCurrent()) return false
        if (positionMs <= 0) return true
        val ready = waitFor(player, readyTimeoutMs) { player.frame.failed || player.frame.durationMs > 0 }
        if (!ready || !isCurrent() || player.frame.failed) return false
        val target = resumeTarget(positionMs, player.frame.durationMs)
        fun reached() = abs(player.frame.positionMs - target) <= 1_000
        if (reached()) return true
        if (withTimeoutOrNull(seekTimeoutMs) { player.seek(target) } == null || !isCurrent()) return false
        return waitFor(player, seekTimeoutMs, ::reached)
    }

    /** Whether [ready] holds within [timeoutMs], checked at once and then on every change of [player]. */
    suspend fun waitFor(player: ContinuityPlayer, timeoutMs: Long, ready: () -> Boolean): Boolean {
        if (ready()) return true
        return withTimeoutOrNull(timeoutMs) {
            player.changes.first { ready() }
            true
        } ?: ready()
    }
}

/**
 * What the system's media controls (the lock screen, a headset's button)
 * do for what plays now. Read-aloud offers only [onStop]; a podcast offers
 * the lot (xta_audio_handler.dart).
 */
class AudioSessionBinding(
    val onPlay: (() -> Unit)? = null,
    val onPause: (() -> Unit)? = null,
    val onStop: () -> Unit,
    val onSeek: ((positionMs: Long) -> Unit)? = null,
)

/**
 * The one media session behind everything the app plays without a screen.
 * Whoever plays binds itself, and a new binding replaces the last, the way
 * starting one thing replaces what is heard.
 */
interface AudioSessions {
    fun bind(title: String, artist: String? = null, binding: AudioSessionBinding)

    fun update(playing: Boolean, positionMs: Long = 0, durationMs: Long? = null)

    fun clear()

    /** Where the platform has no media session: everything plays without one. */
    object None : AudioSessions {
        override fun bind(title: String, artist: String?, binding: AudioSessionBinding) = Unit

        override fun update(playing: Boolean, positionMs: Long, durationMs: Long?) = Unit

        override fun clear() = Unit
    }
}
