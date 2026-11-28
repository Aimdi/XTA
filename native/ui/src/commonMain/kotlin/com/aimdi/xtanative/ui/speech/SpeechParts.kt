package com.aimdi.xtanative.ui.speech

import com.aimdi.xtanative.services.speech.ClipPlayer
import com.aimdi.xtanative.services.speech.InstalledVoice
import com.aimdi.xtanative.services.speech.OfflineSpeechEngine
import com.aimdi.xtanative.services.speech.SpeechEngine
import com.aimdi.xtanative.services.speech.SpeechReader
import com.aimdi.xtanative.services.speech.SpeechSession
import com.aimdi.xtanative.services.speech.StreamTar
import com.aimdi.xtanative.services.speech.VoiceDownloadStore
import com.aimdi.xtanative.services.speech.VoiceEnginePackage
import com.aimdi.xtanative.services.speech.VoiceEngineStore
import com.aimdi.xtanative.services.speech.VoiceSynthesizer
import com.aimdi.xtanative.store.downloads.OkHttpMediaHttp
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.SettingsStore
import com.aimdi.xtanative.store.settings.SpeechPrefs
import java.io.File
import java.util.Locale
import java.util.WeakHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Turns text into speech with a downloaded voice, and plays what it made:
 * sherpa-onnx, loaded from the voice engine [VoiceEngineStore] downloads, or
 * the one the app carries.
 */
interface VoiceSynthesis {
    /** [installed] ready to speak, run by the engine in [engineFolder] (null: the one the app carries). */
    suspend fun load(installed: InstalledVoice, engineFolder: File?): VoiceSynthesizer

    val player: ClipPlayer
}

/** What reading aloud needs from the platform it runs on. */
interface SpeechPlatform {
    /** The platform's own engine: Android's text-to-speech. Null on the desktop, which has none. */
    val engine: SpeechEngine?

    /** The engines and voices the settings offer; null where there is no engine to choose. */
    val voices: SystemVoices?

    val session: SpeechSession

    /** Where downloaded voices, the voice engine and their downloads under way are kept. */
    val voiceFolder: File

    /** The voice engine this device would download; null where none is published for its processor, or the app carries it. */
    val voiceEngine: VoiceEnginePackage?

    /** The app carries the voice engine (the desktop's tarball and Arch package), so there is none to download. */
    val engineBundled: Boolean get() = false

    /** Where voices and the engine download from. */
    val sources: VoiceSources get() = VoiceSources(server = null, releaseTag = "")

    /**
     * Synthesis with a downloaded voice; null where this build cannot reach
     * sherpa-onnx (an APK built without the JNI shim, a desktop run without
     * the engine beside it). Then voices still download, and the platform's
     * engine reads, where it has one.
     */
    val synthesis: VoiceSynthesis?
}

/**
 * Reading aloud's parts, one set per settings store (one per app), made the first time a
 * screen asks: the reading itself ([speech]), the downloadable voices and
 * the engine that runs them ([voices]), and what the platform offers to
 * choose from ([systemVoices]). They outlive every screen.
 */
class SpeechParts private constructor(settings: SettingsStore, platform: SpeechPlatform) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val voices: VoiceDownloadStore = run {
        val transfer = MediaVoiceTransfer(OkHttpMediaHttp(), File(platform.voiceFolder, "partial"))
        val reader = StreamTar()
        val sources = platform.sources
        val engine = VoiceEngineStore(platform.voiceEngine, platform.engineBundled, File(platform.voiceFolder, "engine"), transfer, reader, sources::engineUrls)
        VoiceDownloadStore(engine, File(platform.voiceFolder, "voices"), transfer, reader, sources.catalog)
    }

    val systemVoices: SystemVoices? = platform.voices

    /** Synthesis with a downloaded voice, where this build can reach sherpa-onnx. */
    val synthesis: VoiceSynthesis? = platform.synthesis

    /** Whether downloaded voices can read in this build at all. */
    val synthesisAvailable: Boolean = synthesis != null

    /** Runs [work] for as long as the app does: a download goes on when its screen is left. */
    fun keep(work: suspend () -> Unit) {
        scope.launch { work() }
    }

    val speech: AppSpeech = run {
        val voiceOn = offlineVoiceOn(systemEngine = platform.engine != null)
        val offline = synthesis?.let { offlineEngine(settings, voiceOn, it) }?.also { releasedWhenTurnedOff(it, settings, voiceOn) }
        val engines = listOfNotNull(offline, platform.engine)
        AppSpeech(SpeechReader(engines, platform.session), settings, scope, hasEngine = engines.isNotEmpty(), voicesOnly = platform.engine == null && offline != null)
    }

    /** A downloaded voice reading, while the setting allows it and one speaks the text's language (offline_speech_setup.dart). */
    private fun offlineEngine(settings: SettingsStore, voiceOn: (Settings?) -> Boolean, synthesis: VoiceSynthesis) = OfflineSpeechEngine(
        voiceFor = voices::installedFor,
        enabled = { voiceOn(settings.state.value) },
        load = { installed -> synthesis.load(installed, voices.engine.loadFrom) },
        player = synthesis.player,
        discard = { path -> File(path).delete() },
        scope = scope,
        appLanguage = { Locale.getDefault().language.ifEmpty { null } },
    )

    /** Turning offline voices off lets go of the loaded voice now, not at the next reading. */
    private fun releasedWhenTurnedOff(engine: OfflineSpeechEngine, settings: SettingsStore, voiceOn: (Settings?) -> Boolean) {
        scope.launch {
            settings.state.map(voiceOn).distinctUntilChanged().filter { !it }.collect { engine.release() }
        }
    }

    /**
     * Whether a downloaded voice reads: as the setting says where the system's
     * engine reads otherwise (Android); always without one (the desktop),
     * where turning it off, or a backup restored with it off, would leave
     * nothing to read with.
     */
    private fun offlineVoiceOn(systemEngine: Boolean): (Settings?) -> Boolean =
        if (systemEngine) { stored -> stored?.get(SpeechPrefs.offlineVoice) ?: true } else { _ -> true }

    companion object {
        private val made = WeakHashMap<SettingsStore, SpeechParts>()

        /** The parts that read with [settings], made with [platform] the first time. */
        @Synchronized
        fun of(settings: SettingsStore, platform: () -> SpeechPlatform): SpeechParts = made.getOrPut(settings) { SpeechParts(settings, platform()) }
    }
}
