package com.aimdi.xtanative.plugins.instagram

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.instagram.InstagramAuthor
import com.aimdi.xtanative.networks.instagram.InstagramDiscovery
import com.aimdi.xtanative.networks.instagram.InstagramPost
import com.aimdi.xtanative.ui.common.Avatar
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.swipeTabs
import com.aimdi.xtanative.ui.plugins.EmptyPane
import com.aimdi.xtanative.ui.plugins.NoticeHost
import com.aimdi.xtanative.ui.plugins.PagedItems
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.ReadsMoreNearEnd
import com.aimdi.xtanative.ui.plugins.RefreshesOn
import com.aimdi.xtanative.ui.plugins.RetryMessage
import com.aimdi.xtanative.ui.plugins.RetryRow
import com.aimdi.xtanative.ui.plugins.ScrollsToTop
import com.aimdi.xtanative.ui.plugins.SwipeToUnfollow
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.post.PostIcons

private enum class InstagramTab(val label: PluginText) {
    FOR_YOU(Strings.tab_for_you),
    FOLLOWING(Strings.tab_following),
    ACCOUNTS(Strings.tab_accounts),
}

/**
 * Guest Instagram (instagram_screen.dart): For you (Instagram's Explore with
 * a session, else public posts of well-known accounts), Following (the
 * accounts followed here, newest first) and the accounts themselves; search
 * and the settings above them.
 */
@Composable
internal fun InstagramHome(app: InstagramApp, frame: PluginFrame) {
    var tab by rememberSaveable { mutableStateOf(InstagramTab.FOR_YOU) }
    var searching by remember { mutableStateOf(false) }
    val forYou = rememberKept("instagram-for-you") { scope -> PagedItems<InstagramPost>(scope, { it.id }) { cursor -> app.client().forYou(cursor) } }
    LaunchedEffect(app) { app.follows.load() }
    RefreshesOn(frame.refresh) { if (tab == InstagramTab.FOLLOWING) app.following.refresh(force = true) else forYou.refresh() }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            HomeBar(app, tab, onTab = { tab = it }, onSearch = { searching = true })
            HorizontalDivider()
            Box(Modifier.weight(1f).swipeTabs(InstagramTab.entries, tab) { tab = it }) {
                when (tab) {
                    InstagramTab.FOR_YOU -> ForYou(app, forYou, frame) { searching = true }
                    InstagramTab.FOLLOWING -> Following(app, frame) { searching = true }
                    InstagramTab.ACCOUNTS -> Accounts(app, frame) { searching = true }
                }
            }
        }
        NoticeHost(app.notices)
    }
    if (searching) InstagramSearchSheet(app, initial = null) { searching = false }
}

@Composable
private fun HomeBar(app: InstagramApp, tab: InstagramTab, onTab: (InstagramTab) -> Unit, onSearch: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(InstagramTab.entries, key = { it.name }) { option ->
                FilterChip(
                    selected = tab == option,
                    onClick = { onTab(option) },
                    label = { Text(pluginString(option.label)) },
                    leadingIcon = { Icon(tabIcon(option), contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
        IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, contentDescription = pluginString(Strings.search)) }
        IconButton(onClick = app.host::openSettings) { Icon(Icons.Filled.Settings, contentDescription = pluginString(Strings.settings)) }
    }
}

private fun tabIcon(tab: InstagramTab): ImageVector = when (tab) {
    InstagramTab.FOR_YOU -> InstagramIcons.Explore
    InstagramTab.FOLLOWING -> InstagramIcons.PhotoLibrary
    InstagramTab.ACCOUNTS -> PostIcons.People
}

/** For you: the people it brings up who are not followed yet, its posts, and for a guest what a session would add. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForYou(app: InstagramApp, pages: PagedItems<InstagramPost>, frame: PluginFrame, onSearch: () -> Unit) {
    val paged by pages.state.collectAsState()
    val rows by app.follows.state.collectAsState()
    val settings by app.prefs.collectAsState()
    val guest = !app.hasSession(settings)
    val people = remember(paged.items, rows) { InstagramDiscovery.peopleToFollow(paged.items, { InstagramFollows.follows(rows, it) }) }
    val list = rememberLazyListState()
    ScrollsToTop(list, frame.toTop)
    ReadsMoreNearEnd(list, paged) { pages.more() }
    PullToRefreshBox(isRefreshing = paged.loading && paged.loaded, onRefresh = pages::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = list, contentPadding = frame.padding, modifier = Modifier.fillMaxSize()) {
            val problem = paged.problem
            when {
                paged.items.isEmpty() && problem != null -> item(key = "problem") { RetryMessage(pluginString(InstagramFailures.of(problem)), onRetry = pages::refresh) }
                paged.items.isEmpty() && !paged.loaded -> item(key = "loading") { LoadingRow() }
                paged.items.isEmpty() -> item(key = "empty") { EmptyForYou(pages::refresh, onSearch) }
            }
            if (people.isNotEmpty()) item(key = "people") { PeopleStrip(app, people) }
            items(paged.items, key = { "post:" + it.id }, contentType = { "post" }) { InstagramPostCard(app, it, showFollow = true) }
            item(key = "footer") {
                val more = paged.moreProblem
                when {
                    paged.loadingMore -> LoadingRow()
                    more != null -> RetryRow(pluginString(InstagramFailures.of(more)), pages::retryMore)
                    guest && paged.items.isNotEmpty() -> GuestNote(Strings.for_you_guest_note)
                }
            }
        }
    }
}

@Composable
private fun GuestNote(text: PluginText) {
    Text(
        pluginString(text),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
    )
}

/** "People in this feed": a card each, opening the profile, with Follow under it. */
@Composable
private fun PeopleStrip(app: InstagramApp, people: List<InstagramAuthor>) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 8.dp)) {
        Text(pluginString(Strings.from_feed), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        LazyRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(people, key = { it.username }) { person -> PersonCard(app, person) }
            item(key = "end") { Spacer(Modifier.width(4.dp)) }
        }
    }
}

