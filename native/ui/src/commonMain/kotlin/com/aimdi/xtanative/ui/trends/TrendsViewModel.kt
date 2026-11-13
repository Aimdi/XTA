package com.aimdi.xtanative.ui.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimdi.xtanative.core.x.model.XTrend
import com.aimdi.xtanative.core.x.model.XTrendPlace
import com.aimdi.xtanative.core.x.model.XTrendPlaces
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.store.settings.DiscoverPrefs
import com.aimdi.xtanative.ui.search.SEARCH_SCOPE_X
import com.aimdi.xtanative.ui.search.SearchTiming
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrendsUi(
    /** The places kept, and the one shown. */
    val places: XTrendPlaces = XTrendPlaces(),
    val trends: List<XTrend> = emptyList(),
    val loaded: Boolean = false,
    val loading: Boolean = true,
    val problem: Problem? = null,
    /** What the bar holds. */
    val text: String = "",
    /** Counts the times the screen, not typing, put [text] in the bar. */
    val textSet: Int = 0,
    /** What the bar settled on; while it is empty, the recent searches show. */
    val committed: String = "",
    /** The network the bar searches: X, or a plugin's id ([DiscoverScopes]). */
    val scope: String = SEARCH_SCOPE_X,
)

/** The place picker: the places X has trends for, read when it opens. */
data class PlacesUi(val rows: List<PlaceRow> = emptyList(), val loading: Boolean = true, val problem: Problem? = null)

/**
 * XTA's Discover for X: the trends of the place shown, the places kept as
 * tabs (in XTA's settings, so they travel in its backup), the search bar,
 * the network it searches and that network's recent searches.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrendsViewModel(private val graph: AppGraph) : ViewModel() {
    private val state = MutableStateFlow(TrendsUi())
    val ui: StateFlow<TrendsUi> = state.asStateFlow()

    private val picker = MutableStateFlow(PlacesUi())
    val places: StateFlow<PlacesUi> = picker.asStateFlow()

    val recent: StateFlow<List<String>> = state.map { it.scope }.distinctUntilChanged()
        .flatMapLatest { graph.recentSearches.of(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var reading: Job? = null
    private var settling: Job? = null

    init {
        viewModelScope.launch {
            graph.settings.observe(DiscoverPrefs.trendLocations).map(XTrendPlaces::decode).collect { places ->
                state.update { it.copy(places = places) }
            }
        }
        viewModelScope.launch {
            state.map { it.places.active.woeid }.distinctUntilChanged().collect {
                // Another place's trends are not this one's: they go while its own are read.
                state.update { it.copy(trends = emptyList(), loaded = false) }
                refresh()
            }
        }
    }

    /** The trends of the place shown, read again. */
    fun refresh() {
        val woeid = state.value.places.active.woeid
        reading?.cancel()
        reading = viewModelScope.launch {
            state.update { it.copy(loading = true, problem = null) }
            try {
                val trends = graph.client.trends(woeid).trends
                state.update { it.copy(trends = trends, loaded = true, loading = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                state.update { it.copy(loading = false, problem = Problem.of(error)) }
            }
        }
    }

    /** A place tab: its trends shown. */
    fun show(place: XTrendPlace) = changePlaces { it.shown(place) }

    /** Picked in the place picker: kept as a tab, and shown. */
    fun add(place: XTrendPlace) = changePlaces { it.added(place) }

    /** A tab's Delete: the place before it is shown. */
    fun remove(place: XTrendPlace) = changePlaces { it.removed(place) }

    private fun changePlaces(change: (XTrendPlaces) -> XTrendPlaces) {
        viewModelScope.launch {
            graph.settings.update { settings ->
                val next = change(XTrendPlaces.decode(settings[DiscoverPrefs.trendLocations]))
                settings.with(DiscoverPrefs.trendLocations, next.encode())
            }
        }
    }

    /** The places X has trends for, for the picker. */
    fun loadPlaces() {
        viewModelScope.launch {
            picker.value = PlacesUi()
            try {
                picker.value = PlacesUi(rows = TrendPlacesLogic.rows(graph.client.trendPlaces()), loading = false)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                picker.value = PlacesUi(loading = false, problem = Problem.of(error))
            }
        }
    }

    /** The reader typed in the bar: it settles on what they typed after a pause. */
    fun type(text: String) {
        state.update { it.copy(text = text) }
        settling?.cancel()
        settling = viewModelScope.launch {
            delay(SearchTiming.DISCOVER_DEBOUNCE_MILLIS)
            state.update { it.copy(committed = it.text.trim()) }
        }
    }

    /** A scope chip: the bar searches [scope] now, with what it holds. */
    fun select(scope: String) {
        settling?.cancel()
        state.update { it.copy(scope = scope, committed = it.text.trim()) }
    }

    /**
     * The bar's search key or magnifier: the query to search, kept among
     * the scope's recent searches; null for an empty bar, which only takes focus.
     */
    fun submit(): String? {
        settling?.cancel()
        val query = state.value.text.trim()
        state.update { it.copy(committed = query) }
        if (query.isEmpty()) return null
        val scope = state.value.scope
        viewModelScope.launch { graph.recentSearches.remember(scope, query) }
        return query
    }

    /** A recent search tapped: in the bar, and searched again. */
    fun searchAgain(query: String): String? {
        state.update { it.copy(text = query, textSet = it.textSet + 1) }
        return submit()
    }

    fun clear() {
        settling?.cancel()
        state.update { it.copy(text = "", textSet = it.textSet + 1, committed = "") }
    }

    fun removeRecent(query: String) {
        val scope = state.value.scope
        viewModelScope.launch { graph.recentSearches.remove(scope, query) }
    }

    fun clearRecent() {
        val scope = state.value.scope
        viewModelScope.launch { graph.recentSearches.clear(scope) }
    }
}
