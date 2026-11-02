package com.aimdi.xtanative.core.feed

import com.aimdi.xtanative.core.x.XClient
import com.aimdi.xtanative.core.x.XNoAccountException
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XTimelinePage
import com.aimdi.xtanative.core.x.signing.SigningUnavailableException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/** An account in the Following feed, oldest follow first as XTA orders them. */
data class FeedMember(val id: String, val screenName: String, val followedAt: Long = 0)

/** The two reads the feed makes. */
interface FeedReads {
    suspend fun search(query: String, cursor: String?): XTimelinePage
    suspend fun userPosts(userId: String): XTimelinePage
}

/** The feed's reads over X. */
fun XClient.feedReads(): FeedReads {
    val client = this
    return object : FeedReads {
        override suspend fun search(query: String, cursor: String?) = client.search(query, cursor)
        override suspend fun userPosts(userId: String) = client.userPosts(userId)
    }
}

/**
 * What a load adds to the feed. [failures] are the chunks that brought
 * nothing; [hasMore] is false once every chunk has run out.
 */
data class FeedPage(val threads: List<XThread>, val failures: List<Throwable>, val hasMore: Boolean)

/**
 * The posts newer than the top of a shown feed. [bridged] says every chunk
 * was read down to that top (or ran out, or failed): false means more were
 * written than were read, and the posts above the top have a hole in them.
 */
data class NewerPage(val threads: List<XThread>, val bridged: Boolean, val failures: List<Throwable>)

/** Where one chunk's reading stands, so a later launch can read on from it. */
data class ChunkState(val query: String, val cursor: String?, val exhausted: Boolean, val oldest: Long?, val unread: List<XThread>)

/** Where a whole feed's reading stands, beside the posts it has shown. */
data class FeedState(val chunks: List<ChunkState>)

/**
 * The Following feed, as XTA's group feed reads it: the followed accounts are
 * packed into as few `from:` searches as fit, each chunk is searched, and a
 * chunk whose search fails falls back to its accounts' own posts.
 *
 * Unlike XTA, pages from different chunks are merged in time order: a post is
 * shown only once every chunk has been read past it, so loading more never
 * puts a newer post under an older one.
 */
