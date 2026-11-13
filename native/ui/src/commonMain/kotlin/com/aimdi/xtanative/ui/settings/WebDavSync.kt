package com.aimdi.xtanative.ui.settings

import com.aimdi.xtanative.store.StoreDir
import com.aimdi.xtanative.store.backup.BackupDocument
import com.aimdi.xtanative.store.backup.BackupKeys
import com.aimdi.xtanative.store.backup.WebDavVersion
import com.aimdi.xtanative.store.backup.XtaTime
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Where the reader's backup document lives on their WebDAV server, and how to sign in. */
data class WebDavConfig(val url: String, val username: String, val password: String) {
    val isComplete: Boolean get() = url.isNotBlank() && username.isNotEmpty() && password.isNotEmpty()

    /** The document's address: https only, since the payload can carry X sessions. */
    val address: HttpUrl? get() = url.trim().toHttpUrlOrNull()?.takeIf { it.isHttps && it.host.isNotEmpty() }

    val authHeaders: Map<String, String>
        get() = mapOf("authorization" to "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray()))
}

enum class WebDavOutcome { SUCCESS, NOT_CONFIGURED, INSECURE_URL, UNAUTHORIZED, NOT_FOUND, SERVER_ERROR, NETWORK_ERROR, CONFLICT, UNSAFE_SERVER }

data class WebDavResult(val outcome: WebDavOutcome, val body: String? = null, val detail: String? = null, val etag: String? = null) {
    val isSuccess: Boolean get() = outcome == WebDavOutcome.SUCCESS
}

/** One answer of the server; [etag] as it sent it. */
data class WebDavResponse(val code: Int, val body: String = "", val etag: String? = null)

/** The requests a sync makes; an interface, so tests can stand in for a server. */
fun interface WebDavHttp {
    /** Throws [IOException] when no answer came. */
    suspend fun send(method: String, url: HttpUrl, headers: Map<String, String>, body: String?): WebDavResponse
}

/** The validator of the server's document this device last uploaded or restored, per server and user. */
interface WebDavBaselines {
    suspend fun read(key: String): String?

