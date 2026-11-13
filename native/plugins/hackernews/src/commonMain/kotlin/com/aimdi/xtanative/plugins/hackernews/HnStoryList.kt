package com.aimdi.xtanative.plugins.hackernews

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.aimdi.xtanative.networks.hackernews.HnStory
import com.aimdi.xtanative.ui.plugins.EmptyPane
import com.aimdi.xtanative.ui.plugins.PagedItems
import com.aimdi.xtanative.ui.plugins.PagedState
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.ReadsMoreNearEnd
import com.aimdi.xtanative.ui.plugins.ScrollsToTop
import com.aimdi.xtanative.ui.plugins.pagedRows

/**
 * Stories read page by page and pulled to read again, as XTA's story lists:
 * where there are none, its empty pane with [emptyIcon] over [empty]
 * (hn_screen.dart's forum, hn_search_sheet.dart's search_off).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HnStoryList(app: HnApp, pages: PagedItems<HnStory>, emptyIcon: ImageVector, empty: String, frame: PluginFrame, row: @Composable (HnStory) -> Unit) {
    val paged by pages.state.collectAsState()
    val list = rememberLazyListState()
    ScrollsToTop(list, frame.toTop)
    ReadsMoreNearEnd(list, paged) { pages.more() }
    PullToRefreshBox(isRefreshing = paged.loading && paged.loaded, onRefresh = pages::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = list, contentPadding = frame.padding, modifier = Modifier.fillMaxSize()) {
            if (HnListRules.nothing(paged)) {
                item(key = "empty") { EmptyPane(emptyIcon, empty) }
            } else {
                pagedRows(paged, pages, key = { "${it.id}" }, network = app.network, empty = empty, row = row)
            }
        }
    }
}

internal object HnListRules {
    /** Whether a list read through has nothing to show and nothing to say went wrong: the empty pane's turn. */
    fun nothing(paged: PagedState<*>): Boolean = paged.items.isEmpty() && paged.loaded && !paged.loading && paged.problem == null
}
