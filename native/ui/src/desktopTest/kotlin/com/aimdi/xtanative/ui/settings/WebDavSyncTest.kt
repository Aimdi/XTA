package com.aimdi.xtanative.ui.settings

import com.aimdi.xtanative.store.StoreDir
import java.io.IOException
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl

/** A WebDAV server in memory: documents by path, each with a strong ETag, and the conditional writes a real one honours. */
private class FakeDav(var etags: Boolean = true, var reachable: Boolean = true) : WebDavHttp {
    val files = HashMap<String, String>()
    val folders = HashSet<String>()
    val requests = ArrayList<String>()
    private val revisions = HashMap<String, Int>()
    private var revision = 0

    override suspend fun send(method: String, url: HttpUrl, headers: Map<String, String>, body: String?): WebDavResponse {
        if (!reachable) throw IOException("offline")
        requests += "$method ${url.encodedPath}"
        if (headers["authorization"] != "Basic dXNlcjpwYXNz") return WebDavResponse(401)
        val path = url.encodedPath
        return when (method) {
            "GET" -> files[path]?.let { WebDavResponse(200, it, tag(path)) } ?: WebDavResponse(404)
            "PUT" -> put(path, headers, body.orEmpty())
            "MKCOL" -> WebDavResponse(if (folders.add(path)) 201 else 405)
            "DELETE" -> WebDavResponse(if (files.remove(path) != null) 204 else 404)
            else -> WebDavResponse(405)
        }
    }

    private fun put(path: String, headers: Map<String, String>, body: String): WebDavResponse {
        val parent = path.substringBeforeLast('/')
        if (parent.endsWith(".versions") && parent !in folders) return WebDavResponse(409)
        headers["if-none-match"]?.let { if (path in files) return WebDavResponse(412) }
        headers["if-match"]?.let { if (tag(path) != it) return WebDavResponse(412) }
        files[path] = body
        revisions[path] = ++revision
        return WebDavResponse(201, etag = tag(path))
    }

    private fun tag(path: String): String? = if (etags && path in files) "\"${revisions[path]}\"" else null
}

private class MemoryBaselines : WebDavBaselines {
    val kept = HashMap<String, String>()
    override suspend fun read(key: String) = kept[key]
    override suspend fun write(key: String, etag: String) {
        kept[key] = etag
    }
}

class WebDavSyncTest {
    private val config = WebDavConfig("https://dav.example/xta/backup.json", "user", "pass")
    private val now = Instant.parse("2026-11-03T10:00:00.123456Z")

    @Test
    fun onlyACompleteHttpsAddressIsTried(): Unit = runBlocking {
        val sync = WebDavSync(FakeDav(), MemoryBaselines()) { now }
        assertEquals(WebDavOutcome.NOT_CONFIGURED, sync.upload(config.copy(password = ""), "{}").outcome)
        assertEquals(WebDavOutcome.INSECURE_URL, sync.upload(config.copy(url = "http://dav.example/b.json"), "{}").outcome)
        assertEquals(WebDavOutcome.UNAUTHORIZED, sync.download(config.copy(password = "wrong")).outcome)
        assertEquals(WebDavOutcome.NOT_FOUND, sync.download(config).outcome)
    }

    @Test
    fun theFirstUploadCreatesTheDocumentAndTheNextKeepsTheOneItReplaces(): Unit = runBlocking {
        val server = FakeDav()
        val sync = WebDavSync(server, MemoryBaselines()) { now }
        assertEquals(WebDavOutcome.SUCCESS, sync.upload(config, """{"subscriptions":[]}""").outcome)
        val first = server.files["/xta/backup.json"]!!
        assertTrue(first.contains("\"_xtaWebDavHistory\":[]"))

        assertEquals(WebDavOutcome.SUCCESS, sync.upload(config, """{"subscriptions":[1]}""").outcome)
        val versions = WebDavSync.versionsIn(server.files["/xta/backup.json"])
        assertEquals(1, versions.size)
        assertEquals("2026-11-03T10:00:00.123Z", versions.single().at)
        assertTrue(versions.single().file.startsWith("${now.epochSecond}123456-"))
        assertEquals(first, server.files["/xta/backup.json.versions/" + versions.single().file])
        assertTrue("MKCOL /xta/backup.json.versions" in server.requests)

        val version = sync.downloadVersion(config, versions.single())
        assertEquals(first, version.body)
    }

    @Test
    fun aDocumentChangedElsewhereIsNeverOverwritten(): Unit = runBlocking {
        val server = FakeDav()
        val mine = WebDavSync(server, MemoryBaselines()) { now }
        val theirs = WebDavSync(server, MemoryBaselines()) { now }
        assertEquals(WebDavOutcome.SUCCESS, mine.upload(config, "{}").outcome)
        assertEquals(WebDavOutcome.CONFLICT, theirs.upload(config, """{"other":true}""").outcome)
        // Restoring it first is what lets the other device upload.
        theirs.acknowledge(config, theirs.download(config))
        assertEquals(WebDavOutcome.SUCCESS, theirs.upload(config, """{"other":true}""").outcome)
        assertEquals(WebDavOutcome.CONFLICT, mine.upload(config, "{}").outcome)
    }

    @Test
    fun aServerWithoutValidatorsIsNotTrusted(): Unit = runBlocking {
        val server = FakeDav(etags = false)
        val sync = WebDavSync(server, MemoryBaselines()) { now }
        assertEquals(WebDavOutcome.UNSAFE_SERVER, sync.upload(config, "{}").outcome)
    }

    @Test
    fun noAnswerIsANetworkError(): Unit = runBlocking {
        val sync = WebDavSync(FakeDav(reachable = false), MemoryBaselines()) { now }
        assertEquals(WebDavOutcome.NETWORK_ERROR, sync.download(config).outcome)
        assertEquals(WebDavOutcome.NETWORK_ERROR, sync.upload(config, "{}").outcome)
    }

    @Test
    fun onlyStrongValidatorsCount() {
        assertEquals("\"abc\"", WebDavSync.strongEtag("\"abc\""))
        assertNull(WebDavSync.strongEtag("W/\"abc\""))
        assertEquals(WebDavOutcome.CONFLICT, WebDavSync.outcomeOf(412))
        assertEquals(WebDavOutcome.NOT_FOUND, WebDavSync.outcomeOf(409))
    }

    @Test
    fun baselinesAreKeptOnTheDevice(): Unit = runBlocking {
        val dir = Files.createTempDirectory("dav").toFile()
        try {
            StoredWebDavBaselines(StoreDir(dir)).write("k", "\"1\"")
            assertEquals("\"1\"", StoredWebDavBaselines(StoreDir(dir)).read("k"))
            assertNull(StoredWebDavBaselines(StoreDir(dir)).read("other"))
        } finally {
            dir.deleteRecursively()
        }
    }
}
