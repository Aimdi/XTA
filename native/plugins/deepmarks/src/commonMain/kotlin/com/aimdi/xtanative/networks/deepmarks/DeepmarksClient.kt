package com.aimdi.xtanative.networks.deepmarks

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.NetworkRequestException
import java.net.MalformedURLException
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The user's Deepmarks account: the API base (Deepmarks' own unless they run
 * their own instance) and the API key. Only [of] makes one, so a client never
 * holds what XTA would have called "not configured".
 */
class DeepmarksAccount private constructor(val apiBase: String, val apiKey: String) {
    /**
     * [path] under the base's `/api/v1`, asking [parameter] in place of any
     * query the base had. A base that is not an address cannot be reached: it
     * fails as an IOException, as no connection would.
     */
    internal fun endpoint(path: String, parameter: Pair<String, String>? = null): HttpUrl {
        val base = apiBase.toHttpUrlOrNull() ?: throw MalformedURLException("Not an address: $apiBase")
        return base.newBuilder()
            .encodedPath("/")
            .apply { (base.pathSegments.filter { it.isNotEmpty() } + listOf("api", "v1", path)).forEach(::addPathSegment) }
            .apply { parameter?.let { (name, value) -> query(null).addQueryParameter(name, value) } }
            .build()
    }

    companion object {
        /** Where Deepmarks runs unless the user points elsewhere. */
        const val DEFAULT_API_BASE = "https://api.deepmarks.org"

        /** The account with [apiKey] at [apiBase] (blank for Deepmarks' own); null without a key, so nothing is sent. */
        fun of(apiBase: String, apiKey: String): DeepmarksAccount? =
            if (apiKey.isBlank()) null else DeepmarksAccount(apiBase.trim().ifEmpty { DEFAULT_API_BASE }, apiKey.trim())
    }
}

/** A bookmark event the API took: its id, and the relays that stored it and that did not. */
data class DeepmarksPublishResult(val eventId: String, val publishedTo: List<String> = emptyList(), val failedRelays: List<String> = emptyList()) {
    /** The API took the event but no relay did, so the bookmark is not stored anywhere yet. */
    val reachedNoRelay: Boolean get() = publishedTo.isEmpty() && failedRelays.isNotEmpty()
}

/** Why saving to Deepmarks failed, in the terms XTA's screens tell apart. */
enum class DeepmarksProblem {
    /** [DeepmarksAccount.of] gave no account, or there is no secret key: nothing was sent. */
    NOT_CONFIGURED,

    /** The pasted secret key is not an nsec or hex key ([NostrKeyException]). */
    BAD_SECRET_KEY,

    /** The API key was refused (401). */
    UNAUTHORIZED,

    /** API access is for lifetime members only (402). */
    NOT_LIFETIME_MEMBER,

    /** The signing key belongs to another account than the API key (403). */
    KEY_MISMATCH,

    /** The event was refused for its shape, a tag or its time (400). */
    REJECTED,

    /** Too many requests (429). */
    RATE_LIMITED,

    /** Not a Deepmarks API at that address, or a failure upstream of it. */
    BAD_SERVER,

    /** The server could not be reached. */
    NETWORK,
    ;

    companion object {
        /** What [failure], thrown while signing or by [DeepmarksClient], means to the user. Anything else could not reach the server. */
        fun of(failure: Throwable): DeepmarksProblem = when (failure) {
            is NostrKeyException -> BAD_SECRET_KEY
            is NetworkRequestException -> when (failure.code) {
                400 -> REJECTED
                401 -> UNAUTHORIZED
                402 -> NOT_LIFETIME_MEMBER
                403 -> KEY_MISMATCH
                429 -> RATE_LIMITED
                else -> BAD_SERVER
            }
            else -> NETWORK
        }
    }
}

/**
 * The Deepmarks v1 API, as XTA talks to it: publish a bookmark, and a probe
 * for the settings screen. Deepmarks never signs for the user: a write
 * carries an event signed on the device ([Nostr.signWebBookmark]), and the
 * server only fans it out to relays.
 *
 * Anything but the answer asked for throws a [NetworkRequestException]
 * (its code tells [DeepmarksProblem] apart); no connection is an IOException.
 */
class DeepmarksClient(private val http: XHttp) {
    /** Publishes the signed `kind:39701` [event]. */
    suspend fun publishBookmark(account: DeepmarksAccount, event: NostrEvent): DeepmarksPublishResult {
        val response = http.post(account.endpoint("bookmarks"), headers(account), event.toJson().toString(), JSON)
        val answer = J.parse(response.body)
        if (response.code != 200 || !looksLikeJson(response) || answer.raw !is JsonObject) throw failureOf(response)
        return DeepmarksPublishResult(
            eventId = answer["eventId"].string ?: event.id,
            publishedTo = answer["publishedTo"].list.mapNotNull { it.string },
            failedRelays = answer["failedRelays"].list.mapNotNull { it.string },
        )
    }

    /**
     * Checks the API key, and gives the pubkey Deepmarks has for it when its
     * newest bookmark tells. The API has no "who am I", so an account without
     * a public bookmark gives null: "cannot tell", not "mismatch".
     */
    suspend fun verify(account: DeepmarksAccount): String? {
        val response = http.get(account.endpoint("bookmarks", "limit" to "1"), headers(account), followRedirects = true)
        val answer = J.parse(response.body)
        if (response.code != 200 || !looksLikeJson(response) || answer.raw !is JsonObject) throw failureOf(response)
        return answer["bookmarks"][0]["pubkey"].string
    }

    private fun headers(account: DeepmarksAccount) =
        mapOf("Authorization" to "Bearer ${account.apiKey}", "Content-Type" to JSON, "Accept" to JSON)

    private fun failureOf(response: XResponse) = NetworkRequestException(NETWORK, response.code, "HTTP ${response.code}: ${response.body}")

    companion object {
        /** The name a [NetworkRequestException] from Deepmarks carries. */
        const val NETWORK = "deepmarks"

        private const val JSON = "application/json"
    }
}

/** Whether [response] is an API's JSON: by its type, or by how its body starts when a proxy dropped the type. */
private fun looksLikeJson(response: XResponse): Boolean =
    "application/json" in response.header("content-type").orEmpty() || response.body.trimStart().let { it.startsWith("{") || it.startsWith("[") }
