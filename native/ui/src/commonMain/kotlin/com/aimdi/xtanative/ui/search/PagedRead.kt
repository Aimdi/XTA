package com.aimdi.xtanative.ui.search

import com.aimdi.xtanative.data.Problem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One page of a read: what it brought, and the cursor of the page after. */
data class Page<T>(val items: List<T>, val next: String?)

/**
 * A list read from X page by page, in [state]: [start] reads it from the
 * top with a read of its own (a new search, say), [more] asks that read for
 * the page after. An answer to a read that was started over is dropped.
 */
class PagedRead<T, K>(private val end: PageEnd, private val key: (T) -> K) {
    private val current = MutableStateFlow(Paged<T>())
    val state: StateFlow<Paged<T>> = current.asStateFlow()

    private var read: (suspend (cursor: String?) -> Page<T>)? = null
    private var job: Job? = null
    private var runs = 0

    /** [read]'s first page in [scope], in place of what was shown. */
    fun start(scope: CoroutineScope, read: suspend (cursor: String?) -> Page<T>) {
        this.read = read
        job?.cancel()
        val run = ++runs
        current.value = Paged.reading()
        job = scope.launch { load(run, null, read) }
    }

    /** The first page of the same read again, after it failed. */
    fun retry(scope: CoroutineScope) {
        read?.let { start(scope, it) }
    }

    /** The page after the last one, when there is one and nothing is being read. */
    fun more(scope: CoroutineScope) {
        val now = current.value
        val reader = read ?: return
        if (!now.canLoadMore) return
        val run = runs
        current.value = now.askingMore()
        job = scope.launch { load(run, now.next, reader) }
    }

    /** Nothing shown and nothing read, as for a search cleared. */
    fun clear() {
        job?.cancel()
        runs++
        read = null
        current.value = Paged()
    }

    private suspend fun load(run: Int, cursor: String?, read: suspend (cursor: String?) -> Page<T>) {
        val page = try {
            read(cursor)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (run == runs) current.update { if (cursor == null) it.failed(Problem.of(error)) else it.moreFailed(Problem.of(error)) }
            return
        }
        if (run == runs) current.update { it.appended(page.items, page.next, cursor, end, key) }
    }
}
