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
 * The public page has no cursor. Asking for more reads it again, at most
 * [GUEST_EXTRA_READS] times, for whatever Meta recommends that was not shown
 * yet; the feed ends when a read brings nothing new.
 */
class ThreadsForYou(private val direct: ThreadsDirectClient) {
    /** Whose page a read would ask for now. */
    val source: ThreadsForYouSource
        get() = if (direct.currentSession.hasBearer && !direct.isSessionParked) ThreadsForYouSource.SESSION else ThreadsForYouSource.GUEST

    /** The first page, or the one [cursor] names. */
    suspend fun page(cursor: String? = null): ThreadsForYouPage {
        guestRead(cursor)?.let { return guest(it) }
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
        val posts = direct.guestForYou().items
        val next = (GUEST_CURSOR + (read + 1)).takeIf { posts.isNotEmpty() && read < GUEST_EXTRA_READS }
        return ThreadsForYouPage(ThreadsForYouSource.GUEST, posts, next)
    }

    /** Which re-read of the public page [cursor] asks for; null for a session's cursor, or none. */
    private fun guestRead(cursor: String?): Int? = cursor?.takeIf { it.startsWith(GUEST_CURSOR) }?.removePrefix(GUEST_CURSOR)?.toIntOrNull()

    companion object {
        /** How many more times the public page is read for a feed the reader keeps scrolling. */
        const val GUEST_EXTRA_READS = 2

        private const val GUEST_CURSOR = "guest:"

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
 * and keeps it when that read fails; a page with nothing new ends the feed.
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
            mutable.value = ThreadsForYouState(
                posts = page.posts.distinctBy(ThreadsPost::id),
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
            val page = read(cursor)
            mutable.update { shown ->
                val known = shown.posts.mapTo(HashSet(), ThreadsPost::id)
                val fresh = page.posts.distinctBy(ThreadsPost::id).filter { it.id !in known }
                val next = page.next?.takeIf { it.isNotEmpty() && it != cursor && fresh.isNotEmpty() }
                shown.copy(posts = shown.posts + fresh, next = next, loadingMore = false)
            }
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
}
