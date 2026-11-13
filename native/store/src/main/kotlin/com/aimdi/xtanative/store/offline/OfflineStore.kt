package com.aimdi.xtanative.store.offline

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.store.StoreDir
import com.aimdi.xtanative.store.downloads.MediaHttp
import com.aimdi.xtanative.store.saved.sha256
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The offline library: articles and the media of saved posts, kept to read
 * without the network, under `offline/` with a manifest that lists them. A
 * copy is put together in a folder of its own and named in the manifest only
 * once it is whole, and an older copy stays until its replacement is; what a
 * crash leaves half done is cleared on the next load. Only what is verified
 * on disk is shown as kept. Each change runs after the one before it; a
 * removal stops a copy still being fetched.
 */
class OfflineStore(
    private val dir: StoreDir,
    private val http: MediaHttp,
    private val storageLimit: Long = DEFAULT_STORAGE_LIMIT,
    private val imageLimit: Long = 8L * 1024 * 1024,
    private val videoLimit: Long = 50L * 1024 * 1024,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val root = File(dir.root, "offline")
    private val manifest = dir.file("offline/manifest.json")
    private val current = MutableStateFlow(OfflineState())
    private val queue = Mutex()
    private val loadLock = Mutex()
    private val generations = ConcurrentHashMap<String, Int>()

    @Volatile
    private var loaded = false

    val state: StateFlow<OfflineState> = current.asStateFlow()

    /** Reads the library, once; a failed read is tried again by the next call. */
    suspend fun load() = loadLock.withLock { if (!loaded) read() }

    /** Reads the library again from disk, after what is under way. */
    suspend fun refresh() = queue.withLock {
        loadLock.withLock {
            loaded = false
            current.update { it.copy(loading = true) }
            read()
        }
    }

    /** Keeps [article] with its pictures (as many as fit), replacing an earlier copy. */
    suspend fun keepArticle(article: OfflineArticle) =
        keep(article.id, article.title, article.source, articleImageSources(article), article.url, article, false)

    /** Keeps the pictures and videos of a saved post (id `saved:<post id>`), replacing an earlier copy. */
    suspend fun keepMedia(id: String, title: String, source: String, media: List<OfflineMediaSource>, url: String? = null, sensitive: Boolean = false) =
        keep(id, title, source, media, url, null, sensitive)

    /** Removes [id]'s copy; a copy of it still being fetched is stopped and never shown. */
    suspend fun remove(id: String) {
        generations.merge(id, 1, Int::plus)
        current.update { it.copy(busy = it.busy - id, failed = it.failed - id) }
        queue.withLock {
            try {
                load()
                val entry = current.value.entry(id) ?: return@withLock
                check(loaded) { "Offline storage unavailable" }
                deleteFolder(entry)
                val next = current.value.entries.filterNot { it.id == id }
                writeManifest(next)
                current.update { it.copy(entries = next) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                reflectStorageFailure()
            }
        }
    }

    /** Removes every copy, stopping those still being fetched. */
    suspend fun clear() {
        generations.keys.forEach { generations.merge(it, 1, Int::plus) }
        current.update { it.copy(busy = emptySet(), failed = emptySet()) }
        queue.withLock {
            try {
                load()
                check(loaded) { "Offline storage unavailable" }
                current.value.entries.forEach { deleteFolder(it) }
                writeManifest(emptyList())
                cleanOrphans(emptyList())
                current.update { it.copy(entries = emptyList(), storageError = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                reflectStorageFailure()
            }
        }
    }

    /**
     * The kept text of article [id]; or, when that id is not kept, of the kept
     * article at [canonicalUrl] (a link may name an article by another id than
     * the one it was kept under). Null when none is kept whole.
     */
    suspend fun article(id: String, canonicalUrl: String? = null): OfflineArticle? {
        load()
        val entries = current.value.entries
        val canonical = canonicalArticleUrl(canonicalUrl)
        val entry = entries.firstOrNull { it.id == id }
            ?: canonical?.let { url -> entries.firstOrNull { it.hasArticle && canonicalArticleUrl(it.url) == url } }
        if (entry == null || !entry.hasArticle || !loaded) return null
        return withContext(Dispatchers.IO) { readArticle(entry) }
    }

    /** The file of [media], when it is still there whole. */
    suspend fun mediaFile(entry: OfflineEntry, media: OfflineFile): File? {
        load()
        if (!loaded || !safe(entry) || media !in entry.files) return null
        val file = File(File(root, entry.directory), media.name)
        return withContext(Dispatchers.IO) { file.takeIf { it.isFile && it.length() == media.bytes } }
    }

    /** The kept pictures of article [id], by the address they were kept from; empty when it is not kept. */
    suspend fun articleImages(id: String): Map<String, File> {
        load()
        val entry = current.value.entry(id)?.takeIf { it.hasArticle } ?: return emptyMap()
        return entry.files.mapNotNull { media -> mediaFile(entry, media)?.let { media.url to it } }.toMap()
    }

    /** [sanitizedDocument] of article [id] made to read offline (see [renderOffline]); as it is when the article is not kept. */
    suspend fun renderArticle(id: String, sanitizedDocument: String): String {
        load()
        val entry = current.value.entry(id)?.takeIf { it.hasArticle } ?: return sanitizedDocument
        val kept = articleImages(id)
        return withContext(Dispatchers.IO) { renderOffline(sanitizedDocument, entry.url, kept) }
    }

    private suspend fun keep(
        id: String,
        title: String,
        source: String,
        media: List<OfflineMediaSource>,
        url: String?,
        article: OfflineArticle?,
        sensitive: Boolean,
    ) {
        val generation = generations.merge(id, 1, Int::plus)!!
        current.update { it.copy(busy = it.busy + id, failed = it.failed - id) }
        queue.withLock {
            var staging: File? = null
            try {
                load()
                val state = current.value
                if (state.storageError || !loaded) throw IOException("Offline storage unavailable")
                if (cancelled(id, generation)) return@withLock
                if ((state.entries.size >= MAX_ITEMS && state.entry(id) == null) || media.size > MAX_TOTAL_MEDIA) throw IOException("Offline library full")
                val name = "${sha256(id)}-${micros()}"
                staging = File(root, "$name.tmp").also { withContext(Dispatchers.IO) { if (!it.mkdir()) throw IOException("No staging folder") } }
                val available = storageLimit - state.bytes
                val body = article?.toJson()?.toString()?.toByteArray() ?: ByteArray(0)
                if (body.size > MAX_ARTICLE_BYTES || body.size > available || (article != null && article.bodyHtml.isBlank())) {
                    throw IOException("Article unavailable or too large")
                }
                if (article != null) withContext(Dispatchers.IO) { writeSynced(File(staging, ARTICLE_FILE), body) }
                val budget = if (article == null) available else minOf(available - body.size, MAX_ARTICLE_IMAGE_BYTES)
                val files = retainFiles(staging, media, budget) { cancelled(id, generation) }
                if (cancelled(id, generation)) return@withLock
                if (article == null && files.isEmpty()) throw IOException("No offline media retained")
                val kind = if (article == null) OfflineKind.MEDIA else OfflineKind.ARTICLE
                commit(staging, OfflineEntry(id, name, title, source, kind, media.size, files, clock(), url, body.size.toLong(), sensitive))
                staging = null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (!cancelled(id, generation)) current.update { it.copy(failed = it.failed + id) }
            } finally {
                staging?.let { withContext(NonCancellable + Dispatchers.IO) { it.deleteRecursively() } }
                if (generations[id] == generation) current.update { it.copy(busy = it.busy - id) }
            }
        }
    }

    private fun cancelled(id: String, generation: Int) = generations[id] != generation

    private suspend fun retainFiles(staging: File, media: List<OfflineMediaSource>, budget: Long, cancelled: () -> Boolean): List<OfflineFile> {
        var left = budget
        val files = mutableListOf<OfflineFile>()
        for ((i, source) in media.take(MAX_MEDIA).withIndex()) {
            if (cancelled() || left <= 0) break
            try {
                val limit = minOf(left, if (source.video) videoLimit else imageLimit)
                val file = retainOfflineMedia(http, source, File(staging, "media-$i"), limit, cancelled)
                files += file
                left -= file.bytes
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A picture that could not be kept leaves the copy partial, as the manifest says.
            }
        }
        return files
    }

    /** Names the whole copy in the manifest; its folder goes again if that fails, and an older copy goes once it succeeds. */
    private suspend fun commit(staging: File, entry: OfflineEntry) {
        val folder = File(root, entry.directory)
        withContext(Dispatchers.IO) { if (!staging.renameTo(folder)) throw IOException("Could not place the offline copy") }
        val old = current.value.entry(entry.id)
        val next = listOf(entry) + current.value.entries.filterNot { it.id == entry.id }
        try {
            writeManifest(next)
        } catch (e: Exception) {
            withContext(NonCancellable + Dispatchers.IO) { folder.deleteRecursively() }
            throw e
        }
        current.update { it.copy(entries = next, failed = it.failed - entry.id) }
        old?.let { deleteFolder(it) }
    }

    private suspend fun read() {
        try {
            withContext(Dispatchers.IO) { root.mkdirs() }
            val entries = manifest.read()?.let { parseManifest(it) }.orEmpty()
            withContext(Dispatchers.IO) { cleanOrphans(entries) }
            loaded = true
            current.update { it.copy(entries = entries, loading = false, storageError = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            current.update { it.copy(loading = false, storageError = true) }
        }
    }

    /** The manifest's entries that are safe to follow, each verified on disk; a damaged row hides no other. */
    private suspend fun parseManifest(text: String): List<OfflineEntry> {
        if (text.toByteArray().size > MAX_MANIFEST_BYTES) throw IOException("Oversized manifest")
        val root = J.parse(text)
        if (root["version"].int != 1 || root["entries"].raw !is JsonArray) throw IOException("Invalid manifest")
        val rows = root["entries"].list
        if (rows.size > MAX_ITEMS) throw IOException("Too many entries")
        val entries = rows.mapNotNull(OfflineEntry::fromJson).filter(::safe).distinctBy { it.id }
        return withContext(Dispatchers.IO) { entries.map(::verify) }
    }

    /** An entry whose folder and file names cannot point outside the library, and whose sizes are within the limits. */
    private fun safe(entry: OfflineEntry): Boolean =
        entry.directory.matches(Regex("^[a-f0-9]{64}-[0-9]+$")) &&
            entry.id.isNotEmpty() && entry.id.length < 8192 &&
            entry.files.size <= MAX_MEDIA &&
            entry.totalMedia >= entry.files.size && entry.totalMedia <= MAX_TOTAL_MEDIA &&
            entry.articleBytes in 0..MAX_ARTICLE_BYTES &&
            entry.files.all { it.name.matches(Regex("^media-[0-9]+$")) && it.bytes > 0 && it.bytes <= maxOf(videoLimit, imageLimit) }

    /** [entry] as found on disk: its text only when whole and its own, its files only those of the size listed. */
    private fun verify(entry: OfflineEntry): OfflineEntry {
        val folder = File(root, entry.directory)
        val articleBytes = if (entry.articleBytes > 0 && readArticle(entry) != null) entry.articleBytes else 0
        val files = entry.files.filter { File(folder, it.name).let { file -> file.isFile && file.length() == it.bytes } }
        return entry.verified(articleBytes, files)
    }

    private fun readArticle(entry: OfflineEntry): OfflineArticle? = runCatching {
        val file = File(File(root, entry.directory), ARTICLE_FILE)
        if (!file.isFile || file.length() != entry.articleBytes) return@runCatching null
        OfflineArticle.fromJson(J.parse(file.readText()))?.takeIf { it.id == entry.id && it.bodyHtml.isNotBlank() }
    }.getOrNull()

    /** Folders no entry names and staging left by a crash; the manifest's own file is left alone. */
    private fun cleanOrphans(entries: List<OfflineEntry>) {
        val kept = entries.mapTo(HashSet()) { it.directory }
        root.listFiles()?.forEach { item ->
            when {
                item.isDirectory && item.name !in kept -> item.deleteRecursively()
                item.isFile && item.name.endsWith(".tmp") -> item.delete()
            }
        }
    }

    private suspend fun writeManifest(entries: List<OfflineEntry>) {
        val text = buildJsonObject {
            put("version", 1)
            put("entries", JsonArray(entries.map { it.toJson() }))
        }.toString()
        if (text.toByteArray().size > MAX_MANIFEST_BYTES) throw IOException("Offline index full")
        manifest.write(text)
    }

    private suspend fun deleteFolder(entry: OfflineEntry) = withContext(Dispatchers.IO) { File(root, entry.directory).deleteRecursively() }

    /** After a failed write: the entries as they really are on disk, and the error shown. */
    private suspend fun reflectStorageFailure() {
        val actual = withContext(NonCancellable + Dispatchers.IO) {
            current.value.entries.map { runCatching { verify(it) }.getOrDefault(it.verified(0, emptyList())) }
        }
        current.update { it.copy(entries = actual, storageError = true) }
    }

    private fun writeSynced(file: File, bytes: ByteArray) = FileOutputStream(file).use {
        it.write(bytes)
        it.fd.sync()
    }

    private fun micros(): Long = Instant.now().let { it.epochSecond * 1_000_000 + it.nano / 1_000 }

    companion object {
        const val MAX_ITEMS = 200
        const val MAX_MEDIA = 64
        const val MAX_TOTAL_MEDIA = 10_000
        const val MAX_ARTICLE_BYTES = 2L * 1024 * 1024
        const val MAX_ARTICLE_IMAGE_BYTES = 24L * 1024 * 1024
        const val MAX_MANIFEST_BYTES = 1024 * 1024
        const val DEFAULT_STORAGE_LIMIT = 500L * 1024 * 1024
        private const val ARTICLE_FILE = "article.json"
    }
}
