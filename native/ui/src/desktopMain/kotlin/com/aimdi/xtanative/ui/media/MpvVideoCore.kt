package com.aimdi.xtanative.ui.media

import javax.swing.SwingUtilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * What draws a video player's pictures for it ([MpvFrames]): told when a
 * source opens or stops, so nothing of the one before is shown, and let go
 * with the player, which it ends once it no longer draws.
 */
internal interface MpvPictures {
    fun opened()

    fun close()
}

/** What a failed source leads to (ExoVideoPlayer's failed, video_source_store.dart). */
internal sealed interface VideoFailure {
    /** Back to the variant a change of quality left, from where it was. */
    data class Back(val url: String, val positionMs: Long) : VideoFailure

    /** A GIF opens again by itself: it has no controls to retry with. */
    data object Again : VideoFailure

    /** It shows as failed, with the controls' retry. */
    data object Failed : VideoFailure

    companion object {
        const val GIF_RETRIES = 3

        fun of(previous: Pair<String, Long>?, gif: Boolean, retries: Int): VideoFailure = when {
            previous != null -> Back(previous.first, previous.second)
            gif && retries < GIF_RETRIES -> Again
            else -> Failed
        }
    }
}

/** The controls' state once mpv stands at [mpv], keeping what the player itself decides: the variant, its qualities, the sound, subtitles, whether it is wanted playing. */
internal fun VideoState.after(mpv: MpvState): VideoState = copy(
    playing = mpv.playing,
    completed = mpv.ended,
    positionMs = mpv.positionMs,
    durationMs = mpv.durationMs,
    bufferedMs = mpv.cachedMs,
    speed = mpv.speed,
    hasSubtitles = mpv.subtitleTrack != 0,
)

/**
 * libmpv as tiles take turns at a player and the viewer's page holds one
 * (ExoVideoPlayer on Android, _video.dart's pooled player): it opens a
 * source paused and plays while active; a change of quality opens the other
 * variant where this one was, and when that fails the one before plays on
 * from there; a GIF that fails opens again a few times by itself; a video
 * playing pauses the one that played before it ([OneVideo], told on the main
 * thread through [onMain]). [makePictures] makes what draws its frames
 * ([pictures]). A player mpv would not make shows as failed.
 */
