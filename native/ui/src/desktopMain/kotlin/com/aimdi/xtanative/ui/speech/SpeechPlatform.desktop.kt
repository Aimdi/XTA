package com.aimdi.xtanative.ui.speech

import androidx.compose.runtime.Composable
import com.aimdi.xtanative.services.speech.SpeechEngine
import com.aimdi.xtanative.services.speech.SpeechSession
import com.aimdi.xtanative.services.speech.VoiceEnginePackage
import java.io.File

@Composable
internal actual fun rememberSpeechPlatform(): () -> SpeechPlatform = { DesktopSpeech }

/**
 * The desktop has no speech engine: Linux brings none the app can drive, and
 * this build carries no voice engine of its own. The bar says so when the
 * reader asks to listen, and downloaded voices show as unavailable here.
 */
private object DesktopSpeech : SpeechPlatform {
    override val engine: SpeechEngine? = null
    override val voices: SystemVoices? = null
    override val session: SpeechSession = object : SpeechSession {}
    override val voiceEngine: VoiceEnginePackage? = null
    override val synthesis: VoiceSynthesis? = null

    /** Beside the app's own files: under $XDG_DATA_HOME, else ~/.local/share. */
    override val voiceFolder: File by lazy {
        val base = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }?.let(::File) ?: File(System.getProperty("user.home"), ".local/share")
        File(File(base, "xta-native"), "voices")
    }
}
