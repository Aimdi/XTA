package com.aimdi.xtanative.services.ai

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.XAccount
import com.aimdi.xtanative.core.x.XClient
import com.aimdi.xtanative.core.x.XSigners
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.model.XEntity
import com.aimdi.xtanative.core.x.model.XPost
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl

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
}

/** Where a post's translate control stands. */
enum class TranslationStatus { ORIGINAL, TRANSLATING, FAILED, TRANSLATED }

/** Why X gave no translation. The screens word each one. */
sealed interface TranslationFailure {
    /** X does not translate from this language (its 400, "xx is not supported"). */
    data class Unsupported(val language: String) : TranslationFailure

    /** X answered with a reason of its own. */
    data class Refused(val message: String?) : TranslationFailure

    /** The account is barred from translating (403). */
    data object Banned : TranslationFailure

    /** Too many translations too quickly (429). */
    data object TooMany : TranslationFailure

    /** X failed to translate the post (500), or answered without a translation. */
    data object Failed : TranslationFailure

    /** Grok was still writing the translation after every retry. */
    data object NotReady : TranslationFailure

    /** Any other answer. */
    data class Http(val code: Int) : TranslationFailure
}

class TranslationException(val failure: TranslationFailure) : Exception(failure.toString())

/** Where translations are kept for a while; the store module keeps them on the device. */
interface TranslationCache {
    suspend fun read(key: String): String?

    suspend fun write(key: String, value: String, ttlMillis: Long)
}

/**
 * Translates X posts with X's own Grok translation (utils/translation.dart),
 * signed in as one of the reader's accounts. Successful answers are kept in
 * [cache] for a week under a key naming the post, its language and the
 * target language. While Grok is still writing, X answers something that is
 * not JSON; that is asked again, up to three times, a second apart.
 */