@Composable
private fun PersonCard(app: InstagramApp, person: InstagramAuthor) {
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.width(240.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)) {
        Row(Modifier.fillMaxWidth().clickable { app.openProfile(person.username) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(person.avatarUrl, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(person.displayName, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("@" + person.username, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        HorizontalDivider()
        TextButton(onClick = { app.follow(person) }, modifier = Modifier.fillMaxWidth()) { Text(pluginString(Strings.follow)) }
    }
}

@Composable
private fun EmptyForYou(onRefresh: () -> Unit, onSearch: () -> Unit) {
    EmptyPane(InstagramIcons.Explore, pluginString(Strings.for_you_empty), detail = pluginString(Strings.for_you_guest_note)) {
        Button(onClick = onSearch) {
            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(pluginString(Strings.search), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** Following: every followed account's newest posts, merged newest first, read as each answers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Following(app: InstagramApp, frame: PluginFrame, onSearch: () -> Unit) {
    val state by app.following.state.collectAsState()
    val rows by app.follows.state.collectAsState()
    LaunchedEffect(app) { app.following.refresh() }
    val list = rememberLazyListState()
    ScrollsToTop(list, frame.toTop)
    PullToRefreshBox(isRefreshing = state.loading && state.items.isNotEmpty(), onRefresh = { app.following.refresh(force = true) }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = list, contentPadding = frame.padding, modifier = Modifier.fillMaxSize()) {
            val problem = state.problem
            when {
                state.items.isNotEmpty() -> items(state.items.distinctBy { it.id }, key = { "post:" + it.id }, contentType = { "post" }) { InstagramPostCard(app, it) }
                problem != null -> item(key = "problem") { RetryMessage(pluginString(InstagramFailures.of(problem)), onRetry = { app.following.refresh(force = true) }) }
                !state.loaded -> item(key = "loading") { LoadingRow() }
                else -> item(key = "empty") { EmptyFollowing(!rows.isNullOrEmpty(), onRetry = { app.following.refresh(force = true) }, onSearch = onSearch) }
            }
        }
    }
}

/** No followed account, or none has posted: search, or, with accounts, read them again. */
@Composable
private fun EmptyFollowing(hasAccounts: Boolean, onRetry: () -> Unit, onSearch: () -> Unit) {
    EmptyPane(InstagramIcons.PhotoLibrary, pluginString(if (hasAccounts) Strings.no_posts else Strings.no_accounts), detail = pluginString(Strings.empty_cta)) {
        Button(onClick = if (hasAccounts) onRetry else onSearch) {
            Icon(if (hasAccounts) Icons.Filled.Refresh else Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(pluginString(if (hasAccounts) Strings.retry else Strings.search), modifier = Modifier.padding(start = 8.dp))
        }
        if (hasAccounts) {
            TextButton(onClick = onSearch) {
                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(pluginString(Strings.search), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** The accounts followed here, newest first, each opening its profile, each to put in the app's groups or unfollow after asking. */
@Composable
private fun Accounts(app: InstagramApp, frame: PluginFrame, onSearch: () -> Unit) {
    val rows by app.follows.state.collectAsState()
    var unfollowing by remember { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()
    ScrollsToTop(list, frame.toTop)
    LazyColumn(state = list, contentPadding = frame.padding, modifier = Modifier.fillMaxSize()) {
        val follows = rows
        when {
            follows == null -> item(key = "loading") { LoadingRow() }
            follows.isEmpty() -> item(key = "empty") { EmptyFollowing(false, onRetry = {}, onSearch = onSearch) }
            else -> accountRows(app, follows) { unfollowing = it }
        }
    }
    unfollowing?.let { handle -> UnfollowDialog(handle, onConfirm = { app.unfollow(handle) }) { unfollowing = null } }
}

private fun LazyListScope.accountRows(app: InstagramApp, follows: List<com.aimdi.xtanative.store.backup.InstagramSubscriptionRow>, onUnfollow: (String) -> Unit) {
    items(follows, key = { it.id }) { follow ->
        var menu by remember { mutableStateOf(false) }
        SwipeToUnfollow(onUnfollow = { onUnfollow(follow.id) }) {
            ListItem(
                leadingContent = { Avatar(follow.avatarUrl, 48.dp) },
                headlineContent = { Text(follow.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text("@" + follow.id) },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = pluginString(Strings.more_options)) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text(pluginString(Strings.add_to_group)) }, onClick = { menu = false; app.addToGroup(follow.id) })
                                DropdownMenuItem(text = { Text(pluginString(Strings.unfollow)) }, onClick = { menu = false; onUnfollow(follow.id) })
                            }
                        }
                        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.clickable { app.openProfile(follow.id) },
            )
        }
    }
}

/** "Unfollow @handle?", as XTA asks. */
@Composable
internal fun UnfollowDialog(handle: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(pluginString(Strings.unfollow_confirm, handle)) },
        confirmButton = { FilledTonalButton(onClick = { onDismiss(); onConfirm() }) { Text(pluginString(Strings.unfollow)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(pluginString(Strings.cancel)) } },
    )
}
