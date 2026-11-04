package com.aimdi.xtanative.ui.plugins

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where one read stands: what it last gave, whether it is reading, and why the last read failed. */
data class Fetched<T>(val value: T? = null, val loading: Boolean = false, val problem: Throwable? = null)

/**
 * One read a screen shows (a profile, a conversation, what a search found),
 * read again on asking ([load], with [force] past any cache): what was shown
 * stays while it is read again, and stays when that read fails, the failure
 * beside it, as XTA's stores keep their state through an error. Reads run in
 * [scope]: [rememberKept]'s, or a view model's.
 */
class Fetch<T : Any>(private val scope: CoroutineScope, initial: T? = null, private val read: suspend (force: Boolean) -> T) {
    private val mutable = MutableStateFlow(Fetched(initial))
    val state: StateFlow<Fetched<T>> = mutable.asStateFlow()

    private var job: Job? = null
    private var asked = false

    fun load(force: Boolean = false) {
        asked = true
        job?.cancel()
        job = scope.launch {
            mutable.update { it.copy(loading = true, problem = null) }
            try {
                mutable.value = Fetched(read(force))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(loading = false, problem = e) }
            }
        }
    }

    /** Reads once, the first time a screen shows it. */
    fun ensure() {
        if (!asked) load()
    }
}

/** Where a merged feed stands. */
data class MergedFeedState<T>(
    val items: List<T> = emptyList(),
    val loading: Boolean = false,
    /** A read has answered, with items or without. */
    val loaded: Boolean = false,
    /** Why the last read failed; a screen shows it in place of items only when there are none. */
    val problem: Throwable? = null,
)

/**
 * A feed merged from several accounts' pages, as XTA's account feeds read
 * (threads_store.dart, instagram_store.dart): [read] hands each account's
 * answer to its callback as it comes, and the feed paints it at once. A read
 * while items are shown keeps them on screen, and keeps them when it fails:
 * a soft refresh never blanks a healthy feed.
 */
class MergedFeed<T>(
    private val scope: CoroutineScope,
    private val read: suspend (force: Boolean, onPartial: (List<T>) -> Unit) -> List<T>,
) {
    private val mutable = MutableStateFlow(MergedFeedState<T>())
    val state: StateFlow<MergedFeedState<T>> = mutable.asStateFlow()

    private var job: Job? = null

    /** What the reader last saw, before anything is asked; nothing when items are shown already. */
    fun restore(items: List<T>) {
        mutable.update { if (it.items.isEmpty() && items.isNotEmpty()) it.copy(items = items) else it }
    }

    fun refresh(force: Boolean = false) {
        job?.cancel()
        job = scope.launch {
            mutable.update { it.copy(loading = true, problem = null) }
            try {
                val items = read(force) { partial -> mutable.update { it.copy(items = partial) } }
                mutable.value = MergedFeedState(items, loaded = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(loading = false, loaded = true, problem = e) }
            }
        }
    }

    /** Empties the feed, for when what it was read with is gone. */
    fun forget() {
        job?.cancel()
        mutable.value = MergedFeedState()
    }
}
