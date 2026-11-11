package com.aimdi.xtanative.networks.karakeep

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.NetworkRequestException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The user's own Karakeep: the base its API hangs under and the API key it
 * takes. Only [of] makes one, so a client never holds what XTA would have
 * called "not configured".
 */
class KarakeepServer private constructor(val baseUrl: HttpUrl, val apiKey: String) {
    /** [path] under the instance's `/api/v1`, asking [parameter]. */
    internal fun endpoint(path: String, parameter: Pair<String, String>? = null): HttpUrl = baseUrl.newBuilder()
        .addPathSegments("api/v1/$path")
        .apply { parameter?.let { (name, value) -> addQueryParameter(name, value) } }
        .build()

    companion object {
        /** The server at [address] with [apiKey], as the user typed them; null when either is unusable, so nothing is sent. */
        fun of(address: String, apiKey: String): KarakeepServer? {
            val base = karakeepBaseUrl(address) ?: return null
            return if (apiKey.isBlank()) null else KarakeepServer(base, apiKey)
        }
    }
}

private val HOST = Regex("[A-Za-z0-9]([A-Za-z0-9._-]*[A-Za-z0-9])?")

/**
 * Whatever the user typed, as the base of their instance: `karakeep.example.com`
 * (https is assumed), a trailing slash, or the full `/api/v1` path copied from
 * the docs all work. A path prefix is kept, for Karakeep behind a reverse proxy.
 */
fun karakeepBaseUrl(raw: String): HttpUrl? {
    val text = raw.trim().ifEmpty { return null }
    val url = (if ("://" in text) text else "https://$text").toHttpUrlOrNull() ?: return null
    if (!HOST.matches(url.host)) return null
    val segments = url.pathSegments.filter { it.isNotEmpty() }.dropLastWhile { it == "v1" || it == "api" }
    return HttpUrl.Builder().scheme(url.scheme).host(url.host).port(url.port).apply { segments.forEach(::addPathSegment) }.build()
}

/** Where a saved link ended up. */
enum class KarakeepSaveOutcome {
    /** A new bookmark (HTTP 201). */
    SAVED,

    /** Karakeep already had the URL and answered with that bookmark (HTTP 200). */
    ALREADY_SAVED,
}

/** A saved link, and its bookmark's id when Karakeep said. */
data class KarakeepSaveResult(val outcome: KarakeepSaveOutcome, val bookmarkId: String?)

/**
 * Something answered at the address, but not Karakeep's API: a wrong host, a
 * reverse proxy that never reaches Karakeep. XTA sends the user to check the
 * address for it rather than reporting a failing Karakeep.
 */
class NotKarakeepException(val code: Int) : Exception("Not Karakeep's API at that address (HTTP $code)")

/** Why a save or a check failed, in the terms XTA's screens tell apart. */
enum class KarakeepProblem {
    /** [KarakeepServer.of] gave no server: no usable address or no API key, and nothing was sent. */
    NOT_CONFIGURED,

    /** The address does not lead to Karakeep's API. */
    BAD_SERVER,

    /** Karakeep refused the API key. */
    UNAUTHORIZED,

    /** Karakeep answered with an error of its own. */
    SERVER,

    /** The server could not be reached. */
    NETWORK,
    ;

    companion object {
        /** What [failure], thrown by [KarakeepClient], means to the user. Anything that is not an answer could not reach the server. */
        fun of(failure: Throwable): KarakeepProblem = when (failure) {
            is NotKarakeepException -> BAD_SERVER
            is NetworkRequestException -> when (failure.code) {
                401, 403 -> UNAUTHORIZED
                404 -> BAD_SERVER
                else -> SERVER
            }
            else -> NETWORK
        }
    }
}

/**
 * The user's own Karakeep, as XTA talks to it: save a link as a bookmark, and
 * a probe for the settings screen. Nothing else is read or written.
 *
 * A failed request throws: a [NetworkRequestException] when Karakeep answered
 * with an error, [NotKarakeepException] when something else answered, and the
 * IOException of no connection. [KarakeepProblem.of] says which is which.
 */
class KarakeepClient(private val http: XHttp) {
    /**
     * Saves [url] as a link bookmark, with [title] (cut to what Karakeep takes)
     * and [note] when they say something.
     */
    suspend fun saveLink(server: KarakeepServer, url: String, title: String? = null, note: String? = null): KarakeepSaveResult {
        val body = buildJsonObject {
            put("type", "link")
            put("url", url)
            title?.trim()?.takeIf { it.isNotEmpty() }?.let { put("title", cutTitle(it)) }
            note?.trim()?.takeIf { it.isNotEmpty() }?.let { put("note", it) }
        }
        val response = http.post(server.endpoint("bookmarks"), headers(server), body.toString(), JSON)
        // A proxy that never reaches Karakeep can answer 200 with an HTML page; calling that "already saved" would be a lie.
        if (response.code !in setOf(200, 201) || !looksLikeJson(response)) throw failureOf(response)
        val outcome = if (response.code == 200) KarakeepSaveOutcome.ALREADY_SAVED else KarakeepSaveOutcome.SAVED
        return KarakeepSaveResult(outcome, J.parse(response.body)["id"].string)
    }

    /** Returns when the server answers with these details; throws what is wrong otherwise, so the settings screen can say. */
    suspend fun verify(server: KarakeepServer) {
        val response = http.get(server.endpoint("bookmarks", "limit" to "1"), headers(server), followRedirects = true)
        if (response.code != 200 || !looksLikeJson(response)) throw failureOf(response)
    }

    private fun headers(server: KarakeepServer) =
        mapOf("Authorization" to "Bearer ${server.apiKey}", "Content-Type" to JSON, "Accept" to JSON)

    /** What an answer that is not a success stands for: a refused key first, then a wrong address, then Karakeep's own error. */
    private fun failureOf(response: XResponse): Exception = when {
        response.code == 401 || response.code == 403 || response.code == 404 -> NetworkRequestException(NETWORK, response.code, "HTTP ${response.code}")
        !looksLikeJson(response) -> NotKarakeepException(response.code)
        else -> NetworkRequestException(NETWORK, response.code, "HTTP ${response.code}: ${response.body}")
    }

    companion object {
        /** The name a [NetworkRequestException] from Karakeep carries. */
        const val NETWORK = "karakeep"

        /** Karakeep caps bookmark titles; this keeps well inside the cap. */
        const val MAX_TITLE_LENGTH = 250

        private const val JSON = "application/json"
    }
}

/** [title] cut to [KarakeepClient.MAX_TITLE_LENGTH], never between the halves of an emoji or another character outside the BMP. */
internal fun cutTitle(title: String): String {
    if (title.length <= KarakeepClient.MAX_TITLE_LENGTH) return title
    val end = KarakeepClient.MAX_TITLE_LENGTH
    return title.take(if (Character.isHighSurrogate(title[end - 1])) end - 1 else end)
}

/** Whether [response] is an API's JSON: by its type, or by how its body starts when a proxy dropped the type. */
private fun looksLikeJson(response: XResponse): Boolean =
    "application/json" in response.header("content-type").orEmpty() || response.body.trimStart().let { it.startsWith("{") || it.startsWith("[") }
