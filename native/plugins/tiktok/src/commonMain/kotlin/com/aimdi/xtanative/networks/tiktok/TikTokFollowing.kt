package com.aimdi.xtanative.networks.tiktok

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * The Following timeline, read as XTA's TikTokFollowingStore reads it
 * (account_posts.dart): a guest has no "following" feed to ask TikTok for,
 * so each account followed on the device is asked for its newest videos,
 * [concurrency] at a time, [perAccount] of each joining the timeline, newest
 * first. At most [maxFetches] accounts are asked per read, those never asked
 * first, then those asked longest ago, so many follows fill in over a few
 * reads. A page read within [ttlMillis] is reused unless the read is forced;
 * an account that fails keeps its last page, and the failure surfaces only
 * when nothing at all could be read.
 */
class TikTokFollowing(
    private val read: suspend (secUid: String) -> List<TikTokPost>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val perAccount: Int = PER_ACCOUNT,
    private val concurrency: Int = CONCURRENCY,
    private val maxFetches: Int = MAX_FETCHES,
    private val ttlMillis: Long = TTL_MILLIS,
) {
    private class Held(val at: Long, val posts: List<TikTokPost>)

    /** One read: the requests it may still make, whether anything was read, the first failure. */
    private class Run(budget: Int) {
        val budget = AtomicInteger(budget)
        val readAnything = AtomicBoolean(false)
        val failure = AtomicReference<Exception?>(null)
    }

    private val held = ConcurrentHashMap<String, Held>()
    private val attempted = ConcurrentHashMap<String, Long>()

    /**
     * The timeline of the accounts [secUids] names; [force] asks again even
     * what was just read. [onPartial] hears it grow as each account answers,
     * what the others last said standing in for them meanwhile.
     */
    suspend fun timeline(secUids: List<String>, force: Boolean = false, onPartial: ((List<TikTokPost>) -> Unit)? = null): List<TikTokPost> {
        val keys = prioritized(secUids)
        if (keys.isEmpty()) return emptyList()
        val run = Run(maxFetches)
        val answered = ConcurrentHashMap<String, List<TikTokPost>>()
        val gate = Semaphore(concurrency.coerceAtLeast(1))
        coroutineScope {
            keys.map { key ->
                async {
                    gate.withPermit { answered[key] = answer(key, force, run) }
                    onPartial?.invoke(merged(keys.map { answered[it] ?: held[it]?.posts.orEmpty() }))
                }
            }.awaitAll()
        }
        run.failure.get()?.let { if (!run.readAnything.get()) throw it }
        return merged(keys.map { answered[it].orEmpty() })
    }

    /** Forgets every page, as when the guest session starts anew. */
    fun clear() {
        held.clear()
        attempted.clear()
    }

    /** [secUids] once each, those never asked first, then those asked longest ago; ties keep their order. */
    fun prioritized(secUids: List<String>): List<String> =
        secUids.filter(String::isNotBlank).distinct().withIndex()
            .sortedWith(compareBy<IndexedValue<String>> { attempted[it.value] ?: Long.MIN_VALUE }.thenBy { it.index })
            .map { it.value }

    private suspend fun answer(key: String, force: Boolean, run: Run): List<TikTokPost> {
        val kept = held[key]
        if (!force && kept != null && clock() - kept.at <= ttlMillis) return kept.posts.also { if (it.isNotEmpty()) run.readAnything.set(true) }
        // Past the budget, what is held, stale or not, beats nothing.
        if (run.budget.getAndDecrement() <= 0) return kept?.posts.orEmpty()
        attempted[key] = clock()
        return try {
            read(key).also { posts ->
                held[key] = Held(clock(), posts)
                if (posts.isNotEmpty()) run.readAnything.set(true)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            run.failure.compareAndSet(null, e)
            kept?.posts.orEmpty()
        }
    }

    private fun merged(pages: List<List<TikTokPost>>): List<TikTokPost> =
        pages.flatMap { it.take(perAccount) }.distinctBy { it.id }.sortedByDescending { it.createdAt ?: Instant.MIN }

    companion object {
        /** XTA's AccountPostCache for TikTok: fifteen videos an account, two accounts at a time, twenty asked a read, kept ten minutes. */
        const val PER_ACCOUNT = 15
        const val CONCURRENCY = 2
        const val MAX_FETCHES = 20
        const val TTL_MILLIS = 10 * 60 * 1000L
    }
}
