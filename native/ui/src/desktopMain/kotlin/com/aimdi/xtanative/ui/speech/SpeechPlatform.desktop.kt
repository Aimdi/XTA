package com.aimdi.xtanative.ui.speech

import androidx.compose.runtime.Composable
import com.aimdi.xtanative.services.speech.ClipPlayer
import com.aimdi.xtanative.services.speech.InstalledVoice
import com.aimdi.xtanative.services.speech.SherpaJni
import com.aimdi.xtanative.services.speech.SherpaVoiceSynthesizer
import com.aimdi.xtanative.services.speech.SpeechEngine
import com.aimdi.xtanative.services.speech.SpeechSession
import com.aimdi.xtanative.services.speech.VoiceEnginePackage
import com.aimdi.xtanative.services.speech.VoiceEngines
import com.aimdi.xtanative.services.speech.VoiceSynthesizer
import java.io.File

@Composable
internal actual fun rememberSpeechPlatform(): () -> SpeechPlatform = { DesktopSpeech }

/**
 * Reading aloud on the desktop, which Linux gives the app no engine for:
 * downloaded voices only, run by the voice engine the tarball and the Arch
 * package carry beside the app, as XTA's Linux bundle does
 * (`lib/app/resources/voice-engine`, or XTA_NATIVE_VOICE_ENGINE for a build
 * run from Gradle). Without it, voices show as unavailable here.
 */
private object DesktopSpeech : SpeechPlatform {
    override val engine: SpeechEngine? = null
    override val voices: SystemVoices? = null
    override val session: SpeechSession = object : SpeechSession {}
    override val voiceEngine: VoiceEnginePackage? = null

    private val carried: File? = engineFolder()?.takeIf { VoiceEngines.carriedIn(it) }
    override val engineBundled: Boolean = carried != null
    override val synthesis: VoiceSynthesis? = carried?.let(::DesktopSynthesis)
    override val sources: VoiceSources = VoiceSources(System.getenv("XTA_NATIVE_VOICE_SERVER")?.trimEnd('/')?.ifEmpty { null }, releaseTag = "")

    /** Beside the app's own files: under $XDG_DATA_HOME, else ~/.local/share. */
    override val voiceFolder: File by lazy { File(File(xdg("XDG_DATA_HOME", ".local/share"), "xta-native"), "voices") }

    /** Where the engine is: as named, in Compose's app resources, else beside the launcher jpackage made (bin/xta-native). */
    private fun engineFolder(): File? {
        System.getenv("XTA_NATIVE_VOICE_ENGINE")?.ifEmpty { null }?.let { return File(it) }
        System.getProperty("compose.application.resources.dir")?.let { return File(it, "voice-engine") }
        return System.getProperty("jpackage.app-path")?.let { File(File(it).parentFile?.parentFile, "lib/app/resources/voice-engine") }
    }
}

/** $[name], else [fallback] in the home folder. */
private fun xdg(name: String, fallback: String): File =
    System.getenv(name)?.takeIf { it.isNotBlank() }?.let(::File) ?: File(System.getProperty("user.home"), fallback)

/** Downloaded voices on the desktop: the engine in [folder], clips in the user's cache, played with Java Sound. */
private class DesktopSynthesis(private val folder: File) : VoiceSynthesis {
    private val clips = File(File(xdg("XDG_CACHE_HOME", ".cache"), "xta-native"), "voice-clips")

    override suspend fun load(installed: InstalledVoice, engineFolder: File?): VoiceSynthesizer = SherpaVoiceSynthesizer.load(installed, clips) {
        SherpaJni.open(folder, VoiceEngines.LINUX_X64.libraries.map { it.name }) { System.load(File(folder, System.mapLibraryName(VoiceEngines.SHIM)).path) }
    }

    override val player: ClipPlayer = SoundClipPlayer()
}
