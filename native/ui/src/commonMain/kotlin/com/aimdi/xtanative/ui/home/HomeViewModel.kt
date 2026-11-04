package com.aimdi.xtanative.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimdi.xtanative.core.feed.FeedStrip
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.XAccount
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.store.settings.GeneralPrefs
import com.aimdi.xtanative.store.settings.HomePrefs
import com.aimdi.xtanative.store.settings.PostsPrefs
import com.aimdi.xtanative.store.settings.Settings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUi(
    val session: Session,
    val tabs: List<HomeTab>,
    /** The tab Home opened on, from its first state: where the pager starts. */
    val initialIndex: Int,
    /** The screen XTA's initial page opens over Home, once. */
    val startRoute: String?,
    /** The networks plugins offer for the strip, pinned or not. */
    val networks: List<StripNetwork>,
    /** The networks the reader put on the strip, in their order. */
    val pins: List<String>,
    val filter: HomeFilterDraft,
    /** Settings › Posts › Remember reading position: what lights the unread dots. */
    val readingPosition: Boolean,
    /** The options each network pinned to its top bar, by network id. */
    val topBarPins: Map<String, List<String>> = emptyMap(),
) {
    /** The account whose mark stands for the reader: the first one, as in XTA, which has no single signed-in identity. */
    val account: XAccount? get() = session.accounts.firstOrNull()
}

/** Home's strip, from the session (the groups), the settings (pins, opening tab, filter) and the plugins' networks. */
class HomeViewModel(private val graph: AppGraph, networks: StateFlow<List<StripNetwork>>) : ViewModel() {
    private var opening: Pair<Int, String?>? = null

    /** Null until the session and the settings are read. */
    val ui: StateFlow<HomeUi?> = combine(graph.sessions.state.filterNotNull(), graph.settings.settings, networks) { session, settings, available ->
        build(session, settings, available)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun build(session: Session, settings: Settings, available: List<StripNetwork>): HomeUi {
        val ids = available.mapTo(HashSet()) { it.id }
        val pins = settings[HomePrefs.feedStripNetworks]
        val tabs = HomeStrip.tabs(pins, ids::contains, session.feedGroups)
        val (index, route) = opening ?: openingOf(tabs, settings).also { opening = it }
        return HomeUi(
            session = session,
            tabs = tabs,
            initialIndex = index,
            startRoute = route,
            networks = available,
            pins = pins,
            filter = HomeFilter.draftOf(session, settings),
            readingPosition = settings[PostsPrefs.rememberReadingPosition],
            topBarPins = available.associate { it.id to settings[HomePrefs.topBarPins[it.id]] },
        )
    }

    private fun openingOf(tabs: List<HomeTab>, settings: Settings): Pair<Int, String?> {
        val page = settings[GeneralPrefs.initialTab]
        return HomeStrip.initialIndex(tabs, page, settings[GeneralPrefs.defaultFeedTab]) to HomeStrip.startRoute(page)
    }

    fun pin(id: String) = editPins { pins, _ -> FeedStrip.pinned(pins, id) }

    fun unpin(id: String) = editPins { pins, _ -> FeedStrip.forgotten(pins, id) }

    /** The pinned network at [from] to [to], both places among the networks the strip shows. */
    fun move(from: Int, to: Int) = editPins { pins, available -> StripPins.moved(pins, available, from, to) }

    private fun editPins(change: (List<String>, List<StripNetwork>) -> List<String>) {
        val available = ui.value?.networks ?: return
        viewModelScope.launch {
            graph.settings.update { it.with(HomePrefs.feedStripNetworks, change(it[HomePrefs.feedStripNetworks], available)) }
        }
    }

    /** [tab] opened from the strip: a network goes first among the recent ones, as XTA keeps them. */
    fun opened(tab: HomeTab) {
        if (tab !is HomeTab.Network) return
        viewModelScope.launch {
            graph.settings.update { it.with(HomePrefs.recentNetworks, HomeStrip.touched(it[HomePrefs.recentNetworks], tab.id)) }
        }
    }

    /** The options [networkId] keeps pinned to its top bar, in their order. */
    fun setTopBarPins(networkId: String, pins: List<String>) {
        viewModelScope.launch { graph.settings.set(HomePrefs.topBarPins[networkId], pins) }
    }

    /** The home feed accounts the reader applied: For you's accounts, and the groups kept off the home feeds. */
    fun apply(draft: HomeFilterDraft) {
        viewModelScope.launch {
            graph.settings.update { HomeFilter.applied(it, draft) }
            graph.sessions.update { HomeFilter.applied(it, draft) }
        }
    }
}
