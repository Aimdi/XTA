package com.aimdi.xtanative.ui.group

import com.aimdi.xtanative.core.feed.FeedPlace
import com.aimdi.xtanative.core.feed.FeedOrder
import com.aimdi.xtanative.core.feed.ReadPosition
import com.aimdi.xtanative.core.feed.SavedFeed
import com.aimdi.xtanative.core.feed.Unread
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.data.FeedCache

/**
 * A group feed's read mark (feed_read_position.dart, group_unread_store.dart):
 * the last post the reader is known to have read there, and the newest post
 * the feed has read from X, whose times light the group's unread dot.
 *
 * Both sit in a feed cache of their own beside the feed's ([cache], made for
 * `read-<group id>`): the last read post as its one thread, the newest post
 * read as its place. A new import clears them with the feeds they marked.
 */
class ReadMarks(private val cache: FeedCache) {
    /** The last read post, or null when the feed was never read here. */
    suspend fun position(): ReadPosition? = cache.read()?.threads?.firstOrNull()?.let(ReadPosition::of)

    suspend fun record(thread: XThread, at: Long) {
        cache.write(SavedFeed(listOf(thread), null, savedAt = at))
    }

    /**
     * The newest post the feed read from X: what an unread dot compares the
     * read mark with. Its first post's id, which X gives in time order (a
     * thread's own id is its root's).
     */
    suspend fun noteNewest(thread: XThread) {
        cache.writePlace(FeedPlace(thread.posts.firstOrNull()?.id ?: thread.id, 0))
    }

    /** Whether the feed read something newer than its last read post, for a feed that [tracks] its position. */
    suspend fun unread(tracks: Boolean): Boolean {
        if (!tracks) return false
        val newest = cache.readPlace()?.postId?.let(::timeOfId)
        val lastRead = cache.read()?.threads?.firstOrNull()?.let(::timeOf)
        return Unread.has(tracks, newest, lastRead)
    }

    companion object {
        /** The cache key of [groupId]'s marks. */
        fun key(groupId: String): String = "read-$groupId"

        /** When a post was written, from its first post's time, else from its id. */
        fun timeOf(thread: XThread): Long? =
            thread.posts.firstOrNull()?.createdAt?.toEpochMilli() ?: timeOfId(FeedOrder.timeOf(thread).toString())

        /** When an X id was made: its snowflake's time, or null for one that is no snowflake. */
        fun timeOfId(id: String): Long? = id.toLongOrNull()?.takeIf { it > 0 }?.let { (it shr 22) + TWITTER_EPOCH }

        private const val TWITTER_EPOCH = 1_288_834_974_657L
    }
}
