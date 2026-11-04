package com.aimdi.xtanative.plugins.tiktok

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.store.backup.TikTokSubscriptionRow
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.common.swipeTabs
import com.aimdi.xtanative.ui.plugins.EmptyPane
import com.aimdi.xtanative.ui.plugins.NoticeHost
import com.aimdi.xtanative.ui.plugins.PluginChipTabs
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.RefreshesOn
import com.aimdi.xtanative.ui.plugins.RetryMessage
import com.aimdi.xtanative.ui.plugins.ScrollsToTop
import com.aimdi.xtanative.ui.plugins.SwipeToUnfollow
import com.aimdi.xtanative.ui.plugins.WithSettings
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.search.DiscoverIcons
import com.aimdi.xtanative.ui.subscriptions.GroupIcons

/** The home's tabs, in XTA's order (tiktok_screen.dart). */
internal enum class TikTokTab { FOLLOWING, ACCOUNTS }

/**
 * The home tab (tiktok_screen.dart): Following, the videos of the accounts
 * followed on this device, and Accounts, those accounts; the search and the
 * settings beside them unless the screen around it has its own.
 */
@Composable
internal fun TikTokHome(app: TikTokApp, frame: PluginFrame, ownActions: Boolean = true) = WithSettings(app.host.settings) { settings ->
    var tab by rememberSaveable { mutableStateOf(TikTokTab.FOLLOWING) }
    val follows by app.follows.state.collectAsState()
    val lists = TikTokTab.entries.associateWith { rememberLazyListState() }
    val reduced = LocalReducedMotion.current
    LaunchedEffect(Unit) { app.ready() }
    // Who is followed decides what Following shows: a follow or an unfollow anywhere reads it again.
    LaunchedEffect(follows?.map { it.secUid }) { if (follows != null) app.feed.refresh() }
    RefreshesOn(frame.refresh) { app.feed.refresh(force = true) }
    ScrollsToTop(frame.toTop) { lists.getValue(tab).let { if (reduced) it.scrollToItem(0) else it.animateScrollToItem(0) } }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TabRow(tab, onTab = { tab = it }, actions = if (ownActions) HomeActions({ app.openSearch() }, app.host::openSettings) else null)
            Hairline()
            Box(Modifier.weight(1f).swipeTabs(TikTokTab.entries, tab) { tab = it }) {
                when (tab) {
                    TikTokTab.FOLLOWING -> FollowingTab(app, follows, settings, frame.padding, lists.getValue(tab))
                    TikTokTab.ACCOUNTS -> AccountsTab(app, follows, frame.padding, lists.getValue(tab))
                }
            }
        }
        NoticeHost(app.notices, Modifier.padding(frame.padding))
    }
}

private class HomeActions(val search: () -> Unit, val settings: () -> Unit)

@Composable
private fun TabRow(tab: TikTokTab, onTab: (TikTokTab) -> Unit, actions: HomeActions?) {
    PluginChipTabs(TikTokTab.entries, tab, onTab, icon = { tabLook(it).first }, label = { pluginString(tabLook(it).second) }) {
        if (actions != null) {
            IconButton(onClick = actions.search) { Icon(Icons.Filled.Search, pluginString(Strings.search)) }
            IconButton(onClick = actions.settings) { Icon(Icons.Filled.Settings, pluginString(Strings.settings)) }
        }
    }
}

