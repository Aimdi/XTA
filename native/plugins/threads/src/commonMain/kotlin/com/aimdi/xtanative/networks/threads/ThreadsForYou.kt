package com.aimdi.xtanative.networks.threads

import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex

/** Whose For you page was read. */
enum class ThreadsForYouSource {
    /** The pasted Bearer session's own timeline, from the app API. */
    SESSION,

    /** What threads.com's front page recommends to a logged-out browser. */
    GUEST,
}

/** One page of a For you read. */
data class ThreadsForYouPage(
    val source: ThreadsForYouSource,
    val posts: List<ThreadsPost>,
    /** Where the next page starts; null when there is none. */
    val next: String? = null,
    /** Why the session's own page was not read and the public one stands in; null when it was not tried or it answered. */
    val sessionProblem: ThreadsFailure? = null,
)

/**
 * Threads' own recommended feed, read and never acted on: nothing is liked,
 * followed, reposted or reported as seen.
 *
 * With a pasted `IGT:2` Bearer it is that account's For you timeline from the
 * app API, page after page by Meta's `next_max_id`. Without one, or while the
 * session is parked, or when Meta refuses the session, it is the public front
 * page of threads.com as a logged-out browser gets it. Pasted cookies alone
 * read the public page too: no cookie request for the timeline is known to
 * work, and an untried one is not worth a reader's login.
 *
 * The public page goes on where it names a cursor ([ThreadsNextPages]).
 * Where it names none, or Meta does not answer the next page as expected,
 * asking for more reads the page again, at most [GUEST_EXTRA_READS] times,
 * for whatever Meta recommends that was not shown yet. Then, as the reader
 * scrolls on, it explores: one topic or community page at a time from
 * [explore] (those followed here first, then those of the posts shown),
 * each once per selection, at most [MAX_EXPLORED]; and when the front page
 * shows a guest nothing, exploring stands in for it.
 */
