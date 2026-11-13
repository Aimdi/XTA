package com.aimdi.xtanative.networks.substack

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Notes tab as XTA's SubstackNotesStore runs it: [SubstackNotes] read
 * from the followed publications' hosts in turn, with whether a refresh or a
 * next page is under way and what failed. A refresh that is not forced leaves
 * notes alone that answered within [SubstackFeed.FRESH_MS] for these follows.
 */
class SubstackNotesFeed(
    private val notes: SubstackNotes,
    private val scope: CoroutineScope,
    private val followed: () -> List<SubstackPublication>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class State(
        val notes: List<SubstackNote> = emptyList(),
        val canLoadMore: Boolean = false,
        val refreshing: Boolean = false,
        val loadingMore: Boolean = false,
        val refreshError: Throwable? = null,
        val loadMoreError: Throwable? = null,
        val loaded: Boolean = false,
    )

    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable.asStateFlow()

    private var answeredFor: List<String>? = null
    private var answeredAt: Long? = null
    private var job: Job? = null

    fun refresh(force: Boolean = true) {
        val now = state.value
        if (now.refreshing && !force) return
        val fresh = answeredAt?.let { clock() - it < SubstackFeed.FRESH_MS } == true
        if (!force && answeredFor == identity() && now.refreshError == null && fresh) return
        load(more = false)
    }

    fun loadMore() {
        val now = state.value
        if (now.refreshing || now.loadingMore || !now.canLoadMore) return
        load(more = true)
    }

    private fun identity(): List<String> = followed().map { "${it.id}\n${it.baseUrl}" }.sorted()

    private fun load(more: Boolean) {
        job?.cancel()
        val asked = identity()
        mutable.update { it.copy(refreshing = !more, loadingMore = more, loadMoreError = null, refreshError = if (more) it.refreshError else null) }
        job = scope.launch {
            val before = notes.state
            val after = if (more) notes.more() else notes.refresh()
            val error = after.error.takeIf { it !== before.error }
            if (error == null) {
                answeredFor = asked
                answeredAt = clock()
            }
            mutable.value = State(
                notes = after.notes,
                canLoadMore = after.next != null,
                refreshError = if (more) state.value.refreshError else error,
                loadMoreError = if (more) error else null,
                loaded = true,
            )
        }
    }
}
