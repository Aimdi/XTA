package com.aimdi.xtanative.ui.speech

import com.aimdi.xtanative.data.AppGraph
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
import com.aimdi.xtanative.store.settings.SpeechPrefs
import java.io.File
import java.util.Locale
import java.util.WeakHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
 * Reading aloud's parts, one set per app graph, made the first time a
 * screen asks: the reading itself ([speech]), the downloadable voices and
 * the engine that runs them ([voices]), and what the platform offers to
 * choose from ([systemVoices]). They outlive every screen.
 */
class SpeechParts private constructor(graph: AppGraph, platform: SpeechPlatform) {
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
        val offline = synthesis?.let { offlineEngine(graph, it) }
        val engines = listOfNotNull(offline, platform.engine)
        AppSpeech(SpeechReader(engines, platform.session), graph.settings, scope, hasEngine = engines.isNotEmpty(), voicesOnly = platform.engine == null && offline != null)
    }

    /** A downloaded voice reading, while the setting allows it and one speaks the text's language (offline_speech_setup.dart). */
    private fun offlineEngine(graph: AppGraph, synthesis: VoiceSynthesis) = OfflineSpeechEngine(
        voiceFor = voices::installedFor,
        enabled = { graph.settings.state.value?.get(SpeechPrefs.offlineVoice) ?: true },
        load = { installed -> synthesis.load(installed, voices.engine.loadFrom) },
        player = synthesis.player,
        discard = { path -> File(path).delete() },
        scope = scope,
        appLanguage = { Locale.getDefault().language.ifEmpty { null } },
    )

    companion object {
        private val made = WeakHashMap<AppGraph, SpeechParts>()

        /** [graph]'s parts, made with [platform] the first time. */
        @Synchronized
        fun of(graph: AppGraph, platform: () -> SpeechPlatform): SpeechParts = made.getOrPut(graph) { SpeechParts(graph, platform()) }
    }
}
