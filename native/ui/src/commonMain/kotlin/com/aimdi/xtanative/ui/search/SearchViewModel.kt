package com.aimdi.xtanative.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimdi.xtanative.core.x.model.XCommunity
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.core.x.search.XAdvancedSearch
import com.aimdi.xtanative.core.x.search.XSearchView
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.store.settings.MediaPrefs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUi(
    /** What the field holds: a draft until it is searched. */
    val text: String = "",
    /** Counts the times the screen, not the reader's typing, put [text] in the field: the cursor then goes to its end. */
    val textSet: Int = 0,
    /** The search the results show, and the filters that wrote it. */
    val view: XSearchView = XSearchView(),
    val tab: SearchTab = SearchTab.TOP,
    /** The advanced search form, while it is open. */
    val editing: XAdvancedSearch? = null,
)

/**
 * XTA's search of X: a draft is searched after a pause in typing or at the
 * keyboard's search key, and each tab reads the search the first time it is
 * shown. A search the reader asked for is kept among the recent searches.
 */
class SearchViewModel(private val graph: AppGraph, initial: String?, tab: SearchTab) : ViewModel() {
    private val state = MutableStateFlow(initial.orEmpty().trim().let { SearchUi(text = it, view = XSearchView.of(it), tab = tab) })
    val ui: StateFlow<SearchUi> = state.asStateFlow()

    val top = PagedRead<XThread, String>(PageEnd.POSTS) { it.id }
    val latest = PagedRead<XThread, String>(PageEnd.POSTS) { it.id }
    val media = PagedRead<MediaTile, String>(PageEnd.MEDIA) { it.key }
    val people = PagedRead<XUser, String>(PageEnd.FRESH) { it.id }
    val communities = PagedRead<XCommunity, String>(PageEnd.FRESH) { it.id }

    val recent: StateFlow<List<String>> =
        graph.recentSearches.of(SEARCH_SCOPE_X).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** How the Media tab lays out its tiles, by the reader's Media settings. */
    val mediaShape: StateFlow<MediaGridShape> =
        combine(graph.settings.observe(MediaPrefs.gridLayout), graph.settings.observe(MediaPrefs.gridColumns)) { layout, columns -> MediaGridShape.of(layout, columns) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MediaGridShape(3, ownShape = false))

    private var dispatched = state.value.view.query
    private var debounce: Job? = null

    /** The search each tab shows, once it has been shown. */
    private val shownFor = HashMap<SearchTab, String>()

    init {
        show(state.value.tab)
    }

    /** The reader typed: what is typed is searched once they pause. */
    fun type(text: String) {
        state.update { it.copy(text = text, view = it.view.drafted(text)) }
        if (text.trim() == dispatched) return
        debounce?.cancel()
        debounce = viewModelScope.launch {
            delay(SearchTiming.DEBOUNCE_MILLIS)
            dispatch(state.value.text, asked = false)
        }
    }

    /** The keyboard's search key. */
    fun submit() = dispatch(state.value.text, asked = true)

    fun select(tab: SearchTab) {
        if (tab == state.value.tab) return
        state.update { it.copy(tab = tab) }
        show(tab)
    }

    fun retry(tab: SearchTab) = readOf(tab).retry(viewModelScope)

    fun more(tab: SearchTab) = readOf(tab).more(viewModelScope)

    /** The field's cross: nothing searched, the recent searches shown again. */
    fun clear() {
        state.update { it.copy(view = XSearchView()) }
        setText("")
        dispatch("", asked = true)
    }

    fun searchAgain(query: String) {
        setText(query)
        dispatch(query, asked = true)
    }

    fun removeRecent(query: String) {
        viewModelScope.launch { graph.recentSearches.remove(SEARCH_SCOPE_X, query) }
    }

    fun clearRecent() {
        viewModelScope.launch { graph.recentSearches.clear(SEARCH_SCOPE_X) }
    }

    /** The advanced search form, on the filters shown, else on what the field holds. */
    fun openAdvanced() = state.update { it.copy(editing = SearchFilters.formFor(it.view, it.text)) }

    fun edit(change: (XAdvancedSearch) -> XAdvancedSearch) = state.update { it.copy(editing = it.editing?.let(change)) }

    fun closeAdvanced() = state.update { it.copy(editing = null) }

    /** The form's Search: its filters are shown above the results, and the query they write is searched. */
    fun applyAdvanced() {
        val filters = state.value.editing ?: return
        val view = state.value.view.applied(filters)
        state.update { it.copy(editing = null, view = view) }
        setText(view.query)
        dispatch(view.query, asked = true)
    }

    /** A chip's cross: the search without that filter. */
    fun clearFilter(filter: XAdvancedSearch.Filter) {
        val view = state.value.view.without(filter)
        state.update { it.copy(view = view) }
        setText(view.query)
        dispatch(view.query, asked = true)
    }

    private fun setText(text: String) = state.update { it.copy(text = text, textSet = it.textSet + 1) }

    /** [text] searched on the tab shown; [asked] by the reader, it joins the recent searches. */
    private fun dispatch(text: String, asked: Boolean) {
        debounce?.cancel()
        val query = text.trim()
        dispatched = query
        state.update { it.copy(view = it.view.committed(query)) }
        // People are searched again for every search, on their own tab only, as XTA searches them.
        shownFor.remove(SearchTab.PEOPLE)
        if (state.value.tab != SearchTab.PEOPLE) people.clear()
        show(state.value.tab)
        if (asked && query.isNotEmpty()) viewModelScope.launch { graph.recentSearches.remember(SEARCH_SCOPE_X, query) }
    }

    /** [tab] reads the search the first time it shows it. */
    private fun show(tab: SearchTab) {
        val query = state.value.view.query
        if (shownFor[tab] == query) return
        shownFor[tab] = query
        if (query.isEmpty()) {
            readOf(tab).clear()
            return
        }
        val scope = viewModelScope
        when (tab) {
            SearchTab.TOP, SearchTab.LATEST -> (if (tab == SearchTab.TOP) top else latest).start(scope) { cursor ->
                val page = graph.client.search(query, cursor, product = tab.product!!)
                graph.posts.remember(page.threads)
                Page(page.threads, page.bottomCursor)
            }
            SearchTab.MEDIA -> media.start(scope) { cursor ->
                val page = graph.client.search(query, cursor, product = tab.product!!)
                graph.posts.remember(page.threads)
                Page(MediaTile.of(page.threads), page.bottomCursor)
            }
            // XTA reads one page of people.
            SearchTab.PEOPLE -> people.start(scope) {
                val users = graph.client.searchUsers(query).users
                users.forEach { graph.posts.remember(it) }
                Page(users, null)
            }
            SearchTab.COMMUNITIES -> communities.start(scope) { cursor ->
                val page = graph.client.searchCommunities(query, cursor)
                Page(page.communities, page.cursor)
            }
        }
    }

    private fun readOf(tab: SearchTab): PagedRead<*, *> = when (tab) {
        SearchTab.TOP -> top
        SearchTab.LATEST -> latest
        SearchTab.MEDIA -> media
        SearchTab.PEOPLE -> people
        SearchTab.COMMUNITIES -> communities
    }
}
