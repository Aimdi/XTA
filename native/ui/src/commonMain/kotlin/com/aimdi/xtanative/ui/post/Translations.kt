package com.aimdi.xtanative.ui.post

import com.aimdi.xtanative.core.x.XTranslationException
import com.aimdi.xtanative.services.ai.TranslationCache

/**
 * Translations kept for this run, a week at most, as XTA's cache keeps them;
 * past [max] entries the oldest go first. A post translated, shown in its
 * original and translated again is not asked of X twice.
 */
class TranslationMemory(private val max: Int = 500, private val clock: () -> Long = System::currentTimeMillis) : TranslationCache {
    private val kept = object : LinkedHashMap<String, Pair<String, Long>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<String, Long>>?): Boolean = size > max
    }

    override suspend fun read(key: String): String? = synchronized(kept) {
        val (value, until) = kept[key] ?: return null
        if (clock() < until) return value
        kept.remove(key)
        null
    }

    override suspend fun write(key: String, value: String, ttlMillis: Long) {
        synchronized(kept) { kept[key] = value to clock() + ttlMillis }
    }

    companion object {
        /** The one the screens share. */
        val shared = TranslationMemory()
    }
}

/** Why a translation failed, in the terms a screen words: each of X's refusals has its own message. */
enum class TranslationProblem {
    UNSUPPORTED_LANGUAGE,
    BANNED,
    TOO_MANY,
    FAILED,
    NOT_READY,
    OTHER,
    ;

    companion object {
        fun of(error: Throwable): TranslationProblem = when ((error as? XTranslationException)?.kind) {
            XTranslationException.Kind.UNSUPPORTED_LANGUAGE -> UNSUPPORTED_LANGUAGE
            XTranslationException.Kind.BANNED -> BANNED
            XTranslationException.Kind.TOO_MANY_REQUESTS -> TOO_MANY
            XTranslationException.Kind.FAILED -> FAILED
            XTranslationException.Kind.NOT_READY -> NOT_READY
            XTranslationException.Kind.OTHER, null -> OTHER
        }
    }
}
