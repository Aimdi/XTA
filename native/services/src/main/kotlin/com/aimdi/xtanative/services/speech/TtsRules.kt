package com.aimdi.xtanative.services.speech

/**
 * Which speech engine and voice reading aloud uses, as settings stores them
 * (tts_engines.dart). Android lets several engines be installed; an app that
 * never asks gets the system default, so the reader's pick is kept here.
 */
data class TtsChoice(
    /** The engine's package, e.g. [Tts.SHERPA_ENGINE]; null for the system default. */
    val engine: String? = null,
    /** The voice and its locale, which the platform wants together. */
    val voiceName: String? = null,
    val voiceLocale: String? = null,
    /** On the platform's scale, where 0.5 is normal speed on Android. */
    val rate: Double = DEFAULT_RATE,
) {
    val hasVoice: Boolean get() = voiceName != null && voiceLocale != null

    companion object {
        const val DEFAULT_RATE = 0.45

        /** The stored settings, an empty value read as unset. */
        fun of(engine: String?, voiceName: String?, voiceLocale: String?, rate: Double?) =
            TtsChoice(engine?.ifEmpty { null }, voiceName?.ifEmpty { null }, voiceLocale?.ifEmpty { null }, rate ?: DEFAULT_RATE)
    }
}

/** How one utterance ended. */
enum class UtteranceOutcome { COMPLETED, FAILED, CANCELLED }

/** What to offer when reading aloud fails: pick Sherpa, or install it. */
enum class TtsFailureAction { USE_SHERPA, INSTALL_SHERPA }

/** The rules for choosing an engine, a language and a voice to read with. */
object Tts {
    /** Sherpa ONNX's Android engine ("TTS Engine: Next-gen Kaldi"), which XTA can pick without changing the system default. */
    const val SHERPA_ENGINE = "com.k2fsa.sherpa.onnx.tts.engine"

    /** Where to get the Sherpa engine's APK, per language and processor. */
    const val SHERPA_INSTALL_URL = "https://k2-fsa.github.io/sherpa/onnx/tts/apk-engine.html"

    fun isSherpa(engine: String?): Boolean {
        val lower = engine?.lowercase()?.ifEmpty { null } ?: return false
        return lower == SHERPA_ENGINE || "sherpa" in lower || "k2fsa" in lower || "kaldi" in lower
    }

    /** The Sherpa package actually installed: the official one first, else anything named like it. */
    fun installedSherpa(engines: Iterable<String>): String? = engines.firstOrNull { it == SHERPA_ENGINE } ?: engines.firstOrNull(::isSherpa)

    fun sherpaInstalled(engines: Iterable<String>): Boolean = installedSherpa(engines) != null

    /** The engines that are not Sherpa, so the picker does not list it twice. */
    fun nonSherpa(engines: Iterable<String>): List<String> = engines.filterNot(::isSherpa)

    /** The package to bind when the reader asked for Sherpa: the installed one, else the official id to try. */
    fun sherpaEngineId(engines: Iterable<String>): String = installedSherpa(engines) ?: SHERPA_ENGINE

    fun failureAction(engine: String?): TtsFailureAction = if (isSherpa(engine)) TtsFailureAction.INSTALL_SHERPA else TtsFailureAction.USE_SHERPA

    /**
     * The engine to bind: the one picked, Sherpa's installed package when
     * Sherpa was picked, else the system default.
     */
    fun boundEngine(requested: String?, engines: Iterable<String>, defaultEngine: String?): String? = when {
        requested.isNullOrEmpty() -> defaultEngine?.ifEmpty { null }
        isSherpa(requested) -> sherpaEngineId(engines)
        else -> requested
    }

    /**
     * The engines the platform listed with the system default folded in: a
     * preferred engine Android 11's package visibility hid still counts.
     */
    fun mergeEngines(engines: Iterable<String>, defaultEngine: String?): List<String> {
        val listed = engines.filter { it.isNotEmpty() }
        return if (defaultEngine.isNullOrEmpty() || defaultEngine in listed) listed else listed + defaultEngine
    }

    /** A BCP 47 tag for a short locale: `de` → `de-DE`, `en_GB` → `en-GB`, a few languages by their usual region. */
    fun languageTag(locale: String): String = when {
        locale == "zh" -> "zh-CN"
        locale == "nb" -> "nb-NO"
        locale == "pt" -> "pt-BR"
        '_' in locale -> locale.replace('_', '-')
        '-' in locale -> locale
        else -> "$locale-${locale.uppercase()}"
    }

    /**
     * The languages to try, in order, so an English-only engine is not asked
     * for German: the text's own language first (with the voice's or the
     * app's region when they speak it), then the voice's, the app's, and
     * English. Each tag is followed by its bare language.
     */
    fun languageCandidates(textLanguage: String? = null, voiceLocale: String? = null, appLocale: String? = null): List<String> {
        val tags = LinkedHashSet<String>()
        fun add(tag: String?) {
            val bcp = tag?.ifEmpty { null }?.replace('_', '-') ?: return
            tags.add(bcp)
            bcp.indexOf('-').takeIf { it > 0 }?.let { tags.add(bcp.substring(0, it)) }
        }
        add(withRegion(textLanguage, voiceLocale, appLocale))
        add(voiceLocale)
        add(appLocale)
        add("en-US")
        add("en")
        return tags.toList()
    }

    private fun withRegion(language: String?, voiceLocale: String?, appLocale: String?): String? {
        if (language.isNullOrEmpty()) return null
        return listOf(voiceLocale, appLocale).firstNotNullOfOrNull { locale ->
            locale?.replace('_', '-')?.takeIf { it.substringBefore('-').lowercase() == language }
        } ?: if (language == "en") "en-US" else languageTag(language)
    }

    /**
     * Whether Android's `isLanguageAvailable` said yes: 0, 1 or 2 is
     * available, a negative number missing or unsupported; a plugin may
     * answer a boolean.
     */
    fun languageAvailable(result: Any?): Boolean = when (result) {
        is Boolean -> result
        is Number -> result.toDouble() >= 0
        else -> false
    }

    /** Whether [installed] already offers [tag]: `en-US` and `en`, or `en_US` and `en-US`, match. */
    fun languageListed(installed: Iterable<String>, tag: String): Boolean {
        val needle = tag.lowercase().replace('_', '-')
        return installed.any { item ->
            val have = item.lowercase().replace('_', '-')
            have == needle || have.startsWith("$needle-") || needle.startsWith("$have-")
        }
    }

    /** The first candidate the engine speaks, else the first language it has at all. */
    suspend fun pickLanguage(candidates: Iterable<String>, installed: List<String>, available: suspend (String) -> Boolean): String? {
        for (tag in candidates) if (available(tag) || languageListed(installed, tag)) return tag
        return installed.firstOrNull { it.isNotEmpty() }
    }

    /** Whether a voice for [voiceLocale] can read text in [language]; any voice suits text of unknown language. */
    fun voiceSuits(voiceLocale: String, language: String?): Boolean {
        if (language == null) return true
        fun base(tag: String) = tag.replace('_', '-').substringBefore('-').lowercase()
        return base(voiceLocale) == base(language)
    }

    /**
     * Sherpa's speed for the reader's rate: the rate is on the platform's
     * scale, where 0.5 is normal, and Sherpa's 1.0 is the voice's own pace.
     */
    fun sherpaSpeed(rate: Double): Double = (rate * 2).coerceIn(0.5, 2.0)
}
