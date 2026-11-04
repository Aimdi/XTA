package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.Page
import com.aimdi.xtanative.networks.pixiv.PixivPages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where a list Pixiv hands out page by page stands. */
internal data class PixivListState<T>(
    val items: List<T> = emptyList(),
    /** The first page is on its way, or on its way again. */
    val loading: Boolean = false,
    /** A first page arrived, or failed, at least once. */
    val loaded: Boolean = false,
    /** Why the first page did not arrive, while nothing is shown. */
    val error: Throwable? = null,
    val loadingMore: Boolean = false,
    /** The next page failed; the list keeps what it had and offers Retry. */
    val moreFailed: Boolean = false,
    val hasMore: Boolean = false,
) {
    /** Nothing to show yet, and nothing wrong: a first load. */
    val waiting: Boolean get() = items.isEmpty() && error == null && (loading || !loaded)
}

/**
 * Any paged Pixiv list (works, creators, novels, comments, series), kept in
 * order without repeats as `next_url` pages arrive. The first load shows its
 * loading; a later refresh keeps the list up, and keeps it when it fails. A
 * page the [visible] filter empties is read past ([PixivPages.visiblePage]).
 * Swapping the source ([use]) or a refresh drops whatever was still on its
 * way, so a slow page of the old source never joins the new list.
 */
internal class PixivList<T : Any>(
    private val scope: CoroutineScope,
    private val key: (T) -> Any,
    private var load: suspend (next: String?) -> Page<T>,
    private val visible: (List<T>) -> List<T> = { it },
) {
    private val mutable = MutableStateFlow(PixivListState<T>())
    val state: StateFlow<PixivListState<T>> = mutable.asStateFlow()

    private var next: String? = null
    private val used = HashSet<String>()
    private var job: Job? = null
    private var moreJob: Job? = null

    /** Reads the first page again; the first time, the list shows its loading. */
    fun refresh() {
        job?.cancel()
        moreJob?.cancel()
        val soft = mutable.value.items.isNotEmpty()
        mutable.update { it.copy(loading = true, error = null, loadingMore = false, moreFailed = false) }
        job = scope.launch {
            try {
                val page = PixivPages.visiblePage(null, load, visible)
                used.clear()
                next = page.next
                mutable.value = PixivListState(items = page.items.distinctBy(key), loaded = true, hasMore = page.next != null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { if (soft) it.copy(loading = false) else it.copy(loading = false, loaded = true, error = e) }
            }
        }
    }

    /** The first page, unless a list is already shown or on its way. */
    fun ensure() {
        val now = mutable.value
        if (now.items.isEmpty() && !now.loading) refresh()
    }

    /** The next page, once: a call while one is on its way, after the end or after a failure does nothing. */
    fun more() {
        val cursor = next ?: return
        val now = mutable.value
        if (now.loading || now.loadingMore || now.moreFailed || moreJob?.isActive == true) return
        mutable.update { it.copy(loadingMore = true) }
        moreJob = scope.launch {
            try {
                val page = PixivPages.visiblePage(cursor, load, visible)
                used += cursor
                next = page.next?.takeIf { it !in used }
                mutable.update { it.copy(items = PixivPages.merge(it.items, page.items, key), loadingMore = false, hasMore = next != null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(loadingMore = false, moreFailed = true) }
            }
        }
    }

    /** Asks for the page that failed again. */
    fun retryMore() {
        mutable.update { it.copy(moreFailed = false) }
        more()
    }

    /** Reads from [source] from now on: the list empties and starts over at its first page when next asked. */
    fun use(source: suspend (next: String?) -> Page<T>) {
        load = source
        clear()
    }

    /** Empties the list and forgets its paging, keeping its source. */
    fun clear() {
        job?.cancel()
        moreJob?.cancel()
        next = null
        used.clear()
        mutable.value = PixivListState()
    }
}

/** What the device keeps (bookmarks, history), shown as a list the grids draw: read again whenever it changes. */
internal class PixivLocalList<S, T : Any>(scope: CoroutineScope, key: (T) -> Any, private val convert: (S) -> T) {
    private var source: List<S> = emptyList()
    val list = PixivList(scope, key, { Page(source.map(convert), null) })

    fun show(next: List<S>) {
        if (next == source && list.state.value.loaded) return
        source = next
        list.refresh()
    }
}

/** Something Pixiv answers in one read, [value] once it arrived; [load] reads it again, as Retry and a pull do. */
internal data class PixivFetchState<T>(val value: T? = null, val loading: Boolean = false, val error: Throwable? = null)

internal class PixivFetch<T : Any>(private val scope: CoroutineScope, private val read: suspend () -> T) {
    private val mutable = MutableStateFlow(PixivFetchState<T>())
    val state: StateFlow<PixivFetchState<T>> = mutable.asStateFlow()

    private var job: Job? = null

    fun load() {
        job?.cancel()
        mutable.update { it.copy(loading = true, error = null) }
        job = scope.launch {
            try {
                val value = read()
                mutable.value = PixivFetchState(value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(loading = false, error = e) }
            }
        }
    }

    /** The first read, unless one arrived or is on its way. */
    fun ensure() {
        val now = mutable.value
        if (now.value == null && !now.loading) load()
    }

    fun clear() {
        job?.cancel()
        mutable.value = PixivFetchState()
    }
}
