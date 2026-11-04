package com.aimdi.xtanative.ui.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.aimdi.xtanative.services.speech.ClipPlayer
import com.aimdi.xtanative.services.speech.InstalledVoice
import com.aimdi.xtanative.services.speech.SherpaJni
import com.aimdi.xtanative.services.speech.SherpaVoiceSynthesizer
import com.aimdi.xtanative.services.speech.SpeechEngine
import com.aimdi.xtanative.services.speech.SpeechSession
import com.aimdi.xtanative.services.speech.VoiceEnginePackage
import com.aimdi.xtanative.services.speech.VoiceEngines
import com.aimdi.xtanative.services.speech.VoiceSynthesizer
import com.aimdi.xtanative.ui.media.AudioSessionBinding
import com.aimdi.xtanative.ui.media.AudioSessions
import com.aimdi.xtanative.ui.media.ExoContinuityPlayer
import com.aimdi.xtanative.ui.media.frameworkSessions
import com.aimdi.xtanative.ui.media.rememberPlaybackLabels
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Composable
internal actual fun rememberSpeechPlatform(): () -> SpeechPlatform {
    val context = LocalContext.current.applicationContext
    rememberPlaybackLabels(remember(context) { frameworkSessions(context) })
    return remember(context) { { AndroidSpeech(context) } }
}

/**
 * Reading aloud on Android: the system's text-to-speech, the media session's
 * stop control, and downloaded voices, run by the voice engine for this
 * processor, which downloads from XTA's releases with the first voice
 * (voice_engine_store.dart). The app's JNI shim reaches it; an APK built
 * without one ([synthesis] null) downloads voices but reads with the system.
 */
private class AndroidSpeech(context: Context) : SpeechPlatform {
    private val system = AndroidSpeechEngine(context)

    override val engine: SpeechEngine = system
    override val voices: SystemVoices = system
    override val session: SpeechSession = AndroidSpeechSession(context, frameworkSessions(context))
    override val voiceFolder: File = File(context.filesDir, "voices")
    override val voiceEngine: VoiceEnginePackage? = Build.SUPPORTED_ABIS.firstOrNull()?.let(VoiceEngines::forAbi)
    override val sources: VoiceSources = VoiceSources.fromProperties()
    override val synthesis: VoiceSynthesis? = voiceEngine?.takeIf { shimLoads() }?.let { AndroidSynthesis(context, it) }

    private fun shimLoads(): Boolean = runCatching { System.loadLibrary(VoiceEngines.SHIM) }.isSuccess
}

/**
 * Downloaded voices on Android (offline_speech_setup.dart): sherpa-onnx from
 * the folder the engine downloaded to, clips in the app's cache, played
 * through Media3's player as the podcast player's (clip_player.dart).
 */
private class AndroidSynthesis(context: Context, private val engine: VoiceEnginePackage) : VoiceSynthesis {
    private val clips = File(context.cacheDir, "xta-voice-clips")

    override suspend fun load(installed: InstalledVoice, engineFolder: File?): VoiceSynthesizer {
        val folder = checkNotNull(engineFolder) { "The voice engine is not on this device" }
        return SherpaVoiceSynthesizer.load(installed, clips) {
            SherpaJni.open(folder, engine.libraries.map { it.name }) { System.loadLibrary(VoiceEngines.SHIM) }
        }
    }

    override val player: ClipPlayer = ContinuityClipPlayer(create = { ExoContinuityPlayer(context) }, context = Dispatchers.Main)
}

/**
 * The audio around a reading (speech_store.dart's audio session and lock
 * screen stop): focus that lets other sound duck rather than stop, so a
 * voice speaking from another process (Sherpa's) is still heard, and the one
 * media session showing the title with a stop control in its notification
 * and on the lock screen. Marked playing there, as XTA's media service needs
 * to show it; the framework's session takes no audio focus for that.
 */
private class AndroidSpeechSession(context: Context, private val sessions: AudioSessions) : SpeechSession {
    private val audio: AudioManager? = context.getSystemService(AudioManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .build()

    override suspend fun prepareAudio() {
        runCatching { audio?.requestAudioFocus(focus) }
    }

    override fun bind(title: String, stop: suspend () -> Unit) {
        sessions.bind(title, binding = AudioSessionBinding(onStop = { scope.launch { stop() } }))
        sessions.update(playing = true)
    }

    override fun clear() {
        sessions.clear()
        runCatching { audio?.abandonAudioFocusRequest(focus) }
    }
}
