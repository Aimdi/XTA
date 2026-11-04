package com.aimdi.xtanative.ui.media

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberAudioSessions(): AudioSessions {
    val context = LocalContext.current.applicationContext
    return remember(context) { androidAudioSessions(context) }
}

@Composable
actual fun rememberContinuityPlayers(): (() -> ContinuityPlayer)? {
    val context = LocalContext.current.applicationContext
    return remember(context) { { ExoContinuityPlayer(context) } }
}

private var sessions: FrameworkAudioSessions? = null

/** The process's one media session, made on first use. */
@Synchronized
internal fun androidAudioSessions(context: Context): AudioSessions =
    sessions ?: FrameworkAudioSessions(context.applicationContext).also { sessions = it }

/**
 * Android's own media session (xta_audio_handler.dart): headset buttons, a
 * watch or a car reach what plays through it. The callbacks fan the system's
 * buttons out to whoever is bound.
 */
private class FrameworkAudioSessions(context: Context) : AudioSessions {
    private val session = MediaSession(context, "XTA Native")

    @Volatile
    private var binding: AudioSessionBinding? = null
    private var title: String = ""
    private var artist: String? = null

    init {
        session.setCallback(
            object : MediaSession.Callback() {
                override fun onPlay() {
                    binding?.onPlay?.invoke()
                }

                override fun onPause() {
                    binding?.onPause?.invoke()
                }

                override fun onStop() {
                    binding?.onStop?.invoke()
                    clear()
                }

                override fun onSeekTo(pos: Long) {
                    binding?.onSeek?.invoke(pos)
                }
            },
        )
    }

    override fun bind(title: String, artist: String?, binding: AudioSessionBinding) {
        this.binding = binding
        this.title = title
        this.artist = artist
        session.setMetadata(metadata(null))
        session.isActive = true
    }

    override fun update(playing: Boolean, positionMs: Long, durationMs: Long?) {
        val binding = binding ?: return
        if (durationMs != null) session.setMetadata(metadata(durationMs))
        var actions = PlaybackState.ACTION_STOP
        if (binding.onPlay != null && binding.onPause != null) {
            actions = actions or PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE
        }
        if (binding.onSeek != null) actions = actions or PlaybackState.ACTION_SEEK_TO
        val state = if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        session.setPlaybackState(PlaybackState.Builder().setActions(actions).setState(state, positionMs, if (playing) 1f else 0f).build())
    }

    override fun clear() {
        binding = null
        session.setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_NONE, 0, 0f).build())
        session.setMetadata(null)
        session.isActive = false
    }

    private fun metadata(durationMs: Long?): MediaMetadata {
        val builder = MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title)
        artist?.let { builder.putString(MediaMetadata.METADATA_KEY_ARTIST, it) }
        durationMs?.let { builder.putLong(MediaMetadata.METADATA_KEY_DURATION, it) }
        return builder.build()
    }
}

/**
 * Media3's player as the continuity port (continuity_player.dart's
 * MediaKitContinuityPlayer): a podcast episode playing with speech's audio
 * focus, pausing when headphones are pulled out. Its frame is read on the
 * main thread, as the player wants, and kept for readers on any other.
 */
private class ExoContinuityPlayer(context: Context) : ContinuityPlayer {
    private val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
        .setHandleAudioBecomingNoisy(true)
        .build()
    private val ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile
    private var failed = false

    @Volatile
    override var frame: PlaybackFrame = PlaybackFrame()
        private set

    override val changes: Flow<Unit> = ticks

    init {
        player.addListener(
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = refresh()

                override fun onPlayerError(error: PlaybackException) {
                    failed = true
                    refresh()
                }
            },
        )
        scope.launch {
            while (isActive) {
                delay(500)
                if (player.isPlaying) refresh()
            }
        }
    }

    private fun refresh() {
        val duration = player.duration
        frame = PlaybackFrame(
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = if (duration == C.TIME_UNSET || duration < 0) 0 else duration,
            playing = player.isPlaying,
            completed = player.playbackState == Player.STATE_ENDED,
            failed = failed,
            volume = player.volume * 100.0,
            rate = player.playbackParameters.speed.toDouble(),
        )
        ticks.tryEmit(Unit)
    }

    private suspend fun onMain(block: () -> Unit) = withContext(Dispatchers.Main.immediate) {
        block()
        refresh()
    }

    override suspend fun open(url: String, startMs: Long) = onMain {
        failed = false
        player.setMediaItem(MediaItem.fromUri(url), startMs.coerceAtLeast(0))
        player.playWhenReady = false
        player.prepare()
    }

    override suspend fun play() = onMain { player.play() }

    override suspend fun pause() = onMain { player.pause() }

    override suspend fun seek(positionMs: Long) = onMain { player.seekTo(positionMs.coerceAtLeast(0)) }

    override suspend fun setVolume(value: Double) = onMain { player.volume = (value / 100.0).coerceIn(0.0, 1.0).toFloat() }

    override suspend fun setRate(value: Double) = onMain { player.setPlaybackSpeed(value.coerceIn(0.25, 4.0).toFloat()) }

    override suspend fun stop() = onMain { player.stop() }

    override suspend fun dispose() {
        withContext(Dispatchers.Main.immediate) {
            scope.cancel()
            player.release()
        }
    }
}