class FollowingFeed(
    members: List<FeedMember>,
    private val reads: FeedReads,
    private val includeReplies: Boolean = true,
    private val includeReposts: Boolean = true,
    private val concurrency: Int = 3,
    private val pageSize: Int = 20,
    private val fallbackMillis: Long = 12_000,
) {
    private class Chunk(val members: List<FeedMember>, val query: String) {
        var cursor: String? = null
        var exhausted = false

        /** The oldest post read from this chunk: everything newer has been read. */
        var oldest: Long? = null
        val unread = ArrayList<XThread>()
    }

    private val chunks: List<Chunk> = FeedQueries.pack(
        members.sortedWith(compareBy<FeedMember> { it.followedAt }.thenBy { it.id }),
        { FeedQueries.term(it.screenName) },
        includeReplies,
        includeReposts,
    ).map { Chunk(it, FeedQueries.query(it.map { member -> FeedQueries.term(member.screenName) }, includeReplies, includeReposts)) }

    private val mutex = Mutex()
    private val shown = HashSet<String>()

    val chunkCount: Int get() = chunks.size

    /** The newest posts: every chunk is read from the top again. */
    suspend fun first(): FeedPage = mutex.withLock {
        shown.clear()
        chunks.forEach { it.cursor = null; it.exhausted = false; it.oldest = null; it.unread.clear() }
        val failures = readChunks(chunks, firstPage = true)
        FeedPage(drain(), failures, hasMore())
    }

    /** The next posts in time order, reading on in the chunks that hold the feed back. */
    suspend fun more(): FeedPage = mutex.withLock {
        val threads = ArrayList<XThread>()
        val failures = ArrayList<Throwable>()
        var rounds = 0
        while (threads.size < pageSize && hasMore() && rounds < MAX_ROUNDS) {
            val limiting = chunks.filter { !it.exhausted }.sortedByDescending { it.oldest ?: Long.MAX_VALUE }.take(concurrency)
            failures += readChunks(limiting, firstPage = false)
            threads += drain()
            rounds++
        }
        if (!hasMore()) threads += drain()
        FeedPage(threads, failures, hasMore())
    }

    /**
     * The posts written since [than], the newest post shown: each chunk is read
     * from its top until it reaches [than], at most [pagesPerChunk] pages. The
     * reading further down is left as it was, so loading more goes on below
     * what is shown.
     */
    suspend fun newer(than: Long, pagesPerChunk: Int = 3): NewerPage = mutex.withLock {
        val results = coroutineScope {
            val gate = Semaphore(concurrency)
            chunks.map { chunk -> async { gate.withPermit { newerIn(chunk, than, pagesPerChunk) } } }.awaitAll()
        }
        NewerPage(
            threads = results.flatMap { it.threads }.filter { shown.add(it.id) }.sortedByDescending(::idOf),
            bridged = results.all { it.bridged },
            failures = results.mapNotNull { it.failure },
        )
    }

    private class ChunkNewer(val threads: List<XThread>, val bridged: Boolean, val failure: Throwable?)

    private suspend fun newerIn(chunk: Chunk, than: Long, pages: Int): ChunkNewer {
        val found = ArrayList<XThread>()
        var cursor: String? = null
        repeat(pages) {
            val page = try {
                reads.search(chunk.query, cursor)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (cursor == null && fallsBack(error)) {
                    val own = fallbackThreads(chunk).filter { idOf(it) > than }
                    return ChunkNewer(own, bridged = true, failure = if (own.isEmpty()) error else null)
                }
                // A failure is reported, not taken for a hole: what was read stands.
                return ChunkNewer(found, bridged = true, failure = error)
            }
            found += page.threads.filter(::wanted).filter { idOf(it) > than }
            val oldest = page.threads.minOfOrNull(::idOf)
            if (oldest == null || oldest <= than || page.bottomCursor == null) return ChunkNewer(found, bridged = true, failure = null)
            cursor = page.bottomCursor
        }
        return ChunkNewer(found, bridged = false, failure = null)
    }

    /** Where every chunk's reading stands, to keep beside the posts shown. */
    suspend fun state(): FeedState = mutex.withLock {
        FeedState(chunks.map { ChunkState(it.query, it.cursor, it.exhausted, it.oldest, it.unread.toList()) })
    }

    /**
     * Reads on from [state], with [shownIds] already on screen. Refused (false)
     * when the chunks differ: the accounts followed or the switches changed.
     */
    suspend fun restore(state: FeedState, shownIds: Collection<String>): Boolean = mutex.withLock {
        if (state.chunks.map { it.query } != chunks.map { it.query }) return@withLock false
        chunks.zip(state.chunks).forEach { (chunk, saved) ->
            chunk.cursor = saved.cursor
            chunk.exhausted = saved.exhausted
            chunk.oldest = saved.oldest
            chunk.unread.clear()
            chunk.unread += saved.unread
        }
        shown.clear()
        shown += shownIds
        true
    }

    private fun hasMore() = chunks.any { !it.exhausted } || chunks.any { it.unread.isNotEmpty() }

    /** Takes every unread post the feed is complete down to, newest first. */
    private fun drain(): List<XThread> {
        val open = chunks.filter { !it.exhausted }
        val cutoff = if (open.isEmpty()) Long.MIN_VALUE else open.maxOf { it.oldest ?: Long.MAX_VALUE }
        val ready = chunks.flatMap { chunk ->
            val (now, later) = chunk.unread.partition { idOf(it) >= cutoff }
            chunk.unread.clear()
            chunk.unread.addAll(later)
            now
        }
        return ready.filter { shown.add(it.id) }.sortedByDescending { idOf(it) }
    }

    private suspend fun readChunks(targets: List<Chunk>, firstPage: Boolean): List<Throwable> = coroutineScope {
        val gate = Semaphore(concurrency)
        targets.map { chunk -> async { gate.withPermit { readChunk(chunk, firstPage) } } }.awaitAll().filterNotNull()
    }

    /** Reads one more page of [chunk]; returns why it brought nothing, if it did not. */
    private suspend fun readChunk(chunk: Chunk, firstPage: Boolean): Throwable? {
        val page = try {
            reads.search(chunk.query, chunk.cursor)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            chunk.exhausted = true
            return if (firstPage && fallsBack(error)) fallback(chunk, error) else error
        }
        chunk.unread += page.threads.filter(::wanted)
        chunk.cursor = page.bottomCursor
        chunk.exhausted = page.threads.isEmpty() || page.bottomCursor == null
        // From every post read, filtered or not: the chunk has been read down to it.
        page.threads.minOfOrNull(::idOf)?.let { chunk.oldest = it }
        return null
    }

    /**
     * The chunk's accounts read one by one, as XTA does when a search fails:
     * their own posts, two at a time, within [fallbackMillis].
     */
    private suspend fun fallback(chunk: Chunk, cause: Throwable): Throwable? {
        val threads = fallbackThreads(chunk)
        chunk.unread += threads
        return if (threads.isEmpty()) cause else null
    }

    private suspend fun fallbackThreads(chunk: Chunk): List<XThread> {
        val gate = Semaphore(FALLBACK_CONCURRENCY)
        val pages = ArrayList<XTimelinePage>()
        withTimeoutOrNull(fallbackMillis) {
            coroutineScope {
                chunk.members.map { member ->
                    async {
                        gate.withPermit { runCatching { reads.userPosts(member.id) }.getOrNull() }?.let { page -> synchronized(pages) { pages += page } }
                    }
                }.awaitAll()
            }
        }
        return synchronized(pages) { pages.flatMap { it.threads } }.filter(::wanted)
    }

    private fun wanted(thread: XThread): Boolean {
        val post = thread.posts.firstOrNull() ?: return false
        if (!includeReposts && post.isRepost) return false
        return includeReplies || post.replyToScreenName == null
    }

    private fun idOf(thread: XThread): Long = thread.id.toLongOrNull() ?: thread.posts.firstOrNull()?.id?.toLongOrNull() ?: 0

    companion object {
        private const val MAX_ROUNDS = 3
        private const val FALLBACK_CONCURRENCY = 2

        /**
         * Whether profiles are worth asking for after a failed search: not when
         * there is no connection, no account, or no way to sign requests.
         */
        fun fallsBack(error: Throwable): Boolean =
            error !is IOException && error !is XNoAccountException && error !is SigningUnavailableException
    }
}
