package com.aimdi.xtanative.plugins.mastodon

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.mastodon.MastodonTagReader
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.plugins.MessageText
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.RetryMessage
import com.aimdi.xtanative.ui.plugins.RetryRow
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept

/** One hashtag's public posts (MastodonTagScreen), from the first of the reader's instances and the built-in ones that has them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MastodonTagScreen(app: MastodonApp, name: String) {
    val reader = rememberKept("mastodon-tag:$name") { scope -> MastodonTagReader(app.client, app::discovery, name, scope).also { it.refresh() } }
    val state by reader.state.collectAsState()
    val list = rememberLazyListState()
    val nearEnd by remember(list) { derivedStateOf { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= list.layoutInfo.totalItemsCount - 5 } }
    LaunchedEffect(nearEnd, state.posts.size) { if (nearEnd && state.canLoadMore) reader.loadMore() }
    PluginScaffold(
        title = "#" + name.removePrefix("#"),
        onBack = app.host::back,
        actions = { IconButton(onClick = reader::refresh) { Icon(Icons.Filled.Refresh, pluginString(Strings.retry)) } },
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.loading && state.posts.isNotEmpty(), onRefresh = reader::refresh, modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            LazyColumn(state = list, contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 32.dp), modifier = Modifier.fillMaxSize()) {
                val error = state.error
                when {
                    state.posts.isEmpty() && (state.loading || !state.loaded) -> item(key = "loading") { LoadingRow() }
                    state.posts.isEmpty() && error != null -> item(key = "problem") { RetryMessage(pluginString(problemText(error)), reader::refresh) }
                    state.posts.isEmpty() -> item(key = "empty") { MessageText(pluginString(Strings.no_posts)) }
                    error != null -> item(key = "refresh-failed") { RetryRow(pluginString(Strings.refresh_failed), reader::refresh) }
                }
                items(state.posts, key = { it.url }, contentType = { "post" }) { post -> MastodonPostRow(app, post) }
                item(key = "footer") {
                    val more = state.moreError
                    when {
                        state.loadingMore -> LoadingRow()
                        more != null -> RetryRow(pluginString(Strings.load_more_failed), reader::retryMore)
                        state.canLoadMore -> LoadMoreButton(reader::loadMore)
                    }
                }
            }
        }
    }
}
