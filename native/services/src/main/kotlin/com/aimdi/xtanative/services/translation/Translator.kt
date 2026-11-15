package com.aimdi.xtanative.services.translation

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.services.ai.AiClient
import com.aimdi.xtanative.services.ai.AiException
import com.aimdi.xtanative.services.ai.TranslationCache
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl

/**
 * A text in another language, as the reader's translation service gave it:
 * the [text], the language it was in where the service said
 * ([sourceLanguage], a plain lower-case tag such as `de`), and who
 * translated it ([translator]), for the line that credits it.
 */
data class Translated(val text: String, val sourceLanguage: String?, val translator: String)

/** One language a server translates: its [code] as the server names it, and its [name]. */
data class TranslationLanguage(val code: String, val name: String)

/**
 * Translates the text of posts of any network with the reader's own service
 * ([TranslationConfig]): DeepL, a LibreTranslate server or their AI
 * provider (reader_translation_service.dart). Only what the reader asked to
 * translate is sent, cut by [TranslationPieces] and batched, with the
 * language to translate into and the key the service needs: no account,
 * post id or address of the post. Translations are kept in [cache] for a
 * week under the [TranslationConfig.fingerprint] of the text and language.
 */
class Translator(
    private val http: XHttp,
    private val cache: TranslationCache? = null,
    private val ai: AiClient = AiClient(http),
    private val timeoutMillis: Long = 30_000,
) {
    private val libreCodes = ConcurrentHashMap<String, Set<String>>()

    /**
     * [text] translated into [language] (one of XTA's codes or a tag) with
     * [config]'s service. Throws a [TranslationException] saying why when
     * none came.
     */
    suspend fun translate(text: String, config: TranslationConfig, language: String): Translated {
        if (!config.enabled || !config.isValid) throw TranslationException(TranslationFailure.NOT_CONFIGURED)
        if (text.length > MAX_INPUT) throw TranslationException(TranslationFailure.TOO_LONG)
        val key = CACHE_PREFIX + config.fingerprint(text, language)
        cached(key)?.let { return it }
        val pieces = TranslationPieces.of(text)
        val sources = pieces.filter { it.translate }.map { it.text }
        if (sources.isEmpty()) return Translated(text, null, config.translatorName)
        val answers = batches(sources, config.service).flatMap { batch -> checked(batch, ask(batch, config, language)) }
        var next = 0
        val joined = pieces.joinToString("") { if (it.translate) answers[next++].text else it.text }
        val source = answers.mapNotNull { it.source }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        return Translated(joined, source, config.translatorName).also { keep(key, it) }
    }

    /**
     * What the server of [config] translates, for Settings' Test: a
     * LibreTranslate server's `/languages`, DeepL's target languages. Throws
     * a [TranslationException] when it does not answer with a list.
     */
    suspend fun languages(config: TranslationConfig): List<TranslationLanguage> {
        if (config.service != TranslationService.LIBRE && config.service != TranslationService.DEEPL) throw TranslationException(TranslationFailure.NOT_CONFIGURED)
        val url = config.url("languages")?.let { if (config.service == TranslationService.DEEPL) it.newBuilder().addQueryParameter("type", "target").build() else it }
            ?: throw TranslationException(TranslationFailure.NOT_CONFIGURED)
        val response = call { http.get(url, authorization(config), true) }
        val rows = J.parse(response.body).list
        if (rows.isEmpty() && J.parse(response.body).raw !is JsonArray) throw TranslationException(TranslationFailure.BAD_ANSWER)
        return rows.mapNotNull { row -> (row["code"].string ?: row["language"].string)?.let { TranslationLanguage(it, row["name"].string ?: it) } }
            .also { if (config.service == TranslationService.LIBRE) libreCodes[url.toString()] = it.mapTo(HashSet()) { lang -> lang.code } }
    }

    private data class Answer(val text: String, val source: String?)

    private fun checked(batch: List<String>, answers: List<Answer>): List<Answer> {
        if (answers.size != batch.size || answers.any { it.text.isBlank() }) throw TranslationException(TranslationFailure.BAD_ANSWER)
        return answers.map { it.copy(text = it.text.trim()) }
    }

    private suspend fun ask(batch: List<String>, config: TranslationConfig, language: String): List<Answer> = when (config.service) {
        TranslationService.DEEPL -> deepl(batch, config, language)
        TranslationService.LIBRE -> libre(batch, config, language)
        TranslationService.AI -> aiAnswers(batch, config, language)
        TranslationService.OFF -> throw TranslationException(TranslationFailure.NOT_CONFIGURED)
    }

    private suspend fun deepl(texts: List<String>, config: TranslationConfig, language: String): List<Answer> {
        val body = buildJsonObject {
            putJsonArray("text") { texts.forEach { add(JsonPrimitive(it)) } }
            put("target_lang", TranslationLanguages.deepl(language))
            put("preserve_formatting", true)
        }
        val response = post(config.url()!!, authorization(config), body.toString())
        val rows = J.parse(response.body)["translations"]
        if (rows.raw !is JsonArray) throw TranslationException(TranslationFailure.BAD_ANSWER)
        return rows.list.map { Answer(it["text"].string.orEmpty(), TranslationLanguages.plain(it["detected_source_language"].string)) }
    }

    private suspend fun libre(texts: List<String>, config: TranslationConfig, language: String): List<Answer> {
        val body = buildJsonObject {
            if (texts.size == 1) put("q", texts.single()) else putJsonArray("q") { texts.forEach { add(JsonPrimitive(it)) } }
            put("source", "auto")
            put("target", libreTarget(config, language))
            put("format", "text")
            config.apiKey.trim().takeIf { it.isNotEmpty() }?.let { put("api_key", it) }
        }
        val answer = J.parse(post(config.url()!!, emptyMap(), body.toString()).body)
        val detected = answer["detectedLanguage"]
        fun source(index: Int) = TranslationLanguages.plain((if (detected.raw is JsonArray) detected[index] else detected)["language"].string)
        val translated = answer["translatedText"]
        return translated.string?.let { listOf(Answer(it, source(0))) }
            ?: translated.list.takeIf { translated.raw is JsonArray }?.mapIndexed { i, it -> Answer(it.string.orEmpty(), source(i)) }
            ?: throw TranslationException(TranslationFailure.BAD_ANSWER)
    }

    /** The server's own code for [language], from its `/languages`; one it does not list is refused before anything is sent. */
    private suspend fun libreTarget(config: TranslationConfig, language: String): String {
        val candidates = TranslationLanguages.libre(language)
        val url = config.url("languages")!!
        val known = libreCodes[url.toString()] ?: runCatching { languages(config).mapTo(HashSet()) { it.code } }
            .getOrElse { if (it is CancellationException) throw it else emptySet() }
        if (known.isEmpty()) return candidates.first()
        return candidates.firstOrNull { it in known } ?: throw TranslationException(TranslationFailure.UNSUPPORTED_LANGUAGE)
    }

    private suspend fun aiAnswers(texts: List<String>, config: TranslationConfig, language: String): List<Answer> {
        val name = TranslationLanguages.name(language)
        if (texts.size == 1) {
            val prompt = "Translate the following text into $name. Reply with only the translation, keeping its meaning, " +
                "tone and any names, mentions and hashtags.\n\n${texts.single()}"
            return listOf(Answer(complete(config, prompt), null))
        }
        val prompt = "Translate every string of this JSON array into $name. Reply with only a JSON array of the translated " +
            "strings, in the same order and with the same number of entries.\n\n${JsonArray(texts.map(::JsonPrimitive))}"
        val reply = complete(config, prompt)
        val start = reply.indexOf('[')
        val end = reply.lastIndexOf(']')
        val list = J.parse(if (start < 0 || end <= start) "" else reply.substring(start, end + 1))
        val strings = list.list.mapNotNull { it.string }
        if (list.raw is JsonArray && strings.size == texts.size) return strings.map { Answer(it, null) }
        return texts.flatMap { aiAnswers(listOf(it), config, language) }
    }

    private suspend fun complete(config: TranslationConfig, prompt: String): String = try {
        ai.complete(config.ai, prompt, timeoutMillis = timeoutMillis)
    } catch (e: AiException) {
        throw e.asTranslation()
    } catch (e: IOException) {
        throw TranslationException(TranslationFailure.UNREACHABLE, e)
    }

    private fun authorization(config: TranslationConfig): Map<String, String> =
        if (config.service == TranslationService.DEEPL) mapOf("Authorization" to "DeepL-Auth-Key ${config.apiKey.trim()}") else emptyMap()

    private suspend fun post(url: HttpUrl, headers: Map<String, String>, body: String): XResponse =
        call { http.post(url, headers, body, "application/json") }

    /** One request within the time allowed, its failure typed. */
    private suspend fun call(request: suspend () -> XResponse): XResponse {
        val response = try {
            withTimeoutOrNull(timeoutMillis) { request() } ?: throw TranslationException(TranslationFailure.UNREACHABLE)
        } catch (e: IOException) {
            throw TranslationException(TranslationFailure.UNREACHABLE, e)
        }
        if (!response.isSuccessful) throw TranslationException(failureOf(response.code, response.body.take(2000)))
        return response
    }

    private fun batches(texts: List<String>, service: TranslationService): List<List<String>> {
        val limit = if (service == TranslationService.LIBRE) TranslationPieces.CHUNK else BATCH_UNITS
        val batches = mutableListOf<MutableList<String>>()
        var units = 0
        texts.forEach { text ->
            val last = batches.lastOrNull()
            if (last == null || last.size == BATCH_TEXTS || units + text.length > limit) {
                batches += mutableListOf(text)
                units = text.length
            } else {
                last += text
                units += text.length
            }
        }
        return batches
    }

    private suspend fun cached(key: String): Translated? {
        val kept = runCatching { cache?.read(key) }.getOrNull() ?: return null
        val json = J.parse(kept)
        val text = json["text"].string ?: return null
        return Translated(text, json["source"].string, json["by"].string.orEmpty())
    }

    private suspend fun keep(key: String, translated: Translated) {
        val json = buildJsonObject {
            put("text", translated.text)
            translated.sourceLanguage?.let { put("source", it) }
            put("by", translated.translator)
        }
        runCatching { cache?.write(key, json.toString(), CACHE_TTL_MILLIS) }
    }

    companion object {
        /** The most text one translation takes: an article's worth. */
        const val MAX_INPUT = 400_000

        /** A week, as X's translations are kept. */
        const val CACHE_TTL_MILLIS = 7L * 24 * 60 * 60 * 1000

        const val CACHE_PREFIX = "reader-translation."
        private const val BATCH_TEXTS = 50
        private const val BATCH_UNITS = 60_000
    }
}
