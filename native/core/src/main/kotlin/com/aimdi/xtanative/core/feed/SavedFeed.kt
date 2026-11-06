package com.aimdi.xtanative.core.feed

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.model.XCodec
import com.aimdi.xtanative.core.x.model.XThread
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A feed as it was left: the posts shown and, when it can read on from them,
 * where each chunk's reading stood. Written to disk so a cold start shows the
 * same posts and loads more below them without a hole. [savedAt] (epoch
 * milliseconds) is when they were read: how old they are when a load fails,
 * and whether the feed has posts newer than its last read mark. [key] is the
 * plan's [GroupFeedPlan.readKey] the state belongs to: a feed that reads
 * otherwise does not read on from it.
 */
data class SavedFeed(val threads: List<XThread>, val state: FeedState?, val savedAt: Long? = null, val key: String? = null) {
    companion object {
        private const val VERSION = 1

        fun encode(saved: SavedFeed): String = buildJsonObject {
            put("v", VERSION)
            saved.savedAt?.let { put("savedAt", it) }
            saved.key?.let { put("key", it) }
            put("threads", JsonArray(saved.threads.map(XCodec::threadJson)))
            saved.state?.let { state ->
                put(
                    "chunks",
                    JsonArray(
                        state.chunks.map { chunk ->
                            buildJsonObject {
                                put("query", chunk.query)
                                chunk.cursor?.let { put("cursor", it) }
                                put("exhausted", chunk.exhausted)
                                chunk.oldest?.let { put("oldest", it) }
                                put("unread", JsonArray(chunk.unread.map(XCodec::threadJson)))
                                if (chunk.failed) put("failed", true)
                            }
                        },
                    ),
                )
            }
        }.toString()

        /** The feed in [text]; null when it is unreadable or of another version. */
        fun decode(text: String): SavedFeed? {
            val root = J.parse(text)
            if (root["v"].int != VERSION) return null
            val chunks = root["chunks"]
            val state = if (!chunks.exists) {
                null
            } else {
                FeedState(
                    chunks.list.mapNotNull { chunk ->
                        ChunkState(
                            query = chunk["query"].string ?: return@mapNotNull null,
                            cursor = chunk["cursor"].string,
                            exhausted = chunk["exhausted"].boolean ?: true,
                            oldest = chunk["oldest"].long,
                            unread = chunk["unread"].list.mapNotNull(XCodec::threadOf),
                            failed = chunk["failed"].boolean ?: false,
                        )
                    },
                )
            }
            return SavedFeed(root["threads"].list.mapNotNull(XCodec::threadOf), state, root["savedAt"].long, root["key"].string)
        }
    }
}

/** Where the reader was: the post at the top of the screen and how far it was scrolled past. */
data class FeedPlace(val postId: String, val offset: Int) {
    companion object {
        fun encode(place: FeedPlace): String = buildJsonObject {
            put("post", place.postId)
            put("offset", place.offset)
        }.toString()

        fun decode(text: String): FeedPlace? {
            val root = J.parse(text)
            return FeedPlace(root["post"].string ?: return null, root["offset"].int ?: 0)
        }
    }
}
