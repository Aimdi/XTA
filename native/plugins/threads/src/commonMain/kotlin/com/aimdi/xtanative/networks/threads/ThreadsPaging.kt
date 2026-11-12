package com.aimdi.xtanative.networks.threads

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One page after a list's first: what it brought, where the one after starts, and whether Meta answered in a shape not understood. */
data class ThreadsMore<T>(val items: List<T>, val next: String?, val refused: Boolean = false)

/** Where the pages after a list's first stand. */
data class ThreadsPagingState<T>(
    /** What the pages after the first brought, each once and none the first page showed. */
    val items: List<T> = emptyList(),
    /** Where the next page starts; null when there is none to ask for here. */
    val next: String? = null,
    val loading: Boolean = false,
    /** Why the last page did not arrive; nothing is asked again until the reader retries. */
    val problem: Throwable? = null,
    /** Meta answered a next page in a shape not understood, or the list reached what is kept in memory: the rest is at threads.com. */
    val onWeb: Boolean = false,
) {
    /** The first page named a next one, so the list was paged here. */
    val paged: Boolean get() = next != null || items.isNotEmpty() || onWeb || problem != null
}

/**
 * The pages after the first of a Threads list, read as the reader nears its
 * end: each item once, by [key], against the first page and every page read;
 * a page that brings nothing new is passed over (a few at most) and a cursor
 * that comes round again ends the list. Reads run one at a time in [scope],
 * so they end with the screen; a failure waits for [retry]. Past [limit]
 * items in all the list stops here, at threads.com, so a long scroll never
 * holds a whole account in memory.
 */
class ThreadsPaging<T>(
    private val scope: CoroutineScope,
    private val key: (T) -> String,
    private val limit: Int = MAX_ITEMS,
    private val read: suspend (cursor: String) -> ThreadsMore<T>,
) {
    private val mutable = MutableStateFlow(ThreadsPagingState<T>())
    val state: StateFlow<ThreadsPagingState<T>> = mutable.asStateFlow()

    private var job: Job? = null
    private var started: Pair<String?, Set<String>>? = null
    private var known = HashSet<String>()
    private var firstCount = 0
    private val used = HashSet<String>()

    /** A first page arrived with [first] and [next]: the pages after it start anew, unless it is the first page already started from. */
    fun start(first: List<T>, next: String?) {
        val ids = first.mapTo(HashSet(), key)
        val cursor = next?.ifEmpty { null }
        if (started == (cursor to ids)) return
        started = cursor to ids
        job?.cancel()
        known = HashSet(ids)
        firstCount = ids.size
        used.clear()
        mutable.value = ThreadsPagingState(next = cursor)
    }

    /** Reads the next page; nothing while one is read, after a failure until [retry], or at the end. */
    fun more() {
        val now = mutable.value
        val cursor = now.next ?: return
        if (now.loading || now.problem != null || job?.isActive == true) return
        job = scope.launch { page(cursor) }
    }

    /** Asks again for the page that failed. */
    fun retry() {
        mutable.update { it.copy(problem = null) }
        more()
    }

    private suspend fun page(first: String) {
        mutable.update { it.copy(loading = true, problem = null) }
        try {
            var cursor: String? = first
            var hops = 0
            while (cursor != null && hops++ < PASSES) {
                used += cursor
                val page = read(cursor)
                val fresh = page.items.filter { known.add(key(it)) }
                cursor = page.next?.takeIf { it.isNotEmpty() && it !in used }
                val full = firstCount + mutable.value.items.size + fresh.size >= limit
                val onWeb = page.refused || (full && cursor != null)
                mutable.update { it.copy(items = it.items + fresh, next = cursor.takeIf { !onWeb }, onWeb = onWeb) }
                if (fresh.isNotEmpty() || onWeb) break
            }
            if (hops > PASSES && cursor != null) mutable.update { it.copy(next = null) }
            mutable.update { it.copy(loading = false) }
        } catch (e: CancellationException) {
            mutable.update { it.copy(loading = false) }
            throw e
        } catch (e: Exception) {
            mutable.update { it.copy(loading = false, problem = e) }
        }
    }

    companion object {
        /** How many items one list keeps in all before the rest is left to threads.com. */
        const val MAX_ITEMS = 600

        /** How many pages that bring nothing new are read in a row before the list ends. */
        const val PASSES = 4
    }
}
