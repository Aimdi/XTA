package com.aimdi.xtanative.networks.mastodon

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * The Following timeline, as XTA's MastodonFeedStore reads it. Without an
 * account no instance has a feed of followed accounts to give, so each
 * account's first page is read where it lives (its own instance, then the
 * reader's [configured] ones, then one built-in default) and the pages are
 * merged, newest first. An account's page is reused for ten minutes; a load
 * asks at most twelve accounts, two at a time, the rest showing what they
 * said last until a later load reaches them; one account failing leaves the
 * others' posts standing. Following itself stays in XTA Native.
 */
class MastodonFollowing(
    private val client: MastodonClient,
    /** The reader's own instances, home first. */
    private val configured: () -> List<String> = { emptyList() },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private var cache = newCache()
    private var cachedFor: List<String>? = null
    private val active = HashSet<AccountPostCache<MastodonPost>>()

    @Volatile
    private var epoch = 0

    /**
     * The merged timeline of [accts]: one post per address, an original
     * preferred to a boost of it, newest first by [MastodonPost.timelineDate].
     * [onPartial] hears it grow as accounts answer, beginning with what they
     * said last time. Only when no account gave anything is a failure thrown.
     * A read overtaken by [forget], or by a change of the [configured]
     * instances, delivers nothing.
     */
    suspend fun posts(accts: List<String>, forceRefresh: Boolean = false, onPartial: ((List<MastodonPost>) -> Unit)? = null): List<MastodonPost> {
        val instances = configured()
        val (cache, started) = begin(instances)
        val current = { epoch == started }
        try {
            val partial = onPartial?.let { emit -> { posts: List<MastodonPost> -> if (current()) emit(timeline(posts)) } }
            val posts = cache.merge(accts, forceRefresh, MAX_FETCHES, partial) { acct ->
                client.accountPostsAnywhere(MastodonInstances.feedCandidates(acct, instances), acct, PER_ACCOUNT)
            }
            return if (current()) timeline(posts) else emptyList()
        } finally {
            synchronized(lock) { active -= cache }
        }
    }

    /** Forgets every account's posts; reads still under way deliver nothing. */
    fun forget() {
        synchronized(lock) {
            epoch++
            cache = newCache()
        }
    }

    /**
     * The cache a read fills: a new one when the instances changed, and when
     * another read is still filling the current one, so a slower, older read
     * cannot overwrite what a newer one found.
     */
    private fun begin(instances: List<String>): Pair<AccountPostCache<MastodonPost>, Int> = synchronized(lock) {
        if (cachedFor != instances) {
            cachedFor = instances
            epoch++
            cache = newCache()
        } else if (cache in active) {
            cache = newCache()
        }
        active += cache
        cache to epoch
    }

    private fun newCache() = AccountPostCache<MastodonPost>({ it.timelineDate }, PER_ACCOUNT, CONCURRENCY, TTL_MILLIS, clock)

    private fun timeline(posts: List<MastodonPost>): List<MastodonPost> =
        MastodonFeeds.unique(emptyList(), posts).sortedByDescending { it.timelineDate ?: Instant.MIN }

    companion object {
        /** How many statuses each account gives the merged feed. */
        const val PER_ACCOUNT = 20

        /** How many accounts one load asks; the cache fills the rest on the next loads. */
        const val MAX_FETCHES = 12

        private const val CONCURRENCY = 2
        private const val TTL_MILLIS = 10 * 60 * 1000L
    }
}

/**
 * One page per account merged into one timeline and remembered briefly, as
 * XTA's account_posts.dart does for the networks with no feed of followed
 * accounts on the server. Posts without a date sort last rather than going.
 */
internal class AccountPostCache<T>(
    private val dateOf: (T) -> Instant?,
    private val perAccount: Int,
    private val concurrency: Int,
    private val ttlMillis: Long,
    private val clock: () -> Long,
) {
    private class Entry<T>(val at: Long, val posts: List<T>)

    private val entries = ConcurrentHashMap<String, Entry<T>>()

    /**
     * [keys]' pages merged, newest first, at most [perAccount] posts each.
     * A key read within the time to live is not asked again unless
     * [forceRefresh]; past [maxFetches] asks, a key gives what it said last,
     * however old. A failed key gives what it said last too; the failure is
     * thrown only when nothing at all was read.
     */
    suspend fun merge(
        keys: List<String>,
        forceRefresh: Boolean,
        maxFetches: Int?,
        onPartial: ((List<T>) -> Unit)?,
        fetch: suspend (String) -> List<T>,
    ): List<T> {
        if (keys.isEmpty()) return emptyList()
        val run = Run(maxFetches ?: keys.size, onPartial, if (onPartial == null) emptyMap() else heldFor(keys))
        val gate = Semaphore(concurrency.coerceAtLeast(1))
        val pages = coroutineScope { keys.map { key -> async { gate.withPermit { read(key, forceRefresh, run, fetch) } } }.awaitAll() }
        run.failure?.let { if (!run.readAnything) throw it }
        return merged(pages)
    }

    private suspend fun read(key: String, forceRefresh: Boolean, run: Run, fetch: suspend (String) -> List<T>): List<T> {
        if (!forceRefresh) fresh(key)?.let { return run.answer(key, it, counts = true) }
        if (!run.takeFetch()) return run.answer(key, entries[key]?.posts.orEmpty(), counts = true)
        run.showHeld()
        return try {
            val posts = fetch(key)
            entries[key] = Entry(clock(), posts)
            run.answer(key, posts, counts = true)
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            run.failed(failure)
            run.answer(key, entries[key]?.posts.orEmpty(), counts = false)
        }
    }

    private fun fresh(key: String): List<T>? = entries[key]?.takeIf { clock() - it.at <= ttlMillis }?.posts

    /** What each of [keys] said last, stale or not: shown in its place until it answers again. */
    private fun heldFor(keys: List<String>): Map<String, List<T>> =
        keys.mapNotNull { key -> entries[key]?.posts?.takeIf { it.isNotEmpty() }?.let { key to it } }.toMap()

    private fun merged(pages: List<List<T>>): List<T> = pages.flatMap { it.take(perAccount) }.sortedByDescending { dateOf(it) ?: Instant.MIN }

    /** One merge: its budget of asks, what it read, and the timeline painted as keys answer. */
    private inner class Run(budget: Int, private val emit: ((List<T>) -> Unit)?, held: Map<String, List<T>>) {
        private val budget = AtomicInteger(budget)
        private val held = LinkedHashMap(held)
        private val done = ArrayList<List<T>>()
        private var shownHeld = false

        @Volatile
        var readAnything = false
            private set

        @Volatile
        var failure: Exception? = null
            private set

        /** Takes one ask from the budget; false once it is spent. */
        fun takeFetch(): Boolean = budget.getAndDecrement() > 0

        /** Records [key]'s posts, and paints when they change what is shown. Posts kept only because a read failed do not [counts]. */
        @Synchronized
        fun answer(key: String, posts: List<T>, counts: Boolean): List<T> {
            if (counts && posts.isNotEmpty()) readAnything = true
            val dropped = held.remove(key)
            if (emit != null) {
                done += posts
                if (posts.isNotEmpty() || !dropped.isNullOrEmpty()) paint()
            }
            return posts
        }

        /** Before the first wait on the network, paints what is held, so a refresh never blanks the timeline. */
        @Synchronized
        fun showHeld() {
            if (shownHeld || held.isEmpty()) return
            shownHeld = true
            paint()
        }

        fun failed(error: Exception) {
            failure = error
        }

        private fun paint() {
            emit?.invoke(merged(done + held.values))
        }
    }
}
