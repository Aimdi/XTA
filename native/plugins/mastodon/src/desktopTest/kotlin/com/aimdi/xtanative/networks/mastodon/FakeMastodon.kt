package com.aimdi.xtanative.networks.mastodon

import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import java.util.Collections
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl

/** Stands in for Mastodon instances: [answer] replies to each request, and every request is kept. */
internal class FakeMastodon(private val answer: suspend (Asked) -> XResponse) : XHttp {
    /** One request as the client made it. */
    data class Asked(val method: String, val url: HttpUrl, val headers: Map<String, String>, val body: String? = null, val contentType: String? = null) {
        val host: String get() = url.host
        val path: String get() = url.encodedPath

        fun query(name: String): String? = url.queryParameter(name)

        override fun toString() = "$method $url"
    }

    val asked: MutableList<Asked> = Collections.synchronizedList(ArrayList())

    val paths: List<String> get() = synchronized(asked) { asked.map { "${it.host}${it.path}" } }

    override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean): XResponse =
        Asked("GET", url, headers).also { asked += it }.let { answer(it) }

    override suspend fun post(url: HttpUrl, headers: Map<String, String>, body: String, contentType: String): XResponse =
        Asked("POST", url, headers, body, contentType).also { asked += it }.let { answer(it) }
}

internal fun ok(json: JsonElement) = XResponse(200, json.toString())

internal fun answer(code: Int, body: String = "") = XResponse(code, body)

internal fun obj(vararg fields: Pair<String, Any?>): JsonObject = JsonObject(fields.associate { (key, value) -> key to element(value) })

internal fun list(vararg items: Any?): JsonArray = JsonArray(items.map(::element))

private fun element(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is List<*> -> JsonArray(value.map(::element))
    else -> error("no JSON for $value")
}

/** An account as `/api/v1/accounts/lookup` and statuses carry it. */
internal fun accountJson(id: String, username: String, acct: String = username) = obj(
    "id" to id,
    "username" to username,
    "acct" to acct,
    "display_name" to username.uppercase(),
    "note" to "",
    "url" to "https://example.social/@$username",
)

/** A status, as XTA's tests write one: [timelineId] wraps it as a boost with that id. */
internal fun statusJson(
    id: String,
    url: String,
    text: String,
    username: String = "b",
    acct: String? = null,
    timelineId: String? = null,
    extra: List<Pair<String, Any?>> = emptyList(),
): JsonObject {
    val status = obj(
        "id" to id,
        "created_at" to "2026-08-01T09:00:00.000Z",
        "content" to "<p>$text</p>",
        "url" to url,
        "replies_count" to 1,
        "account" to accountJson("2", username, acct ?: username),
        "media_attachments" to list(),
        *extra.toTypedArray(),
    )
    if (timelineId == null) return status
    return obj("id" to timelineId, "created_at" to "2026-08-02T09:00:00.000Z", "content" to "", "reblog" to status, "account" to accountJson("3", "booster"))
}

/** [count] statuses, newest first, the last one's id (or wrapper id) being "[prefix]-[count - 1]". */
internal fun pageJson(count: Int, prefix: String = "p", wrapped: Boolean = false) = JsonArray(
    (0 until count).map { index ->
        statusJson(
            id = if (wrapped) "original-$index" else "$prefix-$index",
            url = "https://example.social/@b/${if (wrapped) "original-$index" else "$prefix-$index"}",
            text = "post $index",
            timelineId = if (wrapped) "$prefix-$index" else null,
        )
    },
)
