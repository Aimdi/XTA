package com.aimdi.xtanative.plugins.mastodon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.mastodon.MastodonFollowingReader
import com.aimdi.xtanative.networks.mastodon.MastodonTag
import com.aimdi.xtanative.networks.mastodon.MastodonTimeline
import com.aimdi.xtanative.networks.mastodon.MastodonTimelineFilter
import com.aimdi.xtanative.networks.mastodon.MastodonTimelineReader
import com.aimdi.xtanative.networks.mastodon.MastodonTimelineState
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.plugins.EmptyPane
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.RefreshesOn
import com.aimdi.xtanative.ui.plugins.RetryMessage
import com.aimdi.xtanative.ui.plugins.RetryRow
import com.aimdi.xtanative.ui.plugins.ScrollsToTop
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One of the reader's timelines, as XTA's timeline pane shows it: the
 * toolbar, then the posts its filters keep, read again when pulled, the next
 * page as the end comes into view, a failed read over what it kept; opened
 * where it was left ([slot]), the first time this session, and remembered as
 * it scrolls. [empty] is what an empty timeline says, with what it offers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimelinePane(app: MastodonApp, timeline: MastodonTimeline, slot: String, frame: PluginFrame, empty: @Composable () -> Unit) {
    val reader = app.reader(timeline)
    val state by reader.state.collectAsState()
    val prefs by app.prefs.collectAsState()
    val searched by app.searched.collectAsState()
    val filter = app.filter(timeline, prefs, searched)
    val visible = remember(state.posts, filter) { filter.apply(state.posts) }
    val rows = remember(state, visible, timeline) { TimelineRows.of(state, visible, trending = timeline == MastodonTimeline.EXPLORE) }
    val list = rememberLazyListState()
    val idle = !state.loaded && !state.loading
    LaunchedEffect(timeline, idle) { if (idle) openTimeline(app, timeline, slot) }
    OpensWhereLeft(app, slot, list, rows)
    RemembersPlace(app, slot, list, state)
    ReadsMore(list, state, reader)
    ScrollsToTop(list, frame.toTop)
    RefreshesOn(frame.refresh) { refresh(reader) }
    Column(Modifier.fillMaxSize()) {
        TimelineToolbar(filter, state.instance.takeIf { timeline == MastodonTimeline.LOCAL || timeline == MastodonTimeline.FEDERATED }) { app.setFilter(timeline, it) }
        PullToRefreshBox(isRefreshing = state.loading && !state.isEmpty, onRefresh = { refresh(reader) }, modifier = Modifier.weight(1f)) {
            LazyColumn(state = list, contentPadding = frame.padding, modifier = Modifier.fillMaxSize()) {
                items(rows, key = { it.key }, contentType = { if (it is TimelineRow.Post) TimelineRow.POST else it.key }) { row ->
                    TimelineRowView(app, reader, row, state.tags, empty) { app.setFilter(timeline, MastodonTimelineFilter()) }
                }
            }
        }
    }
}

@Composable
private fun TimelineRowView(app: MastodonApp, reader: MastodonTimelineReader, row: TimelineRow, tags: List<MastodonTag>, empty: @Composable () -> Unit, onReset: () -> Unit) {
    when (row) {
        is TimelineRow.RefreshFailed -> RetryRow(pluginString(Strings.refresh_failed), { refresh(reader) })
        TimelineRow.Trending -> TrendingTags(app, tags)
        TimelineRow.Loading -> LoadingRow()
        is TimelineRow.Problem -> RetryMessage(pluginString(problemText(row.error)), { refresh(reader) })
        TimelineRow.Empty -> empty()
        TimelineRow.Filtered -> FilteredEmpty(onReset)
        is TimelineRow.Post -> MastodonPostRow(app, row.post)
        TimelineRow.LoadingMore -> LoadingRow()
        is TimelineRow.MoreFailed -> RetryRow(pluginString(Strings.load_more_failed), reader::retryMore)
        TimelineRow.LoadMore -> LoadMoreButton(reader::loadMore)
    }
}

