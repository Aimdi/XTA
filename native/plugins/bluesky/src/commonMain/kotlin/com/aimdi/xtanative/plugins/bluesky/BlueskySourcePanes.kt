package com.aimdi.xtanative.plugins.bluesky

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.bluesky.Bluesky
import com.aimdi.xtanative.networks.bluesky.BlueskyFeedGenerator
import com.aimdi.xtanative.networks.bluesky.BlueskyListInfo
import com.aimdi.xtanative.networks.bluesky.BlueskyPost
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.plugins.MessageText
import com.aimdi.xtanative.ui.plugins.PagedItems
import com.aimdi.xtanative.ui.plugins.PagedList
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.RefreshesOn
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.saved.SavedIcons
import com.aimdi.xtanative.ui.search.DiscoverIcons
import com.aimdi.xtanative.ui.subscriptions.GroupIcons

/** A feed or list offered above the posts: its URI and the name it is shown under. */
private data class SourceChip(val uri: String, val label: String)

/** What else the header of the feed or list open offers: Find feeds, Import a list. */
private class HeaderExtra(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/**
 * Discover (bluesky_feeds_pane.dart's BlueskyAlgoPane): the custom feeds in a
 * row, Discover first, the one open named above its posts with Open a feed,
 * Pin and Find feeds.
 */
@Composable
internal fun FeedsPane(app: BlueskyApp, frame: PluginFrame) {
    val feeds = app.feeds
    val server by app.prefs.appView.collectAsState()
    val handle by app.prefs.handle.collectAsState()
    LaunchedEffect(server, handle) { feeds.ensureLoaded() }
    val state by feeds.state.collectAsState()
    val pinned by app.prefs.pinnedFeeds.collectAsState()
    val selected = state.selected
    val pages = remember(server, selected.uri) { feeds.pages(server, selected.uri) }
    RefreshesOn(frame.refresh) { pages.refresh() }
    val discover = pluginString(Strings.tab_discover)
    val chips = listOf(SourceChip(Bluesky.DISCOVER_FEED_URI, discover)) + feeds.offered(pinned).map { SourceChip(it.uri, it.displayName) }
    val described = feeds.described(pinned)
    val isPinned = pinned.any { it.uri == selected.uri }
    val title = if (selected.uri == Bluesky.DISCOVER_FEED_URI) discover else described?.displayName ?: selected.name
    var opening by rememberSaveable { mutableStateOf(false) }
    val problem = state.catalog.problem ?: state.createdProblem
    SourceList(app, pages, frame, pluginString(Strings.feed_empty)) {
        item(key = "chips") { ChipStrip(chips, selected.uri) { chip -> feeds.open(chip.uri, chip.label) } }
        item(key = "selected") {
            SelectedHeader(
                title = title,
                creator = described?.creatorHandle,
                description = described?.description.orEmpty(),
                openLabel = pluginString(Strings.open_feed),
                onOpen = { opening = true },
                pinned = isPinned,
                onPin = { if (isPinned) feeds.unpin(selected.uri) else feeds.pin(described ?: BlueskyFeedGenerator(selected.uri, title)) },
                extra = HeaderExtra(SavedIcons.ManageSearch, pluginString(Strings.find_feeds)) { app.host.open(BlueskyRoutes.FEEDS) },
            )
        }
        if (problem != null) item(key = "feeds-problem") { ProblemRow(problem, onRetry = { feeds.ensureLoaded(force = true) }) }
    }
    if (opening) {
        val invalid = pluginString(Strings.invalid_feed)
        TextPrompt(pluginString(Strings.open_feed), pluginString(Strings.feed_hint), onDismiss = { opening = false }) { typed -> feeds.openTyped(typed, invalid) }
    }
}

/**
 * Lists (bluesky_feeds_pane.dart's BlueskyListsPane): the lists pinned and
 * those of the reader's handle in a row, the one open named above its posts
 * with Look up lists, Pin and Import a list.
 */
@Composable
internal fun ListsPane(app: BlueskyApp, frame: PluginFrame) {
    val lists = app.lists
    val server by app.prefs.appView.collectAsState()
    val handle by app.prefs.handle.collectAsState()
    LaunchedEffect(server, handle) { lists.ensureLoaded() }
    val state by lists.state.collectAsState()
    val pinned by app.prefs.pinnedLists.collectAsState()
    val offered = lists.offered(pinned)
    val selected = state.selected
    val described = offered.firstOrNull { it.uri == selected?.uri }
    var looking by rememberSaveable { mutableStateOf(false) }
    val header: LazyListScope.() -> Unit = {
        item(key = "chips") {
            Column {
                ChipStrip(offered.map { SourceChip(it.uri, it.name) }, selected?.uri) { chip -> lists.open(chip.uri, chip.label) }
                if (state.lists.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        listsStatus(app, state)
        item(key = "selected") {
            val isPinned = selected != null && pinned.any { it.uri == selected.uri }
            SelectedHeader(
                title = described?.name?.ifEmpty { null } ?: selected?.name?.ifEmpty { null } ?: pluginString(Strings.tab_lists),
                creator = described?.creatorHandle,
                description = described?.description.orEmpty(),
                openLabel = pluginString(Strings.look_up_lists),
                onOpen = { looking = true },
                pinned = isPinned,
                onPin = selected?.let { open -> { if (isPinned) lists.unpin(open.uri) else lists.pin(described ?: BlueskyListInfo(open.uri, open.name)) } },
                extra = selected?.let { open ->
                    HeaderExtra(DiscoverIcons.CloudDownload, pluginString(Strings.import_list)) { app.host.open(BlueskyRoutes.import(ImportKind.LIST, open.uri)) }
                },
            )
        }
    }
    if (selected == null) {
        LazyColumn(contentPadding = frame.padding, modifier = Modifier.fillMaxSize()) {
            header()
            item(key = "none") { MessageText(pluginString(Strings.lists_empty)) }
        }
    } else {
        val pages = remember(server, selected.uri) { lists.pages(server, selected.uri) }
        RefreshesOn(frame.refresh) { pages.refresh() }
        SourceList(app, pages, frame, pluginString(Strings.feed_empty), header)
    }
    if (looking) {
        val invalid = pluginString(Strings.invalid_list)
        TextPrompt(pluginString(Strings.look_up_lists), pluginString(Strings.import_list_hint), onDismiss = { looking = false }) { typed ->
            lists.lookUpTyped(typed, invalid)
        }
    }
}

/** Why the handle's lists are not offered, that it has none, or that more are there. */
private fun LazyListScope.listsStatus(app: BlueskyApp, state: BlueskyLists.State) {
    val listing = state.lists
    val problem = listing.problem
    when {
        problem != null -> item(key = "lists-problem") { ProblemRow(problem, onRetry = { app.lists.lookUp(state.actor) }) }
        listing.loaded && listing.items.isEmpty() -> item(key = "no-lists") {
            Text(pluginString(Strings.no_lists_found), modifier = Modifier.padding(16.dp))
        }
        listing.more -> item(key = "more-lists") {
            TextButton(onClick = { app.lists.lookUp(state.actor, more = true) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(pluginString(Strings.load_more))
            }
        }
    }
}

/** A custom feed's or list's posts under [header], read page by page. */
@Composable
private fun SourceList(
    app: BlueskyApp,
    pages: PagedItems<BlueskyPost>,
    frame: PluginFrame,
    empty: String,
    header: LazyListScope.() -> Unit,
) {
    PagedList(
        pages,
        key = { it.uri },
        problemText = { pluginString(errorText(it)) },
        empty = empty,
        contentPadding = frame.padding,
        toTop = frame.toTop,
        header = header,
    ) { post -> PostRow(app, post) }
}

/** The feeds or lists offered, the one open checked. */
@Composable
private fun ChipStrip(chips: List<SourceChip>, selected: String?, onChoose: (SourceChip) -> Unit) {
    if (chips.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { chip ->
            FilterChip(
                selected = chip.uri == selected,
                onClick = { onChoose(chip) },
                label = { Text(chip.label, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 240.dp)) },
            )
        }
    }
}

/** The feed or list open: its name, maker and description, and what can be done from it. */
@Composable
private fun SelectedHeader(
    title: String,
    creator: String?,
    description: String,
    openLabel: String,
    onOpen: () -> Unit,
    pinned: Boolean,
    onPin: (() -> Unit)?,
    extra: HeaderExtra?,
) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (creator != null) NoteText("@$creator")
        if (description.isNotEmpty()) Text(description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            HeaderAction(XIcons.Link, openLabel, onOpen)
            if (onPin != null) HeaderAction(if (pinned) XIcons.Pin else GroupIcons.PushPinOutlined, pluginString(if (pinned) Strings.unpin else Strings.pin), onPin)
            extra?.let { HeaderAction(it.icon, it.label, it.onClick) }
        }
    }
}

@Composable
private fun HeaderAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}
