package com.aimdi.xtanative.plugins.instagram

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.instagram.InstagramPost
import com.aimdi.xtanative.networks.instagram.InstagramProfile
import com.aimdi.xtanative.ui.common.Avatar
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.common.countText
import com.aimdi.xtanative.ui.plugins.Fetch
import com.aimdi.xtanative.ui.plugins.MessageText
import com.aimdi.xtanative.ui.plugins.NoticeHost
import com.aimdi.xtanative.ui.plugins.PagedItems
import com.aimdi.xtanative.ui.plugins.PagedState
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.ReadsMoreNearEnd
import com.aimdi.xtanative.ui.plugins.RetryMessage
import com.aimdi.xtanative.ui.plugins.RetryRow
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside
import com.aimdi.xtanative.ui.post.PostIcons
import kotlinx.coroutines.CoroutineScope

/** One profile, remembered among the searches once read, as XTA's profile screen does. */
internal class InstagramProfileModel(app: InstagramApp, handle: String, scope: CoroutineScope) {
    val profile = Fetch<InstagramProfile>(scope) { app.client().profile(handle).also { app.remember(it.username) } }
}

/**
 * One Instagram profile (instagram_profile_screen.dart): its face, name,
 * bio and counts, Follow on this device, then its public posts, paged on
 * with a session; a private account says that its posts need a login.
 */
@Composable
internal fun InstagramProfileScreen(app: InstagramApp, handle: String) {
    val model = rememberKept("instagram-profile-$handle") { scope -> InstagramProfileModel(app, handle, scope) }
    LaunchedEffect(model) { model.profile.ensure() }
    val state by model.profile.state.collectAsState()
    val outside = rememberOpenOutside()
    PluginScaffold(
        title = "@$handle",
        onBack = app.host::back,
        actions = { IconButton(onClick = { outside("https://www.instagram.com/$handle/") }) { Icon(PostIcons.OpenInNew, contentDescription = pluginString(Strings.open_on_site)) } },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val profile = state.value
            val problem = state.problem
            when {
                profile != null -> ProfileBody(app, model, profile, state.loading)
                problem != null -> RetryMessage(pluginString(InstagramFailures.of(problem)), onRetry = { model.profile.load() })
                else -> LoadingRow()
            }
            NoticeHost(app.notices)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileBody(app: InstagramApp, model: InstagramProfileModel, profile: InstagramProfile, refreshing: Boolean) {
    val list = rememberLazyListState()
    if (profile.isPrivate) {
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { model.profile.load() }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                item(key = "header") { Header(app, profile) }
                item(key = "private") { MessageText(pluginString(Strings.profile_private)) }
            }
        }
        return
    }
    val pages = rememberKept("instagram-posts-${profile.username}") { scope ->
        PagedItems<InstagramPost>(scope, { it.id }) { cursor -> app.client().profilePosts(profile.username, cursor, profile.id) }
    }
    val paged by pages.state.collectAsState()
    val settings by app.prefs.collectAsState()
    ReadsMoreNearEnd(list, paged) { pages.more() }
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { model.profile.load(); pages.refresh() }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
            item(key = "header") { Header(app, profile) }
            posts(app, pages, paged, guest = !app.hasSession(settings))
        }
    }
}

private fun LazyListScope.posts(app: InstagramApp, pages: PagedItems<InstagramPost>, paged: PagedState<InstagramPost>, guest: Boolean) {
    val problem = paged.problem
    when {
        paged.items.isEmpty() && problem != null -> item(key = "problem") { RetryRow(pluginString(InstagramFailures.of(problem)), pages::refresh) }
        paged.items.isEmpty() && !paged.loaded -> item(key = "loading") { LoadingRow() }
        paged.items.isEmpty() -> item(key = "empty") { MessageText(pluginString(Strings.empty_posts)) }
    }
    items(paged.items, key = { "post:" + it.id }, contentType = { "post" }) { InstagramPostCard(app, it, openAuthor = false) }
    item(key = "footer") {
        val more = paged.moreProblem
        when {
            paged.loadingMore -> LoadingRow()
            more != null -> RetryRow(pluginString(InstagramFailures.of(more)), pages::retryMore)
            guest && paged.items.isNotEmpty() -> Text(
                pluginString(Strings.more_needs_session),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
            )
        }
    }
}

/** The face, the name, the bio, the three counts, Add to group, and Follow or Unfollow on this device. */
@Composable
private fun Header(app: InstagramApp, profile: InstagramProfile) {
    val rows by app.follows.state.collectAsState()
    val following = InstagramFollows.follows(rows, profile.username)
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(profile.avatarUrl, 72.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(profile.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (profile.isVerified) Icon(XIcons.Verified, contentDescription = null, tint = Color.Unspecified, modifier = Modifier.padding(start = 6.dp).size(20.dp))
                }
                Text("@" + profile.username)
            }
        }
        profile.biography?.trim()?.takeIf { it.isNotEmpty() }?.let { Text(it, modifier = Modifier.padding(top = 12.dp)) }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Stat(profile.followerCount, Strings.stat_followers)
            Stat(profile.followingCount, Strings.stat_following)
            Stat(profile.mediaCount, Strings.stat_posts)
        }
        OutlinedButton(onClick = { app.addToGroup(profile.username) }, modifier = Modifier.padding(top = 16.dp)) {
            Icon(PostIcons.People, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(pluginString(Strings.add_to_group), modifier = Modifier.padding(start = 8.dp))
        }
        FilledTonalButton(
            onClick = { if (following) app.unfollow(profile.username) else app.follow(profile) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(pluginString(if (following) Strings.unfollow else Strings.follow)) }
    }
}

@Composable
private fun RowScope.Stat(value: Long, label: PluginText) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(countText(value), fontWeight = FontWeight.ExtraBold)
        Text(pluginString(label))
    }
}
