package com.aimdi.xtanative.ui.antenna

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.store.antenna.Antenna
import com.aimdi.xtanative.store.antenna.AntennaQuery
import com.aimdi.xtanative.ui.search.Page
import com.aimdi.xtanative.ui.search.PageEnd
import com.aimdi.xtanative.ui.search.PagedRead
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AntennasUi(
    /** The editor, while it is open. */
    val editing: AntennaDraft? = null,
    /** The antenna Delete asks about. */
    val deleting: Antenna? = null,
)

/** XTA's antennas: the list, newest first, the editor for a new one or a changed one, and Delete. */
class AntennasViewModel(private val graph: AppGraph) : ViewModel() {
    private val list = MutableStateFlow<List<Antenna>?>(null)

    /** Null until the store has been read. */
    val antennas: StateFlow<List<Antenna>?> = list.asStateFlow()

    private val failed = MutableStateFlow<Problem?>(null)

    /** Why the list could not be read, until [retry] reads it again. */
    val problem: StateFlow<Problem?> = failed.asStateFlow()

    private val state = MutableStateFlow(AntennasUi())
    val ui: StateFlow<AntennasUi> = state.asStateFlow()

    private var reading: Job? = null

    init {
        retry()
    }

    /** Reads the list again, as the error's Retry asks (antenna_screen.dart's listAntennas). */
    fun retry() {
        reading?.cancel()
        failed.value = null
        reading = viewModelScope.launch {
            try {
                graph.antennas.antennas.collect { list.value = it }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // The device's own file, not X: no network or account advice applies.
                failed.value = Problem.Other
            }
        }
    }

    /** The editor for [antenna], or for a new antenna. */
    fun edit(antenna: Antenna? = null) = state.update { it.copy(editing = AntennaDraft.of(antenna)) }

    fun change(draft: AntennaDraft) = state.update { it.copy(editing = draft) }

    fun closeEditor() = state.update { it.copy(editing = null) }

    /** The editor's OK: the antenna kept, then [opened] with it, as XTA opens its feed. */
    fun save(opened: (Antenna) -> Unit) {
        val draft = state.value.editing?.takeIf { it.canSave } ?: return
        viewModelScope.launch {
            val saved = graph.antennas.save(draft.name, draft.includeTerms, draft.excludeTerms, draft.scope, draft.id) ?: return@launch
            state.update { it.copy(editing = null) }
            opened(saved)
        }
    }

    fun askDelete(antenna: Antenna) = state.update { it.copy(deleting = antenna) }

    fun cancelDelete() = state.update { it.copy(deleting = null) }

    fun delete() {
        val antenna = state.value.deleting ?: return
        state.update { it.copy(deleting = null) }
        viewModelScope.launch { graph.antennas.delete(antenna.id) }
    }
}

/**
 * An antenna's feed, as XTA reads it: X's newest posts with its terms, and
 * for a following antenna only those of the accounts followed when it opened.
 */
class AntennaFeedViewModel(private val graph: AppGraph, private val id: String) : ViewModel() {
    /** The antenna, once the store is read; null after that when it is gone. */
    val antenna: StateFlow<Antenna?> =
        graph.antennas.antennas.map { all -> all.firstOrNull { it.id == id } }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val known = MutableStateFlow(false)

    /** The store has been read: a missing antenna is gone, not loading. */
    val loaded: StateFlow<Boolean> = known.asStateFlow()

    val posts = PagedRead<XThread, String>(PageEnd.POSTS) { it.id }

    init {
        viewModelScope.launch {
            val all = graph.antennas.antennas.first()
            known.value = true
            all.firstOrNull { it.id == id }?.let(::read)
        }
    }

    private fun read(antenna: Antenna) {
        val query = AntennaQuery.search(antenna)
        val followed = AntennaFeed.followedIds(graph.sessions.state.value)
        posts.start(viewModelScope) { cursor ->
            if (query.isEmpty()) return@start Page(emptyList(), null)
            val page = graph.client.search(query, cursor, product = AntennaQuery.PRODUCT)
            graph.posts.remember(page.threads)
            Page(AntennaFeed.kept(antenna, page.threads, followed), page.bottomCursor)
        }
    }

    fun retry() = posts.retry(viewModelScope)

    fun more() = posts.more(viewModelScope)
}
