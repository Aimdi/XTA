package com.aimdi.xtanative.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimdi.xtanative.data.AppGraph
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * XTA's library search (reader_search_store.dart): what is on the device is
 * read at once, the offline collection after it, and typing or a chip only
 * filters what was read.
 */
class LibrarySearchViewModel(private val graph: AppGraph, query: String) : ViewModel() {
    private val index = LibraryIndex(graph)
    private val state = MutableStateFlow(LibrarySearchUi(query = query))
    val ui: StateFlow<LibrarySearchUi> = state.asStateFlow()
    private var reading: Job? = null

    init {
        load()
    }

    /** Everything read again: after a result was opened, as XTA reads again then, or on Try again. */
    fun load() {
        reading?.cancel()
        reading = viewModelScope.launch {
            state.update { it.copy(loading = true, failed = false) }
            try {
                val local = withContext(Dispatchers.IO) { index.local() }
                state.update { it.copy(documents = local) }
                val articles = withContext(Dispatchers.IO) { index.articles() }
                state.update { it.copy(documents = local + articles, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.update { it.copy(loading = false, failed = true) }
            }
        }
    }

    fun type(query: String) = state.update { it.copy(query = query) }

    fun pick(kind: LibraryKind?) = state.update { it.copy(kind = kind) }

    /** Before [result] opens: a saved X post is handed to the post screen, which draws it at once. */
    fun opening(result: LibraryDocument) {
        val target = result.target as? LibraryTarget.XPost ?: return
        graph.saved.state.value?.post(target.id)?.about?.xPost?.let { graph.posts.remember(it) }
    }
}
