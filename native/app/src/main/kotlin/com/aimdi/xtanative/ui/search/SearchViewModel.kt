package com.aimdi.xtanative.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.data.Problem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SearchTab(val product: String) { TOP("Top"), LATEST("Latest") }

data class SearchUi(
    val query: String = "",
    val tab: SearchTab = SearchTab.TOP,
    val results: List<XThread> = emptyList(),
    val searched: Boolean = false,
    val loading: Boolean = false,
    val problem: Problem? = null,
    val cursor: String? = null,
    val loadingMore: Boolean = false,
    val moreProblem: Problem? = null,
)

class SearchViewModel(private val graph: AppGraph, initial: String?) : ViewModel() {
    private val state = MutableStateFlow(SearchUi(query = initial.orEmpty()))
    val ui: StateFlow<SearchUi> = state.asStateFlow()
    private var job: Job? = null

    init {
        if (!initial.isNullOrBlank()) search(initial)
    }

    fun search(query: String, tab: SearchTab = state.value.tab) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        job?.cancel()
        state.update { SearchUi(query = trimmed, tab = tab, searched = true, loading = true) }
        job = viewModelScope.launch {
            try {
                val page = graph.client.search(trimmed, product = tab.product)
                graph.posts.remember(page.threads)
                state.update { it.copy(results = page.threads, cursor = page.bottomCursor.takeIf { _ -> page.threads.isNotEmpty() }, loading = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                state.update { it.copy(loading = false, problem = problemOf(error)) }
            }
        }
    }

    fun select(tab: SearchTab) {
        if (tab != state.value.tab) search(state.value.query, tab)
    }

    fun loadMore() {
        val now = state.value
        val cursor = now.cursor ?: return
        if (now.loading || now.loadingMore) return
        job = viewModelScope.launch {
            state.update { it.copy(loadingMore = true, moreProblem = null) }
            try {
                val page = graph.client.search(now.query, cursor, product = now.tab.product)
                graph.posts.remember(page.threads)
                state.update {
                    val known = it.results.mapTo(HashSet()) { thread -> thread.id }
                    val fresh = page.threads.filter { thread -> thread.id !in known }
                    it.copy(results = it.results + fresh, cursor = page.bottomCursor.takeIf { _ -> fresh.isNotEmpty() }, loadingMore = false)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                state.update { it.copy(loadingMore = false, moreProblem = problemOf(error)) }
            }
        }
    }

    /** A guest's search is refused by X: what it needs is an account. */
    private fun problemOf(error: Throwable): Problem {
        val problem = Problem.of(error)
        val noAccount = graph.sessions.state.value?.accounts.isNullOrEmpty()
        return if (noAccount && problem is Problem.Refused) Problem.NoAccount else problem
    }
}
