package com.aimdi.xtanative.services.speech

import com.aimdi.xtanative.core.format.Languages
import java.io.File

/** Which sherpa-onnx model family a voice is, which decides how it loads. */
enum class VoiceModelKind { VITS, KOKORO }

/**
 * A voice XTA can download for reading aloud on the device: a sherpa-onnx
 * model archive pinned by URL, size and SHA-256, since a release asset can
 * be replaced under the same name and a voice that no longer matches is
 * refused rather than run (offline_voice_catalog.dart). Nothing is fetched
 * until the reader taps Download.
 */
data class OfflineVoice(
    /** Stable, and the voice's folder name on the device. */
    val id: String,
    /** The voice's proper name, the same in every language. */
    val name: String,
    /** The locale it speaks, `de_DE` or `en_US`. */
    val locale: String,
    val url: String,
    val archiveBytes: Long,
    /** SHA-256 of the archive, lower-case hex; null only when it could not be confirmed, and then a sound archive is enough. */
    val sha256: String?,
    /** SPDX id of its licence. */
    val licence: String,
    val kind: VoiceModelKind,
    /** The folder every file of the archive sits under. */
    val archiveRoot: String,
    /** The model, relative to the voice's folder. */
    val model: String,
    val tokens: String = "tokens.txt",
    /** The espeak-ng phoneme data the model reads text through. */
    val dataDir: String = "espeak-ng-data",
    /** Kokoro's speaker embeddings; null for VITS. */
    val voices: String? = null,
    /** Which speaker of a multi-speaker model reads. */
    val speakerId: Int = 0,
    /** Higher wins when two installed voices speak the same language. */
    val preference: Int = 0,
) {
    /** The short language code: `de` for `de_DE`. */
    val language: String get() = locale.substringBefore('_')

    /** The files that must be there for the voice to load. */
    val requiredFiles: List<String> get() = listOfNotNull(model, tokens, "$dataDir/phontab", voices)

    /** The archive's file name as it is downloaded. */
    val archiveName: String get() = "$archiveRoot.tar.bz2"
}

/**
 * What sherpa-onnx is given to load an installed voice (sherpa_voice_synthesizer.dart):
 * its files by path, Kokoro's speaker embeddings when it has them, the
 * speaker that reads, and the threads it synthesises on.
 */
data class SherpaConfig(
    val kind: VoiceModelKind,
    val model: String,
    val tokens: String,
    val dataDir: String,
    val voices: String?,
    val speakerId: Int,
    val threads: Int,
) {
    companion object {
        fun of(installed: InstalledVoice, threads: Int = 2): SherpaConfig {
            val voice = installed.voice
            fun file(name: String) = File(installed.path, name).path
            return SherpaConfig(voice.kind, file(voice.model), file(voice.tokens), file(voice.dataDir), voice.voices?.let(::file), voice.speakerId, threads)
        }
    }
}

/** The downloadable voices and the rule for which one reads a language. */
object OfflineVoices {
    private fun ttsModel(name: String) = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$name.tar.bz2"

    /**
     * Hashes and sizes taken from the archives at these URLs on 2026-10-09.
     * Kokoro loads but is not offered yet: it synthesised at about real time
     * on a desktop, which on a phone means pauses between sentences.
     */
    val CATALOG: List<OfflineVoice> = listOf(
        OfflineVoice(
            id = "de-thorsten-emotional",
            name = "Thorsten",
            locale = "de_DE",
            url = ttsModel("vits-piper-de_DE-thorsten_emotional-medium-int8"),
            archiveBytes = 23_479_221,
            sha256 = "e522bea5bb42d8f572b85a39ccb2d7ec114d6c2838c198827aadaeea3d31822a",
            // The Thorsten-Voice dataset, per the archive's MODEL_CARD.
            licence = "CC0-1.0",
            kind = VoiceModelKind.VITS,
            archiveRoot = "vits-piper-de_DE-thorsten_emotional-medium-int8",
            model = "de_DE-thorsten_emotional-medium.onnx",
            // The model's eight speakers are emotions; 4 is neutral.
            speakerId = 4,
        ),
        OfflineVoice(
            id = "en-libritts-r",
            name = "LibriTTS-R",
            locale = "en_US",
            url = ttsModel("vits-piper-en_US-libritts_r-medium-int8"),
            archiveBytes = 23_398_348,
            sha256 = "7e4552e239988f4896872822b56e99e0e9e00958164e3f6bdf5ee14391fbe829",
            licence = "CC-BY-4.0",
            kind = VoiceModelKind.VITS,
            archiveRoot = "vits-piper-en_US-libritts_r-medium-int8",
            model = "en_US-libritts_r-medium.onnx",
        ),
    )

    fun byId(id: String): OfflineVoice? = CATALOG.firstOrNull { it.id == id }

    /** The voice among [installed] that reads [language] (`de`, `en-GB`, `de_AT`), the preferred one when several do. */
    fun pick(installed: Iterable<OfflineVoice>, language: String?): OfflineVoice? {
        if (language.isNullOrEmpty()) return null
        return installed.filter { Languages.same(it.language, language) }.maxByOrNull { it.preference }
    }
}
