package com.aimdi.xtanative.ui.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.aimdi.xtanative.services.speech.SpeechEngine
import com.aimdi.xtanative.services.speech.SpeechSession
import com.aimdi.xtanative.services.speech.VoiceEnginePackage
import com.aimdi.xtanative.services.speech.VoiceEngines
import com.aimdi.xtanative.ui.media.AudioSessionBinding
import com.aimdi.xtanative.ui.media.AudioSessions
import com.aimdi.xtanative.ui.media.androidAudioSessions
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Composable
internal actual fun rememberSpeechPlatform(): () -> SpeechPlatform {
    val context = LocalContext.current.applicationContext
    return remember(context) { { AndroidSpeech(context) } }
}

/**
 * Reading aloud on Android: the system's text-to-speech, the media session's
 * stop control, and the voice engine for this processor, downloaded from
 * XTA's releases. Downloaded voices cannot read yet: this build has no
 * binding to the sherpa-onnx library ([synthesis] is null), so the system's
 * engine reads.
 */
private class AndroidSpeech(context: Context) : SpeechPlatform {
    private val system = AndroidSpeechEngine(context)

    override val engine: SpeechEngine = system
    override val voices: SystemVoices = system
    override val session: SpeechSession = AndroidSpeechSession(context, androidAudioSessions(context))
    override val voiceFolder: File = File(context.filesDir, "voices")
    override val voiceEngine: VoiceEnginePackage? = Build.SUPPORTED_ABIS.firstOrNull()?.let(VoiceEngines::forAbi)
    override val synthesis: VoiceSynthesis? = null
}

/**
 * The audio around a reading (speech_store.dart's audio session and lock
 * screen stop): focus that lets other sound duck rather than stop, so a
 * voice speaking from another process (Sherpa's) is still heard, and the one
 * media session showing the title with a stop control, without claiming
 * playback.
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
