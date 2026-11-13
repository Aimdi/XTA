package com.aimdi.xtanative.networks.immich

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.NetworkRequestException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** What Immich did with a file it was handed. */
enum class ImmichUploadOutcome {
    /** A new asset was created. */
    CREATED,

    /** Immich recognised the bytes by checksum and kept the asset it had: a success too. */
    DUPLICATE,
}

/** An accepted upload, and the asset it is in Immich when Immich said. */
data class ImmichUploadResult(val outcome: ImmichUploadOutcome, val assetId: String?)

/**
 * Something answered at the address, but not Immich's API: a router's page, a
 * reverse proxy that cannot reach Immich, another app. XTA sends the user to
 * check the address for it rather than reporting a failing Immich.
 */
class NotImmichException(val code: Int) : Exception("Not Immich's API at that address (HTTP $code)")

/** Why talking to Immich failed, in the terms XTA's screens tell apart. */
enum class ImmichProblem {
    /** [ImmichServer.of] gave no server: no usable address or no API key, and nothing was sent. */
    NOT_CONFIGURED,

    /** The address does not lead to Immich's API. */
    BAD_SERVER,

    /** Immich refused the API key. */
    UNAUTHORIZED,

    /** Immich answered with an error of its own. */
    SERVER,

    /** The server could not be reached. */
    NETWORK,
    ;

    companion object {
        /** What [failure], thrown by [ImmichClient], means to the user. Anything that is not an answer could not reach the server. */
        fun of(failure: Throwable): ImmichProblem = when (failure) {
            is NotImmichException -> BAD_SERVER
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
 * The user's own Immich, as XTA talks to it: upload a file, file assets in an
 * album, and a probe for the settings screen. The album list is read only to
 * find an album by name; nothing else about the library is asked for.
 *
 * A failed request throws: a [NetworkRequestException] when Immich answered
 * with an error, [NotImmichException] when something else answered, and the
 * IOException of no connection. [ImmichProblem.of] says which is which.
 */
class ImmichClient(private val http: ImmichHttp) {
    /**
     * Sends [bytes] as a new asset, made at [createdAt]. [deviceAssetId] is how
     * Immich tells this file from the device's others.
     */
    suspend fun upload(server: ImmichServer, bytes: ByteArray, fileName: String, deviceAssetId: String, createdAt: Instant): ImmichUploadResult {
        val stamp = STAMP.format(createdAt)
        val fields = listOf("deviceAssetId" to deviceAssetId, "deviceId" to DEVICE_ID, "fileCreatedAt" to stamp, "fileModifiedAt" to stamp)
        val form = Multipart(fields, FilePart("assetData", fileName, bytes))
        val body = form.bytes()
        val first = http.send("POST", server.endpoint("assets"), headers(server), body, form.contentType)
        // Immich renamed the endpoint; an instance a release or two behind answers on the older path.
        val response = if (first.code == 404) http.send("POST", server.endpoint("asset", "upload"), headers(server), body, form.contentType) else first
        if (response.code !in ACCEPTED || !looksLikeJson(response)) throw failureOf(response)
        val answer = J.parse(response.body)
        val outcome = if (answer["status"].string == "duplicate") ImmichUploadOutcome.DUPLICATE else ImmichUploadOutcome.CREATED
        return ImmichUploadResult(outcome, answer["id"].string)
    }

    /**
     * The id of the album named [name], made when the server has none. Null
     * when it cannot be had: that must not fail an upload already done.
     */
    suspend fun ensureAlbum(server: ImmichServer, name: String): String? = orNull { findAlbum(server, name) ?: createAlbum(server, name) }

    /**
     * Files [assetIds] in [albumId]. Assets the album already has are reported
     * per asset, not as a failure, so this only says whether the request was taken.
     */
    suspend fun addToAlbum(server: ImmichServer, albumId: String, assetIds: List<String>): Boolean {
        if (assetIds.isEmpty()) return true
        val body = buildJsonObject { put("ids", JsonArray(assetIds.map(::JsonPrimitive))) }.toString()
        val url = server.endpoint("albums", albumId, "assets")
        return http.send("PUT", url, headers(server) + JSON_TYPE, body.toByteArray(), JSON_UTF8).code in ACCEPTED
    }

    /** Returns when the server takes these details; throws what is wrong otherwise, so the settings screen can say. */
    suspend fun verify(server: ImmichServer) {
        val first = http.get(server.endpoint("users", "me"), headers(server), followRedirects = true)
        val response = if (first.code == 404) http.get(server.endpoint("user", "me"), headers(server), followRedirects = true) else first
        if (response.code != 200 || !looksLikeJson(response)) throw failureOf(response)
    }

    private suspend fun findAlbum(server: ImmichServer, name: String): String? {
        val response = http.get(server.endpoint("albums"), headers(server), followRedirects = true)
        if (response.code != 200 || !looksLikeJson(response)) return null
        // A list that claims to be JSON and is not: XTA gives up on the album rather than make a second one.
        val albums = J.parse(response.body).takeIf { it.raw != null } ?: throw NotImmichException(response.code)
        return albums.list.firstOrNull { it["albumName"].string == name && it["id"].string != null }?.get("id")?.string
    }

    private suspend fun createAlbum(server: ImmichServer, name: String): String? {
        val body = buildJsonObject { put("albumName", name) }.toString()
        val response = http.post(server.endpoint("albums"), headers(server) + JSON_TYPE, body, JSON)
        return if (response.code in ACCEPTED && looksLikeJson(response)) J.parse(response.body)["id"].string else null
    }

    private fun headers(server: ImmichServer) = mapOf("x-api-key" to server.apiKey, "Accept" to JSON)

    /** What an answer that is not a success stands for: a refused key first, then a wrong address, then Immich's own error. */
    private fun failureOf(response: XResponse): Exception = when {
        response.code == 401 || response.code == 403 || response.code == 404 -> NetworkRequestException(NETWORK, response.code, "HTTP ${response.code}")
        !looksLikeJson(response) -> NotImmichException(response.code)
        else -> NetworkRequestException(NETWORK, response.code, "HTTP ${response.code}: ${response.body}")
    }

    companion object {
        /** The name a [NetworkRequestException] from Immich carries. */
        const val NETWORK = "immich"

        /** The app an asset came from, which Immich shows in the asset's details: XTA's name, kept. */
        const val DEVICE_ID = "xta"

        private const val JSON = "application/json"
        private const val JSON_UTF8 = "application/json; charset=utf-8"
        private val JSON_TYPE = "Content-Type" to JSON
        private val ACCEPTED = setOf(200, 201)

        /** A time as XTA's Dart writes one in UTC, to the millisecond. */
        private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)
    }
}

/** Whether [response] is an API's JSON: by its type, or by how its body starts when a proxy dropped the type. */
private fun looksLikeJson(response: XResponse): Boolean =
    "application/json" in response.header("content-type").orEmpty() || response.body.trimStart().let { it.startsWith("{") || it.startsWith("[") }

/** [block]'s value, or null when it failed in any way but being cancelled. */
internal inline fun <T> orNull(block: () -> T?): T? = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}
