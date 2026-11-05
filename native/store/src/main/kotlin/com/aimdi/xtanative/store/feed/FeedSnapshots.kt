package com.aimdi.xtanative.store.feed

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.store.StoreDir
import com.aimdi.xtanative.store.saved.sha256
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One post a mixed timeline kept: its [id], its place in time ([date], epoch milliseconds) and the record it is drawn from again. */
data class SnapshotPost(val id: String, val date: Long, val snapshot: JsonElement)

/** What one source of a timeline answered last, and when ([at], epoch milliseconds). */
data class FeedSnapshot(val at: Long, val posts: List<SnapshotPost>)

/**
 * The posts each plugin last gave a mixed timeline (XTA's FeedSnapshotCache),
 * kept across launches so they show at once while the plugin is asked again:
 * a week at most, a hundred posts a source, and the newest [MAX_ENTRIES]
 * sources' answers. One file per source and question under `feed-snapshots/`;
 * a snapshot that cannot be read or written is a miss, never a failure of the
 * timeline.
 */
class FeedSnapshots(private val dir: StoreDir, private val clock: () -> Long = System::currentTimeMillis) {
    private val folder = File(dir.root, FOLDER)

    /** What [key] last kept, no older than a week; null when nothing is kept, it is older, or it no longer reads. */
    suspend fun read(key: String): FeedSnapshot? = runCatching {
        val root = J.parse(file(key).read() ?: return null)
        if (root["v"].int != VERSION || root["key"].string != key) return null
        val at = root["at"].long ?: return null
        if (clock() - at > MAX_AGE_MS) return null
        val posts = root["posts"].list.take(MAX_POSTS).mapNotNull { row ->
            val id = row["id"].string ?: return@mapNotNull null
            val date = row["date"].long ?: return@mapNotNull null
            val snapshot = row["snapshot"].raw ?: return@mapNotNull null
            SnapshotPost(id, date, snapshot)
        }
        FeedSnapshot(at, posts)
    }.getOrNull()

    /** Keeps [posts] (their first hundred) for [key], replacing what it kept, then drops the oldest sources past [MAX_ENTRIES]. */
    suspend fun write(key: String, posts: List<SnapshotPost>) {
        val text = buildJsonObject {
            put("v", VERSION)
            put("key", key)
            put("at", clock())
            put(
                "posts",
                JsonArray(
                    posts.take(MAX_POSTS).map { post ->
                        buildJsonObject {
                            put("id", post.id)
                            put("date", post.date)
                            put("snapshot", post.snapshot)
                        }
                    },
                ),
            )
        }.toString()
        runCatching {
            file(key).write(text)
            trim()
        }
    }

    /** Drops every snapshot: after an import the accounts they came from may be gone. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        files().forEach { it.delete() }
    }

    private suspend fun trim() = withContext(Dispatchers.IO) {
        files().sortedByDescending { it.lastModified() }.drop(MAX_ENTRIES).forEach { it.delete() }
    }

    private fun file(key: String) = dir.file("$FOLDER/${sha256(key)}.json")

    private fun files(): List<File> = folder.listFiles()?.filter { it.isFile && it.name.endsWith(".json") }.orEmpty()

    companion object {
        private const val VERSION = 1
        private const val FOLDER = "feed-snapshots"

        /** How many sources' answers are kept, the newest first, as XTA prunes its `feed:` entries. */
        const val MAX_ENTRIES = 40

        /** How many posts of one answer are kept. */
        const val MAX_POSTS = 100

        /** How old a snapshot may be and still show: a week. */
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    }
}