    suspend fun write(key: String, etag: String)
}

/**
 * XTA's WebDAV sync (webdav_sync.dart): the same document an export of
 * everything writes, uploaded only over https. Writes are conditional, so a
 * document another device changed since this one last saw it is never
 * overwritten (a conflict), and the document being replaced is first kept as
 * an immutable version beside it, the newest [VERSIONS] listed in the
 * document itself.
 */
class WebDavSync(
    private val http: WebDavHttp,
    private val baselines: WebDavBaselines,
    private val clock: () -> Instant = Instant::now,
) {
    suspend fun download(config: WebDavConfig): WebDavResult = invalid(config) ?: get(config, config.address!!)

    /** Called once a restore was confirmed, never after a preview alone. */
    suspend fun acknowledge(config: WebDavConfig, result: WebDavResult) {
        val etag = strongEtag(result.etag)
        if (result.isSuccess && etag != null) baselines.write(baselineKey(config), etag)
    }

    suspend fun downloadVersion(config: WebDavConfig, version: WebDavVersion): WebDavResult {
        invalid(config)?.let { return it }
        if (!VERSION_NAME.matches(version.file)) return WebDavResult(WebDavOutcome.NOT_FOUND)
        return get(config, versionAddress(config, version.file))
    }

    suspend fun upload(config: WebDavConfig, body: String): WebDavResult {
        invalid(config)?.let { return it }
        return try {
            val data = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return WebDavResult(WebDavOutcome.SERVER_ERROR)
            val remote = download(config)
            if (!remote.isSuccess && remote.outcome != WebDavOutcome.NOT_FOUND) return remote
            val versions = ArrayList<WebDavVersion>()
            if (remote.isSuccess) {
                val etag = remote.etag ?: return WebDavResult(WebDavOutcome.UNSAFE_SERVER)
                if (baselines.read(baselineKey(config)) != etag) return WebDavResult(WebDavOutcome.CONFLICT)
                val now = clock()
                val previous = remote.body.orEmpty()
                val file = "${now.epochSecond * 1_000_000 + now.nano / 1_000}-${sha256(previous)}.json"
                val archived = put(config, versionAddress(config, file), previous, mapOf("if-none-match" to "*"))
                if (!archived.isSuccess) return archived
                versions += WebDavVersion(file, XtaTime.iso(now))
                versions += versionsIn(previous)
            }
            val payload = JsonObject(
                data - BackupKeys.WEBDAV_HISTORY + (BackupKeys.WEBDAV_HISTORY to JsonArray(versions.take(VERSIONS).map { it.toJson() })),
            ).toString()
            val condition = if (remote.isSuccess) mapOf("if-match" to remote.etag!!) else mapOf("if-none-match" to "*")
            val result = put(config, config.address!!, payload, condition)
            if (!result.isSuccess) return result
            // A lost validator must never become permission to overwrite on a retry.
            val verified = if (result.etag != null) result else download(config)
            if (!verified.isSuccess || verified.etag == null) return WebDavResult(WebDavOutcome.UNSAFE_SERVER)
            if (result.etag == null && verified.body != payload) return WebDavResult(WebDavOutcome.CONFLICT)
            acknowledge(config, verified)
            versions.drop(VERSIONS).forEach { stale ->
                runCatching { http.send("DELETE", versionAddress(config, stale.file), config.authHeaders, null) }
            }
            WebDavResult(WebDavOutcome.SUCCESS)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            WebDavResult(WebDavOutcome.NETWORK_ERROR)
        }
    }

    private fun invalid(config: WebDavConfig): WebDavResult? = when {
        !config.isComplete -> WebDavResult(WebDavOutcome.NOT_CONFIGURED)
        config.address == null -> WebDavResult(WebDavOutcome.INSECURE_URL)
        else -> null
    }

    private suspend fun get(config: WebDavConfig, url: HttpUrl): WebDavResult = try {
        val response = http.send("GET", url, config.authHeaders, null)
        val outcome = outcomeOf(response.code)
        WebDavResult(
            outcome,
            body = response.body.takeIf { outcome == WebDavOutcome.SUCCESS },
            detail = if (outcome == WebDavOutcome.SUCCESS) null else "HTTP ${response.code}",
            etag = strongEtag(response.etag),
        )
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        WebDavResult(WebDavOutcome.NETWORK_ERROR)
    }

    /** A PUT; a missing folder is made once (MKCOL) and the PUT tried again. */
    private suspend fun put(config: WebDavConfig, url: HttpUrl, body: String, condition: Map<String, String>): WebDavResult {
        try {
            val headers = config.authHeaders + ("content-type" to "application/json") + condition
            var response = http.send("PUT", url, headers, body)
            if (response.code == 404 || response.code == 409) {
                val parent = url.newBuilder().removePathSegment(url.pathSize - 1).build()
                val made = http.send("MKCOL", parent, config.authHeaders, null)
                if (made.code != 405 && outcomeOf(made.code) != WebDavOutcome.SUCCESS) {
                    return WebDavResult(outcomeOf(made.code), detail = "MKCOL HTTP ${made.code}")
                }
                response = http.send("PUT", url, headers, body)
            }
            return WebDavResult(outcomeOf(response.code), etag = strongEtag(response.etag), detail = if (response.code >= 400) "HTTP ${response.code}" else null)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return WebDavResult(WebDavOutcome.NETWORK_ERROR)
        }
    }

    private fun versionAddress(config: WebDavConfig, file: String): HttpUrl {
        val address = config.address!!
        return address.newBuilder().encodedPath(address.encodedPath + ".versions/" + file).build()
    }

    companion object {
        const val VERSIONS = 20
        private val VERSION_NAME = Regex("^\\d+-[a-f0-9]{64}\\.json$")
        private val STRONG_ETAG = Regex("^\"[^\"\\r\\n]*\"$")

        fun outcomeOf(status: Int): WebDavOutcome = when (status) {
            in 200..299 -> WebDavOutcome.SUCCESS
            401, 403 -> WebDavOutcome.UNAUTHORIZED
            404, 409 -> WebDavOutcome.NOT_FOUND
            412 -> WebDavOutcome.CONFLICT
            else -> WebDavOutcome.SERVER_ERROR
        }

        /** Only a strong validator protects a write; a weak or malformed one is none. */
        fun strongEtag(value: String?): String? = value?.takeIf(STRONG_ETAG::matches)

        /** The earlier versions a synced document lists, as XTA reads them. */
        fun versionsIn(body: String?): List<WebDavVersion> = body?.let(BackupDocument::parse)?.webDavHistory.orEmpty()

        /** Where a device keeps its baseline for [config]: one per server address and user. */
        fun baselineKey(config: WebDavConfig): String = "webdav-baseline:" + sha256("${config.address}\n${config.username}")

        fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

/** The baselines in one small file of the store, never in a backup. */
class StoredWebDavBaselines(dir: StoreDir) : WebDavBaselines {
    private val file = dir.file("sync/webdav-baselines.json")
    private val mutex = Mutex()

    override suspend fun read(key: String): String? = mutex.withLock { all()[key] }

    override suspend fun write(key: String, etag: String) = mutex.withLock {
        file.write(JsonObject((all() + (key to etag)).mapValues { JsonPrimitive(it.value) }).toString())
    }

    private suspend fun all(): Map<String, String> {
        val root = file.read()?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() } as? JsonObject ?: return emptyMap()
        return root.mapNotNull { (key, value) -> (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { key to it } }.toMap()
    }
}

/** The requests over OkHttp, each given up after 30 seconds. */
class OkHttpWebDav(client: OkHttpClient = OkHttpClient()) : WebDavHttp {
    private val client = client.newBuilder().callTimeout(30, TimeUnit.SECONDS).build()

    override suspend fun send(method: String, url: HttpUrl, headers: Map<String, String>, body: String?): WebDavResponse =
        withContext(Dispatchers.IO) {
            val type = headers.entries.firstOrNull { it.key.equals("content-type", ignoreCase = true) }?.value?.toMediaType()
            val request = Request.Builder().url(url)
                .method(method, body?.toRequestBody(type))
                .apply { headers.forEach { (name, value) -> header(name, value) } }
                .build()
            client.newCall(request).execute().use { response ->
                WebDavResponse(response.code, response.body?.string().orEmpty(), response.header("etag"))
            }
        }
}
