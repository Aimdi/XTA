package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.threads.ThreadsLinks
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsProfile
import com.aimdi.xtanative.networks.threads.ThreadsProfilePage
import com.aimdi.xtanative.networks.threads.ThreadsProfileTab
import com.aimdi.xtanative.ui.common.Avatar
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.common.countText
import com.aimdi.xtanative.ui.plugins.Fetch
import com.aimdi.xtanative.ui.plugins.MessageText
import com.aimdi.xtanative.ui.plugins.NoticeHost
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.RetryMessage
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside
import com.aimdi.xtanative.ui.plugins.rememberShareText
import com.aimdi.xtanative.ui.post.PostIcons
import kotlinx.coroutines.CoroutineScope

/** The profile's tabs: its posts, its replies, its media, and the posts of its the reader liked here (XTA's Saved). */
private enum class ProfileTab(val label: PluginText) {
    POSTS(Strings.posts),
    REPLIES(Strings.profile_replies),
    MEDIA(Strings.media),
    SAVED(Strings.saved),
}

/** One profile as it has been read, and its replies page once its tab asked for it. */
internal class ThreadsProfileModel(private val app: ThreadsApp, private val handle: String, scope: CoroutineScope) {
    val page = Fetch<ThreadsProfilePage>(scope) { force -> app.ready(); app.reader.profile(handle, force) }
    val replies = Fetch<List<ThreadsPost>>(scope) { app.reader.replies(handle) }
}

/**
 * One Threads profile and its public posts (threads_profile_screen.dart):
 * its card, then Posts, Replies, Media and the posts of its the reader liked
 * here. The replies page is asked for only when its tab opens. A read that
 * fails keeps what was shown; only a profile that never had anything to
 * show says why.
 */
@Composable
internal fun ThreadsProfileScreen(app: ThreadsApp, username: String) {
    val handle = remember(username) { (ThreadsLinks.normaliseHandle(username) ?: username).trim().lowercase() }
    val model = rememberKept("threads-profile-$handle") { scope -> ThreadsProfileModel(app, handle, scope) }
    LaunchedEffect(model) { model.page.ensure() }
    val state by model.page.state.collectAsState()
    val url = "${ThreadsLinks.WEB}/@$handle"
    PluginScaffold(title = "@$handle", onBack = app.host::back, actions = { ProfileActions(app, url) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val page = state.value
            val problem = state.problem
            when {
                page == null && problem != null -> RetryMessage(ThreadsFailures.page(problem, currentLanguage()), onRetry = { model.page.load() })
                page?.profile == null -> LoadingRow()
                else -> ProfileBody(app, model, page, page.profile, state.loading)
            }
            NoticeHost(app.notices)
        }
    }
}

@Composable
private fun ProfileActions(app: ThreadsApp, url: String) {
    val share = rememberShareText()
    val outside = rememberOpenOutside()
    val copied = Strings.link_copied.get(currentLanguage())
    IconButton(onClick = { if (!share(url)) app.notices.tell(copied) }) { Icon(Icons.Filled.Share, contentDescription = pluginString(Strings.share_link)) }
    IconButton(onClick = { outside(url) }) { Icon(PostIcons.OpenInNew, contentDescription = pluginString(Strings.open_in_browser)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileBody(app: ThreadsApp, model: ThreadsProfileModel, page: ThreadsProfilePage, profile: ThreadsProfile, refreshing: Boolean) {
    var tab by rememberSaveable { mutableStateOf(ProfileTab.POSTS) }
    val replies by model.replies.state.collectAsState()
    val settings by app.prefs.collectAsState()
    val likedIds by app.likes.state.collectAsState()
    val posts = remember(page, replies, tab, settings, likedIds) {
        when (tab) {
            ProfileTab.POSTS -> page.tab(ThreadsProfileTab.POSTS)
            ProfileTab.REPLIES -> page.copy(replies = replies.value).tab(ThreadsProfileTab.REPLIES)
            ProfileTab.MEDIA -> page.copy(replies = replies.value).tab(ThreadsProfileTab.MEDIA)
            ProfileTab.SAVED -> ThreadsLikes.liked(settings, likedIds).filter { it.handle == profile.username.lowercase() }
        }.distinctBy { it.id }
    }
    val waiting = tab == ProfileTab.REPLIES && replies.loading && posts.isEmpty()
    val list = rememberLazyListState()
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { model.page.load(force = true) }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
            item(key = "card") { ThreadsProfileCard(app, profile, Modifier.padding(16.dp)) }
            item(key = "tabs") {
                PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                    ProfileTab.entries.forEach { option ->
                        Tab(selected = tab == option, onClick = { tab = option; if (option == ProfileTab.REPLIES) model.replies.ensure() }, text = { Text(pluginString(option.label), maxLines = 1) })
                    }
                }
            }
            when {
                waiting -> item(key = "waiting") { LoadingRow() }
                posts.isEmpty() -> item(key = "empty") { MessageText(pluginString(emptyLabel(tab))) }
                else -> items(posts, key = { "post:" + it.id }, contentType = { "post" }) { ThreadsPostCard(app, it) }
            }
        }
    }
}

