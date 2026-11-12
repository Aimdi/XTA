package com.aimdi.xtanative.ui.media

import android.content.Context
import android.view.Gravity
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.SubtitleView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Media3's player under XTA's controls (_video.dart's pooled player and
 * video_source_store.dart), made as Settings › Media says: it reads ahead as
 * far as the prefetch allows ([VideoBuffering]); with Direct hardware
 * decoding its frames go straight to a surface of the screen's own, else
 * they are copied into a texture first; it takes the audio focus only for a
 * video heard, and not at all while other apps may play on, pausing when
 * headphones are pulled out unless so; and it keeps the phone awake for a
 * video going on in the background. A change of quality opens the other
 * variant where this one was, and when that fails the one before plays on
 * from there. Its state is read on the main thread a few times a second
 * while it plays, so the time and the seek bar move.
 */
internal class ExoVideoPlayer(context: Context, private val options: VideoOptions) : VideoPlayer {
    private val attributes = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val player: ExoPlayer = ExoPlayer.Builder(context)
        .setLoadControl(loadControl(VideoBuffering.of(options.prefetchSeconds)))
        .setHandleAudioBecomingNoisy(!options.mixWithOthers)
        .setWakeMode(if (options.backgroundPlay) C.WAKE_MODE_NETWORK else C.WAKE_MODE_NONE)
        .build()

    private val current = MutableStateFlow(VideoState())
    override val state: StateFlow<VideoState> = current.asStateFlow()

    private var source: VideoSource? = null

    /** The variant and place to go back to while a change of quality has not played yet. */
    private var previous: Pair<String, Long>? = null
    private var retries = 0
    private var view: VideoView? = null

    init {
        player.addListener(Events())
        scope.launch {
            while (isActive) {
                delay(250)
                if (player.isPlaying) refresh()
            }
        }
    }

    private inner class Events : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (player.playbackState == Player.STATE_READY) previous = null
            refresh()
        }

        override fun onRenderedFirstFrame() {
            retries = 0
            current.update { it.copy(started = true) }
        }

        override fun onTracksChanged(tracks: Tracks) {
            current.update { it.copy(hasSubtitles = tracks.groups.any { group -> group.type == C.TRACK_TYPE_TEXT }) }
        }

        override fun onPlayerError(error: PlaybackException) = failed()

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (source?.gif != false) return
            if (isPlaying) OneVideo.started(this@ExoVideoPlayer) { player.pause() } else OneVideo.stopped(this@ExoVideoPlayer)
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            view?.shape(videoSize)
        }

        override fun onCues(cueGroup: CueGroup) {
            view?.subtitles?.setCues(cueGroup.cues)
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
                active = player.playWhenReady,
            )
        }
    }

    /**
     * A failed change of quality goes back to the variant before it; a GIF
     * tries again by itself a few times, as it has no controls to; anything
     * else shows as failed.
     */
    private fun failed() {
        val back = previous
        previous = null
        when {
            back != null -> {
                reopen(back.first, back.second)
                current.update { it.copy(qualityFailed = true) }
            }
            source?.gif == true && retries < GIF_RETRIES -> {
                retries++
                player.prepare()
            }
            else -> current.update { it.copy(failed = true) }
        }
    }

    override fun open(source: VideoSource) {
        // The place showing what played before lets go of the picture: the new video's tile takes it.
        view?.let(::detach)
        this.source = source
        previous = null
        retries = 0
        player.repeatMode = if (source.loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        sound(source.muted)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        player.setMediaItem(MediaItem.fromUri(source.url), source.startMs.coerceAtLeast(0))
        player.prepare()
        current.value = VideoState(url = source.url, qualities = source.qualities, muted = source.muted)
    }

    /** A video not heard, or one other apps may play beside, takes no audio focus: it must not pause what the reader is listening to. */
    private fun sound(muted: Boolean) {
        player.volume = if (muted) 0f else 1f
        player.setAudioAttributes(attributes, !muted && !options.mixWithOthers)
    }

    override fun setActive(active: Boolean) {
        player.playWhenReady = active
        refresh()
    }

    override fun stop() {
        view?.let(::detach)
        source = null
        previous = null
        player.stop()
        player.clearMediaItems()
        OneVideo.stopped(this)
        current.value = VideoState()
    }

    override fun release() {
        scope.cancel()
        OneVideo.stopped(this)
        player.release()
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
        if (source?.gif != false) return
        sound(muted)
        current.update { it.copy(muted = muted) }
    }

    override fun setSpeed(speed: Double) {
        player.setPlaybackSpeed(speed.toFloat())
        refresh()
    }

    override fun setQuality(url: String) {
        if (url == current.value.url) return
        previous = current.value.url to player.currentPosition
        reopen(url, player.currentPosition)
    }

    /** [url] from [positionMs], playing on if it was playing. */
    private fun reopen(url: String, positionMs: Long) {
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

    /** A video keeps the screen on while it plays, as XTA's player holds its wake lock; a GIF does not. */
    @Composable
    override fun Picture(fill: Boolean, modifier: Modifier) {
        val now by current.collectAsState()
        val awake = now.playing && source?.gif == false
        AndroidView(
            factory = { context -> VideoView(context, options.directDecoding).also(::attach) },
            update = { view ->
                view.fill(fill)
                view.keepScreenOn = awake
            },
            onRelease = ::detach,
            modifier = modifier,
        )
    }

    /** [view] shows the picture now, in place of the one that did. */
    private fun attach(view: VideoView) {
        val before = this.view
        this.view = view
        if (before !== view) before?.hide(player)
        view.show(player)
    }

    private fun detach(view: VideoView) {
        if (this.view !== view) return
        this.view = null
        view.hide(player)
    }

    private companion object {
        const val GIF_RETRIES = 3

        fun loadControl(buffer: VideoBuffer): DefaultLoadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(buffer.minMs, buffer.maxMs, buffer.playbackMs, buffer.rebufferMs)
            .setTargetBufferBytes(buffer.targetBytes)
            .setBackBuffer(buffer.backMs, false)
            .build()
    }
}

/**
 * A video's picture in a frame of its shape, fitted to its place or filling
 * it, with its subtitles over it. With [direct] the frames go to a surface of
 * the screen's own (Direct hardware decoding, which renders black on some
 * phones), else through a texture, which draws everywhere.
 */
private class VideoView(context: Context, direct: Boolean) : FrameLayout(context) {
    private val frame = AspectRatioFrameLayout(context)
    private val surface: View = if (direct) SurfaceView(context) else TextureView(context)
    val subtitles = SubtitleView(context)

    init {
        frame.addView(surface, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(frame, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))
        subtitles.setUserDefaultStyle()
        subtitles.setUserDefaultTextSize()
        addView(subtitles, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    fun show(player: ExoPlayer) {
        when (surface) {
            is SurfaceView -> player.setVideoSurfaceView(surface)
            is TextureView -> player.setVideoTextureView(surface)
        }
        shape(player.videoSize)
    }

    fun hide(player: ExoPlayer) {
        when (surface) {
            is SurfaceView -> player.clearVideoSurfaceView(surface)
            is TextureView -> player.clearVideoTextureView(surface)
        }
        subtitles.setCues(null)
    }

    fun fill(fill: Boolean) {
        frame.resizeMode = if (fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    fun shape(size: VideoSize) {
        if (size.width > 0 && size.height > 0) frame.setAspectRatio(size.width * size.pixelWidthHeightRatio / size.height)
    }
}
