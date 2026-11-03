package com.aimdi.xtanative.services.ai

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The reader's own AI connection, kept on the device (ai_client.dart). XTA
 * ships no key of its own: with any field empty, every AI feature is off.
 */
data class AiConfig(val baseUrl: String = "", val apiKey: String = "", val model: String = "") {
    val isConfigured: Boolean get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    /** xAI's API, or a Grok model behind another address. */
    val isGrok: Boolean get() = host == "api.x.ai" || model.lowercase().startsWith("grok")

    /** The host requests go to, which XTA names before anything is sent there; the address itself when it has none. */
    val host: String get() = baseUrl.trim().toHttpUrlOrNull()?.host ?: baseUrl

    /** The preset whose host the address is on, for the hint under the key field. */
    val provider: AiProvider? get() = AiProvider.entries.firstOrNull { it.host == host }

    /**
     * This connection with [preset] filled in. Moving to another provider
     * clears the key and takes the preset's model; staying keeps both, unless
     * no model was written yet.
     */
    fun withPreset(preset: AiProvider): AiConfig {
        val sameProvider = baseUrl.trim().toHttpUrlOrNull()?.host == preset.host
        return AiConfig(
            baseUrl = preset.baseUrl,
            apiKey = if (sameProvider) apiKey else "",
            model = if (!sameProvider || model.isBlank()) preset.model else model,
        )
    }
}

/** The providers settings offers as one tap: an OpenAI-compatible root and a model. */
enum class AiProvider(val baseUrl: String, val model: String) {
    GROK("https://api.x.ai/v1", "grok-4"),
    OPENROUTER("https://openrouter.ai/api/v1", "openrouter/free"),
    OPENAI("https://api.openai.com/v1", "gpt-4o-mini"),
    ;

    val host: String get() = baseUrl.toHttpUrlOrNull()!!.host
}

/** Why an AI request gave no answer. */
class AiException(val reason: Reason, val status: Int? = null) :
    Exception(if (status == null) reason.name.lowercase() else "${reason.name.lowercase()} $status") {
    enum class Reason { NOT_CONFIGURED, INVALID_ADDRESS, HTTP, TIMEOUT }
}

/**
 * One chat turn against the reader's OpenAI-compatible provider (xAI,
 * OpenRouter, OpenAI or their own server), through [http].
 */
class AiClient(private val http: XHttp) {
    /**
     * Sends [prompt], with a picture as a `data:` URL when there is one, and
     * answers the reply's text: empty when the reply carried none. Gives up
     * after [timeoutMillis].
     */
    suspend fun complete(config: AiConfig, prompt: String, imageDataUrl: String? = null, timeoutMillis: Long = 30_000): String {
        if (!config.isConfigured) throw AiException(AiException.Reason.NOT_CONFIGURED)
        val url = chatUrl(config.baseUrl)
        val headers = mapOf("Authorization" to "Bearer ${config.apiKey.trim()}")
        val body = request(config.model.trim(), prompt, imageDataUrl).toString()
        val response = withTimeoutOrNull(timeoutMillis) { http.post(url, headers, body, "application/json") }
            ?: throw AiException(AiException.Reason.TIMEOUT)
        if (!response.isSuccessful) throw AiException(AiException.Reason.HTTP, response.code)
        return completionText(response.body)
    }

    companion object {
        /**
         * The chat endpoint for what the reader typed: an API root gets
         * `/chat/completions`, a full endpoint is kept, and OpenRouter's bare
         * host gets its documented `/api/v1`. Credentials in the address, or
         * anything but http(s), are refused.
         */
        fun chatUrl(address: String): HttpUrl {
            val url = address.trim().toHttpUrlOrNull()
            if (url == null || url.username.isNotEmpty() || url.password.isNotEmpty()) throw AiException(AiException.Reason.INVALID_ADDRESS)
            var path = url.encodedPath.trimEnd('/')
            if (url.host == "openrouter.ai" && (path.isEmpty() || path == "/api")) path = "/api/v1"
            if (!path.endsWith("/chat/completions")) path = "$path/chat/completions"
            return url.newBuilder().encodedPath(path).fragment(null).build()
        }

        /** The request body: one user message, the picture beside the text when there is one. */
        fun request(model: String, prompt: String, imageDataUrl: String? = null): JsonObject = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    if (imageDataUrl == null) {
                        put("content", prompt)
                    } else {
                        putJsonArray("content") {
                            addJsonObject { put("type", "text"); put("text", prompt) }
                            addJsonObject { put("type", "image_url"); putJsonObject("image_url") { put("url", imageDataUrl) } }
                        }
                    }
                }
            }
        }

        /**
         * The reply's text, never throwing on a reshaped body: the first
         * choice's message (a string, or its text parts joined by lines), or
         * a legacy completion's `text`; empty otherwise.
         */
        fun completionText(body: String): String {
            val first = J.parse(body)["choices"][0]
            val message = first["message"]
            val content = if (message.raw is JsonObject) message["content"] else first["text"]
            content.string?.let { return it.trim() }
            return content.list.filter { it["type"].string == "text" }.mapNotNull { it["text"].string }.joinToString("\n").trim()
        }
    }
}
