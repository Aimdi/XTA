package com.aimdi.xtanative.plugins.reddit

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.store.settings.RedditFeedMode
import com.aimdi.xtanative.store.settings.RedditPrefs
import com.aimdi.xtanative.store.settings.RedditSource
import com.aimdi.xtanative.ui.common.FeedSkeleton
import com.aimdi.xtanative.ui.common.swipeTabs
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.RefreshesOn
import com.aimdi.xtanative.ui.plugins.ScrollsToTop
import com.aimdi.xtanative.ui.plugins.pluginString

/**
 * The Reddit tab (XTA's `RedditScreen`): the followed communities newest
 * first, Reddit's Popular and All, or one followed community picked from
 * the strip of them; above them the order every listing reads in and the
 * menu of how Reddit is read.
 */
@Composable
internal fun RedditHome(app: RedditApp, frame: PluginFrame) {
    val home by app.home.collectAsState()
    val followed by app.followed.collectAsState()
    Column(Modifier.fillMaxSize()) {
        HomeBar(app, home)
        if (followed.isNotEmpty()) CommunityStrip(app, followed, home)
        // A community picked from the strip is no section of its own: the swipes turn Following, Popular and All.
        val modes = if (home.subreddit == null) RedditFeedMode.entries else emptyList()
        Box(Modifier.weight(1f).swipeTabs(modes, home.mode) { app.selectMode(it) }) {
            val subreddit = home.subreddit
            when {
                subreddit != null -> ListingBody(app, Listing.Subreddit(subreddit), frame)
                home.mode == RedditFeedMode.FOLLOWING -> FollowingFeed(app, frame)
                home.mode == RedditFeedMode.POPULAR -> ListingBody(app, Listing.Subreddit("popular"), frame)
                else -> ListingBody(app, Listing.Subreddit("all"), frame)
            }
        }
    }
}

/** Following, Popular and All, then Reddit's search, the order and the menu, in XTA's order (reddit_actions.dart). */
@Composable
private fun HomeBar(app: RedditApp, home: RedditHomeSource) {
    var sorting by remember { mutableStateOf(false) }
    val choices by app.choices.collectAsState()
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Rail(app, home, RedditFeedMode.FOLLOWING, Icons.Outlined.Home, Strings.feed_following)
            Rail(app, home, RedditFeedMode.POPULAR, RedditGlyphs.Popular, Strings.feed_popular)
            Rail(app, home, RedditFeedMode.ALL, RedditGlyphs.All, Strings.feed_all)
        }
        IconButton(onClick = { app.host.openSearch(null) }) { Icon(Icons.Filled.Search, contentDescription = pluginString(Strings.search_hint)) }
        IconButton(onClick = { sorting = true }) { Icon(sortIcon(choices.sort), contentDescription = pluginString(Strings.sort)) }
        HomeMenu(app, choices)
    }
    if (sorting) SortSheet(app) { sorting = false }
}

@Composable
private fun Rail(app: RedditApp, home: RedditHomeSource, mode: RedditFeedMode, icon: ImageVector, label: PluginText) {
    FilterChip(
        selected = home.railSelected(mode),
        onClick = { app.selectMode(mode) },
        label = { Text(pluginString(label)) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
    )
}

/**
 * How Reddit is read, read when the menu opens so it answers what is in
 * force: the posts saved here, the communities followed, the route (the best
 * credential there is, or the account-free one even with a sign-in), and
 * Reddit's settings.
 */
@Composable
private fun HomeMenu(app: RedditApp, choices: RedditChoices) {
    var open by remember { mutableStateOf(false) }
    var communities by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, contentDescription = pluginString(Strings.more_options)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val close = { open = false }
            MenuRow(RedditGlyphs.BookmarkBorder, pluginString(Strings.saved)) { close(); app.host.open(RedditRoutes.SAVED) }
            MenuRow(Icons.AutoMirrored.Filled.List, pluginString(Strings.subscriptions)) { close(); communities = true }
            HorizontalDivider()
            SourceRow(app, RedditSource.AUTO, Strings.source_auto, Strings.source_auto_description, !choices.preferPublic, close)
            SourceRow(app, RedditSource.PUBLIC, Strings.source_public, Strings.source_public_description, choices.preferPublic, close)
            HorizontalDivider()
            MenuRow(Icons.Outlined.Settings, pluginString(Strings.plugin_name) + " · " + pluginString(Strings.settings)) { close(); app.host.openSettings() }
        }
    }
    if (communities) CommunitiesSheet(app) { communities = false }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(icon, contentDescription = null) }, onClick = onClick)
}

