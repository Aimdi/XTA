package com.aimdi.xtanative.plugins.rss

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.rss.RssFeed
import com.aimdi.xtanative.networks.rss.RssFilter
import com.aimdi.xtanative.networks.rss.RssTags
import com.aimdi.xtanative.networks.rss.RssTimelineRules
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.plugins.EmptyPane
import com.aimdi.xtanative.ui.plugins.PartialFailureButton
import com.aimdi.xtanative.ui.plugins.PluginFeedSkeleton
import com.aimdi.xtanative.ui.plugins.PluginFilterChip
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.RetryMessage
import com.aimdi.xtanative.ui.plugins.ScrollsToTop
import com.aimdi.xtanative.ui.plugins.networkProblemText
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.problemOf
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Home: every followed feed's items, with the filter row over them. A pull
 * reads the feeds again whatever the pane shows: its items, an empty or a
 * failed read, as XTA's RefreshIndicator covers the whole pane.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RssHomePane(app: RssApp, feeds: List<RssFeed>, frame: PluginFrame) {
    val timeline by app.timeline.state.collectAsState()
    var filter by rememberSaveable { mutableStateOf(RssFilter.ALL) }
    var tag by rememberSaveable { mutableStateOf<String?>(null) }
    val refresh = { app.timeline.refresh(force = true) }
    val error = timeline.error
    PullToRefreshBox(isRefreshing = timeline.loading && timeline.loaded, onRefresh = refresh, modifier = Modifier.fillMaxSize()) {
        when {
            feeds.isEmpty() -> PullableEmpty(RssIcons.Feed, pluginString(Strings.empty), frame) { AddButton(app) }
            !timeline.loaded -> Pullable(frame) { PluginFeedSkeleton() }
            error != null && timeline.items.isEmpty() -> Pullable(frame) { RetryMessage(pluginString(Strings.load_error) + "\n" + networkProblemText(problemOf(error), NETWORK), refresh) }
            else -> Timeline(app, timeline, frame, filter, tag, onFilter = { filter = it; tag = null }, onTag = { picked -> filter = RssFilter.ALL; tag = if (tag == picked) null else picked })
        }
    }
}

/** The filter row over the items it leaves, or what to do when it leaves none. */
@Composable
private fun Timeline(app: RssApp, timeline: RssTimelineState, frame: PluginFrame, filter: RssFilter, tag: String?, onFilter: (RssFilter) -> Unit, onTag: (String) -> Unit) {
    val read by app.read.collectAsState()
    val tags by app.tags.collectAsState()
    val shown = remember(timeline.items, filter, read, tag, tags) { RssTimelineRules.shown(timeline.items, filter, read, tag, tags) }
    val filtered = filter != RssFilter.ALL || tag != null
    val refresh = { app.timeline.refresh(force = true) }
    Column(Modifier.fillMaxSize()) {
        FilterRow(filter, tag, RssTags.all(tags), timeline.failed, onFilter, onTag, refresh)
        val list = rememberLazyListState()
        ScrollsToTop(list, frame.toTop)
        if (shown.isEmpty()) {
            PullableEmpty(if (filtered) RssIcons.FilterList else RssIcons.Article, pluginString(if (filtered) Strings.empty_filter else Strings.reader_empty), frame) {
                TextButton(onClick = { if (filtered) onFilter(RssFilter.ALL) else refresh() }) {
                    Icon(if (filtered) RssIcons.FilterListOff else Icons.Default.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(pluginString(if (filtered) Strings.reset_filters else Strings.retry))
                }
            }
        } else {
            LazyColumn(state = list, contentPadding = frame.padding, modifier = Modifier.fillMaxSize()) {
                items(shown, key = { it.id }) { item -> RssItemCard(item, unread = item.id !in read, onOpen = { app.openItem(item) }) }
            }
        }
    }
}

/** The feeds that failed, then All, Unread and each tag, as XTA's filter row: the one chosen shows a check. */
@Composable
private fun FilterRow(filter: RssFilter, tag: String?, tags: List<String>, failed: Int, onFilter: (RssFilter) -> Unit, onTag: (String) -> Unit, onRetry: () -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (failed > 0) PartialFailureButton(pluginString(Strings.partial_error, failed), onRetry)
        PluginFilterChip(pluginString(Strings.all), filter == RssFilter.ALL && tag == null, { onFilter(RssFilter.ALL) })
        PluginFilterChip(pluginString(Strings.unread), filter == RssFilter.UNREAD, { onFilter(RssFilter.UNREAD) })
        tags.forEach { each -> PluginFilterChip(each, tag == each, { onTag(each) }) }
    }
}

