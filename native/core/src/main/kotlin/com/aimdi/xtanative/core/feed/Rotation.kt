package com.aimdi.xtanative.core.feed

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.ceil

/**
 * One page of posts per member, remembered for a while and merged newest
 * first, as XTA reads members that have no shared timeline to ask for
 * (lib/plugins/account_posts.dart) and as group Discover samples a group's
 * members: a capped number of members is asked per call, the ones asked least
 * recently first, so repeated calls walk through the whole group instead of
 * the same few.
 */
class MemberCache<T>(
    private val dateOf: (T) -> Instant?,
    /** How many posts of each member's page reach the merged timeline. */
    private val perMember: Int,
    /** Low on purpose: these are other people's servers, and a burst is what rate limits are for. */
    private val concurrency: Int = 2,
    private val ttlMillis: Long = TTL_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Entry<T>(val at: Long, val posts: List<T>)

    private val entries = ConcurrentHashMap<String, Entry<T>>()
    private val attemptedAt = ConcurrentHashMap<String, Long>()

    /** Forgets everything, for when the answers would now come from elsewhere. */
    fun clear() {
        entries.clear()
        attemptedAt.clear()
    }

    /** When each member last answered (epoch milliseconds), to keep beside its posts. */
    val answeredAt: Map<String, Long> get() = entries.mapValues { it.value.at }

    /**
     * What [key] answered at [at], kept from before a restart, unless something
     * newer is held. Within the cache's lifetime it is not asked again.
     */
    fun seed(key: String, posts: List<T>, at: Long) {
        val held = entries[key]
        if (held != null && held.at >= at) return
        entries[key] = Entry(at, posts)
        attemptedAt.putIfAbsent(key, at)
    }

    /** [keys], the never asked first, then the longest unasked; ties keep their order. */
    fun prioritize(keys: List<String>): List<String> = Rotation.prioritizeByAttempt(keys) { attemptedAt[it] }

    /** How many of [keys] would have to be asked right now. */
    fun pendingCount(keys: List<String>): Int = keys.count { fresh(it) == null }

    private fun fresh(key: String): List<T>? = entries[key]?.takeIf { clock() - it.at <= ttlMillis }?.posts

    /**
     * The merged timeline of [keys], newest first. At most [maxFetches] members
     * are asked; the cached ones are free and always included, and past the
     * budget what a member said last, however old, beats nothing. One member
     * failing keeps what it said last; the failure is thrown only when nothing
     * at all could be read. [onPartial] hears the timeline grow as members
     * answer, with what the others said last in their place until they do.
     */
    suspend fun merge(
        keys: List<String>,
        forceRefresh: Boolean = false,
        maxFetches: Int? = null,
        onPartial: ((List<T>) -> Unit)? = null,
        fetch: suspend (String) -> List<T>,
    ): List<T> {
        if (keys.isEmpty()) return emptyList()
        val budget = AtomicInteger(maxFetches ?: keys.size)
        val lastError = AtomicReference<Throwable?>()
        val readAnything = AtomicBoolean(false)
        val run = Painting(onPartial, if (onPartial == null) emptyMap() else held(keys))
        fun answer(key: String, posts: List<T>): List<T> {
            if (posts.isNotEmpty()) readAnything.set(true)
            return run.answer(key, posts)
        }
        val batches = mapWithConcurrency(keys, concurrency) { key ->
            val cached = if (forceRefresh) null else fresh(key)
            when {
                cached != null -> answer(key, cached)
                budget.getAndDecrement() <= 0 -> answer(key, entries[key]?.posts.orEmpty())
                else -> {
                    attemptedAt[key] = clock()
                    run.showHeld()
                    try {
                        val posts = fetch(key)
                        entries[key] = Entry(clock(), posts)
                        answer(key, posts)
                    } catch (error: Throwable) {
                        if (error is CancellationException) throw error
                        lastError.set(error)
                        run.answer(key, entries[key]?.posts.orEmpty())
                    }
                }
            }
        }
        lastError.get()?.takeIf { !readAnything.get() }?.let { throw it }
        return merged(batches)
    }

    private fun held(keys: List<String>): Map<String, List<T>> =
        keys.mapNotNull { key -> entries[key]?.posts?.takeIf { it.isNotEmpty() }?.let { key to it } }.toMap()

    private fun merged(batches: List<List<T>>): List<T> =
        batches.flatMap { it.take(perMember) }.sortedByDescending { dateOf(it) ?: Instant.MIN }

    /** One merge's answers so far, painted with what the rest said last. */
    private inner class Painting(private val emit: ((List<T>) -> Unit)?, held: Map<String, List<T>>) {
        private val held = LinkedHashMap(held)
        private val done = ArrayList<List<T>>()
        private var shownHeld = false

        @Synchronized
        fun answer(key: String, posts: List<T>): List<T> {
            val dropped = held.remove(key)
            if (emit != null) {
                done += posts
                if (posts.isNotEmpty() || !dropped.isNullOrEmpty()) paint()
            }
            return posts
        }

        /** Before the first wait: a refresh never blanks what is on screen. */
        @Synchronized
        fun showHeld() {
            if (shownHeld || held.isEmpty()) return
            shownHeld = true
            paint()
        }

        private fun paint() = emit?.invoke(merged(done + held.values))
    }

    companion object {
        /** How long a member's posts are reused before it is asked again. */
        const val TTL_MILLIS = 10 * 60 * 1000L
    }
}

/** What a rotating read brought, and how many members it has answers for. */
data class RotatingRead<T>(val rows: List<T>, val read: Int)

/** The rotation every per-member read samples its members with. */
object Rotation {
    /** How long one source may take before the others stop waiting for it. */
    const val SOURCE_MILLIS = 60_000L

    /** [keys] never attempted first, then the longest ago; ties keep the caller's order. */
    fun prioritizeByAttempt(keys: List<String>, attemptedAt: (String) -> Long?): List<String> =
        keys.distinct().withIndex()
            .sortedWith(compareBy<IndexedValue<String>> { attemptedAt(it.value) ?: Long.MIN_VALUE }.thenBy { it.index })
            .map { it.value }

    /**
     * How long one member's read may take so that [fetches] of them,
     * [concurrency] at a time, still finish within [SOURCE_MILLIS] with room
     * to spare: between 5 and 25 seconds.
     */
    fun memberBudgetMillis(fetches: Int, concurrency: Int = 2): Long {
        val waves = ceil(fetches.toDouble() / concurrency.coerceAtLeast(1)).toInt().coerceIn(1, fetches.coerceAtLeast(1))
        return (48 / waves).coerceIn(5, 25) * 1000L
    }

    /**
     * Reads up to [perLoad] members of [keys] (twice that when the reader asks
     * for more) that [cache] has no answer for, the least recently asked first,
     * merged with everything it holds. [onRows] hears the rows as they arrive.
     */
    suspend fun <T> read(
        cache: MemberCache<T>,
        keys: List<String>,
        more: Boolean,
        perLoad: Int,
        fetch: suspend (key: String, budgetMillis: Long) -> List<T>,
        onRows: (RotatingRead<T>) -> Unit = {},
    ): RotatingRead<T> {
        val fetches = if (more) perLoad * 2 else perLoad
        val budget = memberBudgetMillis(fetches)
        val ordered = cache.prioritize(keys)
        fun result(rows: List<T>) = RotatingRead(rows, ordered.size - cache.pendingCount(ordered))
        val rows = cache.merge(ordered, maxFetches = fetches, onPartial = { onRows(result(it)) }) { key -> fetch(key, budget) }
        return result(rows).also(onRows)
    }
}