class ThreadsForYou(
    private val direct: ThreadsDirectClient,
    /** The topics to explore, in order: a topic's name, or a community's page address. */
    private val explore: () -> List<String> = { emptyList() },
) {
    private val explored = LinkedHashSet<String>()
    /** Whose page a read would ask for now. */
    val source: ThreadsForYouSource
        get() = if (direct.currentSession.hasBearer && !direct.isSessionParked) ThreadsForYouSource.SESSION else ThreadsForYouSource.GUEST

    /** The first page, or the one [cursor] names. */
    suspend fun page(cursor: String? = null): ThreadsForYouPage {
        if (cursor == null) synchronized(explored) { explored.clear() }
        if (cursor?.startsWith(EXPLORE_CURSOR) == true) return explorePage()
        guestRead(cursor)?.let { return guest(it) }
        if (ThreadsNextPage.decode(cursor) != null) return guestMore(cursor!!)
        if (source == ThreadsForYouSource.GUEST) return guest(0)
        return try {
            val page = direct.forYouTimeline(cursor)
            ThreadsForYouPage(ThreadsForYouSource.SESSION, page.items, page.next)
        } catch (e: ThreadsException) {
            // Only a first page falls back: a later one failing keeps the session's feed, and says why under it.
            if (cursor != null || e.failure !in SESSION_REFUSALS) throw e
            guest(0).copy(sessionProblem = e.failure)
        }
    }

    private suspend fun guest(read: Int): ThreadsForYouPage {
        val page = direct.guestForYou()
        if (read == 0 && page.items.isEmpty() && toExplore() != null) return explorePage()
        val next = page.next.takeIf { read == 0 }
            ?: (GUEST_CURSOR + (read + 1)).takeIf { page.items.isNotEmpty() && read < GUEST_EXTRA_READS }
            ?: exploreNext()
        return ThreadsForYouPage(ThreadsForYouSource.GUEST, page.items, next)
    }

    /** The next topic or community page to explore, read; nothing more when every one was. */
    private suspend fun explorePage(): ThreadsForYouPage {
        val topic = synchronized(explored) { toExplore()?.also { explored += it.lowercase() } }
            ?: return ThreadsForYouPage(ThreadsForYouSource.GUEST, emptyList(), null)
        val found = if (topic.startsWith("https://")) direct.communityPage(topic) else direct.searchFound(topic, tag = true)
        return ThreadsForYouPage(ThreadsForYouSource.GUEST, found.posts, exploreNext())
    }

    private fun toExplore(): String? = synchronized(explored) {
        if (explored.size >= MAX_EXPLORED) return null
        explore().map { it.trim() }.firstOrNull { it.isNotEmpty() && it.lowercase() !in explored }
    }

    /** A cursor of its own for each topic, so a feed never takes one for the page it came from. */
    private fun exploreNext(): String? = toExplore()?.let { EXPLORE_CURSOR + synchronized(explored) { explored.size } }

    /** The public page's next page; one Meta does not answer as expected gives way to reading the page again. */
    private suspend fun guestMore(cursor: String): ThreadsForYouPage {
        val more = direct.moreRecommended(cursor)
        // CI's live probe: Meta answers a guest's next front page with nothing, while reading the page again brings five new posts.
        if (more.refused || more.items.isEmpty()) return guest(1)
        return ThreadsForYouPage(ThreadsForYouSource.GUEST, more.items, more.next ?: (GUEST_CURSOR + 1))
    }

    /** Which re-read of the public page [cursor] asks for; null for a session's cursor, or none. */
    private fun guestRead(cursor: String?): Int? = cursor?.takeIf { it.startsWith(GUEST_CURSOR) }?.removePrefix(GUEST_CURSOR)?.toIntOrNull()

    companion object {
        /** How many more times the public page is read for a feed the reader keeps scrolling: each read brought five posts not seen before (CI). */
        const val GUEST_EXTRA_READS = 4

        /** How many topic or community pages one selection explores at most. */
        const val MAX_EXPLORED = 12

        private const val GUEST_CURSOR = "guest:"
        private const val EXPLORE_CURSOR = "explore:"

        /** What Meta says to a session that the public page can stand in for. */
        private val SESSION_REFUSALS = setOf(ThreadsFailure.THROTTLED, ThreadsFailure.UNAUTHORIZED, ThreadsFailure.SESSION_SUSPENDED)
    }
}

/** Where the For you feed stands. */
data class ThreadsForYouState(
    val posts: List<ThreadsPost> = emptyList(),
    /** Whose page [posts] are; null until one was read or restored. */
    val source: ThreadsForYouSource? = null,
    val next: String? = null,
    /** The first page is being read, or read again. */
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    /** A first page has answered, with posts or without. */
    val loaded: Boolean = false,
    /** Why the first page did not arrive. */
    val problem: Throwable? = null,
    /** Why the next page did not arrive. */
    val moreProblem: Throwable? = null,
    /** Why the public page stands in for the session's. */
    val sessionProblem: ThreadsFailure? = null,
    /** When [posts]' first page was read. */
    val readAt: Instant? = null,
) {
    /** Every page was read: nothing more to ask for. */
    val ended: Boolean get() = loaded && next == null && posts.isNotEmpty()

    /** Meta showed a guest no For you page: its own needs signing in. */
    val needsSignIn: Boolean get() = loaded && posts.isEmpty() && problem == null && source == ThreadsForYouSource.GUEST

    /** Meta is refusing for now, and until when it said so. */
    val throttledUntil: Instant? get() = (problem as? ThreadsException)?.takeIf { it.failure == ThreadsFailure.THROTTLED }?.retryAt
}

/**
 * The For you feed as a screen shows it: read page by page with [read], each
 * post once. A refresh keeps what is shown until the new first page arrives,
 * and keeps it when that read fails; a guest's refresh puts the new posts
 * above those shown, so the public page's few posts grow into a feed. A
 * page with nothing new is passed over for the one it names, twice at most,
 * and then ends the feed.
 * While Meta is rate limiting, nothing is asked again before the time it
 * named: asking within seconds is how a throttle becomes a ban.
 */
