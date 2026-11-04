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
import com.aimdi.xtanative.store.settings.ThemePrefs
import com.aimdi.xtanative.ui.settings.HomePages
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUi(
    val session: Session,
    val tabs: List<HomeTab>,
    /** The tab Home opened on, from its first state: where the strip starts. */
    val initialIndex: Int,
    /** The networks plugins offer for the strip, pinned or not. */
    val networks: List<StripNetwork>,
    /** The networks the reader put on the strip, in their order. */
    val pins: List<String>,
    val filter: HomeFilterDraft,
    /** Settings › Posts › Remember reading position: what lights the unread dots. */
    val readingPosition: Boolean,
    /** The options each network pinned to its top bar, by network id. */
    val topBarPins: Map<String, List<String>> = emptyMap(),
    /** The bottom bar's pages, in the reader's order (Settings › Home's Home pages). */
    val bar: List<BarPage> = HomeBar.DEFAULT_PAGES,
    /** The bar's page Home opened on, from its first state. */
    val initialBarIndex: Int = 0,
    /** Theme's two navigation switches: the bar's labels, and both bars slipping away while scrolling. */
    val navigationLabels: Boolean = false,
    val hideOnScroll: Boolean = true,
    /** Networks opened lately, newest first: the one the bar keeps when it has no room for all. */
    val recentNetworks: List<String> = emptyList(),
) {
    /** The account whose mark stands for the reader: the first one, as in XTA, which has no single signed-in identity. */
    val account: XAccount? get() = session.accounts.firstOrNull()
}

/**
 * Home's strip and bar, from the session (the groups), the settings (pins,
 * opening page and tab, filter, Home pages, Theme's switches) and the
 * plugins' networks.
 */
class HomeViewModel(private val graph: AppGraph, networks: StateFlow<List<StripNetwork>>) : ViewModel() {
    private var opening: Pair<Int, Int>? = null

    /** Null until the session and the settings are read. */
    val ui: StateFlow<HomeUi?> = combine(graph.sessions.state.filterNotNull(), graph.settings.settings, networks) { session, settings, available ->
        build(session, settings, available)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun build(session: Session, settings: Settings, available: List<StripNetwork>): HomeUi {
        val ids = available.mapTo(HashSet()) { it.id }
        val pins = settings[HomePrefs.feedStripNetworks]
        val tabs = HomeStrip.tabs(pins, ids::contains, session.feedGroups)
        val bar = barOf(session, settings, ids)
        val (index, barIndex) = opening ?: openingOf(tabs, bar, settings).also { opening = it }
        return HomeUi(
            session = session,
            tabs = tabs,
            initialIndex = index,
            networks = available,
            pins = pins,
            filter = HomeFilter.draftOf(session, settings),
            readingPosition = settings[PostsPrefs.rememberReadingPosition],
            topBarPins = available.associate { it.id to settings[HomePrefs.topBarPins[it.id]] },
            bar = bar,
            initialBarIndex = barIndex,
            navigationLabels = settings[ThemePrefs.showNavigationLabels],
            hideOnScroll = settings[ThemePrefs.hideNavigationOnScroll],
            recentNetworks = settings[HomePrefs.recentNetworks],
        )
    }

    /** The pages Settings › Home ticks, as it loads them (a network offered once, an empty bar repaired), that Home can show. */
    private fun barOf(session: Session, settings: Settings, networks: Set<String>): List<BarPage> {
        val loaded = HomePages.load(settings, session.groups.map { it.id })
        return HomeBar.pages(loaded.saved, networks::contains, session.groups.mapTo(HashSet()) { it.id })
    }

    /** The strip's tab and the bar's page Home opens on: a page on the bar leaves the strip on the default feed. */
    private fun openingOf(tabs: List<HomeTab>, bar: List<BarPage>, settings: Settings): Pair<Int, Int> {
        val page = settings[GeneralPrefs.initialTab]
        val stripPage = if (HomeBar.opensOnBar(bar, page)) HomeBar.FEED else page
        return HomeStrip.initialIndex(tabs, stripPage, settings[GeneralPrefs.defaultFeedTab]) to HomeBar.initialIndex(bar, page)
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
        if (tab is HomeTab.Network) touched(tab.id)
    }

    /** Network [id] opened, from the strip or the bar's Networks (rememberNetwork). */
    fun touched(id: String) {
        viewModelScope.launch {
            graph.settings.update { it.with(HomePrefs.recentNetworks, HomeStrip.touched(it[HomePrefs.recentNetworks], id)) }
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
