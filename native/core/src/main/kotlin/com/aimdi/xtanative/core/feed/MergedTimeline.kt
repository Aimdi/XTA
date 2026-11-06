package com.aimdi.xtanative.core.feed

import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XTimelinePage
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * What a load adds. [failures] are the sources that brought nothing; [hasMore]
 * is false once every source has run out. [foldReasons] says why a folded
 * thread is folded, by its id; [unrelated] that X answered a search with posts
 * by accounts outside it, as it sometimes does. [gap]: a first page put over
 * the posts kept from before, whose posts written since were not all read, so
 * a hole lies between them ([GroupFeed.reopen]).
 */
data class FeedPage(
    val threads: List<XThread>,
    val failures: List<Throwable>,
    val hasMore: Boolean,
    val foldReasons: Map<String, String> = emptyMap(),
    val unrelated: Boolean = false,
    val gap: Boolean = false,
)

/**
 * The posts newer than the top of a shown timeline. [bridged] says every source
 * was read down to that top (or ran out, or failed): false means more were
 * written than were read, and the posts above the top have a hole in them.
 */
data class NewerPage(
    val threads: List<XThread>,
    val bridged: Boolean,
    val failures: List<Throwable>,
    val foldReasons: Map<String, String> = emptyMap(),
    val unrelated: Boolean = false,
) {
    /** Everything new was read: a catch-up card may say the reader is done. */
    val complete: Boolean get() = bridged && failures.isEmpty()
}

/** How far a load has come, for the line over the posts and the retry it offers when some sources failed. */
data class FeedProgress(val answered: Int = 0, val total: Int = 0, val failed: Int = 0, val loading: Boolean = false) {
    /** Sources failed and none is still being read: offer to read them again. */
    val canRetry: Boolean get() = !loading && failed > 0
}

/** Where one source's reading stands, so a later launch can read on from it. */
data class ChunkState(
    val query: String,
    val cursor: String?,
    val exhausted: Boolean,
    val oldest: Long?,
    val unread: List<XThread>,
    /** Its last read failed: a retry reads it again. */
    val failed: Boolean = false,
)

/** Where a whole timeline's reading stands, beside the posts it has shown. */
data class FeedState(val chunks: List<ChunkState>) {
    /** Some source has more to read, or holds posts not shown yet. */
    val hasMore: Boolean get() = chunks.any { !it.exhausted || it.unread.isNotEmpty() }
}

/** One newest-first timeline, read page by page; [key] tells it apart in a saved state. */
class TimelineSource(val key: String, val read: suspend (cursor: String?) -> XTimelinePage)

/** What a reader of merged timelines can ask of one. */
interface FeedTimeline {
    /** How far the load under way has come. */
    val progress: StateFlow<FeedProgress>

    /**
     * The newest posts: every source is read from the top again. [onArrived]
     * gets, as each source answers, what those that answered hold down to
     * where all of them have been read: a preview while the rest are read.
     */
    suspend fun first(onArrived: ((FeedPage) -> Unit)? = null): FeedPage

    /** The next posts in time order, reading on in the sources that hold the rest back. */
    suspend fun more(): FeedPage

    /**
     * Reads again only the sources whose last read failed; the others keep
     * their place. A source whose read of the newer posts ([newer]) failed
     * reads them again. What they bring may be newer than posts already
     * shown: the caller places it by time.
     */
    suspend fun retryFailed(): FeedPage

