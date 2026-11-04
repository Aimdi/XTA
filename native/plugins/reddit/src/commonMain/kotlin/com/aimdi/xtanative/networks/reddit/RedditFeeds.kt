package com.aimdi.xtanative.networks.reddit

import com.aimdi.xtanative.networks.Page
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** One cached listing: a community read in one order and window. The page size is not part of it: every surface asks for the same page. */
data class RedditListingKey(val subreddit: String, val sort: RedditSort, val timeFilter: RedditTimeFilter?)

/**
 * The one place a listing is fetched (XTA's `RedditListingCache`), so the
 * surfaces showing Reddit posts read the same fetch instead of each paying
 * for their own. It holds the fetches rather than their results, so surfaces
 * that ask at the same moment join one request. A failure is remembered
 * briefly too: a private or throttled community fails the same way for all.
 */
class RedditListingCache(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val ttlMillis: Long = TTL_MILLIS,
    private val failureTtlMillis: Long = FAILURE_TTL_MILLIS,
    private val maxEntries: Int = MAX_ENTRIES,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /**
     * One fetch and when it settled. It settles inside the fetch itself, so
     * whoever sees the fetch done sees when it settled too: a completion
     * handler runs after an `await` can already have returned.
     */
    private inner class Entry(read: suspend () -> Page<RedditPost>) {
        @Volatile var settledAt: Long? = null

        @Volatile var failed = false

        val fetch: Deferred<Page<RedditPost>> = scope.async(start = CoroutineStart.LAZY) {
            try {
                read().also { settle(failed = false) }
            } catch (e: Throwable) {
                settle(failed = true)
                throw e
            }
        }

        private fun settle(failed: Boolean) {
            this.failed = failed
            settledAt = clock()
        }
    }

    /** Least recently used first. */
    private val entries = LinkedHashMap<RedditListingKey, Entry>(16, 0.75f, true)

    val size: Int get() = synchronized(entries) { entries.size }

    /**
     * [key]'s listing, from a recent read when there is one. [forceRefresh],
     * a pull to refresh, goes past it: else the answer would forever be the
     * one already on screen.
     */
    suspend fun listing(key: RedditListingKey, forceRefresh: Boolean = false, fetch: suspend () -> Page<RedditPost>): Page<RedditPost> {
        val entry = synchronized(entries) {
            prune()
            entries[key]?.takeUnless { forceRefresh } ?: start(key, fetch)
        }
        return entry.fetch.await()
    }

    private fun start(key: RedditListingKey, fetch: suspend () -> Page<RedditPost>): Entry {
        val entry = Entry(fetch)
        entries[key] = entry
        while (entries.size > maxEntries) entries.remove(entries.keys.first())
        entry.fetch.start()
        return entry
    }

    /** A fetch in flight is never stale: joining it is the point. */
    private fun prune() {
        val now = clock()
        entries.values.removeAll { entry ->
            val settledAt = entry.settledAt
            settledAt != null && now - settledAt >= if (entry.failed) failureTtlMillis else ttlMillis
        }
    }

    /** Everything cached answered another question: the reader signed in or out, or chose another route. */
    fun clear() = synchronized(entries) { entries.clear() }

    companion object {
        /** Short: the point is that moving between surfaces does not fetch a community thrice, not a frozen feed. */
        const val TTL_MILLIS = 3 * 60 * 1000L

        /** How long a community that just failed is left alone. */
        const val FAILURE_TTL_MILLIS = 30 * 1000L

        const val MAX_ENTRIES = 24
    }
}

/**
 * Followed communities as one feed (XTA's `RedditPostSource`): the first
 * page of each through the shared [cache], pinned posts dropped, newest
 * first. Reddit pages each listing on its own, so there is no cursor across
 * them; one page each, interleaved by date, is what makes one feed possible.
 */
