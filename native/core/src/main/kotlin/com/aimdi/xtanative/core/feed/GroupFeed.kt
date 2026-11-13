package com.aimdi.xtanative.core.feed

import com.aimdi.xtanative.core.x.XAccount
import com.aimdi.xtanative.core.x.XNoAccountException
import com.aimdi.xtanative.core.x.XRateLimitedException
import com.aimdi.xtanative.core.x.XRequestException
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XTimelinePage
import com.aimdi.xtanative.core.x.signing.SigningUnavailableException
import java.io.IOException
import java.io.InterruptedIOException
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What one feed reads, worked out from its members before anything is fetched
 * (XTA's planGroupFeed): its members, the reply and repost choices resolved
 * against the session's, and what it does to the posts it reads. [key] is the
 * feed's: [FeedKeys.FOLLOWING] or a group's id.
 */
data class GroupFeedPlan(
    val key: String,
    val members: List<FeedMember>,
    val includeReplies: Boolean = true,
    val includeReposts: Boolean = true,
    val shaping: FeedShaping = FeedShaping(),
    /** Catch-up mode: the feed stops at the reader's last read post. */
    val catchUp: Boolean = false,
) {
    /** The members packed into as few searches as fit, oldest follow first. */
    val chunks: List<List<FeedMember>>
        get() = FeedQueries.pack(members.sortedWith(FeedMember.ORDER), FeedMember::term, includeReplies, includeReposts)

    fun queryOf(chunk: List<FeedMember>): String = FeedQueries.query(chunk.map(FeedMember::term), includeReplies, includeReposts)

    val popular: Boolean get() = shaping.sort == FeedSort.POPULAR

    /**
     * What decides which posts the feed reads and in what order: its searches
     * (the members and the reply and repost choices), its order and its rules,
     * as XTA's chunk hashes and rules key. Only a change of it reads the feed
     * again; the rest (the language filter, zen mode, the profiles' hidden
     * reposts and replies, the post caps) applies from the next load on.
     */
    val readKey: String get() = (chunks.map(::queryOf) + shaping.sort.name + shaping.rules.toString()).joinToString("\n")

    /** Whether this feed keeps a read position, with the reader's [readingPosition] switch. */
    fun tracksReadPosition(readingPosition: Boolean, mediaOnly: Boolean = false): Boolean =
        Unread.tracks(popular, readingPosition, catchUp, mediaOnly)
}

/** The two reads the feed makes. */
interface FeedReads {
    /** A chunk's search; without [includeReplies] it keeps no reply, as XTA's searchTweets. */
    suspend fun search(query: String, cursor: String?, includeReplies: Boolean): XTimelinePage

    /** An account's own timeline, with its replies when [includeReplies], as XTA's fallback asks for it. */
    suspend fun userPosts(userId: String, includeReplies: Boolean): XTimelinePage
}

/** A chunk's search gave no answer in its time ([GroupFeed]'s searchMillis): its accounts' own posts are asked for instead. */
class SearchTimeoutException(millis: Long) : InterruptedIOException("The search gave no answer within ${millis}ms")

/**
 * A group's feed (or the Following feed), as XTA's group feed reads it: the
 * members are packed into as few searches as fit, each chunk is searched, and
 * a chunk whose search fails falls back to its accounts' own posts (a saved
 * search has none). What is read is shaped by the plan: hidden as it is read,
 * so it never takes a place on a page, then ordered, folded and capped per load.
 *
 * Unlike XTA, the chunks are merged in time order ([MergedTimeline]), so
 * loading more never puts a newer post under an older one. A search takes at
 * most [searchMillis], its fallback at most [fallbackMillis], as XTA's.
 */
class GroupFeed(
    plan: GroupFeedPlan,
    private val reads: FeedReads,
    concurrency: Int = ChunkConcurrency.MIN,
    pageSize: Int = 20,
    private val fallbackMillis: Long = 12_000,
    private val clock: () -> Instant = Instant::now,
    private val searchMillis: Long = 20_000,
) : FeedTimeline {
    /** What the feed does with what it reads: the plan it was made with, or the one [replan] gave it since. */
    @Volatile
    var plan: GroupFeedPlan = plan
        private set

    private val chunks = plan.chunks
    private val unrelated = AtomicBoolean(false)
    private val mutex = Mutex()
    private val timeline = MergedTimeline(
        chunks.map { chunk ->
            val query = plan.queryOf(chunk)
            TimelineSource(query) { cursor -> readChunk(chunk, query, cursor) }
        },
        concurrency,
        pageSize,
        ::wanted,
    )

    val chunkCount: Int get() = chunks.size

    override val progress: StateFlow<FeedProgress> get() = timeline.progress

    /**
     * Takes [next] for what is read from now on, without reading anything
     * again: refused (false) when it reads otherwise ([GroupFeedPlan.readKey]).
     */
    fun replan(next: GroupFeedPlan): Boolean {
        if (next.readKey != plan.readKey) return false
        plan = next
        return true
    }

    /**
     * A first page starts the unrelated-posts notice again; later loads keep
     * it up until then. [onArrived] gets the posts as the chunks answer.
     */
    override suspend fun first(onArrived: ((FeedPage) -> Unit)?): FeedPage = mutex.withLock {
        unrelated.set(false)
        shaped(timeline.first(onArrived?.let { arrived -> { page -> arrived(shaped(page)) } }))
    }

    /**
     * The first page of a feed opened again from [saved], as XTA's first page
     * shows each chunk's stored posts under its fresh ones: every chunk reads
     * on from where it was left, the posts written since are read down to the
     * newest one kept (at most [GapFill.MAX_FILLS] pages more a chunk), and the
     * kept ones follow under them, read again under the rules as they are now.
     * [FeedPage.gap] says the posts written since were not all read. Null when
     * [saved] cannot be read on from: no state, nothing kept, or another
     * feed's ([SavedFeed.key]).
     */
    suspend fun reopen(saved: SavedFeed): FeedPage? {
        val state = saved.state ?: return null
        if (saved.threads.isEmpty() || saved.key != plan.readKey) return null
        return mutex.withLock {
            if (!timeline.restore(state, saved.threads.map { it.id })) return@withLock null
            unrelated.set(false)
            val newer = timeline.newer(saved.threads.maxOf(FeedOrder::timeOf), 1 + GapFill.MAX_FILLS)
            val now = clock()
            val fresh = plan.shaping.page(newer.threads, now)
            val kept = plan.shaping.again(saved.threads, now)
            FeedPage(
                threads = FeedOrder.dedupe(fresh.threads + kept.threads),
                failures = newer.failures,
                hasMore = timeline.state().hasMore,
                foldReasons = kept.foldReasons + fresh.foldReasons,
                unrelated = unrelated.get(),
                gap = !newer.bridged,
            )
        }
    }

    override suspend fun more(): FeedPage = mutex.withLock { shaped(timeline.more()) }

    override suspend fun retryFailed(): FeedPage = mutex.withLock { shaped(timeline.retryFailed()) }

    override suspend fun newer(than: Long, pagesPerChunk: Int): NewerPage = mutex.withLock {
        val page = timeline.newer(than, pagesPerChunk)
        val shaped = plan.shaping.page(page.threads, clock())
        page.copy(threads = shaped.threads, foldReasons = shaped.foldReasons, unrelated = unrelated.get())
    }

    override suspend fun state(): FeedState = timeline.state()

    override suspend fun restore(state: FeedState, shownIds: Collection<String>): Boolean = timeline.restore(state, shownIds)

    private fun shaped(page: FeedPage): FeedPage {
        val shaped = plan.shaping.page(page.threads, clock())
        return page.copy(threads = shaped.threads, foldReasons = shaped.foldReasons, unrelated = unrelated.get())
    }

    private fun wanted(thread: XThread): Boolean {
        val post = thread.posts.firstOrNull() ?: return false
        val kind = (plan.includeReposts || !post.isRepost) && (plan.includeReplies || post.replyToScreenName == null)
        return kind && plan.shaping.keeps(thread, clock())
    }

    /**
     * A chunk's search, within [searchMillis]; when it fails, on any page, its
     * accounts' own posts as the chunk's last page, as XTA's
     * fetchChunkWithFallback: two at a time, within [fallbackMillis].
     */
    private suspend fun readChunk(chunk: List<FeedMember>, query: String, cursor: String?): XTimelinePage {
        val page = try {
            withTimeoutOrNull(searchMillis) { reads.search(query, cursor, plan.includeReplies) } ?: throw SearchTimeoutException(searchMillis)
        } catch (error: Throwable) {
            if (error is CancellationException || !fallsBack(error)) throw error
            val own = ownPosts(chunk.filterNot(FeedMember::search))
            if (own.isEmpty()) throw error
            return XTimelinePage(own, bottomCursor = null, topCursor = null)
        }
        if (UnrelatedPosts.found(page.threads, chunk)) unrelated.set(true)
        return page
    }

    private suspend fun ownPosts(accounts: List<FeedMember>): List<XThread> {
        if (accounts.isEmpty()) return emptyList()
        val gate = Semaphore(FALLBACK_CONCURRENCY)
        val pages = ArrayList<XTimelinePage>()
        withTimeoutOrNull(fallbackMillis) {
            coroutineScope {
                accounts.map { member ->
                    async {
                        gate.withPermit { runCatching { reads.userPosts(member.id, plan.includeReplies) }.getOrNull() }?.let { page -> synchronized(pages) { pages += page } }
                    }
                }.awaitAll()
            }
        }
        return synchronized(pages) { pages.flatMap { it.threads } }
    }

    companion object {
        /** Fewer than the chunks' searches: two chunks of twenty-odd accounts would otherwise open forty-odd profiles at once. */
        const val FALLBACK_CONCURRENCY = 2

        /**
         * Whether profiles are worth asking for after a failed search: not when
         * there is no connection, no account, or no way to sign requests. A
         * search that timed out is no lost connection: profiles may answer.
         */
        fun fallsBack(error: Throwable): Boolean =
            (error !is IOException || error is InterruptedIOException) && error !is XNoAccountException && error !is SigningUnavailableException
    }
}

/** X sometimes answers a search over several accounts with posts by others. */
object UnrelatedPosts {
    /**
     * Whether [threads] hold a post by an account outside [members], compared
     * by id so a renamed account is still itself. A chunk with a saved search
     * cannot tell: that search's posts come from anyone.
     */
    fun found(threads: List<XThread>, members: List<FeedMember>): Boolean {
        if (members.any(FeedMember::search)) return false
        val ids = members.mapTo(HashSet()) { it.id }
        return threads.any { thread -> thread.posts.any { it.author.id !in ids } }
    }
}

/** Which of a load's failures to show when nothing could be read. */
object FeedErrors {
    /** A rate limit first, which the reader knows what to do about; then a refusal; then the first. */
    fun toShow(failures: List<Throwable>): Throwable? =
        failures.firstOrNull { it is XRateLimitedException }
            ?: failures.firstOrNull { it is XRequestException && it.code in REFUSALS }
            ?: failures.firstOrNull()

    private val REFUSALS = setOf(401, 403, 404)
}

/** How many chunk searches a feed opens at once. */
object ChunkConcurrency {
    /** With one usable account, or none: every chunk at once cascaded into refusals and flagged accounts. */
    const val MIN = 3

    /** However many accounts there are. */
    const val MAX = 6

    /** About two searches per usable account. */
    fun forAccounts(usable: Int): Int = (usable * 2).coerceIn(MIN, MAX)

    /**
     * Accounts a search can go to: on for the home feeds, signed in, and not
     * rate-limited on search. With every account off for the home feeds, the
     * reads fall back to all of them, so then all of them count.
     */
    fun usableAccounts(
        accounts: List<XAccount>,
        disabledIds: Set<String>,
        broken: (XAccount) -> Boolean = { false },
        searchLimited: (XAccount) -> Boolean = { false },
    ): Int {
        val working = accounts.filter { !broken(it) && !searchLimited(it) }
        val enabled = working.count { it.id !in disabledIds }
        return if (enabled > 0) enabled else working.size
    }
}
