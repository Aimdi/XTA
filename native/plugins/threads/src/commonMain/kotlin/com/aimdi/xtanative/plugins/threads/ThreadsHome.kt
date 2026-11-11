package com.aimdi.xtanative.plugins.threads

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.threads.ThreadsDiscovery
import com.aimdi.xtanative.networks.threads.ThreadsException
import com.aimdi.xtanative.networks.threads.ThreadsFailure
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.swipeTabs
import com.aimdi.xtanative.ui.plugins.EmptyPane
import com.aimdi.xtanative.ui.plugins.NoticeHost
import com.aimdi.xtanative.ui.plugins.PluginFeedSkeleton
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginHomeTabs
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.RefreshesOn
import com.aimdi.xtanative.ui.plugins.RetryMessage
import com.aimdi.xtanative.ui.plugins.ScrollsToTop
import com.aimdi.xtanative.ui.plugins.pluginPlural
import com.aimdi.xtanative.ui.plugins.ReadingControlsBar
import com.aimdi.xtanative.ui.plugins.ReadingControlsPinItem
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.post.PostIcons
import com.aimdi.xtanative.ui.saved.SavedIcons
import kotlinx.coroutines.launch

/**
 * The Threads tab (XTA's ThreadsScreen, as it sits in Home's strip): Home,
 * every followed account merged newest first; For you, Threads' own
 * recommended feed; and Liked, the posts liked on this device; search,
 * adding an account and the settings above them. It opens on the section
 * the reader left it on. The
 * feed the reader last saw is painted first; a pull, F5 or the desktop's
 * refresh asks Meta again past the ten minutes each account is kept.
 */
@Composable
internal fun ThreadsHome(app: ThreadsApp, frame: PluginFrame) {
    val section by app.section.collectAsState()
    var searching by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    LaunchedEffect(app) { app.restoreForYou() }
    RefreshesOn(frame.refresh) { app.feed.refresh(force = true) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ReadingControlsBar { HomeBar(app, section, onSearch = { searching = true }, onAdd = { adding = true }) }
            Hairline()
            Box(Modifier.weight(1f).swipeTabs(section.ordinal, ThreadsSection.entries.size) { app.show(ThreadsSection.entries[it]) }) {
                when (section) {
                    ThreadsSection.HOME -> HomePane(app, frame, onAdd = { adding = true }, onSearch = { searching = true })
                    ThreadsSection.FOR_YOU -> ThreadsForYouPane(app, frame)
                    ThreadsSection.LIKED -> LikedPane(app, frame)
                }
            }
        }
        NoticeHost(app.notices)
    }
    if (searching) ThreadsSearchSheet(app, initial = null) { searching = false }
    if (adding) AddAccountDialog(app) { adding = false }
}

/** XTA's chrome for the tab (PluginHomeChrome): Home, For you and Liked, then search and the menu: add an account, what was saved from Threads, the settings. */
@Composable
private fun HomeBar(app: ThreadsApp, section: ThreadsSection, onSearch: () -> Unit, onAdd: () -> Unit) {
    PluginHomeTabs(ThreadsSection.entries, section, app::show, icon = ::iconOf, label = { pluginString(labelOf(it)) }) {
        IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, contentDescription = pluginString(Strings.search)) }
        HomeMenu(app, onAdd)
    }
}

private fun iconOf(section: ThreadsSection): ImageVector = when (section) {
    ThreadsSection.HOME -> Icons.Outlined.Home
    ThreadsSection.FOR_YOU -> PostIcons.Sparkle
    ThreadsSection.LIKED -> ThreadsHeart.Outlined
}

private fun labelOf(section: ThreadsSection): PluginText = when (section) {
    ThreadsSection.HOME -> Strings.home
    ThreadsSection.FOR_YOU -> Strings.for_you
    ThreadsSection.LIKED -> Strings.liked
}

@Composable
private fun HomeMenu(app: ThreadsApp, onAdd: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = pluginString(Strings.more_options)) }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            MenuRow(ThreadsIcons.PersonAdd, Strings.add_account) { menu = false; onAdd() }
            MenuRow(SavedIcons.BookmarkBorder, Strings.saved) { menu = false; app.host.open(ThreadsRoutes.SAVED) }
            MenuRow(Icons.Filled.Settings, Strings.settings) { menu = false; app.host.openSettings() }
            ReadingControlsPinItem()
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: PluginText, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(pluginString(label)) }, leadingIcon = { Icon(icon, contentDescription = null) }, onClick = onClick)
}

/** The parked session's notice, the followed accounts and the filters, then the feed. */
@Composable
private fun HomePane(app: ThreadsApp, frame: PluginFrame, onAdd: () -> Unit, onSearch: () -> Unit) {
    val state by app.feed.state.collectAsState()
    val options by app.options.collectAsState()
    // Read when Home is the section shown: a reader on For you does not pay for the followed accounts' pages.
    LaunchedEffect(app) {
        app.restoreFeed()
        app.feed.refresh()
    }
    Column(Modifier.fillMaxSize()) {
        if (app.direct.isSessionParked) ParkedNotice()
        DockRow(app, options, onAdd)
        Box(Modifier.weight(1f)) {
            val problem = state.problem
            when {
                state.items.isNotEmpty() -> FeedOrFiltered(app, frame, state.items, options)
                problem != null && !notConfigured(problem) -> RetryMessage(ThreadsFailures.feedText(problem, currentLanguage()), onRetry = { app.feed.refresh(force = true) })
                !state.loaded -> PluginFeedSkeleton()
                else -> Refreshable(app, frame) { item(key = "empty") { NothingYet(app, onAdd, onSearch) } }
            }
        }
    }
}

