package com.aimdi.xtanative.networks.stocks

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.core.x.model.XEntity
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XUser
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl

/** A fixture from `src/test/resources/stocks/`. */
fun fixture(name: String): String = checkNotNull(StocksFixtures::class.java.getResource("/stocks/$name")) { "no fixture $name" }.readText()

/** [value] (maps, lists, strings, numbers, booleans, null) as JSON. */
fun jsonOf(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Map<*, *> -> JsonObject(value.entries.associate { (key, item) -> key.toString() to jsonOf(item) })
    is Iterable<*> -> JsonArray(value.map(::jsonOf))
    else -> error("not JSON: $value")
}

fun j(value: Any?): J = J(jsonOf(value))

/** An answer of [code] carrying [value] as JSON (a string body as it is). */
fun answer(value: Any?, code: Int = 200): XResponse = XResponse(code, if (value is String) value else jsonOf(value).toString())

/** Stands in for the price hosts: answers each GET with [respond], and keeps what was asked, with which headers. */
class FakeHttp(private val respond: suspend (HttpUrl) -> XResponse) : XHttp {
    private val lock = Any()
    private val urls = ArrayList<HttpUrl>()
    private val sentHeaders = ArrayList<Map<String, String>>()

    val asked: List<HttpUrl> get() = synchronized(lock) { urls.toList() }
    val hosts: List<String> get() = asked.map { it.host }
    val headers: List<Map<String, String>> get() = synchronized(lock) { sentHeaders.toList() }

    fun clear(): Unit = synchronized(lock) {
        urls.clear()
        sentHeaders.clear()
    }

    override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean): XResponse {
        synchronized(lock) {
            urls.add(url)
            sentHeaders.add(headers)
        }
        return respond(url)
    }
}

/**
 * A Yahoo chart answer: `meta`, the bars' timestamps and closes (and opens,
 * the closes unless given).
 */
fun yahooChart(meta: Map<String, Any?>, timestamps: List<Long>, closes: List<Any?>, opens: List<Any?>? = null): J = j(
    mapOf(
        "chart" to mapOf(
            "result" to listOf(
                mapOf(
                    "meta" to meta,
                    "timestamp" to timestamps,
                    "indicators" to mapOf("quote" to listOf(mapOf("close" to closes, "open" to (opens ?: closes)))),
                ),
            ),
            "error" to null,
        ),
    ),
)

fun user(id: String): XUser = XUser(id = id, screenName = "user$id", name = "User $id")

/** A post by [author] carrying [cashtags] as X's entities, with [links] expanded, written at [at]. */
fun post(
    id: String,
    author: String = "a",
    cashtags: List<String> = emptyList(),
    links: List<String> = emptyList(),
    text: String = cashtags.joinToString(" ") { "$$it" },
    at: Instant? = null,
    reposted: XPost? = null,
): XPost = XPost(
    id = id,
    author = user(author),
    createdAt = at,
    text = text,
    entities = cashtags.mapIndexed { index, tag -> XEntity.Cashtag(index, index + 1, tag) } +
        links.map { XEntity.Link(0, 1, "https://t.co/x", it, it) },
    reposted = reposted,
)

fun thread(id: String, vararg posts: XPost): XThread = XThread(id, posts.toList())

object StocksFixtures
