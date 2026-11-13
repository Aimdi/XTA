package com.aimdi.xtanative.ui.media

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether videos play with their sound off, for the whole app
 * (VideoContextState): muting one video keeps the next one muted, on every
 * screen. Until the reader touches it, the mute setting says.
 */
object VideoMute {
    private val chosen = MutableStateFlow<Boolean?>(null)

    /** What the reader chose last; null until they touch a mute control. */
    val choice: StateFlow<Boolean?> = chosen.asStateFlow()

    fun muted(setting: Boolean): Boolean = chosen.value ?: setting

    fun set(muted: Boolean) {
        chosen.value = muted
    }
}

/**
 * One video plays at a time, as XTA's pool pauses the others when one starts
 * (VideoControllerPool.pauseOthers); GIFs and silent previews loop on and are
 * not counted. Players tell it on the main thread.
 */
internal object OneVideo {
    private var owner: Any? = null
    private var pauseIt: (() -> Unit)? = null

    /** [owner] started playing: the one playing before it is paused with what it gave. */
    fun started(owner: Any, pause: () -> Unit) {
        if (this.owner !== owner) pauseIt?.invoke()
        this.owner = owner
        pauseIt = pause
    }

    /** [owner] paused, stopped or let go: nothing is left to pause for it. */
    fun stopped(owner: Any) {
        if (this.owner !== owner) return
        this.owner = null
        pauseIt = null
    }
}

/** A video in the viewer as its controls draw it. Times are in milliseconds. */
data class VideoState(
    val playing: Boolean = false,
    val completed: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val muted: Boolean = false,
    val speed: Double = 1.0,
    /** The variant playing now, one of [qualities] unless the video has none to pick from. */
    val url: String = "",
    val qualities: List<VideoQuality> = emptyList(),
    /** The video carries a subtitle track, and whether it is shown. */
    val hasSubtitles: Boolean = false,
    val subtitles: Boolean = false,
    /** It failed before its first frame. */
    val failed: Boolean = false,
    /** A change of quality failed, and the one before it plays on; the controls say so once. */
    val qualityFailed: Boolean = false,
    /** The first frame is on screen, so the poster under it can go. */
    val started: Boolean = false,
    /** It plays once it can, or plays now: what play and pause last said. */
    val active: Boolean = false,
)

/** What the viewer's video controls ask of the platform's player (_video_controls.dart's use of it). */
interface VideoPlayback {
    val state: StateFlow<VideoState>

    /** Plays, or pauses; a finished video starts over. */
    fun playOrPause()

    fun seekTo(positionMs: Long)

    /** Mute is the app's, not one video's: muting one leaves the next muted too. */
    fun setMuted(muted: Boolean)

    fun setSpeed(speed: Double)

    /** Plays the variant at [url] from where this one was. */
    fun setQuality(url: String)

    fun setSubtitles(shown: Boolean)

    /** Starts the video again after it failed to load. */
    fun retry()

    fun qualityFailureSeen()
}
