package com.aimdi.xtanative.plugins.mastodon

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.mastodon.MastodonProfileReader
import com.aimdi.xtanative.networks.mastodon.MastodonProfileTab
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.plugins.MessageText
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.PluginTabs
import com.aimdi.xtanative.ui.plugins.RetryMessage
import com.aimdi.xtanative.ui.plugins.RetryRow
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside
import com.aimdi.xtanative.ui.plugins.rememberShareText
import com.aimdi.xtanative.ui.post.PostIcons

/**
 * One Fediverse profile (mastodon_profile_screen.dart): its card, Follow here,
 * and its Posts (pinned ones first), Posts & Replies and Media, each read
 * page by page from the instance that answered for it, as the end of the list
 * comes into view or Load more asks.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun MastodonProfileScreen(app: MastodonApp, acct: String) {
    val reader = rememberKept("mastodon-profile:$acct") { scope -> MastodonProfileReader(app.client, { app.candidates(acct) }, acct, scope).also { it.refresh() } }
    val state by reader.state.collectAsState()
    val follows by app.follows.state.collectAsState()
    LaunchedEffect(Unit) { app.follows.load() }
    val profile = state.profile
    val web = profile?.webUrl
    val share = rememberShareText()
    val openOutside = rememberOpenOutside()
    val list = rememberLazyListState()
    ReadsTabOnward(list, reader)
    PluginScaffold(
        title = "@" + (profile?.acct ?: acct),
        onBack = app.host::back,
        actions = {
            if (web != null) {
                IconButton(onClick = { share(web) }) { Icon(Icons.Filled.Share, pluginString(Strings.share_link)) }
                IconButton(onClick = { openOutside(web) }) { Icon(PostIcons.OpenInNew, pluginString(Strings.open_in_browser)) }
            }
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.loading && profile != null, onRefresh = reader::refresh, modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val columns = (maxWidth / MEDIA_TILE).toInt().coerceIn(2, 5)
                LazyColumn(state = list, contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 32.dp), modifier = Modifier.fillMaxSize()) {
                    if (profile == null) {
                        val error = state.profileError
                        item(key = "opening") { if (error != null) RetryMessage(pluginString(problemText(error)), reader::refresh) else LoadingRow() }
                        return@LazyColumn
                    }
                    item(key = "card") {
                        val following = follows.orEmpty().any { it.isOf(profile.acct) }
                        ProfileCard(app, profile, following) { app.toggleFollow(profile) }
                    }
                    stickyHeader(key = "tabs") { ProfileTabs(state.selected, reader::select) }
                    profileRows(app, state, reader, columns)
                }
            }
        }
    }
}

@Composable
private fun ProfileTabs(selected: MastodonProfileTab, onSelect: (MastodonProfileTab) -> Unit) {
    val labels = listOf(Strings.profile_posts, Strings.profile_replies, Strings.profile_media).map { pluginString(it) }
    PluginTabs(labels, selected.ordinal, { onSelect(MastodonProfileTab.entries[it]) })
}

/** The tab shown: its posts (or Media's grid), what is coming, why it stopped, or that there is nothing. */
private fun LazyListScope.profileRows(app: MastodonApp, state: MastodonProfileReader.State, reader: MastodonProfileReader, columns: Int) {
    val tab = state.selected
    val current = state.current
    if (tab == MastodonProfileTab.MEDIA) {
        items(current.posts.filter { it.hasMedia }.chunked(columns), key = { "media:" + it.first().url }) { row -> MediaGridRow(app, row, columns) }
    } else {
        items(current.posts, key = { "post:" + it.url }, contentType = { "post" }) { post ->
            MastodonPostRow(app, post, pinned = tab == MastodonProfileTab.POSTS && post.id in state.pinnedIds)
        }
    }
    val error = state.error
    item(key = "footer:" + tab.name) {
        when {
            state.loading || current.loading -> LoadingRow()
            error != null -> RetryRow(pluginString(problemText(error)), reader::retry)
            current.posts.isEmpty() && current.loaded -> MessageText(pluginString(Strings.no_posts))
            current.more && current.loaded -> LoadMoreButton(reader::loadMore)
        }
    }
}

/** The tab's next page as the end of the list comes into view. */
@Composable
private fun ReadsTabOnward(list: LazyListState, reader: MastodonProfileReader) {
    val state by reader.state.collectAsState()
    val nearEnd by remember(list) {
        derivedStateOf {
            val info = list.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 3
        }
    }
    val current = state.current
    LaunchedEffect(nearEnd, current.posts.size, state.selected) {
        if (nearEnd && current.loaded && current.more && !current.loading && state.error == null) reader.loadMore()
    }
}

private val MEDIA_TILE = 135.dp
