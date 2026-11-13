package com.aimdi.xtanative.ui.speech

import com.aimdi.xtanative.services.speech.OfflineVoice
import com.aimdi.xtanative.services.speech.OfflineVoices
import com.aimdi.xtanative.services.speech.VoiceEnginePackage
import com.aimdi.xtanative.services.speech.VoiceEngines

/**
 * Where downloaded voices and the voice engine come from (voice_engine.dart's
 * voiceEngineUrls): the voices from k2-fsa's release, the engine from this
 * build's own release ([releaseTag], empty for a local build), then XTA's
 * latest. A CI build names a [server] of its own instead, which serves both
 * by their file names, as XTA's voice-engine check serves its emulator; the
 * pins hold whoever serves them.
 */
class VoiceSources(private val server: String?, private val releaseTag: String) {
    val catalog: List<OfflineVoice> = server?.let { base -> OfflineVoices.CATALOG.map { it.copy(url = "$base/${it.archiveName}") } } ?: OfflineVoices.CATALOG

    fun engineUrls(engine: VoiceEnginePackage): List<String> = server?.let { listOf("$it/${engine.archiveName}") } ?: VoiceEngines.urls(engine, releaseTag)

    companion object {
        /** The system property the Android app sets from -PvoiceServer, a CI build's; never set for a release. */
        const val SERVER_PROPERTY = "xtanative.voice_server"

        /** The system property the Android app sets, and the desktop's build passes, to the prerelease it is built for. */
        const val RELEASE_TAG_PROPERTY = "xtanative.release_tag"

        /** As the app's shell set them for this process. */
        fun fromProperties(): VoiceSources =
            VoiceSources(System.getProperty(SERVER_PROPERTY)?.trimEnd('/')?.ifEmpty { null }, System.getProperty(RELEASE_TAG_PROPERTY).orEmpty())
    }
}
