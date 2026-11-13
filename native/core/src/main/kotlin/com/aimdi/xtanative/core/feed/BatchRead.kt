package com.aimdi.xtanative.core.feed

import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [mapper] over [items], at most [concurrency] at once, the results in the
 * order of [items]: a large group cannot open every search at once and trip X
 * into a cascade of refusals.
 */
suspend fun <E, R> mapWithConcurrency(items: List<E>, concurrency: Int, mapper: suspend (E) -> R): List<R> {
    if (items.isEmpty()) return emptyList()
    val gate = Semaphore(concurrency.coerceAtLeast(1))
    return coroutineScope { items.map { item -> async { gate.withPermit { mapper(item) } } }.awaitAll() }
}

/** A read that took longer than its share of the time. */
class ReadTimeoutException(message: String) : Exception(message)

/** How far a batched read has come: what each key brought, which of them failed, of how many. */
data class BatchState<T>(
    val results: Map<String, T> = emptyMap(),
    val failed: Set<String> = emptySet(),
    val total: Int = 0,
    val loading: Boolean = false,
)

/**
 * Reads a set of keyed batches a few at a time, as XTA's group feed reads its
 * chunks (lib/group/batch_read_store.dart): each batch shows as soon as it
 * answers, one that takes longer than [batchMillis] fails, and the load as a
 * whole ends after [loadMillis] with whatever is unfinished failed. A retry
 * reads only the failed batches again; a cancelled or superseded load changes
 * nothing.
 */
class BatchRead<T>(private val batchMillis: Long = 35_000, private val loadMillis: Long = 90_000) {
    private val current = MutableStateFlow(BatchState<T>())
    private val generation = AtomicInteger()

    @Volatile
    private var running: Job? = null

    val state: StateFlow<BatchState<T>> = current.asStateFlow()

    /** Stops the load under way; what it already brought stays. */
    fun cancel() {
        generation.incrementAndGet()
        running?.cancel()
        running = null
        current.update { if (it.loading) it.copy(loading = false) else it }
    }

    /** Stops the load under way and forgets everything. */
    fun reset() {
        cancel()
        current.value = BatchState()
    }

    /**
     * Reads [keys]; with [retryFailed], only those that failed or never
     * answered, the others keeping what they brought. [onError] turns a
     * failure into a result, which [failed] recognises. The results come in
     * the order of [keys]. Throws [CancellationException] when cancelled or
     * superseded by another load.
     */
    suspend fun load(
        keys: Collection<String>,
        fetch: suspend (String) -> T,
        onError: (Throwable) -> T,
        failed: (T) -> Boolean,
        retryFailed: Boolean = false,
        concurrency: Int = 3,
        onProgress: ((List<T>) -> Unit)? = null,
    ): List<T> {
        cancel()
        val mine = generation.get()
        val all = keys.distinct()
        val run = Run(all, if (retryFailed) current.value else BatchState(), failed, onProgress) { generation.get() == mine }
        return coroutineScope {
            val work = async(start = CoroutineStart.LAZY) { run.read(fetch, onError, concurrency) }
            running = work
            if (generation.get() != mine) work.cancel()
            try {
                work.await()
            } finally {
                run.publish(loading = false)
            }
            all.map { run.result(it) }
        }
    }

    /** One load: what it has brought so far, published while it is the current one. */
    private inner class Run(
        private val all: List<String>,
        before: BatchState<T>,
        private val failed: (T) -> Boolean,
        private val onProgress: ((List<T>) -> Unit)?,
        private val isCurrent: () -> Boolean,
    ) {
        private val results = LinkedHashMap<String, T>(before.results.filterKeys { it in all })
        private val errors = LinkedHashSet(before.failed.filter { it in all })
        private val pending = all.filter { it !in results || it in errors }
        private val remaining = LinkedHashSet(pending)

        suspend fun read(fetch: suspend (String) -> T, onError: (Throwable) -> T, concurrency: Int) {
            publish(loading = true)
            val deadline = System.nanoTime() + loadMillis * 1_000_000
            withTimeoutOrNull(loadMillis) {
                mapWithConcurrency(pending, concurrency) { key ->
                    // The time-out may cancel from another thread just after a queued
                    // batch got its turn: one whose turn comes too late is not started.
                    if (System.nanoTime() < deadline) record(key, attempt(key, fetch, onError))
                }
            }
            // The load ran out of time: what never answered failed, and a retry reads it again.
            synchronized(this) { remaining.toList() }.forEach { record(it, onError(ReadTimeoutException("No answer within ${loadMillis}ms"))) }
        }

        private suspend fun attempt(key: String, fetch: suspend (String) -> T, onError: (Throwable) -> T): T = try {
            // Boxed: a result may itself be null.
            val answer = withTimeoutOrNull(batchMillis) { Box(fetch(key)) }
            if (answer == null) onError(ReadTimeoutException("$key gave no answer within ${batchMillis}ms")) else answer.value
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            onError(error)
        }

        private fun record(key: String, value: T) {
            if (!isCurrent()) throw CancellationException("A newer load replaced this one")
            val soFar = synchronized(this) {
                results[key] = value
                remaining -= key
                if (failed(value)) errors += key else errors -= key
                results.values.toList()
            }
            onProgress?.invoke(soFar)
            publish(loading = true)
        }

        fun publish(loading: Boolean) {
            if (!isCurrent()) return
            current.value = synchronized(this) { BatchState(LinkedHashMap(results), LinkedHashSet(errors), all.size, loading) }
        }

        @Suppress("UNCHECKED_CAST")
        fun result(key: String): T = synchronized(this) { results[key] as T }
    }

    private class Box<T>(val value: T)
}
