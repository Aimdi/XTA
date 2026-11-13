package com.aimdi.xtanative.ui.media

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.reader_media_pause
import com.aimdi.xtanative.ui.generated.resources.reader_media_play
import com.aimdi.xtanative.ui.generated.resources.reader_speech_stop
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
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun rememberAudioSessions(): AudioSessions {
    val context = LocalContext.current.applicationContext
    val sessions = remember(context) { frameworkSessions(context) }
    rememberPlaybackLabels(sessions)
    return sessions
}

@Composable
actual fun rememberContinuityPlayers(): (() -> ContinuityPlayer)? {
    val context = LocalContext.current.applicationContext
    return remember(context) { { ExoContinuityPlayer(context) } }
}

/** The words of the media notification's buttons, in the reader's language. */
internal data class PlaybackLabels(val play: String, val pause: String, val stop: String) {
    fun of(control: PlaybackControl): String = when (control) {
        PlaybackControl.PLAY -> play
        PlaybackControl.PAUSE -> pause
        PlaybackControl.STOP -> stop
    }
}

/** Gives [sessions] its buttons' words, again whenever the language changes. */
@Composable
internal fun rememberPlaybackLabels(sessions: FrameworkAudioSessions) {
    val labels = PlaybackLabels(stringResource(Res.string.reader_media_play), stringResource(Res.string.reader_media_pause), stringResource(Res.string.reader_speech_stop))
    SideEffect { sessions.labels = labels }
}

private var sessions: FrameworkAudioSessions? = null

/** The process's one media session, made on first use. */
@Synchronized
internal fun frameworkSessions(context: Context): FrameworkAudioSessions =
    sessions ?: FrameworkAudioSessions(context.applicationContext).also { sessions = it }

internal fun androidAudioSessions(context: Context): AudioSessions = frameworkSessions(context)

/**
 * Android's own media session (xta_audio_handler.dart, audio_service on
 * Android): headset buttons, a watch or a car reach what plays through it,
 * and while it plays its notification and the lock screen carry the
 * controls, the playback service keeping the process meanwhile. The
 * callbacks fan the system's buttons out to whoever is bound. Everything
 * happens on the main thread, whichever thread asked.
 */
internal class FrameworkAudioSessions(context: Context) : AudioSessions {
    private val main = Handler(Looper.getMainLooper())
    private val session = MediaSession(context, "XTA Native")
    val notifications = PlaybackNotifications(context, session)

    @Volatile
    var labels: PlaybackLabels? = null

    private var binding: AudioSessionBinding? = null
    private var title: String = ""
    private var artist: String? = null
    private var durationMs: Long? = null

    init {
        session.setCallback(
            object : MediaSession.Callback() {
                override fun onPlay() = press(PlaybackControl.PLAY)

                override fun onPause() = press(PlaybackControl.PAUSE)

                override fun onStop() = press(PlaybackControl.STOP)

                override fun onSeekTo(pos: Long) {
                    binding?.onSeek?.invoke(pos)
                }

                override fun onCustomAction(action: String, extras: Bundle?) {
                    if (action == STOP_ACTION) press(PlaybackControl.STOP)
                }
            },
            main,
        )
    }

    /** A headset's button the system broadcast to the app: the session's callback turns it into its control. */
    fun mediaButton(event: KeyEvent) {
        main.post { session.controller.dispatchMediaButtonEvent(event) }
    }

    /** A control pressed on the notification, the lock screen or a headset; stop also takes the session down. */
    fun press(control: PlaybackControl) {
        main.post {
            val bound = binding
            if (bound == null) {
                // A notification an ended process left behind.
                notifications.hide()
                return@post
            }
            PlaybackControls.press(bound, control)
            if (control == PlaybackControl.STOP) clearNow()
        }
    }

    override fun bind(title: String, artist: String?, binding: AudioSessionBinding) {
        main.post {
            this.binding = binding
            this.title = title
            this.artist = artist
            durationMs = null
            notifications.openApp?.let(session::setSessionActivity)
            session.setMetadata(metadata())
            session.isActive = true
        }
    }

    override fun update(playing: Boolean, positionMs: Long, durationMs: Long?) {
        main.post {
            val bound = binding ?: return@post
            if (durationMs != null && durationMs != this.durationMs) {
                this.durationMs = durationMs
                session.setMetadata(metadata())
            }
            session.setPlaybackState(state(bound, playing, positionMs))
            notifications.show(PlaybackControls.notice(bound, title, artist, playing), labels)
        }
    }

    override fun clear() {
        main.post { clearNow() }
    }

    private fun clearNow() {
        binding = null
        session.setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_NONE, 0, 0f).build())
        session.setMetadata(null)
        session.isActive = false
        notifications.hide()
    }

    private fun state(binding: AudioSessionBinding, playing: Boolean, positionMs: Long): PlaybackState {
        var actions = PlaybackState.ACTION_STOP
        if (binding.onPlay != null && binding.onPause != null) {
            actions = actions or PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE
        }
        if (binding.onSeek != null) actions = actions or PlaybackState.ACTION_SEEK_TO
        val state = if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        val builder = PlaybackState.Builder().setActions(actions).setState(state, positionMs, if (playing) 1f else 0f)
        // Android 13's player on the lock screen draws a session's own actions, where stop is none of the standard ones.
        labels?.stop?.takeIf { it.isNotEmpty() }?.let { builder.addCustomAction(PlaybackState.CustomAction.Builder(STOP_ACTION, it, PlaybackIcons.STOP).build()) }
        return builder.build()
    }

    private fun metadata(): MediaMetadata {
        val builder = MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title)
        artist?.let { builder.putString(MediaMetadata.METADATA_KEY_ARTIST, it) }
        durationMs?.let { builder.putLong(MediaMetadata.METADATA_KEY_DURATION, it) }
        return builder.build()
    }

    private companion object {
        const val STOP_ACTION = "com.aimdi.xtanative.playback.STOP"
    }
}

/**
 * Media3's player as the continuity port (continuity_player.dart's
 * MediaKitContinuityPlayer): a podcast episode playing with speech's audio
 * focus, pausing when headphones are pulled out. Its frame is read on the
 * main thread, as the player wants, and kept for readers on any other.
 */
internal class ExoContinuityPlayer(context: Context) : ContinuityPlayer {
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
