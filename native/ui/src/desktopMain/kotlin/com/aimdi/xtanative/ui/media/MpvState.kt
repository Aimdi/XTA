package com.aimdi.xtanative.ui.media

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** What a player observes of mpv, by the number its events name each with ([id]). [ofFile] is about the file open, not the player. */
internal enum class MpvProperty(val key: String, val format: Int, val ofFile: Boolean) {
    PAUSE("pause", Mpv.FORMAT_FLAG, false),
    ENDED("eof-reached", Mpv.FORMAT_FLAG, true),
    POSITION("time-pos", Mpv.FORMAT_DOUBLE, true),
    DURATION("duration", Mpv.FORMAT_DOUBLE, true),
    CACHED("demuxer-cache-time", Mpv.FORMAT_DOUBLE, true),
    SPEED("speed", Mpv.FORMAT_DOUBLE, false),
    VOLUME("volume", Mpv.FORMAT_DOUBLE, false),
    MUTED("mute", Mpv.FORMAT_FLAG, false),
    WIDTH("dwidth", Mpv.FORMAT_INT64, true),
    HEIGHT("dheight", Mpv.FORMAT_INT64, true),
    ;

    val id: Long get() = ordinal + 1L

    companion object {
        fun of(id: Long): MpvProperty? = entries.getOrNull((id - 1).toInt())
    }
}

/**
 * Where a player stands, as media_kit's player state does for XTA. [load]
 * counts what the app opened, [entry] is mpv's playlist entry for it once
 * mpv took the command: a file's events count only for that entry, and what
 * mpv says of "the file" only once it has [started] it, so nothing of the
 * file before (its end, its length) passes for this one's. [ready] is mpv's
 * playback restart: its first frame is decoded, so a frame drawn from then
 * on is this source's.
 */
internal data class MpvState(
    val load: Long = 0,
    val entry: Long = 0,
    val started: Boolean = false,
    val ready: Boolean = false,
    val paused: Boolean = true,
    val ended: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val cachedMs: Long = 0,
    val speed: Double = 1.0,
    val volume: Double = 100.0,
    val muted: Boolean = false,
    val width: Int = 0,
    val height: Int = 0,
    /** Degrees clockwise the picture is turned to be seen upright (a phone's video), which the app does: mpv's renderer cannot. */
    val rotation: Int = 0,
    /** The first subtitle track's id, 0 when the file has none. */
    val subtitleTrack: Int = 0,
    val failed: Boolean = false,
    /** mpv's error when it [failed]. */
    val error: Int = 0,
) {
    /** Source [load] asked for from [startMs]: nothing is known of it yet; the player's own settings stay. */
    fun opening(load: Long, startMs: Long) = MpvState(load = load, positionMs = startMs.coerceAtLeast(0), paused = paused, speed = speed, volume = volume, muted = muted)

    /** mpv took the open of [load] as playlist [entry]; a negative one is the error it refused it with. */
    fun opened(load: Long, entry: Long): MpvState = when {
        load != this.load -> this
        entry < 0 -> copy(failed = true, error = entry.toInt())
        else -> copy(entry = entry)
    }

    /** Nothing open any more. */
    fun stopped() = MpvState(paused = paused, speed = speed, volume = volume, muted = muted)

    fun after(event: MpvEvent): MpvState = when (event.id) {
        MpvEvent.START_FILE -> if (isMine(event)) copy(started = true) else this
        MpvEvent.END_FILE -> if (isMine(event)) ended(event) else this
        MpvEvent.PLAYBACK_RESTART -> if (started && !failed) copy(ready = true) else this
        MpvEvent.PROPERTY_CHANGE -> MpvProperty.of(event.reply)?.let { with(it, event.value) } ?: this
        else -> this
    }

    /** [property] is now [value], NaN while it has none. */
    fun with(property: MpvProperty, value: Double): MpvState {
        if (property.ofFile && !started) return this
        val known = !value.isNaN()
        return when (property) {
            MpvProperty.PAUSE -> if (known) copy(paused = value != 0.0) else this
            MpvProperty.ENDED -> copy(ended = known && value != 0.0)
            MpvProperty.POSITION -> if (known) copy(positionMs = millis(value)) else this
            MpvProperty.DURATION -> copy(durationMs = if (known) millis(value) else 0)
            MpvProperty.CACHED -> copy(cachedMs = if (known) millis(value) else 0)
            MpvProperty.SPEED -> if (known) copy(speed = value) else this
            MpvProperty.VOLUME -> if (known) copy(volume = value) else this
            MpvProperty.MUTED -> if (known) copy(muted = value != 0.0) else this
            MpvProperty.WIDTH -> copy(width = if (known) value.toInt() else 0)
            MpvProperty.HEIGHT -> copy(height = if (known) value.toInt() else 0)
        }
    }

    /** Playing, as media_kit says it: something open, not paused, not at its end and not failed. */
    val playing: Boolean get() = entry != 0L && !paused && !ended && !failed

    /** A frame drawn now is this source's picture: it is ready, and its size is known. */
    val drawable: Boolean get() = ready && width > 0 && height > 0

    /** The continuity port's frame (continuity_player.dart's, from media_kit's state). */
    val frame: PlaybackFrame
        get() = PlaybackFrame(positionMs = positionMs, durationMs = durationMs, playing = playing, completed = ended, failed = failed, volume = volume, rate = speed)

    private fun isMine(event: MpvEvent) = entry != 0L && event.entry == entry

    /** The file's end: an error fails it; its end is its end; a stop or a replacement is the app's own doing. */
    private fun ended(event: MpvEvent): MpvState = when (event.endReason) {
        MpvEvent.END_ERROR -> copy(failed = true, error = event.endError, ready = false)
        MpvEvent.END_EOF -> copy(ended = true)
        else -> this
    }

    companion object {
        fun millis(seconds: Double): Long = (seconds * 1000).toLong().coerceAtLeast(0)

        /** A file's rotation as mpv writes it ("270"), in the quarter turns it can be: 0 otherwise. */
        fun rotationOf(value: String?): Int {
            val degrees = value?.toIntOrNull()?.mod(360) ?: return 0
            return if (degrees % 90 == 0) degrees else 0
        }
    }
}