/** Feeds: the feeds followed, each opening its items, with its tags and its menu. */
@Composable
internal fun RssFeedsPane(app: RssApp, feeds: List<RssFeed>, frame: PluginFrame) {
    if (feeds.isEmpty()) return PullableEmpty(RssIcons.Feed, pluginString(Strings.following_empty), frame) { AddButton(app) }
    val tags by app.tags.collectAsState()
    val list = rememberLazyListState()
    ScrollsToTop(list, frame.toTop)
    var tagging by remember { mutableStateOf<RssFeed?>(null) }
    LazyColumn(state = list, contentPadding = frame.padding, modifier = Modifier.fillMaxSize()) {
        items(feeds, key = { it.id }) { feed ->
            FeedRow(feed, tags[feed.id].orEmpty(), onOpen = { app.openFeed(feed) }, onTag = { tagging = feed }, onGroup = { app.addToGroup(feed) }, onUnfollow = { app.launch { app.unfollow(feed.id) } })
            Hairline()
        }
    }
    tagging?.let { feed ->
        TagDialog(RssTags.field(tags, feed.id), onDismiss = { tagging = null }) { typed ->
            tagging = null
            app.launch { app.setTags(feed.id, RssTags.parse(typed)) }
        }
    }
}

@Composable
private fun FeedRow(feed: RssFeed, tags: List<String>, onOpen: () -> Unit, onTag: () -> Unit, onGroup: () -> Unit, onUnfollow: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        FeedMark()
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(feed.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(hostOf(feed), tags.joinToString(" · ").ifEmpty { null }).joinToString("\n"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, pluginString(Strings.more_options)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(pluginString(Strings.set_tag)) }, onClick = { menu = false; onTag() })
                DropdownMenuItem(text = { Text(pluginString(Strings.add_to_group)) }, onClick = { menu = false; onGroup() })
                DropdownMenuItem(text = { Text(pluginString(Strings.unfollow)) }, onClick = { menu = false; onUnfollow() })
            }
        }
    }
}

/** The round RSS mark a feed is drawn with, as XTA's CircleAvatar. */
@Composable
internal fun FeedMark() {
    Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        Icon(RssIcons.Feed, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

/** The feed's site, or its own address when it names none: what its row says under its name. */
internal fun hostOf(feed: RssFeed): String = (feed.siteUrl ?: feed.feedUrl).let { it.toHttpUrlOrNull()?.host ?: it }

@Composable
private fun TagDialog(initial: String, onDismiss: () -> Unit, onTag: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluginString(Strings.set_tag)) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, placeholder = { Text(pluginString(Strings.tag_hint)) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onTag(text) }) { Text(pluginString(Strings.tag)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(pluginString(Strings.cancel)) } },
    )
}

@Composable
internal fun AddButton(app: RssApp) {
    Button(onClick = app::openAdd) {
        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(pluginString(Strings.add))
    }
}

/** The kit's [EmptyPane] in a list, so that a pull reads the list again. */
@Composable
internal fun PullableEmpty(icon: ImageVector, message: String, frame: PluginFrame, action: @Composable () -> Unit = {}) {
    Pullable(frame) { EmptyPane(icon, message, actions = action) }
}

/** [content] in a list of its own, so that a pull over it reads again. */
@Composable
internal fun Pullable(frame: PluginFrame, content: @Composable () -> Unit) {
    LazyColumn(contentPadding = frame.padding, modifier = Modifier.fillMaxSize()) {
        item { content() }
    }
}

/** How the network names itself where a read failed. */
internal const val NETWORK = "RSS"
