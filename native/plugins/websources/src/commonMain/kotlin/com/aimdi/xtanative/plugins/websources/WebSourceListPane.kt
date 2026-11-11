package com.aimdi.xtanative.plugins.websources

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.websources.WebSourceFailureKind
import com.aimdi.xtanative.networks.websources.WebSourceItem
import com.aimdi.xtanative.networks.websources.WebSourceLayout
import com.aimdi.xtanative.networks.websources.WebSourceReading
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.plugins.EmptyPane
import com.aimdi.xtanative.ui.plugins.PluginFeedSkeleton
import com.aimdi.xtanative.ui.plugins.PluginGridSkeleton
import com.aimdi.xtanative.ui.plugins.RetryMessage
import com.aimdi.xtanative.ui.plugins.ScrollsToTop
import com.aimdi.xtanative.ui.plugins.pluginString

/**
 * One list of a source (web_source_list_pane.dart): its posts, or a grid of
 * pictures for a gallery, reading the next page as the reader nears the
 * end, pulled to read again. What is shown stays when a read fails.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WebSourceListPane(app: WebSourcesApp, feed: WebSourceFeed, padding: PaddingValues = PaddingValues(), toTop: Int = 0) {
    val state by feed.state.collectAsState()
    LaunchedEffect(feed) { if (!feed.state.value.loaded && !feed.state.value.loading) feed.refresh() }
    val actions = rememberItemActions(app, feed.source)
    val error = state.error
    when {
        state.items.isEmpty() && error != null -> return RetryMessage(failureText(error), feed::refresh)
        state.items.isEmpty() && !state.loaded -> return if (feed.source.layout == WebSourceLayout.GALLERY) PluginGridSkeleton(Modifier.padding(padding)) else PluginFeedSkeleton(Modifier.padding(padding))
    }
    PullToRefreshBox(isRefreshing = state.loading && state.loaded, onRefresh = feed::refresh, modifier = Modifier.fillMaxSize()) {
        when {
            state.items.isEmpty() -> Empty()
            feed.source.layout == WebSourceLayout.GALLERY -> Gallery(app, feed, state.items, padding, toTop)
            else -> Posts(feed, state.items, state.loadingMore, actions, padding, toTop)
        }
    }
}

@Composable
private fun Posts(feed: WebSourceFeed, items: List<WebSourceItem>, loadingMore: Boolean, actions: ItemActions, padding: PaddingValues, toTop: Int) {
    val list = rememberLazyListState()
    ScrollsToTop(list, toTop)
    ReadsMoreNearEnd(list, items.size, feed::more)
    LazyColumn(state = list, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
        items(items, key = { it.key }) { item -> WebSourceItemCard(feed.source, item, actions) }
        item(key = "footer") { if (loadingMore) LoadingRow() }
    }
}

@Composable
private fun Gallery(app: WebSourcesApp, feed: WebSourceFeed, items: List<WebSourceItem>, padding: PaddingValues, toTop: Int) {
    val grid = rememberLazyGridState()
    val fontScale = LocalDensity.current.fontScale
    ScrollsToTop(toTop) { grid.animateScrollToItem(0) }
    ReadsMoreNearEnd(grid, items.size, feed::more)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        LazyVerticalGrid(GridCells.Fixed(WebSourceReading.columns(maxWidth.value, fontScale)), state = grid, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            items(items, key = { it.key }) { item ->
                WebSourcePictureTile(item, onMore = { app.host.openPostMenu(app.shared.menu(feed.source, item)) }) { galleryPictures(items, item)?.let { (media, start) -> app.host.openMedia(media, start) } }
            }
        }
    }
}

@Composable
private fun ReadsMoreNearEnd(list: LazyListState, count: Int, more: () -> Unit) {
    val near by remember(list) { derivedStateOf { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= list.layoutInfo.totalItemsCount - 5 } }
    LaunchedEffect(near, count) { if (near) more() }
}

@Composable
private fun ReadsMoreNearEnd(grid: LazyGridState, count: Int, more: () -> Unit) {
    val near by remember(grid) { derivedStateOf { (grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= grid.layoutInfo.totalItemsCount - 8 } }
    LaunchedEffect(near, count) { if (near) more() }
}

/** Nothing in the list: in a list of its own, so that a pull reads it again. */
@Composable
private fun Empty() {
    LazyColumn(Modifier.fillMaxSize()) { item { EmptyPane(WebSourcesIcons.Inbox, pluginString(Strings.list_empty)) } }
}

/** What went wrong reading a page, for the reader, as XTA's webSourceFailureMessage words it. */
@Composable
internal fun failureText(error: Throwable): String {
    val (kind, status) = WebSourceReading.failure(error)
    return when (kind) {
        WebSourceFailureKind.BLOCKED -> pluginString(Strings.failure_blocked)
        WebSourceFailureKind.STATUS -> pluginString(Strings.failure_status, status?.toString().orEmpty())
        WebSourceFailureKind.TOO_LARGE -> pluginString(Strings.failure_too_large)
        WebSourceFailureKind.UNREACHABLE -> pluginString(Strings.failure_unreachable)
    }
}
