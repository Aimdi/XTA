package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl

/** A request the fake Meta was sent. */
data class Asked(
    val method: String,
    val url: HttpUrl,
    val headers: Map<String, String>,
    val body: String? = null,
    val contentType: String? = null,
) {
    val path: String get() = url.encodedPath

    /** The form body's fields, decoded. */
    val form: Map<String, String>
        get() = body.orEmpty().split('&').filter { '=' in it }.associate { field ->
            val (name, value) = field.split('=', limit = 2)
            URLDecoder.decode(name, Charsets.UTF_8) to URLDecoder.decode(value, Charsets.UTF_8)
        }
}

/** Stands in for Meta's hosts: answers each request with [answer] and keeps what it was asked. */
class FakeMeta(private val answer: suspend (Asked) -> XResponse) : XHttp {
    val asked = CopyOnWriteArrayList<Asked>()

    override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean): XResponse =
        record(Asked("GET", url, headers))

    override suspend fun post(url: HttpUrl, headers: Map<String, String>): XResponse = record(Asked("POST", url, headers, ""))

    override suspend fun post(url: HttpUrl, headers: Map<String, String>, body: String, contentType: String): XResponse =
        record(Asked("POST", url, headers, body, contentType))

    private suspend fun record(request: Asked): XResponse {
        asked += request
        return answer(request)
    }
}

/** A clock whose sleep moves it on, so pacing is measured without waiting. */
class FakeTime(@Volatile var now: Long = 1_700_000_000_000L) {
    val slept = CopyOnWriteArrayList<Long>()
    val clock: () -> Long = { now }
    val sleep: suspend (Long) -> Unit = { millis ->
        slept += millis
        now += millis
    }
}

const val COOKIES = "sessionid=s; csrftoken=c; ds_user_id=1; mid=m; ig_did=g"

fun fixture(name: String): String = FakeMeta::class.java.getResource("/threads/$name")!!.readText()

fun ok(body: String) = XResponse(200, body)

/** A client over [meta] with no pacing, the clock of [time]. */
fun direct(
    meta: FakeMeta,
    session: ThreadsSession = ThreadsSession(),
    memory: ThreadsMemory = ThreadsMemory.InMemory(),
    time: FakeTime = FakeTime(),
    pacing: ThreadsPacing = ThreadsPacing(0, 0, 0, 0, sleep = time.sleep),
    repairs: ThreadsRepairs = ThreadsRepairs(),
) = ThreadsDirectClient(meta, { session }, memory, { repairs }, pacing, time.clock)

/** Kotlin maps, lists and values as JSON, so posts read like the map literals of XTA's tests. */
fun json(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Map<*, *> -> JsonObject(value.entries.associate { (key, item) -> key.toString() to json(item) })
    is List<*> -> JsonArray(value.map(::json))
    else -> error("not JSON: $value")
}

fun j(value: Any?): J = J(json(value))

/** A Meta post object, as XTA's threads_parse_test writes one. */
fun postJson(
    pk: String,
    user: String = "zuck",
    text: String = "post $pk",
    takenAt: Long? = 1_720_000_000,
    info: Map<String, Any?>? = null,
    extra: Map<String, Any?> = emptyMap(),
): Map<String, Any?> = buildMap {
    put("pk", pk)
    put("code", "C$pk")
    takenAt?.let { put("taken_at", it) }
    put("caption", mapOf("text" to text))
    put("user", mapOf("username" to user, "full_name" to user.uppercase()))
    info?.let { put("text_post_app_info", it) }
    putAll(extra)
}

/** A public page (a post, a profile, a search) carrying [chains] in `thread_items`, as its `data-sjs` scripts do. */
fun page(chains: List<List<Map<String, Any?>>>, head: String = ""): String {
    val edges = chains.map { chain -> mapOf("node" to mapOf("thread_items" to chain.map { mapOf("post" to it) })) }
    return "<html><head>$head</head><script type=\"application/json\" data-sjs>${json(mapOf("data" to mapOf("edges" to edges)))}</script></html>"
}

/** A guest GraphQL answer, one thread per post. */
fun graphql(vararg posts: Map<String, Any?>): String =
    json(mapOf("data" to mapOf("mediaData" to mapOf("threads" to posts.map { mapOf("thread_items" to listOf(mapOf("post" to it))) })))).toString()

/** A profile page with the LSD token and the owner's id guest GraphQL needs, and whatever [more] it carries. */
fun profilePage(lsd: String? = "tok", userId: String? = "63404918397", more: String = ""): String = buildString {
    append("<html>")
    lsd?.let { append("<script>[\"LSD\",[],{\"token\":\"$it\"}]</script>") }
    userId?.let { append("<script>{\"props\":{\"is_self_profile\":false,\"user_id\":\"$it\"}}</script>") }
    append(more)
    append("</html>")
}
