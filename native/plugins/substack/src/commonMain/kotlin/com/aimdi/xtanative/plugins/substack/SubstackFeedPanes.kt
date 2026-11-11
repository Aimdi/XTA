package com.aimdi.xtanative.plugins.substack

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.substack.SubstackFeedFilter
import com.aimdi.xtanative.networks.substack.SubstackHomeFeed
import com.aimdi.xtanative.networks.substack.SubstackHomeRules
import com.aimdi.xtanative.networks.substack.SubstackPost
import com.aimdi.xtanative.store.settings.SubstackPrefs
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.ScrollsToTop
import com.aimdi.xtanative.ui.plugins.pluginString
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

/** Home: every followed publication's posts, newest first, through the reader's filters. */
@Composable
internal fun HomeFeedPane(app: SubstackApp, state: HomeState, frame: PluginFrame) {
    val publications by app.publications.collectAsState()
    val followed = publications ?: return LoadingRow()
    if (followed.isEmpty()) return NoFollows(app, frame, discover = true)
    val feed by state.feed.state.collectAsState()
    val marks = rememberMarks(app)
    val controls = state.controls
    val options = controls.home
    val visible = SubstackHomeRules.home(feed.posts, followed, controls.filter, marks.readIds, marks::saved, options)
    val list = rememberLazyListState()
    val markOnScroll by app.host.settings.observe(SubstackPrefs.markReadOnScroll).collectAsState(false)
    if (markOnScroll) MarksReadScrolledPast(app, list, visible)
    FeedList(state.feed, feed, list, frame) {
        pinnedToolbar { ReadingToolbar(app, state, HomePane.HOME, followed) }
        if (visible.isEmpty()) {
            item(key = "empty") { EmptyFeed(state, HomePane.HOME) }
        } else {
            items(visible, key = { "post-" + it.key }, contentType = { "post" }) { post ->
                SubstackPostCard(app, post, marks, SubstackHomeRules.logoOf(followed, post))
            }
        }
        item(key = "end") { LoadMoreRow(feed.loadingMore, feed.loadMoreError, feed.canLoadMore, state.feed::loadMore, enabled = !feed.refreshing) }
    }
}

/** Inbox: the unread posts of the publications followed, a local stand-in for Substack's own. */
@Composable
internal fun InboxPane(app: SubstackApp, state: HomeState, frame: PluginFrame) {
    val publications by app.publications.collectAsState()
    val followed = publications ?: return LoadingRow()
    if (followed.isEmpty()) return NoFollows(app, frame, discover = false)
    val feed by state.feed.state.collectAsState()
    val marks = rememberMarks(app)
    val options = state.controls.inbox
    val unread = SubstackHomeRules.inbox(feed.posts, followed, marks.readIds, options)
    val list = rememberLazyListState()
    FeedList(state.feed, feed, list, frame) {
        pinnedToolbar { ReadingToolbar(app, state, HomePane.INBOX, followed) }
        if (unread.isEmpty()) {
            item(key = "empty") { EmptyInbox(state) }
        } else {
            items(unread, key = { "post-" + it.key }, contentType = { "post" }) { post ->
                SubstackPostCard(app, post, marks, SubstackHomeRules.logoOf(followed, post))
            }
        }
        item(key = "end") { LoadMoreRow(feed.loadingMore, feed.loadMoreError, feed.canLoadMore, state.feed::loadMore, enabled = !feed.refreshing) }
    }
}

/** A feed's list: pulled to read again, its first read or failure in its place, a failed refresh above what stays. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedList(feed: SubstackHomeFeed, state: SubstackHomeFeed.State, list: LazyListState, frame: PluginFrame, rows: LazyListScope.() -> Unit) {
    ScrollsToTop(list, frame.toTop)
    val error = state.refreshError
    PullToRefreshBox(isRefreshing = state.refreshing && state.loaded, onRefresh = { feed.refresh(force = true) }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = list, contentPadding = frame.padding, modifier = Modifier.fillMaxSize()) {
            when {
                !state.loaded && error != null -> item(key = "problem") { FailurePane(error, onRetry = { feed.refresh(force = true) }) }
                !state.loaded -> item(key = "loading") { LoadingRow() }
                else -> rows()
            }
        }
    }
}

/**
 * The feed's toolbar, pinned above its posts: its search and filters stay in
 * reach however far the reader scrolls, as XTA keeps them in the home's dock.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.pinnedToolbar(toolbar: @Composable () -> Unit) {
    stickyHeader(key = "toolbar", contentType = "toolbar") {
        Surface(color = MaterialTheme.colorScheme.background) { toolbar() }
    }
}

@Composable
private fun NoFollows(app: SubstackApp, frame: PluginFrame, discover: Boolean) {
    LazyColumn(contentPadding = frame.padding, modifier = Modifier.fillMaxSize()) {
        item(key = "none") {
            EmptyState(
                pluginString(Strings.empty),
                icon = if (discover) SubstackIcons.Newspaper else null,
                description = if (discover) pluginString(Strings.empty_description) else null,
            ) {
                if (discover) {
                    FilledTonalButton(onClick = app::openDiscover, modifier = Modifier.padding(top = 16.dp)) {
                        Icon(SubstackIcons.Explore, null, Modifier.padding(end = 8.dp))
                        Text(pluginString(Strings.discover))
                    }
                }
                OutlinedButton(onClick = app::openAdd, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                    Icon(Icons.Default.Add, null, Modifier.padding(end = 8.dp))
                    Text(pluginString(Strings.add))
                }
            }
        }
    }
}

@Composable
private fun EmptyFeed(state: HomeState, pane: HomePane) {
    val controls = state.controls
    val filtered = controls.filter != SubstackFeedFilter.ALL || controls.options(pane).searching
    EmptyState(pluginString(if (filtered) Strings.empty_filter else Strings.feed_empty)) {
        TextButton(onClick = { if (filtered) controls.reset(pane) else state.feed.refresh(force = true) }) {
            Icon(if (filtered) Icons.Default.Clear else Icons.Default.Refresh, null, Modifier.padding(end = 8.dp))
            Text(pluginString(if (filtered) Strings.reset_filters else Strings.retry))
        }
    }
}

@Composable
private fun EmptyInbox(state: HomeState) {
    val searching = state.controls.inbox.searching
    EmptyState(pluginString(if (searching) Strings.empty_filter else Strings.inbox_empty)) {
        if (searching) TextButton(onClick = { state.controls.reset(HomePane.INBOX) }) { Text(pluginString(Strings.reset_filters)) }
        Text(
            pluginString(Strings.inbox_intro),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

/**
 * Marks posts read as their cards leave the top of the list while the reader
 * scrolls down, at most once a second, as XTA's SubstackScrolledPast: the
 * setting "Mark posts read as you scroll past". The list's first row is its
 * toolbar, so the post at [posts]' index i is row i + 1.
 */
@Composable
private fun MarksReadScrolledPast(app: SubstackApp, list: LazyListState, posts: List<SubstackPost>) {
    val shown by rememberUpdatedState(posts)
    LaunchedEffect(list) {
        var furthest = 0
        snapshotFlow { list.firstVisibleItemIndex }.distinctUntilChanged().collect { first ->
            if (first <= furthest) return@collect
            val past = shown.take((first - 1).coerceAtMost(shown.size)).drop((furthest - 1).coerceAtLeast(0))
            furthest = first
            if (past.isEmpty()) return@collect
            delay(1_000)
            val unread = past.filterNot(app.read::isRead)
            if (unread.isNotEmpty()) app.launch { app.read.markRead(unread) }
        }
    }
}