@Composable
private fun SourceRow(
    app: RedditApp,
    source: RedditSource,
    label: PluginText,
    description: PluginText,
    selected: Boolean,
    close: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Column {
                Text(pluginString(label))
                Text(pluginString(description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        trailingIcon = if (selected) { { Icon(Icons.Filled.Check, contentDescription = null) } } else null,
        onClick = {
            close()
            if (!selected) {
                app.change { it.with(RedditPrefs.source, source) }
            }
        },
    )
}

/** The followed communities as a strip, so r/one and r/other stay on this tab; the one open again goes back to the rail. */
@Composable
private fun CommunityStrip(app: RedditApp, names: List<String>, home: RedditHomeSource) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            pluginString(Strings.followed_communities),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            names.forEach { name ->
                val selected = home.isSelected(name)
                FilterChip(
                    selected = selected,
                    onClick = { if (selected) app.selectMode(home.mode) else app.selectSubreddit(name) },
                    label = { Text("r/$name") },
                )
            }
        }
    }
}

/**
 * Every followed community, newest first (XTA's `RedditFeedList`): read once
 * the list of them is, shown again without reading while it is fresh, read
 * past every cache on a pull.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FollowingFeed(app: RedditApp, frame: PluginFrame) {
    val rows by app.subscriptions.state.collectAsState()
    val state by app.feed.state.collectAsState()
    val names = remember(rows) { RedditSubscriptions.names(rows) }
    LaunchedEffect(rows) { if (rows != null) app.feed.refresh(names) else app.subscriptions.load() }
    RefreshesOn(frame.refresh) { app.feed.refresh(names, force = true) }
    val list = rememberLazyListState()
    ScrollsToTop(list, frame.toTop)
    PullToRefreshBox(isRefreshing = state.loading && state.posts.isNotEmpty(), onRefresh = { app.feed.refresh(names, force = true) }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = list, contentPadding = frame.padding, modifier = Modifier.fillMaxSize()) {
            val problem = state.problem
            when (RedditFeedBody.of(rows != null, names.isEmpty(), state)) {
                RedditFeedBody.NOTHING_FOLLOWED, RedditFeedBody.EMPTY -> item(key = "nothing") { AddFirst(app) }
                RedditFeedBody.PROBLEM -> item(key = "problem") { RedditProblemView(problem!!, onRetry = { app.feed.refresh(names, force = true) }) }
                RedditFeedBody.BONES -> item(key = "loading") { FeedSkeleton() }
                RedditFeedBody.POSTS -> Unit
            }
            items(state.posts, key = { "post:" + it.id }, contentType = { "post" }) { post -> RedditPostCard(app, post) }
        }
    }
}

/** The tab a reader following nothing, or nothing that posted, sees: where to start (reddit_feed_list.dart's empty state). */
@Composable
private fun AddFirst(app: RedditApp) {
    var adding by remember { mutableStateOf(false) }
    RedditEmpty(RedditGlyphs.Forum, pluginString(Strings.empty)) {
        FilledTonalButton(onClick = { adding = true }) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(pluginString(Strings.add), modifier = Modifier.padding(start = 8.dp))
        }
    }
    if (adding) AddSubredditDialog(app) { adding = false }
}