internal class MpvVideoCore<P : MpvPictures>(
    options: VideoOptions,
    makePictures: (player: MpvPlayer, onFirst: (load: Long) -> Unit) -> P,
    private val onMain: (() -> Unit) -> Unit = { block -> SwingUtilities.invokeLater(block) },
) : PlayerCore {
    /**
     * Held while the source, its number and the state that goes with them
     * change: a source's state is set before mpv hears of it, so what mpv
     * says of the one before, or of this one at once (a failure), never
     * lands on the wrong one.
     */
    private val lock = Any()
    private var source: VideoSource? = null
    private var load = 0L

    /** The variant and place to go back to while a change of quality has not played yet. */
    private var previous: Pair<String, Long>? = null
    private var retries = 0

    /** Plays once it can: what play and pause last said (Media3's playWhenReady). */
    @Volatile
    private var wanted = false

    /** Let go: what mpv still says on its way out plays no part in which video plays. */
    @Volatile
    private var released = false

    private val player = MpvPlayer.create(MpvOptions.video(options.prefetchSeconds, loop = false, muted = true), video = true, name = "mpv-video")
    private val current = MutableStateFlow(VideoState(failed = player == null))
    override val state: StateFlow<VideoState> = current.asStateFlow()

    val pictures: P? = player?.let { makePictures(it, ::firstFrame) }

    /** The source playing now, by the number its frames are known by; 0 for none. */
    val playing: Long get() = synchronized(lock) { load }

    init {
        player?.onChange = ::changed
    }

    override fun open(source: VideoSource) {
        val opened = player ?: return
        pictures?.opened()
        wanted = false
        opened.setLoop(source.loop)
        opened.setMuted(source.muted)
        opened.setSubtitles(false)
        synchronized(lock) {
            this.source = source
            previous = null
            retries = 0
            current.value = VideoState(url = source.url, qualities = source.qualities, muted = source.muted)
            load = opened.open(source.url, 0, play = false)
        }
    }

    override fun setActive(active: Boolean) {
        wanted = active
        if (active) player?.play() else player?.pause()
        current.update { it.copy(active = active) }
    }

    override fun stop() {
        pictures?.opened()
        wanted = false
        synchronized(lock) {
            source = null
            previous = null
            load = 0
            current.value = VideoState()
        }
        player?.stop()
        OneVideo.stopped(this)
    }

    override fun release() {
        released = true
        OneVideo.stopped(this)
        pictures?.close() ?: player?.close()
    }

    override fun playOrPause() {
        val now = state.value
        when {
            now.completed -> {
                player?.seek(0)
                setActive(true)
            }
            now.playing -> setActive(false)
            else -> setActive(true)
        }
    }

    /** Sought, it plays on if it was wanted playing, as Media3 does past a video's end. */
    override fun seekTo(positionMs: Long) {
        player?.seek(positionMs)
        if (wanted) player?.play()
        current.update { it.copy(positionMs = positionMs.coerceAtLeast(0)) }
    }

    override fun setMuted(muted: Boolean) {
        if (synchronized(lock) { source?.gif } != false) return
        player?.setMuted(muted)
        current.update { it.copy(muted = muted) }
    }

    override fun setSpeed(speed: Double) {
        player?.setSpeed(speed)
        current.update { it.copy(speed = speed) }
    }

    override fun setQuality(url: String) {
        val now = state.value
        val opened = player ?: return
        if (url == now.url) return
        synchronized(lock) {
            previous = now.url to now.positionMs
            current.update { it.copy(url = url) }
            load = opened.open(url, now.positionMs, play = wanted)
        }
    }

    override fun setSubtitles(shown: Boolean) {
        player?.setSubtitles(shown)
        current.update { it.copy(subtitles = shown) }
    }

    override fun retry() {
        val now = state.value
        val opened = player ?: return
        wanted = true
        synchronized(lock) {
            current.update { it.copy(failed = false, active = true) }
            load = opened.open(now.url, now.positionMs, play = true)
        }
    }

    override fun qualityFailureSeen() {
        current.update { it.copy(qualityFailed = false) }
    }

    /** What mpv says [name] is now (the video check's: the video's size, the sound's output). */
    suspend fun property(name: String): String? = player?.property(name)

    /** What mpv said, on the player's thread: the controls' state, a failure answered, and the one video playing told. */
    private fun changed(before: MpvState, after: MpvState) {
        val gif = synchronized(lock) {
            if (after.load != load) return
            if (after.failed && !before.failed) return failed()
            if (after.ready) previous = null
            current.update { it.after(after) }
            source?.gif ?: true
        }
        if (gif || after.playing == before.playing) return
        onMain { if (after.playing && !released) OneVideo.started(this) { setActive(false) } else OneVideo.stopped(this) }
    }

    /** Holding [lock]: back to the variant before, again for a GIF, or failed. */
    private fun failed() {
        val opened = player ?: return
        val now = source ?: return
        when (val step = VideoFailure.of(previous, now.gif, retries)) {
            is VideoFailure.Back -> {
                previous = null
                load = opened.open(step.url, step.positionMs, play = wanted)
                current.update { it.copy(url = step.url, qualityFailed = true) }
            }
            VideoFailure.Again -> {
                retries++
                load = opened.open(now.url, 0, play = wanted)
            }
            VideoFailure.Failed -> current.update { it.copy(failed = true, playing = false) }
        }
    }

    /** The first frame of the source playing now is on its way to the screen: the poster can go. */
    private fun firstFrame(load: Long) {
        synchronized(lock) {
            if (load != this.load) return
            retries = 0
            current.update { it.copy(started = true) }
        }
    }
}
