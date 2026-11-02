package com.aimdi.xtanative.core.feed

import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XTimelinePage
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * What a load adds. [failures] are the sources that brought nothing; [hasMore]
 * is false once every source has run out.
 */
data class FeedPage(val threads: List<XThread>, val failures: List<Throwable>, val hasMore: Boolean)

/**
 * The posts newer than the top of a shown timeline. [bridged] says every source
 * was read down to that top (or ran out, or failed): false means more were
 * written than were read, and the posts above the top have a hole in them.
 */
data class NewerPage(val threads: List<XThread>, val bridged: Boolean, val failures: List<Throwable>)

/** Where one source's reading stands, so a later launch can read on from it. */
data class ChunkState(val query: String, val cursor: String?, val exhausted: Boolean, val oldest: Long?, val unread: List<XThread>)

/** Where a whole timeline's reading stands, beside the posts it has shown. */
data class FeedState(val chunks: List<ChunkState>)

/** One newest-first timeline, read page by page; [key] tells it apart in a saved state. */
class TimelineSource(val key: String, val read: suspend (cursor: String?) -> XTimelinePage)

/** What a reader of merged timelines can ask of one. */
interface FeedTimeline {
    /** The newest posts: every source is read from the top again. */
    suspend fun first(): FeedPage

    /** The next posts in time order, reading on in the sources that hold the rest back. */
    suspend fun more(): FeedPage

    /**
     * The posts written since [than], the newest post shown: each source is read
     * from its top until it reaches [than], at most [pagesPerChunk] pages. The
     * reading further down is left as it was, so loading more goes on below
     * what is shown.
     */
    suspend fun newer(than: Long, pagesPerChunk: Int = 3): NewerPage

    /** Where every source's reading stands, to keep beside the posts shown. */
    suspend fun state(): FeedState

    /**
     * Reads on from [state], with [shownIds] already on screen. Refused (false)
     * when the sources differ.
     */
    suspend fun restore(state: FeedState, shownIds: Collection<String>): Boolean
}

/**
 * Several newest-first timelines read as one, in time order: a post is shown
 * only once every source has been read past it, so loading more never puts a
 * newer post under an older one. A source that fails is set aside and its
 * failure reported; the others go on.
 */
class MergedTimeline(
    private val sources: List<TimelineSource>,
    private val concurrency: Int = 3,
    private val pageSize: Int = 20,
    private val wanted: (XThread) -> Boolean = { true },
) : FeedTimeline {
    private class Reading(val source: TimelineSource) {
        var cursor: String? = null
        var exhausted = false

        /** The oldest post read from this source: everything newer has been read. */
        var oldest: Long? = null
        val unread = ArrayList<XThread>()
    }

    private val readings = sources.map(::Reading)
    private val mutex = Mutex()
    private val shown = HashSet<String>()

    override suspend fun first(): FeedPage = mutex.withLock {
        shown.clear()
        readings.forEach { it.cursor = null; it.exhausted = false; it.oldest = null; it.unread.clear() }
        val failures = read(readings)
        FeedPage(drain(), failures, hasMore())
    }

    override suspend fun more(): FeedPage = mutex.withLock {
        val threads = ArrayList<XThread>()
        val failures = ArrayList<Throwable>()
        var rounds = 0
        while (threads.size < pageSize && hasMore() && rounds < MAX_ROUNDS) {
            val limiting = readings.filter { !it.exhausted }.sortedByDescending { it.oldest ?: Long.MAX_VALUE }.take(concurrency)
            failures += read(limiting)
            threads += drain()
            rounds++
        }
        if (!hasMore()) threads += drain()
        FeedPage(threads, failures, hasMore())
    }

    override suspend fun newer(than: Long, pagesPerChunk: Int): NewerPage = mutex.withLock {
        val results = coroutineScope {
            val gate = Semaphore(concurrency)
            readings.map { reading -> async { gate.withPermit { newerIn(reading.source, than, pagesPerChunk) } } }.awaitAll()
        }
        NewerPage(
            threads = results.flatMap { it.threads }.filter { shown.add(it.id) }.sortedByDescending(::idOf),
            bridged = results.all { it.bridged },
            failures = results.mapNotNull { it.failure },
        )
    }

    private class SourceNewer(val threads: List<XThread>, val bridged: Boolean, val failure: Throwable?)

    private suspend fun newerIn(source: TimelineSource, than: Long, pages: Int): SourceNewer {
        val found = ArrayList<XThread>()
        var cursor: String? = null
        repeat(pages) {
            val page = try {
                source.read(cursor)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                // A failure is reported, not taken for a hole: what was read stands.
                return SourceNewer(found, bridged = true, failure = error)
            }
            found += page.threads.filter(wanted).filter { idOf(it) > than }
            val oldest = page.threads.minOfOrNull(::idOf)
            if (oldest == null || oldest <= than || page.bottomCursor == null) return SourceNewer(found, bridged = true, failure = null)
            cursor = page.bottomCursor
        }
        return SourceNewer(found, bridged = false, failure = null)
    }

    override suspend fun state(): FeedState = mutex.withLock {
        FeedState(readings.map { ChunkState(it.source.key, it.cursor, it.exhausted, it.oldest, it.unread.toList()) })
    }

    override suspend fun restore(state: FeedState, shownIds: Collection<String>): Boolean = mutex.withLock {
        if (state.chunks.map { it.query } != readings.map { it.source.key }) return@withLock false
        readings.zip(state.chunks).forEach { (reading, saved) ->
            reading.cursor = saved.cursor
            reading.exhausted = saved.exhausted
            reading.oldest = saved.oldest
            reading.unread.clear()
            reading.unread += saved.unread
        }
        shown.clear()
        shown += shownIds
        true
    }

    private fun hasMore() = readings.any { !it.exhausted } || readings.any { it.unread.isNotEmpty() }

    /** Takes every unread post the timeline is complete down to, newest first. */
    private fun drain(): List<XThread> {
        val open = readings.filter { !it.exhausted }
        val cutoff = if (open.isEmpty()) Long.MIN_VALUE else open.maxOf { it.oldest ?: Long.MAX_VALUE }
        val ready = readings.flatMap { reading ->
            val (now, later) = reading.unread.partition { idOf(it) >= cutoff }
            reading.unread.clear()
            reading.unread.addAll(later)
            now
        }
        return ready.filter { shown.add(it.id) }.sortedByDescending(::idOf)
    }

    private suspend fun read(targets: List<Reading>): List<Throwable> = coroutineScope {
        val gate = Semaphore(concurrency)
        targets.map { reading -> async { gate.withPermit { readOne(reading) } } }.awaitAll().filterNotNull()
    }

    /** Reads one more page of [reading]; returns why it brought nothing, if it did not. */
    private suspend fun readOne(reading: Reading): Throwable? {
        val page = try {
            reading.source.read(reading.cursor)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            reading.exhausted = true
            return error
        }
        reading.unread += page.threads.filter(wanted)
        reading.cursor = page.bottomCursor
        reading.exhausted = page.threads.isEmpty() || page.bottomCursor == null
        // From every post read, filtered or not: the source has been read down to it.
        page.threads.minOfOrNull(::idOf)?.let { reading.oldest = it }
        return null
    }

    private companion object {
        const val MAX_ROUNDS = 3

        fun idOf(thread: XThread): Long = thread.id.toLongOrNull() ?: thread.posts.firstOrNull()?.id?.toLongOrNull() ?: 0
    }
}