private fun notConfigured(problem: Throwable) = (problem as? ThreadsException)?.failure == ThreadsFailure.NOT_CONFIGURED

@Composable
private fun ParkedNotice() {
    ListItem(
        leadingContent = { Icon(ThreadsIcons.PauseCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer) },
        headlineContent = { Text(pluginString(Strings.session_parked), color = MaterialTheme.colorScheme.onErrorContainer) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.errorContainer),
    )
}

/** Every loaded post hidden by the reader's filters: said so, rather than looking as if Meta sent nothing. */
@Composable
private fun FeedOrFiltered(app: ThreadsApp, frame: PluginFrame, posts: List<ThreadsPost>, options: ThreadsFeedOptions) {
    val shown = remember(posts, options) { options.shown(posts).distinctBy { it.id } }
    if (shown.isEmpty()) {
        Refreshable(app, frame) {
            item(key = "filtered") {
                EmptyPane(PostIcons.Tune, pluginString(Strings.empty_filter)) {
                    FilledTonalButton(onClick = { app.setOptions(ThreadsFeedOptions()) }) {
                        Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(pluginString(Strings.reset_filters), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    } else {
        Feed(app, frame, shown)
    }
}

/**
 * The feed: the people it brings up who are not followed yet, after its
 * first few posts, and how many accounts are still to be read under it.
 */
@Composable
private fun Feed(app: ThreadsApp, frame: PluginFrame, posts: List<ThreadsPost>) {
    val rows by app.accounts.state.collectAsState()
    val people = remember(posts, rows) { ThreadsDiscovery.peopleToFollow(posts, { ThreadsAccounts.follows(rows, it) }) }
    val pending = remember(posts, rows) { app.reader.pending(ThreadsAccounts.handles(rows)) }
    val at = minOf(PEOPLE_AFTER, posts.size)
    Refreshable(app, frame) {
        items(posts.take(at), key = { "post:" + it.id }, contentType = { "post" }) { ThreadsPostCard(app, it) }
        if (people.isNotEmpty()) item(key = "people") { PeopleStrip(app, people) }
        items(posts.drop(at), key = { "post:" + it.id }, contentType = { "post" }) { ThreadsPostCard(app, it) }
        if (pending > 0) item(key = "pending") { PendingNote(pending) }
    }
}

/** Where the people strip sits in a feed shown in Home's strip: after this many posts. */
private const val PEOPLE_AFTER = 3

/** The tab's list, pulled to ask Meta again past each account's ten minutes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Refreshable(app: ThreadsApp, frame: PluginFrame, content: LazyListScope.() -> Unit) {
    val state by app.feed.state.collectAsState()
    val list = rememberLazyListState()
    ScrollsToTop(list, frame.toTop)
    PullToRefreshBox(isRefreshing = state.loading && state.loaded, onRefresh = { app.feed.refresh(force = true) }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = list, contentPadding = frame.padding, modifier = Modifier.fillMaxSize(), content = content)
    }
}

@Composable
private fun PendingNote(pending: Int) {
    NoteText(pluginPlural(Strings.accounts_pending, pending, pending), Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp))
}

/** Following no one, or no one has posted: where to start. */
@Composable
private fun NothingYet(app: ThreadsApp, onAdd: () -> Unit, onSearch: () -> Unit) {
    val rows by app.accounts.state.collectAsState()
    val state by app.feed.state.collectAsState()
    val pending = remember(rows, state.items) { app.reader.pending(ThreadsAccounts.handles(rows)) }
    if (pending > 0) PendingNote(pending)
    EmptyPane(ThreadsIcons.Mark, pluginString(if (rows.isNullOrEmpty()) Strings.no_accounts else Strings.no_posts), detail = pluginString(Strings.empty_cta)) {
        Button(onClick = onAdd) {
            Icon(ThreadsIcons.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(pluginString(Strings.add_account), modifier = Modifier.padding(start = 8.dp))
        }
        TextButton(onClick = onSearch) {
            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(pluginString(Strings.discover), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** The posts liked on this device, newest like first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LikedPane(app: ThreadsApp, frame: PluginFrame) {
    val settings by app.prefs.collectAsState()
    val ids by app.likes.state.collectAsState()
    val posts = remember(settings, ids) { ThreadsLikes.liked(settings, ids).distinctBy { it.id } }
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    ScrollsToTop(list, frame.toTop)
    PullToRefreshBox(isRefreshing = false, onRefresh = { scope.launch { app.likes.load() } }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = list, contentPadding = frame.padding, modifier = Modifier.fillMaxSize()) {
            if (posts.isEmpty()) item(key = "empty") { EmptyPane(ThreadsHeart.Outlined, pluginString(Strings.liked_empty)) }
            items(posts, key = { "liked:" + it.id }, contentType = { "post" }) { ThreadsPostCard(app, it) }
        }
    }
}
