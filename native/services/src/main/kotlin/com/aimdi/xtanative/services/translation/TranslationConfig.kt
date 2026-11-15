package com.aimdi.xtanative.services.translation

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.services.ai.AiClient
import com.aimdi.xtanative.services.ai.AiConfig
import com.aimdi.xtanative.services.ai.AiException
import com.aimdi.xtanative.services.ai.AiProvider
import java.security.MessageDigest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The services that translate posts of every network for the reader
 * (reader_translation_config.dart): none by default, DeepL, a LibreTranslate
 * server, or the AI provider the reader set up. [stored] is the name XTA
 * keeps in its settings.
 */
enum class TranslationService(val stored: String) {
    OFF("disabled"),
    DEEPL("deepl"),
    LIBRE("libre"),
    AI("ai"),
    ;

    companion object {
        fun of(stored: String?): TranslationService? = entries.firstOrNull { it.stored == stored }
    }
}

/**
 * The reader's translation setting (Settings › Translation): which
 * [service], its server [endpoint] (empty for DeepL's own), its [apiKey]
 * (kept under its own secret preference, never in an export), the language
 * to translate into ([target], one of XTA's language codes such as `de` or
 * `pt_BR`; empty follows the app's), and the AI connection [ai] uses.
 * Nothing is sent anywhere until the reader asks for a translation.
 */
