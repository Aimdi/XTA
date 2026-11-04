package com.aimdi.xtanative.ui.media

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.aimdi.xtanative.core.x.model.PhotoSize
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.photoUrl
import com.aimdi.xtanative.store.settings.MediaPrefs
import com.aimdi.xtanative.store.settings.MediaQuality
import com.aimdi.xtanative.ui.common.LocalGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal actual val viewerPageButtons: Boolean = false

/**
 * Media3's player under XTA's controls for a video; a GIF loops without
 * them, silent. The variant is the one the video-quality setting picks, the
 * sound as the app-wide mute says, and it loops when the loop setting is on.
 * The poster covers the player until its first frame.
 */
@Composable
internal actual fun VideoPage(media: XMedia, active: Boolean, onPlaying: (String) -> Unit, onDownload: ((playingUrl: String) -> Unit)?) {
    val context = LocalContext.current
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val gif = media.kind == XMedia.Kind.GIF
    val playback = remember(media) {
        val settings = graph.settings.state.value
        val qualities = VideoRules.qualities(media)
        val quality = settings?.get(MediaPrefs.videoQuality) ?: MediaQuality.MEDIUM
        val url = VideoRules.defaultUrl(qualities, VideoRules.streamUrl(media), quality)
        url?.let {
            ExoVideoPlayback(
                context = context,
                url = it,
                qualities = qualities,
                gif = gif,
                loop = gif || settings?.get(MediaPrefs.loop) == true,
                muted = gif || VideoMute.muted(settings?.get(MediaPrefs.mute) ?: true),
                scope = scope,
            )
        }
    } ?: return
    DisposableEffect(playback) { onDispose { playback.release() } }
    LaunchedEffect(active) { playback.setActive(active) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (graph.settings.state.value?.get(MediaPrefs.backgroundPlayback) == false) playback.setActive(false)
    }
    val state by playback.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.url) { onPlaying(state.url) }
    var fill by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        PlayerSurface(playback.player, fill, Modifier.fillMaxSize())
        // Over the player: its surface is behind the window, and would clear a poster drawn under it.
        if (!state.started) {
            AsyncImage(model = photoUrl(media.url, PhotoSize.LARGE), contentDescription = media.alt, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
        if (!gif) {
            VideoControls(playback, fill, onToggleFill = { fill = !fill }, onDownload = onDownload?.let { save -> { save(state.url) } })
        }
    }
}

@Composable
private fun PlayerSurface(player: ExoPlayer, fill: Boolean, modifier: Modifier) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                setShutterBackgroundColor(AndroidColor.TRANSPARENT)
                setBackgroundColor(AndroidColor.TRANSPARENT)
                this.player = player
            }
        },
        update = { view -> view.resizeMode = if (fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT },
        onRelease = { view -> view.player = null },
        modifier = modifier,
    )
}

/**
 * Media3's player as the viewer's controls see it (_video.dart's pooled
 * player and video_source_store.dart). A change of quality opens the other
 * variant where this one was, and when it fails the one before plays on
 * from there. Its state is read on the main thread a few times a second
 * while it plays, so the time and the seek bar move.
 */
private class ExoVideoPlayback(
    context: Context,
    url: String,
    qualities: List<VideoQuality>,
    private val gif: Boolean,
    loop: Boolean,
    muted: Boolean,
    scope: CoroutineScope,
) : VideoPlayback {
    private val attributes = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build()

    val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        // A muted video takes no audio focus: it must not pause what the reader is listening to.
        setAudioAttributes(attributes, !muted)
        volume = if (muted) 0f else 1f
        repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        trackSelectionParameters = trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        setMediaItem(MediaItem.fromUri(url))
        prepare()
    }

    private val current = MutableStateFlow(VideoState(url = url, qualities = qualities, muted = muted))
    override val state: StateFlow<VideoState> = current.asStateFlow()

    /** The variant and place to go back to while a change of quality has not played yet. */
    private var previous: Pair<String, Long>? = null
    private val ticker: Job

    init {
        player.addListener(
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    if (player.playbackState == Player.STATE_READY) previous = null
                    refresh()
                }

                override fun onRenderedFirstFrame() {
                    current.update { it.copy(started = true) }
                }

                override fun onTracksChanged(tracks: Tracks) {
                    current.update { it.copy(hasSubtitles = tracks.groups.any { group -> group.type == C.TRACK_TYPE_TEXT }) }
                }

                override fun onPlayerError(error: PlaybackException) = failed()
            },
        )
        ticker = scope.launch {
            while (isActive) {
                delay(250)
                if (player.isPlaying) refresh()
            }
        }
    }

    private fun refresh() {
        val duration = player.duration
        current.update {
            it.copy(
                playing = player.isPlaying,
                completed = player.playbackState == Player.STATE_ENDED,
                positionMs = player.currentPosition.coerceAtLeast(0),
                durationMs = if (duration == C.TIME_UNSET || duration < 0) 0 else duration,
                bufferedMs = player.bufferedPosition.coerceAtLeast(0),
                speed = player.playbackParameters.speed.toDouble(),
            )
        }
    }

    /** A failed change of quality goes back to the variant before it; anything else shows as failed. */
    private fun failed() {
        val back = previous
        previous = null
        if (back != null) {
            open(back.first, back.second)
            current.update { it.copy(qualityFailed = true) }
        } else {
            current.update { it.copy(failed = true) }
        }
    }

    fun setActive(active: Boolean) {
        player.playWhenReady = active
        refresh()
    }

    override fun playOrPause() {
        when {
            player.playbackState == Player.STATE_ENDED -> {
                player.seekTo(0)
                player.play()
            }
            player.isPlaying -> player.pause()
            else -> player.play()
        }
        refresh()
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0))
        refresh()
    }

    override fun setMuted(muted: Boolean) {
        if (gif) return
        player.volume = if (muted) 0f else 1f
        player.setAudioAttributes(attributes, !muted)
        current.update { it.copy(muted = muted) }
    }

    override fun setSpeed(speed: Double) {
        player.setPlaybackSpeed(speed.toFloat())
        refresh()
    }

    override fun setQuality(url: String) {
        if (url == current.value.url) return
        previous = current.value.url to player.currentPosition
        open(url, player.currentPosition)
    }

    /** [url] from [positionMs], playing on if it was playing. */
    private fun open(url: String, positionMs: Long) {
        val playing = player.playWhenReady
        player.setMediaItem(MediaItem.fromUri(url), PlaybackRestore.resumeTarget(positionMs, current.value.durationMs))
        player.prepare()
        player.playWhenReady = playing
        current.update { it.copy(url = url) }
    }

    override fun setSubtitles(shown: Boolean) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !shown).build()
        current.update { it.copy(subtitles = shown) }
    }

    override fun retry() {
        current.update { it.copy(failed = false) }
        player.prepare()
        player.playWhenReady = true
    }

    override fun qualityFailureSeen() {
        current.update { it.copy(qualityFailed = false) }
    }

    fun release() {
        ticker.cancel()
        player.release()
    }
}
