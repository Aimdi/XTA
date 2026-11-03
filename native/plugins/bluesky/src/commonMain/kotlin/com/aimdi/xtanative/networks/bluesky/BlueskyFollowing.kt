package com.aimdi.xtanative.networks.bluesky

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * The Following timeline, read as XTA reads it. A public AppView has no
 * "my following feed" to ask for, so each locally followed account's author
 * feed is read, [Bluesky.POSTS_PER_ACCOUNT] posts each, two accounts at a
 * time, and the pages merged ([BlueskyTimeline.stabilize]).
 *
 * At most [maxFetches] accounts are asked per read: those never asked first,
 * then those asked longest ago, so hundreds of imported follows fill in over
 * a few reads instead of hundreds of requests at once. A page read within
 * [ttlMillis] is reused unless the read is forced. An account that fails
 * keeps its last page in the timeline, and makes the next read ask every
 * account again. Everything is forgotten when the client's AppView changes.
 */
class BlueskyFollowing(
    private val client: BlueskyClient,
    /**
     * The author-feed filter of every page: null for the AppView's default,
     * [Bluesky.AUTHOR_FEED_NO_REPLIES] for a feed that hides replies.
     */
    private val filter: String? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxFetches: Int = Bluesky.MAX_ACCOUNTS_PER_LOAD,
    private val ttlMillis: Long = TTL_MILLIS,
) {
    /** A read's timeline, and the first failure among its accounts, which is set even when the others filled the timeline. */
    data class Result(val posts: List<BlueskyPost>, val error: Throwable?)

    private class Held(val posts: List<BlueskyPost>, val at: Long)

    /** One read: the AppView it reads, whether fresh pages are read again, how many requests it may still make, what failed. */
    private class Run(val server: String, val refresh: Boolean, val budget: AtomicInteger, val errors: ConcurrentLinkedQueue<Throwable>)

    private val held = ConcurrentHashMap<String, Held>()
    private val attemptedAt = ConcurrentHashMap<String, Long>()
    private val failures = ConcurrentHashMap<String, Throwable>()
    private val requests = ConcurrentHashMap<String, Int>()

    @Volatile
    private var appView: String? = null

    /** The merged timeline of [actors] (handles or DIDs); [force] asks again even what was just read. */
    suspend fun read(actors: List<String>, force: Boolean = false): Result {
        val server = enter()
        val ordered = prioritized(actors)
        val run = Run(server, force || ordered.any(failures::containsKey), AtomicInteger(maxFetches), ConcurrentLinkedQueue())
        val gate = Semaphore(CONCURRENCY)
        val pages = coroutineScope {
            ordered.map { actor -> async { gate.withPermit { page(actor, run) } } }.awaitAll()
        }
        val failed = ordered.filter(failures::containsKey)
        val posts = pages.flatMap { it.take(Bluesky.POSTS_PER_ACCOUNT) } + failed.flatMap { held[it]?.posts.orEmpty() }
        return Result(BlueskyTimeline.stabilize(posts), run.errors.peek() ?: failed.firstNotNullOfOrNull { failures[it] })
    }

    /** As [read], for a group's feed: an error only when nothing at all could be read. */
    suspend fun posts(actors: List<String>, force: Boolean = false): List<BlueskyPost> {
        val result = read(actors, force)
        val error = result.error
        if (result.posts.isEmpty() && error != null) throw error
        return result.posts
    }

    /** How many of [actors] a read would still ask for: failed, or not read within [ttlMillis]. */
    fun pending(actors: List<String>): Int {
        if (appView != client.baseUrl) return actors.distinct().size
        val now = clock()
        return actors.distinct().count { actor ->
            failures.containsKey(actor) || held[actor].let { it == null || now - it.at > ttlMillis }
        }
    }

    /** The AppView this read goes to; what another one answered is forgotten. */
    private fun enter(): String {
        val current = client.baseUrl
        if (appView != current) {
            held.clear()
            attemptedAt.clear()
            failures.clear()
            requests.clear()
            appView = current
        }
        return current
    }

    /** Never asked first, then failed ones, then by how long ago each was read (failed: asked). */
    private fun prioritized(actors: List<String>): List<String> = actors.distinct().sortedWith(
        compareBy<String> { attemptedAt.containsKey(it) }
            .thenBy { !failures.containsKey(it) }
            .thenBy { (if (failures.containsKey(it)) attemptedAt[it] else held[it]?.at) ?: 0L },
    )

    /** What [actor] adds to the timeline: its fresh page, else a new one while the budget lasts, else its last one (or none). */
    private suspend fun page(actor: String, run: Run): List<BlueskyPost> {
        val kept = held[actor]
        return when {
            !run.refresh && kept != null && clock() - kept.at <= ttlMillis -> kept.posts
            run.budget.getAndDecrement() <= 0 -> kept?.posts.orEmpty()
            else -> fetch(actor, run) ?: kept?.posts.orEmpty()
        }
    }

    /** The account's page, or null when it failed (recorded, so the next read asks again). */
    private suspend fun fetch(actor: String, run: Run): List<BlueskyPost>? {
        val request = requests.merge(actor, 1) { old, one -> old + one }
        attemptedAt[actor] = clock()
        return try {
            check(client.baseUrl == run.server) { "Bluesky AppView changed" }
            val posts = client.authorFeed(actor, limit = Bluesky.POSTS_PER_ACCOUNT, filter = filter).items
            if (requests[actor] != request) return held[actor]?.posts ?: posts
            held[actor] = Held(posts, clock())
            failures.remove(actor)
            posts
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (requests[actor] == request) failures[actor] = error
            run.errors += error
            null
        }
    }

    private companion object {
        const val CONCURRENCY = 2

        /** How long an account's page is reused: the tab, the home timeline and every group read the same accounts. */
        const val TTL_MILLIS = 10 * 60 * 1000L
    }
}
