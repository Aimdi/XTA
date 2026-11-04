package com.aimdi.xtanative.networks.substack

import com.aimdi.xtanative.core.json.J
import java.security.MessageDigest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Files of the plugin's own folder, by name ("articles/ab12.json"). */
interface SubstackFiles {
    suspend fun read(name: String): String?

    suspend fun write(name: String, text: String)

    suspend fun delete(name: String)
}

/**
 * Public article bodies kept on the device after a first open, as XTA's
 * SubstackArticleCache: never a source of truth, so a miss only means the
 * network is asked. At most [maxArticles] and [maxBytes]; opening an article
 * writes it again, so the least recently read go first, and none is used
 * after [ttlMillis].
 */
class SubstackArticleCache(
    private val files: SubstackFiles,
    private val maxArticles: Int = 200,
    private val maxBytes: Long = 16L shl 20,
    private val ttlMillis: Long = 14L * 24 * 60 * 60 * 1000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** One article in the cache's index: its key, its size, and when it was kept (epoch milliseconds). */
    data class Entry(val key: String, val bytes: Long, val savedAt: Long)

    private val lock = Mutex()

    /** The kept body of [publication]'s post [slug], within its time; null when there is none. */
    suspend fun get(publication: SubstackPublication, slug: String): SubstackPost? = lock.withLock {
        val key = keyOf(publication, slug)
        val entry = index().firstOrNull { it.key == key } ?: return@withLock null
        if (clock() - entry.savedAt > ttlMillis) return@withLock null
        val json = J.parse(files.read(fileOf(key)).orEmpty())
        val post = SubstackPostJson.decode(json).copy(
            bodyHtml = json["body_html"].string,
            publicationBaseUrl = publication.baseUrl,
            publicationName = publication.name.ifEmpty { json["publicationName"].string.orEmpty() },
        )
        post.takeIf { it.slug == slug.trim() && !it.bodyHtml.isNullOrBlank() }
    }

    /** Keeps [post]'s body, as the newest entry; nothing for a post without one. */
    suspend fun put(post: SubstackPost) = lock.withLock {
        val body = post.bodyHtml?.trim()
        if (body.isNullOrEmpty()) return@withLock
        val key = keyOf(post.publication, post.slug)
        val text = JsonObject(SubstackPostJson.encode(post) + ("body_html" to JsonPrimitive(body))).toString()
        files.write(fileOf(key), text)
        val entries = index().filterNot { it.key == key } + Entry(key, text.length.toLong(), clock())
        val kept = keep(entries, maxArticles, maxBytes)
        entries.filterNot { it in kept }.forEach { files.delete(fileOf(it.key)) }
        files.write(INDEX, encode(kept))
    }

    private suspend fun index(): List<Entry> = J.parse(files.read(INDEX).orEmpty()).list.mapNotNull { row ->
        Entry(row["key"].string ?: return@mapNotNull null, row["bytes"].long ?: 0, row["savedAt"].long ?: 0)
    }

    private fun encode(entries: List<Entry>): String = JsonArray(
        entries.map { buildJsonObject { put("key", it.key); put("bytes", it.bytes); put("savedAt", it.savedAt) } },
    ).toString()

    companion object {
        private const val INDEX = "articles/index.json"

        /** The publication's origin and the slug, as XTA keys a kept article. */
        fun keyOf(publication: SubstackPublication, slug: String): String = "${SubstackHosts.originOf(publication.baseUrl)}::${slug.trim()}"

        private fun fileOf(key: String): String =
            "articles/" + MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).take(12).joinToString("") { "%02x".format(it) } + ".json"

        /** The newest entries of [index], oldest first, that fit both limits; the newest stays whatever its size: it is the one just read. */
        fun keep(index: List<Entry>, articles: Int, bytes: Long): List<Entry> {
            var total = 0L
            var start = index.size
            while (start > 0 && index.size - start < articles) {
                total += index[start - 1].bytes
                if (total > bytes && start < index.size) break
                start--
            }
            return index.subList(start, index.size).toList()
        }

        /**
         * Whether [cached] still is the post [listed] was opened as: another
         * date or title means it was edited or moved. A side that does not
         * know a field, like a shared link, does not count.
         */
        fun isCurrent(cached: SubstackPost, listed: SubstackPost): Boolean {
            fun same(a: String?, b: String?): Boolean {
                val left = a?.trim().orEmpty()
                val right = b?.trim().orEmpty()
                return left.isEmpty() || right.isEmpty() || left == right
            }
            return same(cached.postDate, listed.postDate) && same(cached.title, listed.title)
        }
    }
}
