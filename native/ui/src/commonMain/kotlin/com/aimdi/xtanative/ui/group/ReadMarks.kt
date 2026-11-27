package com.aimdi.xtanative.ui.group

import com.aimdi.xtanative.core.feed.FeedPlace
import com.aimdi.xtanative.core.feed.FeedOrder
import com.aimdi.xtanative.core.feed.ReadPosition
import com.aimdi.xtanative.core.feed.SavedFeed
import com.aimdi.xtanative.core.feed.Unread
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.data.FeedCache
import java.time.Instant
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A group feed's read mark (feed_read_position.dart, group_unread_store.dart):
 * the last post the reader is known to have read there, and the newest post
 * the feed has read from X, whose times light the group's unread dot.
 *
 * Both sit in a feed cache of their own beside the feed's ([cache], made for
 * `read-<group id>`): the last read post as its one thread, the newest post
 * read as its place. A new import clears them with the feeds they marked.
 * Each write is told on [changes], so the unread counts are read again.
 */
class ReadMarks(private val cache: FeedCache) {
    /** The last read post, or null when the feed was never read here. */
    suspend fun position(): ReadPosition? = cache.read()?.threads?.firstOrNull()?.let(ReadPosition::of)

    suspend fun record(thread: XThread, at: Long) {
        cache.write(SavedFeed(listOf(thread), null, savedAt = at))
        changed.tryEmit(Unit)
    }

    /**
     * The newest post the feed read from X: what an unread dot compares the
     * read mark with. Its first post's id, which X gives in time order (a
     * thread's own id is its root's).
     */
    suspend fun noteNewest(thread: XThread) {
        cache.writePlace(FeedPlace(thread.posts.firstOrNull()?.id ?: thread.id, 0))
        changed.tryEmit(Unit)
    }

    /** Whether the feed read something newer than its last read post, for a feed that [tracks] its position. */
    suspend fun unread(tracks: Boolean): Boolean {
        if (!tracks) return false
        val newest = cache.readPlace()?.postId?.let(::timeOfId)
        return Unread.has(tracks, newest, lastReadAt())
    }

    /**
     * How many of [threads] (what the feed keeps) came after the last read
     * post: none while the feed is not [unread], one at least while it is,
     * though what it read newer is not kept.
     */
    suspend fun unreadCount(tracks: Boolean, threads: List<XThread>): Int {
        if (!unread(tracks)) return 0
        return newerThan(threads, lastReadAt()).coerceAtLeast(1)
    }

    /**
     * Everything the feed read from X counts as read: its newest post becomes
     * the last read one. False when it never read anything.
     */
    suspend fun markNewestRead(at: Long): Boolean {
        val newest = cache.readPlace()?.postId ?: return false
        val time = timeOfId(newest)?.let(Instant::ofEpochMilli)
        record(marked(ReadPosition(newest, time)), at)
        return true
    }

    private suspend fun lastReadAt(): Long? = cache.read()?.threads?.firstOrNull()?.let(::readAt)

    companion object {
        private val changed = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        /** A read mark written, of any feed. */
        val changes: SharedFlow<Unit> = changed.asSharedFlow()

        /** The most a count says; past it, "99+". */
        const val MOST_SHOWN = 99

        /** [count] as a badge writes it: the number, or "99+" past [MOST_SHOWN]. */
        fun label(count: Int): String = if (count > MOST_SHOWN) "$MOST_SHOWN+" else count.toString()

        /** How many of [threads] were written after [lastReadAt], each once; all of them for a feed never read. */
        fun newerThan(threads: List<XThread>, lastReadAt: Long?): Int =
            threads.distinctBy { it.id }.count { thread -> lastReadAt == null || (readAt(thread) ?: 0) > lastReadAt }

        /**
         * When a thread's newest post came, as the newest read is compared:
         * its first post's snowflake, to the millisecond, else when it says
         * it was written.
         */
        fun readAt(thread: XThread): Long? = thread.posts.firstOrNull()?.id?.let(::timeOfId) ?: timeOf(thread)

        /** A thread standing for [position] alone, to [record] where no post of it is at hand. */
        fun marked(position: ReadPosition): XThread =
            XThread(position.threadId, listOf(XPost(id = position.threadId, author = XUser("", "", ""), createdAt = position.createdAt, text = "")))

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