private fun tabLook(tab: TikTokTab): Pair<ImageVector, PluginText> = when (tab) {
    TikTokTab.FOLLOWING -> TikTokIcons.Mark to Strings.tab_following
    TikTokTab.ACCOUNTS -> GroupIcons.PeopleOutlined to Strings.tab_accounts
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FollowingTab(app: TikTokApp, follows: List<TikTokSubscriptionRow>?, settings: Settings, padding: PaddingValues, list: LazyListState) {
    val state by app.feed.state.collectAsState()
    val error = state.problem
    when {
        state.items.isEmpty() && (state.loading || !state.loaded || follows == null) -> LoadingRow()
        state.items.isEmpty() && error != null -> RetryMessage(pluginString(TikTokText.error(error)), { app.feed.refresh(force = true) }, Modifier.padding(padding))
        state.items.isEmpty() -> NothingFollowed(app, hasAccounts = !follows.isNullOrEmpty(), padding)
        else -> {
            val likes = remember(settings) { TikTokMarks.likes(settings).toSet() }
            PullToRefreshBox(isRefreshing = state.loading, onRefresh = { app.feed.refresh(force = true) }, modifier = Modifier.fillMaxSize()) {
                LazyColumn(state = list, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
                    items(state.items, key = { it.id }) { post -> TikTokPostCard(app, post, post.id in likes) }
                }
            }
        }
    }
}

/** Nothing on Following: no account followed yet, or none of them posted; a search, or a read again. */
@Composable
private fun NothingFollowed(app: TikTokApp, hasAccounts: Boolean, padding: PaddingValues) {
    EmptyPane(
        TikTokIcons.Mark,
        pluginString(if (hasAccounts) Strings.no_posts else Strings.no_accounts),
        Modifier.padding(padding),
        detail = pluginString(Strings.empty_cta),
    ) {
        Button(onClick = { if (hasAccounts) app.feed.refresh(force = true) else app.openSearch() }) {
            Icon(if (hasAccounts) Icons.Filled.Refresh else Icons.Filled.Search, contentDescription = null)
            Text(pluginString(if (hasAccounts) Strings.retry else Strings.search), Modifier.padding(start = 8.dp))
        }
        if (hasAccounts) {
            TextButton(onClick = { app.openSearch() }) {
                Icon(Icons.Filled.Search, contentDescription = null)
                Text(pluginString(Strings.search), Modifier.padding(start = 8.dp))
            }
        }
    }
}

/**
 * The accounts followed here, the newest first: one opens its profile, its
 * menu puts it in the app's groups; swiped away or from its menu, it is
 * unfollowed once confirmed.
 */
@Composable
private fun AccountsTab(app: TikTokApp, follows: List<TikTokSubscriptionRow>?, padding: PaddingValues, list: LazyListState) {
    var unfollowing by remember { mutableStateOf<String?>(null) }
    when {
        follows == null -> LoadingRow()
        follows.isEmpty() -> NothingFollowed(app, hasAccounts = false, padding)
        else -> LazyColumn(state = list, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item(key = "title") {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
                    Text(pluginString(Strings.tab_accounts), style = MaterialTheme.typography.titleLarge)
                    OutlinedButton(onClick = { app.openSearch() }, modifier = Modifier.padding(top = 8.dp)) {
                        Icon(DiscoverIcons.PersonSearch, contentDescription = null)
                        Text(pluginString(Strings.search), Modifier.padding(start = 8.dp))
                    }
                }
            }
            items(follows, key = { it.id }) { follow -> AccountRow(app, follow) { unfollowing = follow.id } }
        }
    }
    unfollowing?.let { handle ->
        UnfollowDialog(handle, onDismiss = { unfollowing = null }) {
            unfollowing = null
            app.launch { app.unfollow(handle) }
        }
    }
}

@Composable
private fun AccountRow(app: TikTokApp, follow: TikTokSubscriptionRow, onUnfollow: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    SwipeToUnfollow(onUnfollow) {
        ListItem(
            leadingContent = { TikTokAvatar(follow.avatarUrl, follow.name, follow.id, 48.dp) },
            headlineContent = { Text(follow.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text("@${follow.id}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingContent = {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = null) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(pluginString(Strings.add_to_group)) }, onClick = { menu = false; app.addToGroup(follow) })
                        DropdownMenuItem(text = { Text(pluginString(Strings.unfollow)) }, onClick = { menu = false; onUnfollow() })
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            modifier = Modifier.clickable { app.openProfile(follow.id) },
        )
    }
}

@Composable
internal fun UnfollowDialog(handle: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(pluginString(Strings.unfollow_confirm, handle)) },
        confirmButton = { Button(onClick = onConfirm) { Text(pluginString(Strings.unfollow)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(pluginString(Strings.cancel)) } },
    )
}
