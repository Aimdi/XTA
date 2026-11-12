package com.aimdi.xtanative.networks.threads

import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * One page of posts per followed account, merged into one timeline and
 * remembered briefly, as XTA's AccountPostCache reads them for networks with
 * no timeline of their own to ask for. Generic: Bluesky and Mastodon read
 * their follows the same way in XTA.
 */
internal class AccountPosts<T>(
    private val dateOf: (T) -> Instant?,
    /** How many posts of each account's page reach the merged timeline. */
    private val perAccount: Int,
    /** How many accounts are read at once. */
    private val concurrency: Int,
    private val ttlMillis: Long,
    private val clock: () -> Long,
    /** A failure that says asking more now is no use (a throttle): the rest of the load gives what is held. */
    private val stopsLoad: (Exception) -> Boolean = { (it as? ThreadsException)?.failure == ThreadsFailure.THROTTLED },
) {
    private class Entry<T>(val at: Long, val posts: List<T>)

    private val entries = HashMap<String, Entry<T>>()
    private val attemptedAt = HashMap<String, Long>()

    /** Forgets everything, for when the answers would now come from elsewhere. */
    fun clear() {
        synchronized(this) {
            entries.clear()
            attemptedAt.clear()
        }
    }

    /** When each account last answered. */
    val answeredAt: Map<String, Long> get() = synchronized(this) { entries.mapValues { it.value.at } }

    /**
     * Takes what [key] answered at [at], as a snapshot from before a restart
     * says, unless something newer is held. Within the cache window the account
     * is then not asked again, as if it had been read in this session.
     */
    fun seed(key: String, posts: List<T>, at: Long) {
        synchronized(this) {
            val held = entries[key]
            if (held != null && held.at >= at) return
            entries[key] = Entry(at, posts)
            attemptedAt.putIfAbsent(key, at)
        }
    }

    /**
     * [keys] with the ones never asked first, then the ones asked longest ago;
     * ties keep the caller's order. A capped [merge] spends its budget on the
     * front of the list, so the same order every time would read the same
     * accounts every time and never reach the rest.
     */
    fun prioritize(keys: List<String>): List<String> {
        val attempted = synchronized(this) { HashMap(attemptedAt) }
        val position = keys.withIndex().associate { it.value to it.index }
        return keys.distinct().sortedWith(compareBy<String> { attempted[it] ?: Long.MIN_VALUE }.thenBy { position.getValue(it) })
    }

    /** How many of [keys] would have to be read now: what the cache cannot answer. */
    fun pendingCount(keys: List<String>): Int = keys.count { fresh(it) == null }

    /**
     * The merged timeline for [keys], newest first and undated posts last. An
     * account that fails keeps what it said last, so one renamed handle does
     * not empty the timeline; the failure surfaces only when nothing at all
     * could be read. At most [maxFetches] accounts are asked; the rest give what
     * is held, stale or not. [onPartial] hears the timeline grow as each account
     * answers, what the others last said painted in their place.
     */
    suspend fun merge(
        keys: List<String>,
        fetch: suspend (String) -> List<T>,
        forceRefresh: Boolean = false,
        maxFetches: Int? = null,
        onPartial: ((List<T>) -> Unit)? = null,
    ): List<T> {
        if (keys.isEmpty()) return emptyList()
        val budget = AtomicInteger(maxFetches ?: keys.size)
        val run = MergeRun(onPartial, if (onPartial != null) heldFor(keys) else emptyMap())
        val answers = arrayOfNulls<List<T>>(keys.size)
        val next = AtomicInteger()
        coroutineScope {
            repeat(minOf(maxOf(concurrency, 1), keys.size)) {
                launch {
                    while (true) {
                        val index = next.getAndIncrement()
                        if (index >= keys.size) break
                        answers[index] = answer(keys[index], fetch, forceRefresh, budget, run)
                    }
                }
            }
        }
        val failure = run.failure
        // Posts kept only because their refresh failed are no answer: then the failure is what the reader needs.
        if (!run.readAnything && failure != null) throw failure
        return merged(answers.map { it.orEmpty() })
    }

    private suspend fun answer(key: String, fetch: suspend (String) -> List<T>, force: Boolean, budget: AtomicInteger, run: MergeRun): List<T> {
        if (!force) fresh(key)?.let { return run.answer(key, it) }
        // Past the budget, what the cache holds, stale or not, beats nothing.
        if (budget.getAndDecrement() <= 0) return run.answer(key, held(key))
        synchronized(this) { attemptedAt[key] = clock() }
        run.showHeld()
        return try {
            val posts = fetch(key)
            synchronized(this) { entries[key] = Entry(clock(), posts) }
            run.answer(key, posts)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            run.failed(e)
            if (stopsLoad(e)) budget.set(0)
            run.answer(key, held(key), read = false)
        }
    }

    private fun fresh(key: String): List<T>? = synchronized(this) {
        entries[key]?.takeIf { clock() - it.at <= ttlMillis }?.posts
    }

    private fun held(key: String): List<T> = synchronized(this) { entries[key]?.posts.orEmpty() }

    private fun heldFor(keys: List<String>): Map<String, List<T>> = synchronized(this) {
        keys.mapNotNull { key -> entries[key]?.posts?.takeIf { it.isNotEmpty() }?.let { key to it } }.toMap()
    }

    private fun merged(batches: Collection<List<T>>): List<T> =
        batches.flatMap { it.take(perAccount) }.sortedWith(compareByDescending<T> { dateOf(it) ?: Instant.MIN })

    /** One merge: the accounts answered so far, and what the rest last said, painted together. */
    private inner class MergeRun(private val emit: ((List<T>) -> Unit)?, held: Map<String, List<T>>) {
        private val held = LinkedHashMap(held)
        private val done = mutableListOf<List<T>>()
        private var shownHeld = false

        @Volatile
        var readAnything = false
            private set

        @Volatile
        var failure: Exception? = null
            private set

        fun failed(error: Exception) {
            failure = error
        }

        /** Records [key]'s answer, and repaints when it changed what is shown. [read] is false for posts kept only because their refresh failed. */
        fun answer(key: String, posts: List<T>, read: Boolean = true): List<T> {
            val paint = synchronized(this) {
                if (read && posts.isNotEmpty()) readAnything = true
                val dropped = held.remove(key)
                if (emit == null) return@synchronized null
                done += posts
                if (posts.isNotEmpty() || !dropped.isNullOrEmpty()) merged(done + held.values) else null
            }
            paint?.let { emit?.invoke(it) }
            return posts
        }

        /** Paints what is held before the first wait for the network, so a refresh does not blank the feed. */
        fun showHeld() {
            val paint = synchronized(this) {
                if (emit == null || shownHeld || held.isEmpty()) return@synchronized null
                shownHeld = true
                merged(done + held.values)
            }
            paint?.let { emit?.invoke(it) }
        }
    }
}
