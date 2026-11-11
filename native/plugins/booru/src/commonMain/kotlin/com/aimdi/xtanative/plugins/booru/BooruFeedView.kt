package com.aimdi.xtanative.plugins.booru

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.booru.BooruPost
import com.aimdi.xtanative.ui.common.GridSkeleton
import com.aimdi.xtanative.ui.plugins.RetryMessage
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import com.aimdi.xtanative.ui.plugins.pluginString

/**
 * A feed's posts (booru_feed_view.dart): loading until its first page, then
 * the grid, which pages on as it scrolls; why, when nothing came; a notice
 * floating over the posts when a refresh failed; [empty] when the feed has
 * nothing to show. Above it, how many posts the blacklist hid; given [isNew],
 * how many came in since the last visit, each marked. [above] sits over the
 * grid once there are posts (a search's related tags).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedView(
    app: BooruApp,
    feed: BooruFeed,
    empty: String,
    padding: PaddingValues,
    modifier: Modifier = Modifier,
    grid: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    isNew: ((BooruPost) -> Boolean)? = null,
    search: BooruSearch? = null,
    retryWhenEmpty: Boolean = true,
    above: @Composable () -> Unit = {},
) {
    val state by feed.state.collectAsState()
    Column(modifier.fillMaxSize()) {
        HiddenNotice(state, feed)
        if (isNew != null) NewPostsNotice(state.posts, isNew)
        Box(Modifier.weight(1f)) {
            when (state.view) {
                FeedView.LOADING -> GridSkeleton(columns = 2)
                FeedView.ERROR -> Scrolled(padding) { RetryMessage(errorText(state.error), onRetry = { feed.refresh() }) }
                FeedView.EMPTY -> Scrolled(padding) { EmptyFeed(empty, retryWhenEmpty, feed) }
                FeedView.HIDDEN_SO_FAR -> Scrolled(padding) { HiddenSoFar(state.paging, feed) }
                FeedView.POSTS -> Column(Modifier.fillMaxSize()) {
                    above()
                    PullToRefreshBox(isRefreshing = state.loading, onRefresh = { feed.refresh() }, modifier = Modifier.weight(1f)) {
                        PostGrid(app, state, feed, grid, padding, isNew, search)
                    }
                }
            }
            state.refreshFailure?.let { error -> RefreshNotice(error, feed, Modifier.align(Alignment.TopCenter)) }
        }
    }
}

/** What fills a feed's place when there is no list to scroll: it still scrolls, clear of the app's bars. */
@Composable
private fun Scrolled(padding: PaddingValues, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding)) { content() }
}

@Composable
private fun EmptyFeed(empty: String, retry: Boolean, feed: BooruFeed) {
    EmptyPane(GroupIcons.PhotoLibraryOutlined, empty) {
        if (retry) {
            Button(onClick = { feed.refresh() }) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Text(pluginString(Strings.retry), Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** Every post so far was hidden by the rating cap, the blacklist or a missing file while the host has more: the reader can go on to the next pages. */
@Composable
private fun HiddenSoFar(paging: BooruPaging, feed: BooruFeed) {
    val failed = paging == BooruPaging.FAILED
    EmptyPane(GroupIcons.VisibilityOffOutlined, pluginString(if (failed) Strings.load_more_failed else Strings.hidden_so_far)) {
        when {
            paging == BooruPaging.LOADING -> CircularProgressIndicator()
            failed -> Button(onClick = feed::retryMore) { Text(pluginString(Strings.retry)) }
            else -> Button(onClick = feed::loadMore) {
                Icon(GroupIcons.ExpandMore, contentDescription = null)
                Text(pluginString(Strings.load_more), Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** Above a feed whose posts the blacklist partly hid: how many, and a button to show them in their places, or hide them again. */
@Composable
private fun HiddenNotice(state: BooruFeedState, feed: BooruFeed) {
    if (state.hidden == 0) return
    NoticeLine(
        if (state.hiddenShown) BooruIcons.Visibility else GroupIcons.VisibilityOffOutlined,
        pluginString(Strings.hidden_count, state.hidden),
        actionLabel = pluginString(if (state.hiddenShown) Strings.hide else Strings.show),
        onAction = { if (state.hiddenShown) feed.hideHidden() else feed.showHidden() },
    )
}

/** Above a feed: how many of its posts came in since the reader's last visit. */
@Composable
private fun NewPostsNotice(posts: List<BooruPost>, isNew: (BooruPost) -> Boolean) {
    val count = posts.count(isNew)
    if (count > 0) NoticeLine(BooruIcons.NewPost, pluginString(Strings.new_since_visit, count))
}

/** Over a feed whose refresh failed: why, that the posts from before are still shown, Retry, and a way to put it away. */
@Composable
private fun RefreshNotice(error: Throwable, feed: BooruFeed, modifier: Modifier) {
    Surface(
        modifier.padding(12.dp).widthIn(max = 560.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp,
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(errorText(error), style = MaterialTheme.typography.bodyMedium)
                Text(pluginString(Strings.showing_earlier), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { feed.refresh() }) { Text(pluginString(Strings.retry)) }
            IconButton(onClick = feed::dismissRefreshFailure) { Icon(Icons.Filled.Close, pluginString(Strings.dismiss)) }
        }
    }
}
