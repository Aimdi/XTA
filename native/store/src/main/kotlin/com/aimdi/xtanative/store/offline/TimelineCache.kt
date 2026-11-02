package com.aimdi.xtanative.store.offline

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.model.XCodec
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.store.StoreDir
import com.aimdi.xtanative.store.saved.sha256
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The first page of a thread or a profile timeline as last read, with the
 * cursors that page on from it: without them a page shown from the cache
 * could not be read past, and a conversation would end where it was cut.
 */
data class CachedTimeline(
    val threads: List<XThread>,
    val bottomCursor: String? = null,
    val topCursor: String? = null,
    /** X's "show additional replies" cursor. */
    val showMoreCursor: String? = null,
)

/**
 * Last-known first pages of threads and profile timelines, so opening the
 * same post twice costs one request, and offline or while every account is
 * rate limited something read before shows instead of an error. Only the
 * first page is kept: every visit starts there, and it bounds the cache.
 * One file per page under `timelines/`; a cache that cannot be read or
 * written is a miss, never a failure of the timeline.
 */
class TimelineCache(private val dir: StoreDir, private val clock: () -> Long = System::currentTimeMillis) {
    private val folder = File(dir.root, FOLDER)

    /** The page of [key] no older than [maxAgeMs]; null when none is kept, it is older, or it no longer reads. */
    suspend fun read(key: String, maxAgeMs: Long): CachedTimeline? {
        val (at, page) = entry(key) ?: return null
        return page.takeIf { clock() - at <= maxAgeMs }
    }

    /** The page of [key] however old: for when the network gives nothing. */
    suspend fun readStale(key: String): CachedTimeline? = entry(key)?.second

    /** Keeps [page] for [key], replacing the one before; an empty page never replaces what is kept. */
    suspend fun write(key: String, page: CachedTimeline) {
        if (page.threads.isEmpty()) return
        val text = buildJsonObject {
            put("v", VERSION)
            put("key", key)
            put("at", clock())
            put("threads", J.parse(XCodec.encode(page.threads)).raw ?: JsonNull)
            page.bottomCursor?.let { put("bottom", it) }
            page.topCursor?.let { put("top", it) }
            page.showMoreCursor?.let { put("showMore", it) }
        }.toString()
        runCatching { file(key).write(text) }
    }

    /** Drops [key], so the next read misses and the network is asked again. */
    suspend fun remove(key: String) {
        runCatching { file(key).delete() }
    }

    /** Drops every page. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        pages().forEach { it.delete() }
    }

    /**
     * Drops pages older than [maxAgeMs], then all but the newest [keep]: a
     * page is written for every thread opened and every profile visited, and
     * none is read again once the reader has moved on.
     */
    suspend fun trim(keep: Int = MAX_ENTRIES, maxAgeMs: Long = MAX_AGE_MS) = withContext(Dispatchers.IO) {
        val now = clock()
        val (fresh, old) = pages().partition { now - it.lastModified() <= maxAgeMs }
        (old + fresh.sortedByDescending { it.lastModified() }.drop(keep)).forEach { it.delete() }
    }

    private suspend fun entry(key: String): Pair<Long, CachedTimeline>? = runCatching {
        val root = J.parse(file(key).read() ?: return null)
        if (root["v"].int != VERSION || root["key"].string != key) return null
        val threads = XCodec.decode(root["threads"].raw.toString())
        if (threads.isEmpty()) return null
        (root["at"].long ?: 0L) to CachedTimeline(threads, root["bottom"].string, root["top"].string, root["showMore"].string)
    }.getOrNull()

    private fun file(key: String) = dir.file("$FOLDER/${sha256(key)}.json")

    private fun pages(): List<File> = folder.listFiles()?.filter { it.isFile && it.name.endsWith(".json") }.orEmpty()

    companion object {
        private const val VERSION = 1
        private const val FOLDER = "timelines"

        /** How many pages survive [trim], newest first. */
        const val MAX_ENTRIES = 300

        /** How old a page [trim] keeps: a week. */
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

        fun threadKey(postId: String): String = "thread:$postId"

        /** A profile's timeline of [type] (posts, media, …), with or without replies: never shared between the two. */
        fun profileKey(userId: String, type: String, includeReplies: Boolean): String =
            "profile:$userId:$type:${if (includeReplies) "replies" else "noreplies"}"
    }
}
