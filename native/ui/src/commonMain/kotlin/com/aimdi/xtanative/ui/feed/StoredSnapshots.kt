package com.aimdi.xtanative.ui.feed

import com.aimdi.xtanative.store.feed.FeedSnapshots
import com.aimdi.xtanative.store.feed.SnapshotPost
import com.aimdi.xtanative.ui.plugins.NetworkPost
import java.time.Instant

/**
 * Each plugin's last answer to a timeline, kept in the device's [store] as
 * the rows it showed ([NetworkPostCodec]): one copy per plugin and question,
 * so a group and the Following feed asking the same plugin different things
 * keep apart.
 */
class StoredSnapshots(private val store: FeedSnapshots) : SourceSnapshots {
    override suspend fun read(source: String, ask: SourceAsk): SavedAnswer? {
        val kept = store.read(keyOf(source, ask)) ?: return null
        val posts = kept.posts.mapNotNull { row ->
            NetworkPostCodec.decode(row.snapshot)?.let { post -> if (post.createdAt == null) post.copy(createdAt = Instant.ofEpochMilli(row.date)) else post }
        }
        return SavedAnswer(kept.at, posts)
    }

    override suspend fun write(source: String, ask: SourceAsk, posts: List<NetworkPost>) {
        val rows = posts.mapNotNull { post ->
            val at = post.createdAt ?: return@mapNotNull null
            SnapshotPost(post.id, at.toEpochMilli(), NetworkPostCodec.encode(post))
        }
        store.write(keyOf(source, ask), rows)
    }

    companion object {
        /** What tells one plugin's question apart (XTA's key of a source and its ids): its accounts, whether its follows are read, and the reply and repost settings. */
        fun keyOf(source: String, ask: SourceAsk): String =
            "feed:$source:${ask.members.sorted().joinToString(",")}:${ask.followed}:${ask.kinds.replies}:${ask.kinds.reposts}"
    }
}
