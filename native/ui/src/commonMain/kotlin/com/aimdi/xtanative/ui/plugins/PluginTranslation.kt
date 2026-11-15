package com.aimdi.xtanative.ui.plugins

/**
 * The reader's translation service, as a plugin offers it
 * ([PluginHost.translator]): who translates ([name], for the line that
 * credits a translation, such as "LibreTranslate") and the language it
 * translates into ([target], a BCP 47 tag), for a plugin to offer Translate
 * only on a post in another language.
 */
class PluginTranslator(val name: String, val target: String) {
    override fun equals(other: Any?): Boolean = other is PluginTranslator && other.name == name && other.target == target

    override fun hashCode(): Int = name.hashCode() * 31 + target.hashCode()
}

/**
 * A post's words as the reader's translation service gave them
 * ([PluginHost.translate]): the [text], the language they were in where the
 * service said ([sourceLanguage], a lower-case tag such as `de`), and who
 * translated them ([translator]).
 */
class PluginTranslation(val text: String, val sourceLanguage: String?, val translator: String)

/** Why [PluginHost.translate] gave no translation; the plugin words it. */
enum class PluginTranslationProblem {
    /** No service is set, or what is set cannot be used: Settings › Translation. */
    NOT_SET_UP,

    /** More text than is translated at once. */
    TOO_LONG,

    /** The service did not answer. */
    UNREACHABLE,

    /** The service refused: it needs a key, or the key is wrong. */
    REFUSED,

    /** Too many translations for now. */
    RATE_LIMITED,

    /** The service does not translate that language. */
    UNSUPPORTED_LANGUAGE,

    /** Anything else. */
    FAILED,
}

/** A translation that did not come, and why. */
class PluginTranslationException(val problem: PluginTranslationProblem) : Exception(problem.name.lowercase())
