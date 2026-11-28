package com.aimdi.xtanative.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.core.x.parse.XText
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.store.history.HistoryEntry
import com.aimdi.xtanative.store.history.HistoryKind
import com.aimdi.xtanative.store.history.HistoryPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What History shows: the entries the search finds, newest first, and whether the history is kept. */
data class HistoryUi(
    val loaded: Boolean = false,
    val keep: Boolean = true,
    val query: String = "",
    /** Everything kept, before the search. */
    val total: Int = 0,
    val shown: List<HistoryEntry> = emptyList(),
)

/**
 * History (Ctrl+H): the posts and profiles opened on this device, searched
 * by name, handle or text, each opened again or forgotten, all of them
 * cleared, and the switch that stops the history being kept.
 */
class HistoryViewModel(private val graph: AppGraph) : ViewModel() {
    private val query = MutableStateFlow("")

    val ui: StateFlow<HistoryUi> = combine(graph.history.entries, graph.settings.observe(HistoryPrefs.keep), query) { entries, keep, query ->
        HistoryUi(loaded = true, keep = keep, query = query, total = entries.size, shown = entries.filter { it.matches(query) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUi())

    fun search(text: String) {
        query.value = text
    }

    fun remove(entry: HistoryEntry) {
        viewModelScope.launch { graph.history.remove(entry.key) }
    }

    fun clear() {
        viewModelScope.launch { graph.history.clear() }
    }

    fun keep(on: Boolean) {
        viewModelScope.launch { graph.settings.set(HistoryPrefs.keep, on) }
    }
}

/** What opening a post or a profile adds to History, while the reader keeps one. */
object HistoryRecorder {
    fun post(graph: AppGraph, post: XPost) = record(graph, entryOf(post, System.currentTimeMillis()))

    fun profile(graph: AppGraph, user: XUser) = record(graph, entryOf(user, System.currentTimeMillis()))

    fun entryOf(post: XPost, at: Long): HistoryEntry =
        HistoryEntry(HistoryKind.POST, post.id, post.author.name, post.author.screenName, XText.plain(post).trim(), at)

    fun entryOf(user: XUser, at: Long): HistoryEntry =
        HistoryEntry(HistoryKind.PROFILE, user.screenName, user.name, user.screenName, at = at)

    private fun record(graph: AppGraph, entry: HistoryEntry) {
        if (entry.target.isBlank()) return
        graph.keep {
            if (graph.settings.get(HistoryPrefs.keep)) graph.history.record(entry)
        }
    }
}