    /**
     * The posts written since [than], the newest post shown: each source is read
     * from its top until it reaches [than], at most [pagesPerChunk] pages. The
     * reading further down is left as it was, so loading more goes on below
     * what is shown.
     */
    suspend fun newer(than: Long, pagesPerChunk: Int = 1 + GapFill.MAX_FILLS): NewerPage

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
 * failure reported; the others go on, and a retry reads it again. A source
 * read takes at most [readMillis], a load at most [loadMillis]: what is still
 * unanswered then fails, as XTA's batch reads do.
 */
class MergedTimeline(
    private val sources: List<TimelineSource>,
    private val concurrency: Int = 3,
    private val pageSize: Int = 20,
    private val wanted: (XThread) -> Boolean = { true },
    readMillis: Long = 35_000,
    loadMillis: Long = 90_000,
) : FeedTimeline {
    private class Reading(val index: Int, val source: TimelineSource) {
        var cursor: String? = null
        var exhausted = false

        /** The oldest post read from this source: everything newer has been read. */
        var oldest: Long? = null
        val unread = ArrayList<XThread>()

        /** Why its last read brought nothing; null when it answered. */
        var failure: Throwable? = null
    }

    private val readings = sources.mapIndexed(::Reading)
    private val mutex = Mutex()
    private val shownThreads = HashSet<String>()
    private val shownPosts = HashSet<String>()
    private val batch = BatchRead<Throwable?>(readMillis, loadMillis)
    private val loading = MutableStateFlow(FeedProgress())

    /** The top the last [newer] read down to, and the sources whose read of it failed, for a retry. */
    private var newerThan: Long? = null
    private var newerPages = 1
    private val newerFailed = HashSet<Int>()

    override val progress: StateFlow<FeedProgress> = loading.asStateFlow()

    override suspend fun first(onArrived: ((FeedPage) -> Unit)?): FeedPage = mutex.withLock {
        clearShown()
        forgetNewer()
        readings.forEach { it.cursor = null; it.exhausted = false; it.oldest = null; it.unread.clear(); it.failure = null }
        val failures = read(readings, onArrived)
        FeedPage(drain(), failures, hasMore())
    }

    override suspend fun more(): FeedPage = mutex.withLock {
        val threads = ArrayList<XThread>()
        val failures = ArrayList<Throwable>()
        var rounds = 0
        try {
            while (threads.size < pageSize && hasMore() && rounds < MAX_ROUNDS) {
                val limiting = readings.filter { !it.exhausted }.sortedByDescending { it.oldest ?: Long.MAX_VALUE }.take(concurrency)
                failures += read(limiting)
                threads += drain()
                rounds++
            }
        } catch (cancelled: CancellationException) {
            // A load stopped halfway (a feed scrolled out of sight) loses nothing it took: the next one shows it.
            unshow(threads)
            throw cancelled
        }
        if (!hasMore()) threads += drain()
        FeedPage(threads, failures, hasMore())
    }

    override suspend fun retryFailed(): FeedPage = mutex.withLock {
        val failed = readings.filter { it.failure != null }
        failed.forEach { it.exhausted = false; it.failure = null }
        val failures = read(failed).toMutableList()
        val drained = drain()
        val than = newerThan
        val fresh = if (than == null || newerFailed.isEmpty()) {
            emptyList()
        } else {
            val again = readings.filter { it.index in newerFailed }
            val results = readNewer(again, than, newerPages)
            failures += results.mapNotNull { it.failure }
            results.flatMap { it.threads }.filter(::firstShowing)
        }
        FeedPage((drained + fresh).sortedByDescending(FeedOrder::timeOf), failures, hasMore())
    }

    override suspend fun newer(than: Long, pagesPerChunk: Int): NewerPage = mutex.withLock {
        newerThan = than
        newerPages = pagesPerChunk
        newerFailed.clear()
        val results = readNewer(readings, than, pagesPerChunk)
        NewerPage(
            threads = results.flatMap { it.threads }.filter(::firstShowing).sortedByDescending(FeedOrder::timeOf),
            bridged = results.all { it.bridged },
            failures = results.mapNotNull { it.failure },
        )
    }

    /** Each of [targets] read from its top down to [than], with the progress of it; those that failed are kept for a retry. */
    private suspend fun readNewer(targets: List<Reading>, than: Long, pages: Int): List<SourceNewer> {
        if (targets.isEmpty()) return emptyList()
        val answered = AtomicInteger()
        val failed = AtomicInteger()
        loading.value = FeedProgress(0, targets.size, 0, loading = true)
        val results = try {
            coroutineScope {
                val gate = Semaphore(concurrency)
                targets.map { reading ->
                    async {
                        gate.withPermit { newerIn(reading.source, than, pages) }.also { result ->
                            if (result.failure != null) failed.incrementAndGet()
                            loading.value = FeedProgress(answered.incrementAndGet(), targets.size, failed.get(), loading = true)
                        }
                    }
                }.awaitAll()
            }
        } catch (cancelled: CancellationException) {
            loading.value = loading.value.copy(loading = false)
            throw cancelled
        }
        targets.zip(results).forEach { (reading, result) -> if (result.failure != null) newerFailed += reading.index else newerFailed -= reading.index }
        loading.value = FeedProgress(targets.size, targets.size, results.count { it.failure != null }, loading = false)
        return results
    }

    private fun forgetNewer() {
        newerThan = null
        newerFailed.clear()
    }

    private class SourceNewer(val threads: List<XThread>, val bridged: Boolean, val failure: Throwable?)

    private suspend fun newerIn(source: TimelineSource, than: Long, pages: Int): SourceNewer {
        val found = ArrayList<XThread>()
        var cursor: String? = null
        var fills = 0
        while (true) {
            val page = try {
                source.read(cursor)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                // A failure is reported, not taken for a hole: what was read stands.
                return SourceNewer(found, bridged = true, failure = error)
            }
            found += page.threads.filter(wanted).filter { FeedOrder.timeOf(it) > than }
            val oldest = page.threads.minOfOrNull(FeedOrder::timeOf)
            val reachesDown = page.threads.isNotEmpty() && page.bottomCursor != null && (oldest ?: 0) > than
            if (!reachesDown) return SourceNewer(found, bridged = true, failure = null)
            if (!GapFill.shouldContinue(than, oldest, page.threads.isNotEmpty(), page.bottomCursor != null, fills, pages - 1)) {
                return SourceNewer(found, bridged = false, failure = null)
            }
            cursor = page.bottomCursor
            fills++
        }
    }

    override suspend fun state(): FeedState = mutex.withLock {
        FeedState(readings.map { ChunkState(it.source.key, it.cursor, it.exhausted, it.oldest, it.unread.toList(), it.failure != null) })
    }

    override suspend fun restore(state: FeedState, shownIds: Collection<String>): Boolean = mutex.withLock {
        if (state.chunks.map { it.query } != readings.map { it.source.key }) return@withLock false
        readings.zip(state.chunks).forEach { (reading, saved) ->
            reading.cursor = saved.cursor
            reading.exhausted = saved.exhausted
            reading.oldest = saved.oldest
            reading.unread.clear()
            reading.unread += saved.unread
            reading.failure = if (saved.failed) NotReadYet() else null
        }
        clearShown()
        forgetNewer()
        shownThreads += shownIds
        val failed = readings.count { it.failure != null }
        loading.value = FeedProgress(readings.size, readings.size, failed, loading = false)
        true
    }

    private fun hasMore() = readings.any { !it.exhausted } || readings.any { it.unread.isNotEmpty() }

    private fun clearShown() {
        shownThreads.clear()
        shownPosts.clear()
    }

    /** [threads] taken but never shown: back among the unread, to be shown by the next load. */
    private fun unshow(threads: List<XThread>) {
        if (threads.isEmpty()) return
        threads.forEach { thread ->
            shownThreads -= thread.id
            thread.posts.firstOrNull()?.id?.let(shownPosts::remove)
        }
        readings.firstOrNull()?.unread?.addAll(threads)
    }

    /**
     * What the sources that answered so far hold, newest first, down to where
     * every one of them that reads on has been read: a first load's preview.
     */
    private fun arrived(): List<XThread> = synchronized(readings) {
        val answered = readings.filter { it.oldest != null || it.exhausted }
        val open = answered.filter { !it.exhausted }
        val cutoff = if (open.isEmpty()) Long.MIN_VALUE else open.maxOf { it.oldest ?: Long.MAX_VALUE }
        val ready = answered.flatMap { reading -> reading.unread.filter { FeedOrder.timeOf(it) >= cutoff } }
        FeedOrder.dedupe(ready.sortedByDescending(FeedOrder::timeOf)).filter { it.id !in shownThreads }
    }

    /** True the first time [thread], or the post it starts with, is shown. */
    private fun firstShowing(thread: XThread): Boolean {
        val lead = thread.posts.firstOrNull()?.id
        if (thread.id in shownThreads || (lead != null && lead in shownPosts)) return false
        shownThreads += thread.id
        lead?.let(shownPosts::add)
        return true
    }

    /** Takes every unread post the timeline is complete down to, newest first. */
    private fun drain(): List<XThread> {
        val open = readings.filter { !it.exhausted }
        val cutoff = if (open.isEmpty()) Long.MIN_VALUE else open.maxOf { it.oldest ?: Long.MAX_VALUE }
        val ready = readings.flatMap { reading ->
            val (now, later) = reading.unread.partition { FeedOrder.timeOf(it) >= cutoff }
            reading.unread.clear()
            reading.unread.addAll(later)
            now
        }
        return ready.sortedByDescending(FeedOrder::timeOf).filter(::firstShowing)
    }

    /**
     * Reads one more page of each of [targets]; returns why the ones that
     * brought nothing did not. [onArrived] gets [arrived] as each answers.
     */
    private suspend fun read(targets: List<Reading>, onArrived: ((FeedPage) -> Unit)? = null): List<Throwable> {
        if (targets.isEmpty()) return emptyList()
        val keys = targets.map { it.index.toString() }
        loading.value = FeedProgress(0, keys.size, 0, loading = true)
        val outcomes = try {
            batch.load(
                keys,
                fetch = { key -> readOne(readings[key.toInt()]) },
                onError = { it },
                failed = { it != null },
                concurrency = concurrency,
                onProgress = { done ->
                    loading.value = FeedProgress(done.size, keys.size, done.count { it != null }, loading = true)
                    onArrived?.invoke(FeedPage(arrived(), emptyList(), hasMore = true))
                },
            )
        } catch (cancelled: CancellationException) {
            loading.value = loading.value.copy(loading = false)
            throw cancelled
        }
        targets.zip(outcomes).forEach { (reading, failure) ->
            if (failure != null) {
                reading.exhausted = true
                reading.failure = failure
            }
        }
        loading.value = FeedProgress(outcomes.size, keys.size, outcomes.count { it != null }, loading = false)
        return outcomes.filterNotNull()
    }

    /** Reads one more page of [reading]; returns why it brought nothing, if it did not. */
    private suspend fun readOne(reading: Reading): Throwable? {
        val page = try {
            reading.source.read(reading.cursor)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            return error
        }
        val kept = page.threads.filter(wanted)
        synchronized(readings) {
            reading.failure = null
            reading.unread += kept
            reading.cursor = page.bottomCursor
            reading.exhausted = page.threads.isEmpty() || page.bottomCursor == null
            // From every post read, filtered or not: the source has been read down to it.
            page.threads.minOfOrNull(FeedOrder::timeOf)?.let { reading.oldest = it }
        }
        return null
    }

    /** A source a saved state says failed: it has not answered since. */
    private class NotReadYet : Exception("Not read since its last read failed")

    private companion object {
        const val MAX_ROUNDS = 3
    }
}