/** What each player starts with, as XTA's media_kit players are set (_video.dart, continuity_player.dart). */
internal object MpvOptions {
    /** XTA's feed videos read ten seconds ahead (kVideoReadaheadSeconds), and keep at most these many bytes ahead and behind. */
    const val READAHEAD_SECONDS = 10
    const val DEMUXER_MAX_BYTES = 16 * 1024 * 1024
    const val DEMUXER_MAX_BACK_BYTES = 4 * 1024 * 1024

    /**
     * Every player: none of mpv's own configuration, scripts, terminal or
     * keys; opened paused, the last frame kept at the end (a finished video
     * shows it); no youtube-dl; no sound card means silence, not a failure;
     * subtitles hidden until asked for.
     */
    val BASE: List<Pair<String, String>> = listOf(
        "config" to "no",
        "load-scripts" to "no",
        "ytdl" to "no",
        "terminal" to "no",
        "input-default-bindings" to "no",
        "input-vo-keyboard" to "no",
        "osc" to "no",
        "idle" to "yes",
        "pause" to "yes",
        "keep-open" to "yes",
        "audio-fallback-to-null" to "yes",
        "sub-visibility" to "no",
    )

    /** Options without which a player cannot work at all, or not safely; the others it may do without, an older or newer mpv lacking one. */
    val REQUIRED = setOf("vo", "vid", "video-rotate")

    /**
     * A video's player: drawn through the render API, reading ahead as XTA's
     * videos do (the reader's prefetch, else ten seconds, and the demuxer's
     * bounds), looping and muted as asked. mpv does not turn the picture:
     * its software renderer aborts the process on a turned one (mpv 0.37),
     * so the app turns it ([MpvState.rotation]).
     */
    fun video(prefetchSeconds: Int, loop: Boolean, muted: Boolean): List<Pair<String, String>> = BASE + listOf(
        "vo" to "libmpv",
        "video-rotate" to "no",
        "cache-secs" to "${if (prefetchSeconds > 0) prefetchSeconds else READAHEAD_SECONDS}",
        "demuxer-readahead-secs" to "$READAHEAD_SECONDS",
        "demuxer-max-bytes" to "$DEMUXER_MAX_BYTES",
        "demuxer-max-back-bytes" to "$DEMUXER_MAX_BACK_BYTES",
        "loop-file" to loopValue(loop),
        "mute" to if (muted) "yes" else "no",
    )

    /** A podcast's or a voice's player: sound alone, no picture of it and no cover art. */
    val AUDIO: List<Pair<String, String>> = BASE + listOf("vid" to "no", "audio-display" to "no")

    fun loopValue(loop: Boolean): String = if (loop) "inf" else "no"

    /**
     * Where a source starts, as mpv's `start` option takes it: whole seconds,
     * since mpv reads a decimal point in the C locale, which the process's
     * need not be; "none" for its beginning.
     */
    fun start(ms: Long): String = if (ms < 1_000) "none" else (ms / 1_000).toString()
}

/** The size a video's frames are drawn at, in pixels. */
internal data class FrameSize(val width: Int, val height: Int) {
    /** A row's bytes: four a pixel, rounded up to 64, as mpv's renderer wants them aligned. */
    val stride: Int get() = (width * 4 + 63) / 64 * 64
}

internal object VideoFrameSizes {
    /** Drawn this small while no surface shows the video, so mpv keeps playing. */
    const val IDLE = 64

    /**
     * The frame for a video of [videoWidth] x [videoHeight], turned
     * [rotation] degrees on a surface of [targetWidth] x [targetHeight]: the
     * video's own shape, as large as the surface shows it (fitted, or
     * covering it when [crop]ped), never larger than the video itself; small
     * while there is no surface. Null while the video's size is not known.
     */
    fun of(videoWidth: Int, videoHeight: Int, rotation: Int, targetWidth: Int, targetHeight: Int, crop: Boolean): FrameSize? {
        if (videoWidth <= 0 || videoHeight <= 0) return null
        val scale = if (targetWidth <= 0 || targetHeight <= 0) {
            IDLE.toFloat() / max(videoWidth, videoHeight)
        } else {
            shownScale(videoWidth, videoHeight, rotation, targetWidth.toFloat(), targetHeight.toFloat(), crop)
        }.coerceAtMost(1f)
        return FrameSize((videoWidth * scale).roundToInt().coerceAtLeast(2), (videoHeight * scale).roundToInt().coerceAtLeast(2))
    }

    /**
     * The scale a frame of [frameWidth] x [frameHeight], turned [rotation]
     * degrees about its centre, is drawn at on a surface of [boxWidth] x
     * [boxHeight]: fitted in it, or covering it when [crop]ped.
     */
    fun shownScale(frameWidth: Int, frameHeight: Int, rotation: Int, boxWidth: Float, boxHeight: Float, crop: Boolean): Float {
        val turned = rotation % 180 != 0
        val across = boxWidth / (if (turned) frameHeight else frameWidth)
        val down = boxHeight / (if (turned) frameWidth else frameHeight)
        return if (crop) max(across, down) else min(across, down)
    }
}
