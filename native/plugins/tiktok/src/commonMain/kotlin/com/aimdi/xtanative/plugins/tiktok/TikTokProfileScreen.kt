package com.aimdi.xtanative.plugins.tiktok

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.Page
import com.aimdi.xtanative.networks.tiktok.TikTokPost
import com.aimdi.xtanative.networks.tiktok.TikTokProfile
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.countText
import com.aimdi.xtanative.ui.plugins.Fetch
import com.aimdi.xtanative.ui.plugins.MessageText
import com.aimdi.xtanative.ui.plugins.NoticeHost
import com.aimdi.xtanative.ui.plugins.PagedItems
import com.aimdi.xtanative.ui.plugins.PagedState
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.ReadsMoreNearEnd
import com.aimdi.xtanative.ui.plugins.RetryMessage
import com.aimdi.xtanative.ui.plugins.RetryRow
import com.aimdi.xtanative.ui.plugins.WithSettings
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside
import com.aimdi.xtanative.ui.post.PostIcons
import com.aimdi.xtanative.ui.search.DiscoverIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** A profile as read: the account, and its videos page by page (none for a private account). */
internal class TikTokProfileRead(val profile: TikTokProfile, val posts: PagedItems<TikTokPost>?)

/**
 * A profile's screen as one screen keeps it (XTA's TikTokProfileStore and
 * its feed): read again, an account already shown stays when the read
 * fails, and its videos are read again from the first page. Opening a
 * profile puts it among the recent searches.
 */
internal class TikTokProfilePage(private val app: TikTokApp, private val scope: CoroutineScope, private val handle: String) {
    private var posts: PagedItems<TikTokPost>? = null

    val read = Fetch(scope) { _ ->
        val profile = app.client().profile(handle)
        app.rememberSearch(profile.uniqueId)
        TikTokProfileRead(profile, postsOf(profile))
    }

    init {
        read.load()
    }

    private fun postsOf(profile: TikTokProfile): PagedItems<TikTokPost>? = when {
        profile.privateAccount -> null
        else -> posts?.also { it.refresh() } ?: PagedItems(scope, key = { it.id }) { cursor -> videos(profile, cursor) }.also { posts = it }
    }

    private suspend fun videos(profile: TikTokProfile, cursor: String?): Page<TikTokPost> = app.client().creatorItems(profile.secUid, cursor)
}

/**
 * A profile (tiktok_profile_screen.dart): its picture, name, marks and bio,
 * its followers, videos and likes, Add to group and Follow on this device,
 * then its videos; a private account says why there are none. The bar opens
 * it on TikTok.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TikTokProfileScreen(app: TikTokApp, route: TikTokRoute.Profile) = WithSettings(app.host.settings) { settings ->
    val page = rememberKept("tiktok-profile:${route.handle}") { scope -> TikTokProfilePage(app, scope, route.handle) }
    val state by page.read.state.collectAsState()
    val read = state.value
    val profile = read?.profile
    val openOutside = rememberOpenOutside()
    val error = state.problem
    PluginScaffold(
        title = profile?.displayName ?: "@${route.handle}",
        onBack = app.host::back,
        actions = {
            if (profile != null) IconButton(onClick = { openOutside(profile.url) }) { Icon(PostIcons.OpenInNew, pluginString(Strings.open_on_site)) }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                profile == null && error != null && !state.loading -> RetryMessage(pluginString(TikTokText.error(error)), { page.read.load() })
                profile == null -> LoadingRow()
                else -> PullToRefreshBox(isRefreshing = state.loading, onRefresh = { page.read.load() }, modifier = Modifier.fillMaxSize()) {
                    ProfileList(app, profile, read.posts, settings)
                }
            }
            NoticeHost(app.notices)
        }
    }
}

@Composable
private fun ProfileList(app: TikTokApp, profile: TikTokProfile, posts: PagedItems<TikTokPost>?, settings: Settings) {
    val list = rememberLazyListState()
    val paged = posts?.state?.collectAsState()?.value
    val likes = remember(settings) { TikTokMarks.likes(settings).toSet() }
    if (posts != null && paged != null) ReadsMoreNearEnd(list, paged) { posts.more() }
    LazyColumn(state = list, contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
        item(key = "header") { ProfileHeader(app, profile) }
        if (posts != null && paged != null) videoRows(app, paged, posts, likes)
    }
}

private fun LazyListScope.videoRows(app: TikTokApp, paged: PagedState<TikTokPost>, posts: PagedItems<TikTokPost>, likes: Set<String>) {
    val problem = paged.problem
    when {
        paged.items.isEmpty() && problem != null -> item(key = "problem") { RetryMessage(pluginString(TikTokText.problem(problem)), posts::refresh) }
        paged.items.isEmpty() && paged.loaded && !paged.loading -> item(key = "empty") { MessageText(pluginString(Strings.empty_posts)) }
        paged.items.isEmpty() -> item(key = "loading") { LoadingRow() }
    }
    items(paged.items, key = { it.id }) { post -> TikTokPostCard(app, post, post.id in likes, openAuthor = false) }
    item(key = "more") {
        val more = paged.moreProblem
        when {
            paged.loadingMore -> LoadingRow()
            more != null -> RetryRow(pluginString(TikTokText.problem(more)), posts::retryMore)
        }
    }
}

@Composable
private fun ProfileHeader(app: TikTokApp, profile: TikTokProfile) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TikTokAvatar(profile.avatarUrl, profile.displayName, profile.uniqueId, 72.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(profile.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (profile.verified) VerifiedMark(20.dp)
                    if (profile.privateAccount) Icon(Icons.Filled.Lock, contentDescription = null, tint = muted, modifier = Modifier.padding(start = 6.dp).size(18.dp))
                }
                Text("@${profile.uniqueId}", style = MaterialTheme.typography.bodyMedium, color = muted)
            }
        }
        profile.signature?.trim()?.takeIf(String::isNotEmpty)?.let { Text(it, Modifier.padding(top = 14.dp)) }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Stat(pluginString(Strings.stat_followers), profile.followerCount, Modifier.weight(1f))
            Stat(pluginString(Strings.stat_videos), profile.videoCount, Modifier.weight(1f))
            Stat(pluginString(Strings.stat_likes), profile.heartCount, Modifier.weight(1f))
        }
        if (profile.privateAccount) {
            Text(pluginString(Strings.profile_private), style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(top = 16.dp))
        } else {
            OutlinedButton(onClick = { app.addToGroup(profile) }, modifier = Modifier.padding(top = 16.dp)) {
                Icon(PostIcons.GroupAdd, contentDescription = null)
                Text(pluginString(Strings.add_to_group), Modifier.padding(start = 8.dp))
            }
            FollowButton(app, profile)
        }
    }
}

@Composable
private fun Stat(label: String, value: Long, modifier: Modifier) {
    Column(modifier) {
        Text(countText(value), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Follow on this device, or stop: TikTok is never told. */
@Composable
private fun FollowButton(app: TikTokApp, profile: TikTokProfile) {
    val follows by app.follows.state.collectAsState()
    val following = follows.orEmpty().any { it.id == profile.uniqueId.lowercase() }
    FilledTonalButton(
        onClick = { app.launch { if (following) app.unfollow(profile.uniqueId) else app.follows.follow(profile) } },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Icon(if (following) DiscoverIcons.PersonRemove else DiscoverIcons.PersonAdd, contentDescription = null)
        Text(pluginString(if (following) Strings.unfollow else Strings.follow), Modifier.padding(start = 8.dp))
    }
}
