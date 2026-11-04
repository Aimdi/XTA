package com.aimdi.xtanative.ui.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.aimdi.xtanative.store.settings.MediaPrefs
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.links.LocalLinks
import com.aimdi.xtanative.ui.post.PlayBadge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * libmpv under the viewer's controls (_video.dart's pooled player and
 * video_source_store.dart), as ExoVideoPlayback is on Android: [video] opens
 * paused and plays while active; a change of quality opens the other
 * variant where this one was, and when that fails the one before plays on
 * from there. A player mpv would not make shows as failed.
 */
internal class MpvVideoPlayback(video: ViewerVideo) : VideoPlayback {
    private val gif = video.gif
    private val lock = Any()
    private var load = 0L

    /** The variant and place to go back to while a change of quality has not played yet. */
    private var previous: Pair<String, Long>? = null

    @Volatile
    private var wanted = false

    private val player = MpvPlayer.create(MpvOptions.video(video.prefetchSeconds, video.loop, video.muted), video = true, name = "mpv-video")
    private val current = MutableStateFlow(VideoState(url = video.url, qualities = video.qualities, muted = video.muted, failed = player == null))
    override val state: StateFlow<VideoState> = current.asStateFlow()

    /** Its frames, for the surface; none when mpv made no player. */
    val frames: MpvFrames? = player?.let { MpvFrames(it, ::firstFrame) }

    init {
        player?.onChange = ::changed
        synchronized(lock) { load = player?.open(video.url, 0, play = false) ?: 0 }
    }

    /** Plays while [active], as a page shown does; paused otherwise. */
    fun setActive(active: Boolean) {
        wanted = active
        if (active) player?.play() else player?.pause()
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

    override fun seekTo(positionMs: Long) {
        player?.seek(positionMs)
        current.update { it.copy(positionMs = positionMs.coerceAtLeast(0)) }
    }

    override fun setMuted(muted: Boolean) {
        if (gif) return
        player?.setMuted(muted)
        current.update { it.copy(muted = muted) }
    }

    override fun setSpeed(speed: Double) {
        player?.setSpeed(speed)
        current.update { it.copy(speed = speed) }
    }

    override fun setQuality(url: String) {
        val now = state.value
        if (url == now.url || player == null) return
        synchronized(lock) {
            previous = now.url to now.positionMs
            load = player.open(url, now.positionMs, play = wanted)
        }
        current.update { it.copy(url = url) }
    }

    override fun setSubtitles(shown: Boolean) {
        player?.setSubtitles(shown)
        current.update { it.copy(subtitles = shown) }
    }

    override fun retry() {
        val now = state.value
        val opened = player ?: return
        wanted = true
        synchronized(lock) { load = opened.open(now.url, now.positionMs, play = true) }
        current.update { it.copy(failed = false) }
    }

    override fun qualityFailureSeen() {
        current.update { it.copy(qualityFailed = false) }
    }

    /** Lets the player go: its frames end it once they no longer draw. */
    fun release() {
        frames?.close() ?: player?.close()
    }

    /** What mpv says [name] is now (the check's: the video's size, the sound's output). */
    suspend fun property(name: String): String? = player?.property(name)

    /** What mpv said, on the player's thread: the controls' state, and a failed change of quality undone. */
    private fun changed(before: MpvState, after: MpvState) {
        synchronized(lock) {
            if (after.load != load) return
            if (after.failed && !before.failed) return failed()
            if (after.ready) previous = null
        }
        current.update {
            it.copy(
                playing = after.playing,
                completed = after.ended,
                positionMs = after.positionMs,
                durationMs = after.durationMs,
                bufferedMs = after.cachedMs,
                speed = after.speed,
                hasSubtitles = after.subtitleTrack != 0,
            )
        }
    }

    /** A failed change of quality goes back to the variant before it, where it was; anything else shows as failed. */
    private fun failed() {
        val back = previous
        previous = null
        val opened = player
        if (back != null && opened != null) {
            load = opened.open(back.first, back.second, play = wanted)
            current.update { it.copy(url = back.first, qualityFailed = true) }
        } else {
            current.update { it.copy(failed = true, playing = false) }
        }
    }

    /** The first frame of the source playing now is on its way to the screen: the poster can go. */
    private fun firstFrame(load: Long) {
        if (load == synchronized(lock) { this.load }) current.update { it.copy(started = true) }
    }
}

/**
 * [video] playing in the app while [active] (a page shown), as Android's
 * viewer plays one: paused when the window goes away unless the background
 * playback setting says otherwise. [onPlaying] hears the variant it plays,
 * which [onDownload] saves.
 */
@Composable
internal fun PlayingVideo(
    video: ViewerVideo,
    poster: Any?,
    alt: String?,
    active: Boolean,
    onPlaying: (String) -> Unit = {},
    onDownload: ((playingUrl: String) -> Unit)? = null,
) {
    val graph = LocalGraph.current
    val playback = remember(video) { MpvVideoPlayback(video) }
    DisposableEffect(playback) { onDispose { playback.release() } }
    LaunchedEffect(playback, active) { playback.setActive(active) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (graph.settings.state.value?.get(MediaPrefs.backgroundPlayback) == false) playback.setActive(false)
    }
    val state by playback.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.url) { onPlaying(state.url) }
    EngineVideo(playback, poster, alt, controls = !video.gif, onDownload = onDownload?.let { save -> { save(state.url) } })
}

/** [playback]'s frames, its [poster] over them until the first, and the viewer's [controls] (none for a GIF). */
@Composable
internal fun EngineVideo(
    playback: MpvVideoPlayback,
    poster: Any?,
    alt: String?,
    controls: Boolean,
    onDownload: (() -> Unit)?,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    val state by playback.state.collectAsStateWithLifecycle()
    var fill by remember { mutableStateOf(false) }
    Box(modifier) {
        playback.frames?.let { VideoSurface(it, crop = fill, modifier = Modifier.fillMaxSize()) }
        if (!state.started && poster != null) {
            AsyncImage(model = poster, contentDescription = alt, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
        if (controls) VideoControls(playback, fill, onToggleFill = { fill = !fill }, onDownload = onDownload)
    }
}

/**
 * Where the desktop has no player (no libmpv, [MpvEngine]): the poster, and
 * a click hands [url] to the system, which opens it in the browser or the
 * video player set for it.
 */
@Composable
internal fun OutsideVideo(poster: Any?, alt: String?, url: String?) {
    val links = LocalLinks.current
    val uri = LocalUriHandler.current
    Box(
        Modifier.fillMaxSize().clickable(enabled = url != null) {
            url?.let { target -> links?.openOutside(target) ?: runCatching { uri.openUri(target) } }
        },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(model = poster, contentDescription = alt, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        if (url != null) PlayBadge(Modifier)
    }
}
