package com.aimdi.xtanative.core.feed

import com.aimdi.xtanative.core.x.model.XTimelinePage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * [reads] with the first reads of the feed the app opens on made ahead, while
 * its window is still being made ([ahead]). A read made ahead goes, once, to
 * the first feed that asks for the same read within [keepMillis] of it being
 * made, as if that feed had made it then; every other read goes to [reads].
 * The first ask of any feed ends the reading ahead: what it had not asked yet
 * is not asked, so nothing is read twice, and what it asked is waited for.
 */
class EarlyReads(
    private val reads: FeedReads,
    private val scope: CoroutineScope,
    private val keepMillis: Long = KEEP_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis,
) : FeedReads {
    private sealed interface Read {
        data class Search(val query: String, val cursor: String?, val includeReplies: Boolean) : Read

        data class Posts(val userId: String, val includeReplies: Boolean) : Read
    }

    private class Made(val at: Long, val answer: Deferred<XTimelinePage>)

    private val made = HashMap<Read, Made>()
    private var asked = false
    private var reading: Job? = null

    /** Whether a feed asked, after which nothing more is read ahead. */
    val claimed: Boolean get() = synchronized(this) { asked }

    /** Runs [block] with reads that are made ahead; it stops at a feed's first ask. Once at most. */
    fun ahead(block: suspend (FeedReads) -> Unit) {
        synchronized(this) {
            if (asked || reading != null) return
            reading = scope.launch { block(early) }
        }
    }

    override suspend fun search(query: String, cursor: String?, includeReplies: Boolean): XTimelinePage =
        taken(Read.Search(query, cursor, includeReplies))?.await() ?: reads.search(query, cursor, includeReplies)

    override suspend fun userPosts(userId: String, includeReplies: Boolean): XTimelinePage =
        taken(Read.Posts(userId, includeReplies))?.await() ?: reads.userPosts(userId, includeReplies)

    private val early = object : FeedReads {
        override suspend fun search(query: String, cursor: String?, includeReplies: Boolean): XTimelinePage =
            madeAhead(Read.Search(query, cursor, includeReplies)) { reads.search(query, cursor, includeReplies) }

        override suspend fun userPosts(userId: String, includeReplies: Boolean): XTimelinePage =
            madeAhead(Read.Posts(userId, includeReplies)) { reads.userPosts(userId, includeReplies) }
    }

    /** [read] started on its own, so the feed that takes it gets it even after the reading ahead stopped. */
    private suspend fun madeAhead(key: Read, read: suspend () -> XTimelinePage): XTimelinePage {
        val answer = synchronized(this) {
            if (asked) null else made[key]?.answer ?: scope.async { read() }.also { made[key] = Made(clock(), it) }
        }
        return answer?.await() ?: throw CancellationException("a feed asked first")
    }

    private fun taken(key: Read): Deferred<XTimelinePage>? {
        val (answer, stop) = synchronized(this) {
            val now = clock()
            made.entries.removeAll { now - it.value.at > keepMillis }
            val first = !asked
            asked = true
            made.remove(key)?.answer to reading.takeIf { first }
        }
        stop?.cancel()
        return answer
    }

    companion object {
        /** How long a read made ahead waits for its feed: a window is made in seconds, and a feed reads fresh after. */
        const val KEEP_MILLIS = 20_000L
    }
}
