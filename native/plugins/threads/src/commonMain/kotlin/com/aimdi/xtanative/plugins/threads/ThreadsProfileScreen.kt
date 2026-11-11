package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
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
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.common.countText
import com.aimdi.xtanative.ui.common.swipeTabs
import com.aimdi.xtanative.ui.plugins.PluginAvatar
import com.aimdi.xtanative.ui.plugins.Fetch
import com.aimdi.xtanative.ui.plugins.MediaItem
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** The profile's tabs: its posts, its replies, its media, and the posts of its the reader liked here (XTA's Saved). */
private enum class ProfileTab(val label: PluginText) {
    POSTS(Strings.posts),
    REPLIES(Strings.profile_replies),
    MEDIA(Strings.media),
    SAVED(Strings.saved),
}

/** One profile as it has been read, what it showed last time until then, and its replies page once its tab asked for it. */
internal class ThreadsProfileModel(private val app: ThreadsApp, private val handle: String, scope: CoroutineScope) {
    val kept = MutableStateFlow<ThreadsKeptProfile?>(null)
    val page = Fetch<ThreadsProfilePage>(scope) { force -> app.ready(); app.reader.profile(handle, force).also { app.keepProfile(handle, it) } }
    val replies = Fetch<List<ThreadsPost>>(scope) { app.reader.replies(handle) }

    init {
        scope.launch { kept.value = app.keptProfile(handle) }
    }
}

/**
 * One Threads profile and its public posts (threads_profile_screen.dart):
 * its card, then Posts, Replies, Media and the posts of its the reader liked
 * here. The replies page is asked for only when its tab opens. A read that
 * fails keeps what was shown; only a profile that never had anything to
 * show says why. More than XTA's: a profile opened before shows at once
 * what it showed then, a line at the top while it is read again, and a read
 * again that fails says so over what is shown.
 */
@Composable
internal fun ThreadsProfileScreen(app: ThreadsApp, username: String) {
    val handle = remember(username) { (ThreadsLinks.normaliseHandle(username) ?: username).trim().lowercase() }
    val model = rememberKept("threads-profile-$handle") { scope -> ThreadsProfileModel(app, handle, scope) }
    LaunchedEffect(model) { model.page.ensure() }
    val state by model.page.state.collectAsState()
    val kept by model.kept.collectAsState()
    val url = "${ThreadsLinks.WEB}/@$handle"
    PluginScaffold(title = "@$handle", onBack = app.host::back, actions = { ProfileActions(app, url) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val page = state.value ?: kept?.page
            val showingKept = state.value == null && page != null
            val problem = state.problem?.let { ThreadsFailures.profile(it, currentLanguage()) }
            when {
                page == null && problem != null -> RetryMessage(problem, onRetry = { model.page.load() })
                page?.profile == null -> LoadingRow()
                else -> ProfileBody(app, model, page, page.profile, refreshing = state.loading && !showingKept)
            }
            if (page?.profile != null) ReloadStatus(state.loading && showingKept, problem, onRetry = { model.page.load(force = true) })
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
    val scope = rememberCoroutineScope()
    val select: (ProfileTab) -> Unit = { option ->
        // The tab already shown, tapped again, goes back to its top.
        if (option == tab) scope.launch { list.animateScrollToItem(0) }
        tab = option
        if (option == ProfileTab.REPLIES) model.replies.ensure()
    }
    val refresh = {
        model.page.load(force = true)
        if (tab == ProfileTab.REPLIES || tab == ProfileTab.MEDIA) model.replies.load(force = true)
    }
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().swipeTabs(ProfileTab.entries, tab, select)) {
            item(key = "card") { ThreadsProfileCard(app, profile, Modifier.padding(16.dp)) }
            item(key = "tabs") {
                ProfileTabs(tab, select)
            }
            when {
                waiting -> item(key = "waiting") { LoadingRow() }
                posts.isEmpty() -> item(key = "empty") { MessageText(pluginString(emptyLabel(tab))) }
                else -> items(posts, key = { "post:" + it.id }, contentType = { "post" }) { ThreadsPostCard(app, it) }
            }
        }
    }
}

/**
 * The tabs as XTA draws them (plugin_profile_tabs.dart): one segmented
 * button across the width, without the tick, scrolled sideways where the
 * four labels are wider than the phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileTabs(selected: ProfileTab, onSelect: (ProfileTab) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.horizontalScroll(rememberScrollState()).widthIn(min = maxWidth)) {
            ProfileTab.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = selected == option,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, ProfileTab.entries.size),
                    icon = {},
                ) { Text(pluginString(option.label), maxLines = 1, softWrap = false) }
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
 * Threads page, the counts Meta told, Add account while not followed here
 * (Unfollow once it is), Add to group, and a word when the profile is private.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThreadsProfileCard(app: ThreadsApp, profile: ThreadsProfile, modifier: Modifier = Modifier) {
    val rows by app.accounts.state.collectAsState()
    val outside = rememberOpenOutside()
    var unfollowing by remember { mutableStateOf(false) }
    if (unfollowing) UnfollowDialog(profile.username, onConfirm = { app.unfollow(profile.username) }) { unfollowing = false }
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val picture = profile.pictureUrl.ifEmpty { null }
            PluginAvatar(picture, profile.displayName, profile.username, 72.dp, onClick = picture?.let { { app.host.openMedia(listOf(MediaItem(imageUrl = it, aspectRatio = 1f, shareUrl = "${ThreadsLinks.WEB}/@${profile.username}")), 0) } })
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
                Text(ThreadsProfiles.linkLabel(link), color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp))
            }
        }
        val counts = ThreadsProfiles.counts(profile)
        if (counts.isNotEmpty()) {
            FlowRow(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                counts.forEach { (n, kind) -> CountText(n, countLabel(kind)) }
            }
        }
        val followed = ThreadsAccounts.follows(rows, profile.username)
        if (rows != null && !followed) {
            Button(onClick = { app.followProfile(profile) }, modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
                Icon(ThreadsIcons.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(pluginString(Strings.add_account), modifier = Modifier.padding(start = 8.dp))
            }
        }
        // More than XTA's card: an account followed here can be unfollowed from its own page, after the same question.
        if (followed) {
            OutlinedButton(onClick = { unfollowing = true }, modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
                Icon(ThreadsIcons.PersonRemove, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(pluginString(Strings.unfollow), modifier = Modifier.padding(start = 8.dp))
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