private fun emptyLabel(tab: ProfileTab): PluginText = when (tab) {
    ProfileTab.REPLIES -> Strings.replies_empty
    ProfileTab.SAVED -> Strings.liked_empty
    else -> Strings.no_posts
}

/**
 * The profile itself: face, name, what they say about themselves (its
 * mentions, links and tags tappable), their link unless it is their own
 * Threads page, the counts Meta told, Add account while not followed here,
 * Add to group, and a word when the profile is private.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThreadsProfileCard(app: ThreadsApp, profile: ThreadsProfile, modifier: Modifier = Modifier) {
    val rows by app.accounts.state.collectAsState()
    val outside = rememberOpenOutside()
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(profile.profilePicUrl.ifEmpty { null }, 64.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(profile.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (profile.isVerified) Icon(XIcons.Verified, contentDescription = null, tint = Color.Unspecified, modifier = Modifier.padding(start = 6.dp).size(20.dp))
                    if (profile.isPrivate) Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp).size(18.dp))
                }
                Text("@" + profile.username, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        profile.biography.trim().takeIf { it.isNotEmpty() }?.let { bio ->
            ThreadsRichText(app, bio, emptyList(), Modifier.padding(top = 14.dp), style = MaterialTheme.typography.bodyMedium)
        }
        ThreadsProfiles.outsideLink(profile)?.let { link ->
            Row(Modifier.padding(top = 8.dp).clickable { outside(link) }, verticalAlignment = Alignment.CenterVertically) {
                Icon(XIcons.Link, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                Text(link, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp))
            }
        }
        val counts = ThreadsProfiles.counts(profile)
        if (counts.isNotEmpty()) {
            FlowRow(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                counts.forEach { (n, kind) -> CountText(n, countLabel(kind)) }
            }
        }
        if (rows != null && !ThreadsAccounts.follows(rows, profile.username)) {
            Button(onClick = { app.followProfile(profile) }, modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
                Icon(ThreadsIcons.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(pluginString(Strings.add_account), modifier = Modifier.padding(start = 8.dp))
            }
        }
        OutlinedButton(onClick = { app.addToGroup(ThreadsAccount.of(profile)) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Icon(PostIcons.People, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(pluginString(Strings.add_to_group), modifier = Modifier.padding(start = 8.dp))
        }
        if (profile.isPrivate) {
            Text(pluginString(Strings.profile_private), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 14.dp))
        }
    }
}

private fun countLabel(kind: ThreadsCount): PluginText = when (kind) {
    ThreadsCount.FOLLOWERS -> Strings.followers
    ThreadsCount.FOLLOWING -> Strings.following
    ThreadsCount.POSTS -> Strings.posts
}

@Composable
private fun CountText(n: Long, label: PluginText) {
    val strong = MaterialTheme.colorScheme.onSurface
    val number = countText(n)
    val word = pluginString(label)
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = strong)) { append(number) }
            append(" ")
            append(word)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