data class TranslationConfig(
    val service: TranslationService = TranslationService.OFF,
    val endpoint: String = "",
    val apiKey: String = "",
    val target: String = "",
    val ai: AiConfig = AiConfig(),
) {
    val enabled: Boolean get() = service != TranslationService.OFF

    /** Whether translating can be tried as set: an address that parses, DeepL's key, a configured AI provider. */
    val isValid: Boolean
        get() = when {
            endpoint.length > 2048 || apiKey.length > 8192 -> false
            target.isNotEmpty() && !TARGET.matches(target) -> false
            else -> when (service) {
                TranslationService.OFF -> true
                TranslationService.AI -> ai.isConfigured && runCatching { AiClient.chatUrl(ai.baseUrl) }.isSuccess
                TranslationService.DEEPL -> apiKey.isNotBlank() && url() != null
                TranslationService.LIBRE -> endpoint.isNotBlank() && url() != null
            }
        }

    /**
     * Who translates, as a translation is credited: DeepL, LibreTranslate,
     * or the AI provider's own name (its host when it is none of the presets).
     */
    val translatorName: String
        get() = when (service) {
            TranslationService.OFF -> ""
            TranslationService.DEEPL -> "DeepL"
            TranslationService.LIBRE -> "LibreTranslate"
            TranslationService.AI -> when (ai.provider) {
                AiProvider.GROK -> "Grok"
                AiProvider.OPENROUTER -> "OpenRouter"
                AiProvider.OPENAI -> "OpenAI"
                null -> ai.host
            }
        }

    /** The language translated into: [target], else [appLanguage], a tag or one of XTA's codes. */
    fun language(appLanguage: String): String = target.ifEmpty { appLanguage }

    /**
     * The address of the service's [action] (`translate`, `languages`): a
     * base path the reader gave is kept, a full endpoint is cut back to it,
     * DeepL's is under `/v2`, DeepL Free's key (`…:fx`) has its own host.
     * Null for an address that is not a plain http(s) one (credentials, a
     * query or a fragment in it).
     */
    fun url(action: String = "translate"): HttpUrl? {
        val address = endpoint.trim().ifEmpty { if (service == TranslationService.DEEPL) deeplBase(apiKey) else "" }
        val url = address.toHttpUrlOrNull() ?: return null
        if (url.username.isNotEmpty() || url.password.isNotEmpty() || url.encodedQuery != null || url.encodedFragment != null) return null
        var path = url.encodedPath.trimEnd('/')
        KNOWN_ENDS.firstOrNull { path.endsWith(it) }?.let { path = path.removeSuffix(it) }
        val deepl = service == TranslationService.DEEPL
        if (deepl) path = path.removeSuffix("/v2")
        return url.newBuilder().encodedPath(if (deepl) "$path/v2/$action" else "$path/$action").build()
    }

    /**
     * One translation of [text] into [language] under every setting that
     * could change it, hashed: what the device keeps it under. The key
     * itself never leaves the hash.
     */
    fun fingerprint(text: String, language: String): String {
        val parts = listOf(service.stored, endpoint.trim(), apiKey.trim(), language, ai.baseUrl, ai.apiKey, ai.model, text)
        return sha256(JsonArray(parts.map(::JsonPrimitive)).toString())
    }

    /** What is kept in the settings beside the key: XTA's `{"version":1,…}`. */
    fun toJson(): String = buildJsonObject {
        put("version", 1)
        put("provider", service.stored)
        put("endpoint", endpoint)
        put("target", target)
    }.toString()

    companion object {
        const val DEEPL_FREE = "https://api-free.deepl.com"
        const val DEEPL_PRO = "https://api.deepl.com"

        private val TARGET = Regex("^[A-Za-z]{2,3}([_-][A-Za-z0-9]{2,8})*$")
        private val KNOWN_ENDS = listOf("/v2/translate", "/translate", "/languages")

        /** DeepL's host for [apiKey]: a Free key ends in `:fx`. */
        fun deeplBase(apiKey: String): String = if (apiKey.trim().endsWith(":fx")) DEEPL_FREE else DEEPL_PRO

        /**
         * The setting as kept: [json] in XTA's shape, the key from its own
         * preference, the AI connection from Settings › AI. Anything
         * unreadable, or a choice that cannot be used, is Off; an AI choice
         * stays while the AI is not set up, as translating then says so.
         */
        fun read(json: String, apiKey: String, ai: AiConfig): TranslationConfig {
            val root = J.parse(json.takeIf { it.length <= 15_000 } ?: "")
            if (root["version"].int != 1) return TranslationConfig(apiKey = apiKey, ai = ai)
            val config = TranslationConfig(
                service = TranslationService.of(root["provider"].string) ?: TranslationService.OFF,
                endpoint = root["endpoint"].string.orEmpty(),
                apiKey = apiKey,
                target = root["target"].string.orEmpty(),
                ai = ai,
            )
            return if (config.service == TranslationService.AI || config.isValid) config else TranslationConfig(target = config.target, apiKey = apiKey, ai = ai)
        }

        fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

/** The language codes each service names a reader's language by. */
object TranslationLanguages {
    private fun parts(code: String) = code.replace('-', '_').split('_').map { it.lowercase() }

    private val HANT = setOf("hant", "tw", "hk", "mo")

    /** DeepL's `target_lang` for one of XTA's codes or a tag: `EN-US`, `PT-BR`, `ZH-HANT`. */
    fun deepl(code: String): String {
        val parts = parts(code)
        val rest = parts.drop(1).toSet()
        return when (parts.first()) {
            "en" -> if ("gb" in rest) "EN-GB" else "EN-US"
            "pt" -> if ("br" in rest) "PT-BR" else "PT-PT"
            "zh" -> if (rest.intersect(HANT).isEmpty()) "ZH-HANS" else "ZH-HANT"
            "nb", "no", "nn" -> "NB"
            else -> parts.first().uppercase()
        }
    }

    /** The codes a LibreTranslate server may name [code] by, best first: servers differ. */
    fun libre(code: String): List<String> {
        val parts = parts(code)
        val rest = parts.drop(1).toSet()
        return when (parts.first()) {
            "zh" -> if (rest.intersect(HANT).isNotEmpty()) listOf("zh-Hant", "zt", "zh-TW", "zh") else listOf("zh-Hans", "zh", "zh-CN")
            "pt" -> if ("br" in rest) listOf("pt-BR", "pb", "pt") else listOf("pt")
            "nb", "no", "nn" -> listOf("nb", "no")
            else -> listOf(parts.first())
        }
    }

    /** How the AI's prompt names [code]. */
    fun name(code: String): String = when (code.replace('-', '_')) {
        "ar" -> "Arabic"
        "be" -> "Belarusian"
        "be_Latn", "be_XL" -> "Belarusian written in the Latin alphabet (Łacinka)"
        "ca" -> "Catalan"
        "cs" -> "Czech"
        "de" -> "German"
        "en" -> "English"
        "eo" -> "Esperanto"
        "es" -> "Spanish"
        "et" -> "Estonian"
        "eu" -> "Basque"
        "fr" -> "French"
        "hi" -> "Hindi"
        "id", "in" -> "Indonesian"
        "it" -> "Italian"
        "ja" -> "Japanese"
        "ko" -> "Korean"
        "nb_NO", "nb" -> "Norwegian Bokmål"
        "nl" -> "Dutch"
        "pl" -> "Polish"
        "pt" -> "European Portuguese"
        "pt_BR" -> "Brazilian Portuguese"
        "ro" -> "Romanian"
        "ru" -> "Russian"
        "tr" -> "Turkish"
        "uk" -> "Ukrainian"
        "vi" -> "Vietnamese"
        "zh_Hans", "zh_CN" -> "Simplified Chinese"
        "zh_Hant", "zh_TW" -> "Traditional Chinese"
        else -> code
    }

    /** A service's name for a language (`DE`, `pt-BR`) as a plain lower-case tag (`de`, `pt-br`), for comparing. */
    fun plain(code: String?): String? = code?.trim()?.replace('_', '-')?.lowercase()?.ifEmpty { null }
}

/** Why a translation failed: worded by the screen that asked. */
enum class TranslationFailure {
    /** No service is chosen, or what is set cannot be used. */
    NOT_CONFIGURED,

    /** More text than a translation is asked for at once. */
    TOO_LONG,

    /** The server did not answer: no connection, a wrong address, a timeout. */
    UNREACHABLE,

    /** The server refused: it needs a key, or the key is wrong. */
    REFUSED,

    /** Too many translations: the server's limit or the key's quota. */
    RATE_LIMITED,

    /** The server does not translate into (or from) that language. */
    UNSUPPORTED_LANGUAGE,

    /** An answer that holds no translation. */
    BAD_ANSWER,
}

/** A translation that did not come, and why. */
class TranslationException(val failure: TranslationFailure, cause: Throwable? = null) : Exception(failure.name.lowercase(), cause)

/** [AiException]'s reason in a translation's terms. */
internal fun AiException.asTranslation(): TranslationException = TranslationException(
    when (reason) {
        AiException.Reason.NOT_CONFIGURED, AiException.Reason.INVALID_ADDRESS -> TranslationFailure.NOT_CONFIGURED
        AiException.Reason.TIMEOUT -> TranslationFailure.UNREACHABLE
        AiException.Reason.HTTP -> failureOf(status ?: 0, "")
    },
    this,
)

/** A non-success answer's status, and its body for a 400, as a failure. */
internal fun failureOf(status: Int, body: String): TranslationFailure = when {
    status == 401 || status == 403 -> TranslationFailure.REFUSED
    status == 429 || status == 456 -> TranslationFailure.RATE_LIMITED
    status == 400 && Regex("not supported|language", RegexOption.IGNORE_CASE).containsMatchIn(body) -> TranslationFailure.UNSUPPORTED_LANGUAGE
    status == 400 && Regex("api.?key", RegexOption.IGNORE_CASE).containsMatchIn(body) -> TranslationFailure.REFUSED
    else -> TranslationFailure.BAD_ANSWER
}
