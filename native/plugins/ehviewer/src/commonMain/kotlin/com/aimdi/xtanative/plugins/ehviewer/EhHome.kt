package com.aimdi.xtanative.plugins.ehviewer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.ehviewer.EhToplistPeriod
import com.aimdi.xtanative.ui.common.GridSkeleton
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.common.swipeTabs
import com.aimdi.xtanative.ui.plugins.EmptyPane
import com.aimdi.xtanative.ui.plugins.NoticeHost
import com.aimdi.xtanative.ui.plugins.PluginChipTabs
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.RefreshesOn
import com.aimdi.xtanative.ui.plugins.ScrollsToTop
import com.aimdi.xtanative.ui.plugins.WithSettings
import com.aimdi.xtanative.ui.plugins.ReadingControlsBar
import com.aimdi.xtanative.ui.plugins.ReportsReadingScroll
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.post.PostIcons

/**
 * The home tab (eh_screen.dart): Popular, the front page, the toplist with
 * its windows, Watched, and the history and favourites kept here, with the
 * search and the settings beside them unless the screen around it has its
 * own ([ownActions] false).
 */
@Composable
internal fun EhHome(app: EhApp, frame: PluginFrame, ownActions: Boolean = true) = WithSettings(app.host.settings) {
    var tab by rememberSaveable { mutableStateOf(EhTab.POPULAR) }
    val lists = rememberKept("eh-home") { scope -> EhLists(app, scope) }
    val grids = EhTab.entries.associateWith { rememberLazyGridState() }
    val reduced = LocalReducedMotion.current
    LaunchedEffect(Unit) { app.ready() }
    LaunchedEffect(tab) { lists.of(tab)?.ensure() }
    RefreshesOn(frame.refresh) { lists.of(tab)?.refresh() }
    ScrollsToTop(frame.toTop) { grids.getValue(tab).let { if (reduced) it.scrollToItem(0) else it.animateScrollToItem(0) } }
    ReportsReadingScroll(grids.getValue(tab))
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ReadingControlsBar {
                Column {
                    TabRow(tab, onTab = { tab = it }, actions = if (ownActions) HomeActions({ app.openSearch() }, app.host::openSettings) else null)
                    if (tab == EhTab.TOPLIST) PeriodRow(lists)
                }
            }
            Hairline()
            val grid = grids.getValue(tab)
            Box(Modifier.weight(1f).swipeTabs(EhTab.entries, tab) { tab = it }) {
                when (tab) {
                    EhTab.HISTORY -> HistoryTab(app, frame.padding, grid)
                    EhTab.FAVORITES -> FavoritesTab(app, frame.padding, grid)
                    else -> lists.of(tab)?.let { list -> ListTab(app, list, emptyText(tab), frame.padding, grid) }
                }
            }
        }
        NoticeHost(app.notices, Modifier.padding(frame.padding))
    }
}

private class HomeActions(val search: () -> Unit, val settings: () -> Unit)

@Composable
private fun TabRow(tab: EhTab, onTab: (EhTab) -> Unit, actions: HomeActions?) {
    PluginChipTabs(EhTab.entries, tab, onTab, icon = { tabLook(it).first }, label = { pluginString(tabLook(it).second) }) {
        if (actions != null) {
            IconButton(onClick = actions.search) { Icon(Icons.Filled.Search, pluginString(Strings.search)) }
            IconButton(onClick = actions.settings) { Icon(Icons.Filled.Settings, pluginString(Strings.settings)) }
        }
    }
}

private fun tabLook(tab: EhTab): Pair<ImageVector, PluginText> = when (tab) {
    EhTab.POPULAR -> EhIcons.Popular to Strings.tab_popular
    EhTab.FRONT -> Icons.Outlined.Home to Strings.tab_front
    EhTab.TOPLIST -> EhIcons.Toplist to Strings.tab_toplist
    EhTab.WATCHED -> EhIcons.Watched to Strings.tab_watched
    EhTab.HISTORY -> PostIcons.History to Strings.tab_history
    EhTab.FAVORITES -> Icons.Filled.FavoriteBorder to Strings.tab_favorites
}

private fun emptyText(tab: EhTab): PluginText = if (tab == EhTab.WATCHED) Strings.empty_watched else Strings.empty_list

/** The toplist's windows: yesterday, the past month, the past year, all time. */
@Composable
private fun PeriodRow(lists: EhLists) {
    val period by lists.period.collectAsState()
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EhToplistPeriod.entries.forEach { choice ->
            FilterChip(
                selected = period == choice,
                onClick = { lists.choose(choice) },
                label = { Text(pluginString(EhText.toplist(choice))) },
                leadingIcon = chipCheck(period == choice),
            )
        }
    }
}

/** A list of the site's: its galleries, or why there are none and a way to read it again. */
@Composable
private fun ListTab(app: EhApp, list: EhList, empty: PluginText, padding: PaddingValues, grid: LazyGridState) {
    val state by list.feed.state.collectAsState()
    val attempted by list.attempted.collectAsState()
    val error = state.error
    when {
        error != null && !state.loading -> EhProblem(error, list::refresh, Modifier.padding(padding))
        state.galleries.isEmpty() && (state.loading || !attempted) -> GridSkeleton(columns = 2)
        state.galleries.isEmpty() -> EmptyPane(EhIcons.Collections, pluginString(empty), Modifier.padding(padding)) {
            Button(onClick = list::refresh) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Text(pluginString(Strings.retry), Modifier.padding(start = 8.dp))
            }
        }
        else -> GalleryGrid(
            app,
            state.galleries,
            padding,
            grid,
            refreshing = state.loading,
            onRefresh = list::refresh,
            loadingMore = state.loadingMore,
            onNearEnd = list::more,
        )
    }
}

/** The galleries read here, the last one read first. */
@Composable
private fun HistoryTab(app: EhApp, padding: PaddingValues, grid: LazyGridState) {
    val rows by app.history.state.collectAsState()
    val shown = rows
    when {
        shown == null -> LoadingRow()
        shown.isEmpty() -> NothingKept(app, PostIcons.History, Strings.empty_history, padding)
        else -> GalleryGrid(app, shown.map { EhRows.gallery(it.gallery) }, padding, grid)
    }
}

/** The galleries favourited here, the newest first. */
@Composable
private fun FavoritesTab(app: EhApp, padding: PaddingValues, grid: LazyGridState) {
    val rows by app.favorites.state.collectAsState()
    val shown = rows
    when {
        shown == null -> LoadingRow()
        shown.isEmpty() -> NothingKept(app, Icons.Filled.FavoriteBorder, Strings.empty_favorites, padding)
        else -> GalleryGrid(app, shown.map { EhRows.gallery(it.gallery) }, padding, grid)
    }
}

@Composable
private fun NothingKept(app: EhApp, icon: ImageVector, message: PluginText, padding: PaddingValues) {
    EmptyPane(icon, pluginString(message), Modifier.padding(padding)) {
        Button(onClick = { app.openSearch() }) {
            Icon(Icons.Filled.Search, contentDescription = null)
            Text(pluginString(Strings.search), Modifier.padding(start = 8.dp))
        }
    }
}
