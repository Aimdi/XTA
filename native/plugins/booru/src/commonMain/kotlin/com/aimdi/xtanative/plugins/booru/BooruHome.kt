package com.aimdi.xtanative.plugins.booru

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.RefreshesOn
import com.aimdi.xtanative.ui.plugins.ScrollsToTop
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.subscriptions.GroupIcons

/**
 * The home tab (booru_screen.dart): Latest, Popular and Following, with the
 * tag search and the settings beside them unless the screen around it has
 * its own ([ownActions] false).
 */
@Composable
internal fun BooruHome(app: BooruApp, frame: PluginFrame, ownActions: Boolean = true) = Bound(app) { settings ->
    var tab by rememberSaveable { mutableStateOf(HomeTab.LATEST) }
    val feeds = rememberKept("booru-home") { scope -> HomeFeeds(app, scope) }
    val follows by app.follows.state.collectAsState()
    val grids = HomeTab.entries.associateWith { rememberLazyStaggeredGridState() }
    val reduced = LocalReducedMotion.current
    LaunchedEffect(tab, settings, follows) { feeds.ensure(tab) }
    RefreshesOn(frame.refresh) { feeds.feed(tab).refresh() }
    ScrollsToTop(frame.toTop) { grids.getValue(tab).let { if (reduced) it.scrollToItem(0) else it.animateScrollToItem(0) } }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            HomeBar(tab, onTab = { tab = it }, actions = if (ownActions) HomeActions({ app.openSearch() }, app.host::openSettings) else null)
            Hairline()
            when (tab) {
                HomeTab.LATEST -> FeedView(app, feeds.latest, pluginString(Strings.empty_latest), frame.padding, grid = grids.getValue(tab))
                HomeTab.POPULAR -> PopularTab(app, feeds, settings, frame, grids.getValue(tab))
                HomeTab.FOLLOWING -> FollowingTab(app, feeds, follows, frame, grids.getValue(tab))
            }
        }
        BooruMessages(app, Modifier.align(Alignment.BottomCenter).padding(frame.padding))
    }
}

/** The tag search and the settings, as the home tab's row offers them. */
private class HomeActions(val search: () -> Unit, val settings: () -> Unit)

@Composable
private fun HomeBar(tab: HomeTab, onTab: (HomeTab) -> Unit, actions: HomeActions?) {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeTab.entries.forEach { choice ->
                val (icon, label) = tabLook(choice)
                FilterChip(
                    selected = tab == choice,
                    onClick = { onTab(choice) },
                    label = { Text(label) },
                    leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
        if (actions != null) {
            IconButton(onClick = actions.search) { Icon(Icons.Filled.Search, pluginString(Strings.search)) }
            IconButton(onClick = actions.settings) { Icon(Icons.Filled.Settings, pluginString(Strings.settings)) }
        }
    }
}

@Composable
private fun tabLook(tab: HomeTab): Pair<ImageVector, String> = when (tab) {
    HomeTab.LATEST -> GroupIcons.PhotoLibraryOutlined to pluginString(Strings.tab_latest)
    HomeTab.POPULAR -> BooruIcons.Popular to pluginString(Strings.popular)
    HomeTab.FOLLOWING -> BooruIcons.Sell to pluginString(Strings.tab_following)
}

/** Following: nothing to read until tags are followed; then the followed tags in a row, a warning among them while some did not load, and their posts. */
@Composable
private fun FollowingTab(app: BooruApp, feeds: HomeFeeds, tags: List<String>?, frame: PluginFrame, grid: LazyStaggeredGridState) {
    when {
        tags == null -> LoadingRow()
        tags.isEmpty() -> NothingFollowed(app, frame)
        else -> FollowedPosts(app, feeds, tags, frame, grid)
    }
}

@Composable
private fun NothingFollowed(app: BooruApp, frame: PluginFrame) {
    EmptyPane(BooruIcons.Sell, pluginString(Strings.empty_following), Modifier.padding(frame.padding)) {
        Button(onClick = { app.openSearch() }) {
            Icon(Icons.Filled.Search, contentDescription = null)
            Text(pluginString(Strings.search), Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun FollowedPosts(app: BooruApp, feeds: HomeFeeds, tags: List<String>, frame: PluginFrame, grid: LazyStaggeredGridState) {
    val state by feeds.following.state.collectAsState()
    Column(Modifier.fillMaxSize()) {
        FollowingRow(app, tags, state.failedQueries, onRetry = { feeds.following.refresh() })
        FeedView(app, feeds.following, pluginString(Strings.empty_following_posts), frame.padding, grid = grid, isNew = feeds::isNew)
    }
}

/** The followed tags, led by a warning while some of them did not load: a tag opens its search, its cross unfollows it. */
@Composable
private fun FollowingRow(app: BooruApp, tags: List<String>, failed: Int, onRetry: () -> Unit) {
    var explaining by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (failed > 0) {
            IconButton(onClick = { explaining = true }) {
                Icon(GroupIcons.WarningAmber, pluginString(Strings.following_partial), tint = MaterialTheme.colorScheme.error)
            }
        }
        tags.forEach { tag ->
            InputChip(
                selected = false,
                onClick = { app.openSearch(tag) },
                label = { Text(tag) },
                trailingIcon = {
                    IconButton(onClick = { app.launch { app.unfollow(tag) } }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Close, pluginString(Strings.unsubscribe), modifier = Modifier.size(18.dp))
                    }
                },
            )
        }
    }
    if (explaining) PartialFailureDialog(onRetry = { explaining = false; onRetry() }, onDismiss = { explaining = false })
}

/** Why some followed tags are missing from the feed, and a way to read them again. */
@Composable
private fun PartialFailureDialog(onRetry: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(pluginString(Strings.following_partial)) },
        confirmButton = { TextButton(onClick = onRetry) { Text(pluginString(Strings.retry)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(pluginString(Strings.cancel)) } },
    )
}