/** No post matches the filters: say so, and offer to reset them. */
@Composable
private fun FilteredEmpty(onReset: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(GroupIcons.FilterAltOutlined, contentDescription = null, modifier = Modifier.padding(bottom = 12.dp).size(36.dp))
        Text(pluginString(Strings.empty_filter), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        TextButton(onClick = onReset) { Text(pluginString(Strings.reset_filters)) }
    }
}

@Composable
internal fun LoadMoreButton(onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
        OutlinedButton(onClick = onClick) { Text(pluginString(Strings.load_more)) }
    }
}

/** Explore's heading: the trending tags, each opening its own timeline. */
@Composable
private fun TrendingTags(app: MastodonApp, tags: List<MastodonTag>) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp)) {
        Text(
            pluginString(Strings.trending),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            items(tags, key = { it.name }) { tag ->
                AssistChip(onClick = { app.openTag(tag.name) }, label = { Text(tag.name) }, leadingIcon = { Icon(MastodonIcons.Tag, contentDescription = null, modifier = Modifier.size(16.dp)) })
            }
        }
    }
}

/**
 * In place of an empty timeline, as XTA's EmptyPane draws it: the
 * timeline's [icon], what there is to say, and what it offers instead
 * ([action] under [actionIcon]).
 */
@Composable
internal fun EmptyTimeline(icon: ImageVector, message: String, action: String? = null, actionIcon: ImageVector? = null, onAction: () -> Unit = {}) {
    EmptyPane(icon, message) {
        if (action != null) {
            Button(onClick = onAction) {
                if (actionIcon != null) {
                    Icon(actionIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(action)
            }
        }
    }
}

private fun refresh(reader: MastodonTimelineReader) = if (reader is MastodonFollowingReader) reader.refresh(force = true) else reader.refresh()

/**
 * A timeline shown with nothing read yet, as XTA's _onTab opens one: where
 * it was left, when that is kept and this is its first time this session
 * (Following keeps only what followed accounts wrote or boosted); else read.
 */
private suspend fun openTimeline(app: MastodonApp, timeline: MastodonTimeline, slot: String) {
    if (timeline == MastodonTimeline.FOLLOWING) app.follows.load()
    val reader = app.reader(timeline)
    app.places.takeRestore(slot)?.let { point ->
        val posts = if (timeline == MastodonTimeline.FOLLOWING) MastodonFollowingReader.kept(point.posts, app.follows::follows) else point.posts
        reader.restore(posts, point.instance, point.tags)
    }
    if (!reader.state.value.loaded && !reader.state.value.loading) reader.refresh()
}

/** The list moved to where [slot] was left, once its post is there, the first time this session. */
@Composable
private fun OpensWhereLeft(app: MastodonApp, slot: String, list: LazyListState, rows: List<TimelineRow>) {
    val current by rememberUpdatedState(rows)
    LaunchedEffect(slot) {
        val point = app.places.takePosition(slot) ?: return@LaunchedEffect
        val start = withTimeoutOrNull(OPEN_WAIT_MS) { snapshotFlow { TimelineRows.start(current, point) }.first { it != null } } ?: return@LaunchedEffect
        list.scrollToItem(start.first, start.second)
    }
}

/** Where the list is, kept a moment after it settles. */
@Composable
private fun RemembersPlace(app: MastodonApp, slot: String, list: LazyListState, state: MastodonTimelineState) {
    val current by rememberUpdatedState(state)
    LaunchedEffect(list, slot) {
        snapshotFlow { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }.collectLatest {
            delay(SETTLE_MS)
            val visible = list.layoutInfo.visibleItemsInfo.map { it.key to it.offset }
            TimelineRows.place(visible, current)?.let { app.places.remember(slot, it) }
        }
    }
}

/** The next page as the list's end comes into view, as XTA asks for it. */
@Composable
private fun ReadsMore(list: LazyListState, state: MastodonTimelineState, reader: MastodonTimelineReader) {
    val nearEnd by remember(list) {
        derivedStateOf {
            val info = list.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - NEAR_END
        }
    }
    LaunchedEffect(nearEnd, state.posts.size, state.next) { if (nearEnd && state.canLoadMore) reader.loadMore() }
}

private const val OPEN_WAIT_MS = 4_000L
private const val SETTLE_MS = 600L
private const val NEAR_END = 5