class XTranslator(
    private val http: XHttp,
    private val accounts: () -> List<XAccount>,
    private val signers: XSigners,
    private val cache: TranslationCache? = null,
    private val retryDelayMillis: Long = 1_000,
) {
    /** [postId] translated into [target], a BCP 47 tag such as `de-DE`; [sourceLanguage] is the post's `lang`. */
    suspend fun translate(postId: String, sourceLanguage: String?, target: String): Translation {
        val key = cacheKey(postId, sourceLanguage.orEmpty(), target)
        decodeCached(cache?.let { runCatching { it.read(key) }.getOrNull() })?.let { return it }
        repeat(ATTEMPTS) { attempt ->
            val response = send(postId, target)
            strictJson(response.body)?.let { body ->
                val translation = answer(response.code, body)
                runCatching { cache?.write(key, response.body, CACHE_TTL_MILLIS) }
                return translation
            }
            if (attempt < ATTEMPTS - 1) delay(retryDelayMillis)
        }
        throw TranslationException(TranslationFailure.NotReady)
    }

    /**
     * One request, as XTA sends it: X's headers and the first account's
     * session, the body as text. The signature names GET, as XTA's does;
     * the `Accept-Encoding` XTA set is left to the HTTP client, which only
     * decodes what it asked for itself.
     */
    private suspend fun send(postId: String, target: String) = run {
        val account = accounts().firstOrNull()
        val signer = signers.forSession(account?.cookie)
        val headers = XClient.BASE_HEADERS.filterKeys { !it.equals("content-type", ignoreCase = true) } +
            account?.headers.orEmpty() +
            ("x-client-transaction-id" to signer.transactionId("GET", URL.encodedPath))
        http.post(URL, headers, request(postId, target).toString(), "text/plain;charset=UTF-8")
    }

    companion object {
        val URL = "https://api.x.com/2/grok/translation.json".toHttpUrl()

        private const val ATTEMPTS = 3

        /** A week: the key names one post, so the cache would otherwise grow with every post ever translated. */
        const val CACHE_TTL_MILLIS = 7L * 24 * 60 * 60 * 1000

        /** X's codes for posts with nothing to translate: media only, mentions only, cashtags only, undetermined. */
        val UNTRANSLATABLE = setOf("zxx", "qme", "qam", "qct", "qht", "qst", "und")

        private val notSupported = Regex("^(\\w+) is not supported$")

        /**
         * [text] when it is JSON, read strictly: while Grok writes, X answers
         * a page, which the JSON reader would take for a bare literal.
         */
        private fun strictJson(text: String): J? {
            val element = runCatching { Json.parseToJsonElement(text) }.getOrNull() ?: return null
            val valid = element !is JsonPrimitive || element is JsonNull || element.isString ||
                element.booleanOrNull != null || element.doubleOrNull != null
            return if (valid) J(element) else null
        }

        fun request(postId: String, target: String): JsonObject = buildJsonObject {
            put("id", postId)
            put("dst_lang", target)
            put("content_type", "POST")
        }

        /**
         * The cache key of one translation. The target language is part of
         * it, so a reader who changed their language is not served the old
         * one.
         */
        fun cacheKey(postId: String, sourceLanguage: String, target: String): String = "translation.$sourceLanguage.$target.$postId"

        /** A kept translation, or null when what is kept is unusable and the post should be asked for again. */
        fun decodeCached(stored: String?): Translation? =
            stored?.ifEmpty { null }?.let { J.parse(it) }?.takeIf { it.raw is JsonObject }?.let(::translationIn)

        /**
         * Whether a post in [lang] offers translation to a reader in
         * [appLanguage]: not when it is already in that language, nor when X
         * marks it as having no words; a post with no language keeps the
         * offer, as an idle button beats a stranded reader.
         */
        fun offers(lang: String?, appLanguage: String): Boolean {
            if (lang.isNullOrEmpty()) return true
            if (lang in UNTRANSLATABLE) return false
            return lang.substringBefore('-').lowercase() != appLanguage.substringBefore('-').substringBefore('_').lowercase()
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

        /** X's answer: the translation, or the failure its status and body say. */
        internal fun answer(code: Int, body: J): Translation {
            if (code == 200 && body.raw is JsonObject) {
                if (body["error"].exists) {
                    val error = body["error"]
                    throw TranslationException(TranslationFailure.Refused(error["message"].string ?: error["code"].let { it.string ?: it.long?.toString() }))
                }
                return translationIn(body) ?: throw TranslationException(TranslationFailure.Failed)
            }
            throw TranslationException(
                when (code) {
                    400 -> body["error"].string.let { error ->
                        error?.let(notSupported::find)?.let { TranslationFailure.Unsupported(it.groupValues[1]) } ?: TranslationFailure.Refused(error)
                    }
                    403 -> TranslationFailure.Banned
                    429 -> TranslationFailure.TooMany
                    500 -> TranslationFailure.Failed
                    else -> TranslationFailure.Http(code)
                },
            )
        }

        private fun translationIn(body: J): Translation? {
            val result = body["result"]
            val text = result["text"].string ?: return null
            return Translation(text, entities(result["entities"]))
        }

        /**
         * The translation's entities, in X's legacy shape. The links X put in
         * for media become links too, under their `t.co` address, so a post
         * hides them as it hides its own.
         */
        internal fun entities(entities: J): List<XEntity> {
            fun span(entity: J): Pair<Int, Int>? = entity["indices"].list.mapNotNull { it.int }.takeIf { it.size == 2 }?.let { it[0] to it[1] }
            val links = (entities["urls"].list + entities["media"].list).mapNotNull { url ->
                val (start, end) = span(url) ?: return@mapNotNull null
                val short = url["url"].string ?: return@mapNotNull null
                XEntity.Link(start, end, short, url["expanded_url"].string ?: short, url["display_url"].string ?: short)
            }
            val mentions = entities["user_mentions"].list.mapNotNull { mention ->
                val (start, end) = span(mention) ?: return@mapNotNull null
                XEntity.Mention(start, end, mention["screen_name"].string ?: return@mapNotNull null)
            }
            val hashtags = entities["hashtags"].list.mapNotNull { tag ->
                val (start, end) = span(tag) ?: return@mapNotNull null
                XEntity.Hashtag(start, end, tag["text"].string ?: return@mapNotNull null)
            }
            val cashtags = entities["symbols"].list.mapNotNull { symbol ->
                val (start, end) = span(symbol) ?: return@mapNotNull null
                XEntity.Cashtag(start, end, symbol["text"].string?.ifEmpty { null } ?: return@mapNotNull null)
            }
            return (links + mentions + hashtags + cashtags).sortedBy { it.start }
        }
    }
}