class RedditFollowingFeed(
    private val client: RedditClient,
    private val cache: RedditListingCache = RedditListingCache(),
) {
    @Volatile private var lastSession: RedditSession? = null

    /**
     * At most [limit] posts of each of [subreddits], newest first. One
     * unreachable community does not empty the feed of the others: it adds
     * nothing. An empty answer is a real one: a community that is gone takes
     * its posts with it.
     */
    suspend fun posts(
        subreddits: Collection<String>,
        sort: RedditSort = RedditSort.HOT,
        timeFilter: RedditTimeFilter = RedditTimeFilter.DAY,
        hideNsfw: Boolean = false,
        limit: Int = RedditClient.PAGE_SIZE,
        forceRefresh: Boolean = false,
        session: RedditSession = RedditSession.ANONYMOUS,
    ): List<RedditPost> {
        val names = subreddits.distinct()
        if (names.isEmpty()) return emptyList()
        forgetOnSessionChange(session)
        val window = timeFilter.takeIf { sort.usesTimeFilter }
        val pages = coroutineScope {
            names.map { name ->
                async {
                    postsOf(name, RedditListingKey(name, sort, window), forceRefresh, hideNsfw, limit) {
                        client.listing(name, sort, timeFilter, session = session)
                    }
                }
            }.awaitAll()
        }
        return newestFirst(pages.flatten())
    }

    private suspend fun postsOf(
        name: String,
        key: RedditListingKey,
        forceRefresh: Boolean,
        hideNsfw: Boolean,
        limit: Int,
        fetch: suspend () -> Page<RedditPost>,
    ): List<RedditPost> = try {
        filterRedditPosts(cache.listing(key, forceRefresh, fetch).items.filterNot { it.stickied }, hideNsfw).take(limit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    /** Signing in or out and choosing the account-free route change which Reddit answers, so what was cached is not the answer. */
    private fun forgetOnSessionChange(session: RedditSession) {
        if (lastSession != session) cache.clear()
        lastSession = session
    }
}

/**
 * Newest first. A pair of which either has no date has no chronological
 * answer, so it falls back to score rather than inventing one. That order is
 * not total when dated and undated posts meet, which the JVM's sort may
 * throw on, so a merge sort that never throws keeps it.
 */
internal fun newestFirst(posts: List<RedditPost>): List<RedditPost> {
    if (posts.size < 2) return posts
    val half = posts.size / 2
    val left = newestFirst(posts.subList(0, half))
    val right = newestFirst(posts.subList(half, posts.size))
    val merged = ArrayList<RedditPost>(posts.size)
    var l = 0
    var r = 0
    while (l < left.size && r < right.size) merged += if (newer(right[r], left[l])) right[r++] else left[l++]
    return merged + left.drop(l) + right.drop(r)
}

/** Whether [a] goes strictly before [b]. */
private fun newer(a: RedditPost, b: RedditPost): Boolean {
    val first = a.createdAt
    val second = b.createdAt
    return if (first == null || second == null) a.score > b.score else first.isAfter(second)
}

/**
 * The pictures of gallery posts a listing carried without them (XTA's
 * `RedditGalleryLoader`), from the post's own JSON: one request per post,
 * remembered for the session, shared by two cards asking at once, and
 * bounded so it does not grow for the app's life. An empty answer is also
 * what a refusal looks like, so it is asked again a few times before it is
 * taken as final.
 */
class RedditGalleryLoader(
    private val fetch: suspend (String) -> List<String>,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    constructor(client: RedditClient) : this(client::galleryImages)

    private val lock = Any()

    /** Answers by permalink, oldest first. */
    private val known = LinkedHashMap<String, List<String>>()
    private val emptyAnswers = LinkedHashMap<String, Int>()
    private val inFlight = HashMap<String, Deferred<List<String>>>()

    val size: Int get() = synchronized(lock) { known.size }

    /** What is already known about [permalink]'s gallery, without asking. */
    fun known(permalink: String): List<String>? = synchronized(lock) { known[permalink] }

    /** [permalink]'s pictures: remembered, from a fetch already in flight, or freshly asked for. */
    suspend fun images(permalink: String): List<String> {
        val fetching = synchronized(lock) {
            val answer = known[permalink]
            if (answer != null && (answer.isNotEmpty() || (emptyAnswers[permalink] ?: 0) >= EMPTY_RETRIES)) return answer
            inFlight[permalink] ?: start(permalink)
        }
        return fetching.await()
    }

    private fun start(permalink: String): Deferred<List<String>> {
        val job = scope.async(start = CoroutineStart.LAZY) {
            try {
                remember(permalink, fetch(permalink))
            } finally {
                // Settled or failed, the fetch leaves the map before anyone can see it done:
                // a failed one pinned there would refuse this post for good.
                val self = coroutineContext[Job]
                synchronized(lock) { if (inFlight[permalink] === self) inFlight.remove(permalink) }
            }
        }
        inFlight[permalink] = job
        job.start()
        return job
    }

    private fun remember(permalink: String, images: List<String>): List<String> = synchronized(lock) {
        if (images.isEmpty()) {
            emptyAnswers[permalink] = (emptyAnswers.remove(permalink) ?: 0) + 1
            trim(emptyAnswers)
        } else {
            emptyAnswers.remove(permalink)
        }
        known.remove(permalink)
        known[permalink] = images
        trim(known)
        images
    }

    private fun trim(map: LinkedHashMap<String, *>) {
        while (map.size > CACHE_CAP) map.remove(map.keys.first())
    }

    companion object {
        /** A session's worth of scrolling, not the app's lifetime. */
        const val CACHE_CAP = 200

        /** How often an empty answer is asked again before it is final: enough for a refusal to pass, few enough not to pelt Reddit. */
        const val EMPTY_RETRIES = 3
    }
}
