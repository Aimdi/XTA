package com.aimdi.xtanative.services.ai

import com.aimdi.xtanative.core.format.Languages
import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.XClient
import com.aimdi.xtanative.core.x.XTranslationException
import com.aimdi.xtanative.core.x.model.XEntity
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XTranslation
import com.aimdi.xtanative.core.x.parse.XParser
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A post's text in another language, as X's translation answered it: the
 * text and its entities, indexed in code points like a post's own.
 */
data class Translation(val text: String, val entities: List<XEntity>) {
    /**
     * [post] showing this translation instead of its text. The whole text
     * shows; the links X put in for the post's media still hide, as they
     * keep their `t.co` address.
     */
    fun applyTo(post: XPost): XPost = post.copy(text = text, entities = entities, displayStart = 0, displayEnd = null)

    companion object {
        fun of(translation: XTranslation): Translation = Translation(translation.text, translation.entities)
    }
}

/** Where a post's translate control stands. */
enum class TranslationStatus { ORIGINAL, TRANSLATING, FAILED, TRANSLATED }

/** Where translations are kept for a while; the store module keeps them on the device. */
interface TranslationCache {
    suspend fun read(key: String): String?

    suspend fun write(key: String, value: String, ttlMillis: Long)
}

/**
 * Translates X posts with X's own Grok translation (utils/translation.dart),
 * asked by [translate]: [XClient.translate], which signs in as one of the
 * reader's accounts, asks again while Grok is still writing, and says why X
 * gave none with an [XTranslationException]. Successful answers are kept in
 * [cache] for a week under a key naming the post, its language and the
 * target language, in the shape X answered them, as XTA keeps them.
 */
class XTranslator(
    private val translate: suspend (postId: String, language: String) -> XTranslation,
    private val cache: TranslationCache? = null,
) {
    /**
     * [postId] translated into [target], a BCP 47 tag such as `de-DE`;
     * [sourceLanguage] is the post's `lang`. X's refusals reach the caller as
     * the [XTranslationException] the client threw, and are not kept.
     */
    suspend fun translate(postId: String, sourceLanguage: String?, target: String): Translation {
        val key = cacheKey(postId, sourceLanguage.orEmpty(), target)
        decodeCached(cache?.let { runCatching { it.read(key) }.getOrNull() })?.let { return it }
        val translation = Translation.of(translate(postId, target))
        runCatching { cache?.write(key, encodeCached(translation), CACHE_TTL_MILLIS) }
        return translation
    }

    companion object {
        /** A week: the key names one post, so the cache would otherwise grow with every post ever translated. */
        const val CACHE_TTL_MILLIS = 7L * 24 * 60 * 60 * 1000

        /** X's codes for posts with nothing to translate: media only, mentions only, cashtags only, undetermined. */
        val UNTRANSLATABLE = setOf("zxx", "qme", "qam", "qct", "qht", "qst", "und")

        /**
         * The cache key of one translation. The target language is part of
         * it, so a reader who changed their language is not served the old
         * one.
         */
        fun cacheKey(postId: String, sourceLanguage: String, target: String): String = "translation.$sourceLanguage.$target.$postId"

        /** A kept translation, or null when what is kept is unusable and the post should be asked for again. */
        fun decodeCached(stored: String?): Translation? =
            stored?.ifEmpty { null }?.let { J.parse(it) }?.takeIf { it.raw is JsonObject }?.let(XParser::translation)?.let(Translation::of)

        /**
         * [translation] as X answered it (`{"result":{"text","entities"}}`),
         * which [decodeCached] reads back: links under `urls`, mentions,
         * hashtags and cashtags under their own names.
         */
        fun encodeCached(translation: Translation): String = buildJsonObject {
            put(
                "result",
                buildJsonObject {
                    put("text", translation.text)
                    put("entities", entitiesJson(translation.entities))
                },
            )
        }.toString()

        private fun entitiesJson(entities: List<XEntity>): JsonObject {
            fun indices(entity: XEntity) = JsonArray(listOf(JsonPrimitive(entity.start), JsonPrimitive(entity.end)))
            fun group(of: (XEntity) -> JsonObject?) = JsonArray(entities.mapNotNull(of))
            return JsonObject(
                mapOf(
                    "urls" to group { entity ->
                        (entity as? XEntity.Link)?.let {
                            buildJsonObject {
                                put("url", it.url)
                                put("expanded_url", it.expanded)
                                put("display_url", it.display)
                                put("indices", indices(it))
                            }
                        }
                    },
                    "user_mentions" to group { entity ->
                        (entity as? XEntity.Mention)?.let { buildJsonObject { put("screen_name", it.screenName); put("indices", indices(it)) } }
                    },
                    "hashtags" to group { entity ->
                        (entity as? XEntity.Hashtag)?.let { buildJsonObject { put("text", it.tag); put("indices", indices(it)) } }
                    },
                    "symbols" to group { entity ->
                        (entity as? XEntity.Cashtag)?.let { buildJsonObject { put("text", it.symbol); put("indices", indices(it)) } }
                    },
                ),
            )
        }

        /**
         * Whether a post in [lang] offers translation to a reader in
         * [appLanguage]: not when it is already in that language, nor when X
         * marks it as having no words; a post with no language keeps the
         * offer, as an idle button beats a stranded reader.
         */
        fun offers(lang: String?, appLanguage: String): Boolean {
            if (lang.isNullOrEmpty()) return true
            if (lang in UNTRANSLATABLE) return false
            return !Languages.same(lang, appLanguage)
        }

        /**
         * The language to translate into: the app's language setting, or the
         * system's when it is `system` or unset, as a BCP 47 tag. A POSIX
         * locale's encoding (`en_US.UTF-8`) is dropped, which XTA kept.
         */
        fun target(appLocale: String?, systemLocale: String): String {
            val locale = appLocale?.takeIf { it.isNotEmpty() && it != "system" } ?: systemLocale
            val parts = locale.substringBefore('.').substringBefore('@').split('-', '_').filter { it.isNotEmpty() }
            return parts.take(2).joinToString("-").ifEmpty { "en" }
        }
    }
}
