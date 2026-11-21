package com.aimdi.xtanative.plugins.reddit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.store.settings.RedditFeedMode
import com.aimdi.xtanative.ui.common.FeedSkeleton
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.swipeTabs
import com.aimdi.xtanative.ui.plugins.LocalPluginClientBar
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginHomeTab
import com.aimdi.xtanative.ui.plugins.PluginHomeTabs
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.ReadingControlsBar
import com.aimdi.xtanative.ui.plugins.RefreshesOn
import com.aimdi.xtanative.ui.plugins.ScrollsToTop
import com.aimdi.xtanative.ui.plugins.ShellFeeds
import com.aimdi.xtanative.ui.plugins.ShellFilter
import com.aimdi.xtanative.ui.plugins.ShellMenuButton
import com.aimdi.xtanative.ui.plugins.ShellSubtitle
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
    val place = RedditHomeSource.communitiesPlace(followed, inHome = LocalPluginClientBar.current == null)
    // Home draws Reddit's sections and actions in its own bars: there the order is a chip leading the communities' strip.
    val oneBar = LocalPluginClientBar.current != null
    Column(Modifier.fillMaxSize()) {
        ReadingControlsBar {
            Column {
                if (oneBar) {
                    ShellBar(app, home, followed)
                } else {
                    HomeBar(app, home, if (place == CommunitiesPlace.MENU) followed else emptyList())
                    if (place == CommunitiesPlace.STRIP) CommunityStrip(app, followed, home)
                }
            }
        }
        // A community picked from the strip is no section of its own: the swipes turn Following, Popular and All (Home keeps its swipes to itself).
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

/**
 * Reddit in the app's shell (the unified plugin shell): its feeds, Home
 * (the communities followed), Popular, All and each community followed, as
 * the shell's second row with the order as its filter; the account read
 * with under its name; its search alone on the bar, its menu in the plugin
 * sheet. No sections of its own, so no bottom bar.
 */
@Composable
private fun ShellBar(app: RedditApp, home: RedditHomeSource, followed: List<String>) {
    var sorting by remember { mutableStateOf(false) }
    val choices by app.choices.collectAsState()
    val identity by app.identity.collectAsState()
    val account by produceState<String?>(null, app, identity) { value = app.accountName() }
    val modes = RedditFeedMode.entries.map { mode ->
        PluginHomeTab(barIcon(mode), pluginString(feedLabel(mode)), !home.viewingSubreddit && home.mode == mode) { app.selectMode(mode) }
    }
    val communities = followed.map { name ->
        PluginHomeTab(RedditGlyphs.Forum, "r/$name", home.isSelected(name)) { app.selectSubreddit(name) }
    }
    val feeds = modes + communities
    PluginHomeTabs(emptyList()) {
        IconButton(onClick = { app.host.openSearch(null) }) { Icon(Icons.Filled.Search, contentDescription = pluginString(Strings.search_hint)) }
        HomeMenu(app, choices, account)
    }
    ShellFeeds(feeds, ShellFilter(active = RedditHomeChrome.sortChanged(choices)) { sorting = true })
    ShellSubtitle(account?.let { "u/$it" })
    if (sorting) SortSheet(app, subtitle = feeds.firstOrNull { it.selected }?.label) { sorting = false }
}

/** A feed's word on the shell's row: Home for the communities followed, as Reddit's own app names it. */
private fun feedLabel(mode: RedditFeedMode): PluginText = when (mode) {
    RedditFeedMode.FOLLOWING -> Strings.feed_home
    else -> railLabel(mode)
}

/**
 * Following, Popular and All, then Reddit's search, the order and the menu,
 * in XTA's order (reddit_screen.dart's RedditHomeChrome, reddit_actions.dart):
 * one bar on Reddit's own screen, a strip folding into a section picker in Home.
 */
@Composable
private fun HomeBar(app: RedditApp, home: RedditHomeSource, communities: List<String>) {
    var sorting by remember { mutableStateOf(false) }
    val choices by app.choices.collectAsState()
    val tabs = RedditFeedMode.entries.map { mode ->
        PluginHomeTab(railIcon(mode), pluginString(railLabel(mode)), home.railSelected(mode)) { app.selectMode(mode) }
    }
    PluginHomeTabs(tabs) {
        if (communities.isNotEmpty()) CommunitiesMenu(app, communities, home)
        RedditHomeChrome.actions.forEach { action ->
            when (action) {
                RedditHomeChrome.Action.SEARCH -> IconButton(onClick = { app.host.openSearch(null) }) { Icon(Icons.Filled.Search, contentDescription = pluginString(Strings.search_hint)) }
                RedditHomeChrome.Action.SORT -> IconButton(onClick = { sorting = true }) { Icon(sortIcon(choices.sort), contentDescription = pluginString(Strings.sort)) }
                RedditHomeChrome.Action.MENU -> HomeMenu(app, choices)
            }
        }
    }
    if (sorting) SortSheet(app) { sorting = false }
}

private fun railIcon(mode: RedditFeedMode): ImageVector = when (mode) {
    RedditFeedMode.FOLLOWING -> Icons.Outlined.Home
    RedditFeedMode.POPULAR -> RedditGlyphs.Popular
    RedditFeedMode.ALL -> RedditGlyphs.All
}

/** The sections' icons on Home's bottom bar: a list for Following (the house there leads back to XTA) and a rising line for Popular (the flame is the Hot order). */
private fun barIcon(mode: RedditFeedMode): ImageVector = when (mode) {
    RedditFeedMode.FOLLOWING -> Icons.AutoMirrored.Filled.List
    RedditFeedMode.POPULAR -> RedditGlyphs.Trending
    RedditFeedMode.ALL -> RedditGlyphs.All
}

private fun railLabel(mode: RedditFeedMode): PluginText = when (mode) {
    RedditFeedMode.FOLLOWING -> Strings.feed_following
    RedditFeedMode.POPULAR -> Strings.feed_popular
    RedditFeedMode.ALL -> Strings.feed_all
}

/**
 * How Reddit is read, read when the menu opens so it answers what is in
 * force: the posts saved here, the communities followed, the route (the best
 * credential there is, or the account-free one even with a sign-in), and
 * Reddit's settings.
 */
@Composable
private fun HomeMenu(app: RedditApp, choices: RedditChoices, account: String? = null) {
    var communities by remember { mutableStateOf(false) }
    ShellMenuButton { close ->
        // The signed-in reader's own profile, read like anyone's: posts and comments, nothing sent.
        if (account != null) MenuRow(Icons.Outlined.Person, pluginString(Strings.your_profile)) { close(); app.host.open(RedditRoutes.user(account)) }
        RedditHomeChrome.menu.forEach { item ->
            when (item) {
                RedditHomeChrome.MenuItem.SAVED -> MenuRow(RedditGlyphs.BookmarkBorder, pluginString(Strings.saved)) { close(); app.host.open(RedditRoutes.SAVED) }
                RedditHomeChrome.MenuItem.SUBSCRIPTIONS -> MenuRow(Icons.AutoMirrored.Filled.List, pluginString(Strings.subscriptions)) { close(); communities = true }
                RedditHomeChrome.MenuItem.SOURCE_AUTO -> {
                    HorizontalDivider()
                    SourceRow(app, item, Strings.source_auto, Strings.source_auto_description, RedditHomeChrome.checked(item, choices), close)
                }
                RedditHomeChrome.MenuItem.SOURCE_PUBLIC -> SourceRow(app, item, Strings.source_public, Strings.source_public_description, RedditHomeChrome.checked(item, choices), close)
                RedditHomeChrome.MenuItem.SETTINGS -> {
                    HorizontalDivider()
                    MenuRow(RedditGlyphs.Forum, pluginString(Strings.plugin_name) + " · " + pluginString(Strings.settings)) { close(); app.host.openSettings() }
                }
            }
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
    item: RedditHomeChrome.MenuItem,
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
                app.change { RedditHomeChrome.choosing(it, item) ?: it }
            }
        },
    )
}

