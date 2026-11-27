package com.aimdi.xtanative.ui.speech

import androidx.compose.runtime.Composable
import com.aimdi.xtanative.services.speech.ClipPlayer
import com.aimdi.xtanative.services.speech.InstalledVoice
import com.aimdi.xtanative.services.speech.SherpaJni
import com.aimdi.xtanative.services.speech.SherpaVoiceSynthesizer
import com.aimdi.xtanative.services.speech.SpeechEngine
import com.aimdi.xtanative.services.speech.SpeechSession
import com.aimdi.xtanative.services.speech.UtteranceOutcome
import com.aimdi.xtanative.services.speech.VoiceEnginePackage
import com.aimdi.xtanative.services.speech.VoiceEngines
import com.aimdi.xtanative.services.speech.VoiceSynthesizer
import com.aimdi.xtanative.ui.common.AppResources
import com.aimdi.xtanative.ui.common.XdgFolders
import com.aimdi.xtanative.ui.media.AudioSessionBinding
import com.aimdi.xtanative.ui.media.MpvContinuityPlayer
import com.aimdi.xtanative.ui.media.MpvEngine
import com.aimdi.xtanative.ui.media.mpris.MprisSessions
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
    override val session: SpeechSession = DesktopSpeechSession
    override val voiceEngine: VoiceEnginePackage? = null

    private val carried: File? = engineFolder()?.takeIf { VoiceEngines.carriedIn(it) }
    override val engineBundled: Boolean = carried != null
    override val synthesis: VoiceSynthesis? = carried?.let(::DesktopSynthesis)
    override val sources: VoiceSources = VoiceSources(System.getenv("XTA_NATIVE_VOICE_SERVER")?.trimEnd('/')?.ifEmpty { null }, releaseTag = "")

    /** Beside the app's own files: under $XDG_DATA_HOME, else ~/.local/share. */
    override val voiceFolder: File by lazy { File(File(xdg("XDG_DATA_HOME", ".local/share"), "xta-native"), "voices") }

    private fun engineFolder(): File? = AppResources.folder("voice-engine", System.getenv("XTA_NATIVE_VOICE_ENGINE"))
}

/**
 * A reading as the desktop's media controls see it (MPRIS): its title, and
 * the play/pause or stop key ends it, as the media notification's stop does
 * on Android.
 */
private object DesktopSpeechSession : SpeechSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun bind(title: String, stop: suspend () -> Unit) {
        val sessions = MprisSessions.service
        sessions.bind(this, title, artist = null, binding = AudioSessionBinding(onStop = { scope.launch { stop() } }), reading = true)
        sessions.update(this, playing = true, positionMs = 0, durationMs = null)
    }

    // Only the reading's own: a video that took the keys since keeps them.
    override fun clear() = MprisSessions.service.clear(this)
}

/** $[name], else [fallback] in the home folder. */
private fun xdg(name: String, fallback: String): File = XdgFolders.base(name, fallback)

/** Downloaded voices on the desktop: the engine in [folder], clips in the user's cache, played as [DesktopClipPlayer] plays them. */
private class DesktopSynthesis(private val folder: File) : VoiceSynthesis {
    private val clips = File(File(xdg("XDG_CACHE_HOME", ".cache"), "xta-native"), "voice-clips")

    override suspend fun load(installed: InstalledVoice, engineFolder: File?): VoiceSynthesizer = SherpaVoiceSynthesizer.load(installed, clips) {
        SherpaJni.open(folder, VoiceEngines.LINUX_X64.libraries.map { it.name }) { System.load(File(folder, System.mapLibraryName(VoiceEngines.SHIM)).path) }
    }

    override val player: ClipPlayer = DesktopClipPlayer()
}

/**
 * Clips through libmpv's continuity player where the desktop has libmpv, as
 * XTA plays them through media_kit (clip_player.dart), so a reading goes
 * out wherever the podcasts do; through Java Sound without it. Chosen at the
 * first clip, so nothing opens libmpv before a downloaded voice speaks.
 */
private class DesktopClipPlayer : ClipPlayer {
    private val chosen = lazy<ClipPlayer> { if (MpvEngine.ready) ContinuityClipPlayer(create = { MpvContinuityPlayer.create() }) else SoundClipPlayer() }

    override suspend fun play(path: String): UtteranceOutcome = chosen.value.play(path)

    override suspend fun stop() {
        if (chosen.isInitialized()) chosen.value.stop()
    }
}