class ThreadsForYouFeed(
    private val read: suspend (cursor: String?) -> ThreadsForYouPage,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutable = MutableStateFlow(ThreadsForYouState())
    val state: StateFlow<ThreadsForYouState> = mutable.asStateFlow()

    private val reading = Mutex()

    /** What the reader last saw, painted before anything is asked; nothing when posts are shown already. */
    fun restore(posts: List<ThreadsPost>, source: ThreadsForYouSource?, readAt: Instant?) {
        if (posts.isEmpty()) return
        mutable.update { if (it.posts.isEmpty()) it.copy(posts = posts.distinctBy(ThreadsPost::id), source = source, readAt = readAt) else it }
    }

    /** Whether a read may leave now: not while one is under way, nor before a rate limit's end. */
    val mayAsk: Boolean get() = !reading.isLocked && !waitsForMeta()

    /** Reads the first page again. */
    suspend fun refresh() {
        if (waitsForMeta() || !reading.tryLock()) return
        try {
            mutable.update { it.copy(loading = true, problem = null, moreProblem = null) }
            val page = read(null)
            val before = mutable.value
            val kept = if (page.source == ThreadsForYouSource.GUEST && before.source == ThreadsForYouSource.GUEST) before.posts else emptyList()
            mutable.value = ThreadsForYouState(
                posts = (page.posts + kept).distinctBy(ThreadsPost::id).take(KEPT),
                source = page.source,
                next = page.next?.ifEmpty { null },
                loaded = true,
                sessionProblem = page.sessionProblem,
                readAt = Instant.ofEpochMilli(clock()),
            )
        } catch (e: CancellationException) {
            mutable.update { it.copy(loading = false) }
            throw e
        } catch (e: Exception) {
            mutable.update { it.copy(loading = false, loaded = true, problem = e) }
        } finally {
            reading.unlock()
        }
    }

    /** Reads the page after the ones shown; nothing while another read runs, after a failure until [retryMore], or at the end. */
    suspend fun more() {
        if (mutable.value.moreProblem != null) return
        nextPage()
    }

    /** Asks again for the page that failed. */
    suspend fun retryMore() {
        mutable.update { it.copy(moreProblem = null) }
        nextPage()
    }

    private suspend fun nextPage() {
        val cursor = mutable.value.next ?: return
        if (!reading.tryLock()) return
        try {
            mutable.update { it.copy(loadingMore = true, moreProblem = null) }
            var asked = cursor
            repeat(EMPTY_PAGES_PASSED + 1) {
                val page = read(asked)
                val known = mutable.value.posts.mapTo(HashSet(), ThreadsPost::id)
                val fresh = page.posts.distinctBy(ThreadsPost::id).filter { it.id !in known }
                val next = page.next?.takeIf { it.isNotEmpty() && it != asked }
                if (fresh.isNotEmpty() || next == null) {
                    mutable.update { it.copy(posts = it.posts + fresh, next = next.takeIf { fresh.isNotEmpty() }, loadingMore = false) }
                    return
                }
                asked = next
            }
            mutable.update { it.copy(next = null, loadingMore = false) }
        } catch (e: CancellationException) {
            mutable.update { it.copy(loadingMore = false) }
            throw e
        } catch (e: Exception) {
            mutable.update { it.copy(loadingMore = false, moreProblem = e) }
        } finally {
            reading.unlock()
        }
    }

    /** Forgets the feed, for when the session it was read with is gone. */
    fun forget() {
        mutable.value = ThreadsForYouState()
    }

    private fun waitsForMeta(): Boolean = mutable.value.throttledUntil?.let { clock() < it.toEpochMilli() } ?: false

    companion object {
        /** How many posts a guest's selection keeps as it grows over refreshes. */
        const val KEPT = 300

        /** How many pages with nothing new a read for more passes over before the feed ends. */
        const val EMPTY_PAGES_PASSED = 2
    }
}