/** The followed communities as a strip, so r/one and r/other stay on this tab; the one open again goes back to the rail. */
@Composable
private fun CommunityStrip(app: RedditApp, names: List<String>, home: RedditHomeSource) {
    val strip = pluginString(Strings.followed_communities)
    val accent = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth()) {
        // In the app's own manner, as a profile's: nothing boxed, the accent on what is tapped, white on what is shown.
        Row(
            Modifier.fillMaxWidth().height(48.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp).semantics { contentDescription = strip },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            names.forEach { name ->
                val selected = home.isSelected(name)
                Box(
                    Modifier
                        .fillMaxHeight()
                        .clickable(role = Role.Tab) { if (selected) app.selectMode(home.mode) else app.selectSubreddit(name) }
                        .semantics { this.selected = selected }
                        .underlined(selected, accent)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "r/$name",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
        Hairline()
        if (names.isNotEmpty()) {
            // How many are followed, as a profile counts: the number in white, what it counts in grey.
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp)) {
                Text(names.size.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Text(strip, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A three dp line in [color] along the bottom of the one [shown], its ends rounded, as Home's own tabs are underlined. */
private fun Modifier.underlined(shown: Boolean, color: Color): Modifier = if (!shown) this else drawBehind {
    val line = 3.dp.toPx()
    val inset = 6.dp.toPx()
    drawRoundRect(color, topLeft = Offset(inset, size.height - line), size = Size(size.width - inset * 2, line), cornerRadius = CornerRadius(line / 2, line / 2))
}

/**
 * The followed communities folded in Home, as XTA's dock moves the strip into
 * Home's options: a menu of them, its button in the accent while one is open
 * (XTA's attention), the one open again going back to the rail.
 */
@Composable
private fun CommunitiesMenu(app: RedditApp, names: List<String>, home: RedditHomeSource) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                RedditGlyphs.FilterAlt,
                contentDescription = pluginString(Strings.followed_communities),
                tint = if (home.viewingSubreddit) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            names.forEach { name ->
                val selected = home.isSelected(name)
                DropdownMenuItem(
                    text = { Text("r/$name") },
                    leadingIcon = { SubredditAvatar(app, name, 24.dp) },
                    trailingIcon = if (selected) { { Icon(Icons.Filled.Check, contentDescription = null) } } else null,
                    onClick = {
                        open = false
                        if (selected) app.selectMode(home.mode) else app.selectSubreddit(name)
                    },
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
                RedditFeedBody.NOTHING_FOLLOWED -> item(key = "nothing") { AddFirst(app) }
                RedditFeedBody.EMPTY -> item(key = "quiet") { RedditEmpty(RedditGlyphs.Forum, pluginString(Strings.nothing_new)) }
                RedditFeedBody.PROBLEM -> item(key = "problem") { RedditProblemView(problem!!, onRetry = { app.feed.refresh(names, force = true) }) }
                RedditFeedBody.BONES -> item(key = "loading") { FeedSkeleton() }
                RedditFeedBody.POSTS -> Unit
            }
            items(state.posts, key = { "post:" + it.id }, contentType = { "post" }) { post -> RedditPostCard(app, post) }
        }
    }
}

/**
 * The tab a reader following nothing sees, and only then (the unified
 * shell's empty state): where to start, adding a subreddit or reading
 * Popular meanwhile. A feed whose subreddits only have nothing new says so
 * quietly instead.
 */
@Composable
private fun AddFirst(app: RedditApp) {
    var adding by remember { mutableStateOf(false) }
    RedditEmpty(RedditGlyphs.Forum, pluginString(Strings.empty)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FilledTonalButton(onClick = { adding = true }) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(pluginString(Strings.add), modifier = Modifier.padding(start = 8.dp))
            }
            TextButton(onClick = { app.selectMode(RedditFeedMode.POPULAR) }, modifier = Modifier.heightIn(min = 44.dp)) {
                Text(pluginString(Strings.see_popular))
            }
        }
    }
    if (adding) AddSubredditDialog(app) { adding = false }
}
