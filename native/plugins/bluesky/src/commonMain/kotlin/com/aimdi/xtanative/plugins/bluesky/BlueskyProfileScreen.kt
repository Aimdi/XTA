package com.aimdi.xtanative.plugins.bluesky

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.networks.Page
import com.aimdi.xtanative.networks.bluesky.BlueskyLocal
import com.aimdi.xtanative.networks.bluesky.BlueskyMediaGrid
import com.aimdi.xtanative.networks.bluesky.BlueskyMediaTile
import com.aimdi.xtanative.networks.bluesky.BlueskyPost
import com.aimdi.xtanative.networks.bluesky.BlueskyProfile
import com.aimdi.xtanative.networks.bluesky.BlueskyProfileTab
import com.aimdi.xtanative.networks.bluesky.BlueskySearchKind
import com.aimdi.xtanative.ui.common.Avatar
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.countText
import com.aimdi.xtanative.ui.common.monthText
import com.aimdi.xtanative.ui.plugins.LinkedText
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.MessageText
import com.aimdi.xtanative.ui.plugins.NetworkProblemView
import com.aimdi.xtanative.ui.plugins.PagedItems
import com.aimdi.xtanative.ui.plugins.PagedState
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.ReadsMoreNearEnd
import com.aimdi.xtanative.ui.plugins.pagedRows
import com.aimdi.xtanative.ui.plugins.pluginPlural
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.problemOf
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.plugins.rememberShareText
import com.aimdi.xtanative.ui.post.PostIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A profile's tabs (bluesky_profile_screen.dart): its posts, its replies, its media in a grid, and the posts of it liked here. */
internal enum class ProfileTab(val read: BlueskyProfileTab?, val label: PluginText) {
    POSTS(BlueskyProfileTab.POSTS, Strings.profile_posts),
    REPLIES(BlueskyProfileTab.REPLIES, Strings.profile_replies),
    MEDIA(BlueskyProfileTab.MEDIA, Strings.profile_media),
    LIKES(null, Strings.profile_likes),
}

/**
 * A Bluesky profile, as XTA shows one: its banner and picture (each opening
 * in the viewer), Follow here, its name, handle, description, when it joined
 * and its counts (following and followers open their lists), then its tabs.
 * Its bar searches its posts and shares its link.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun BlueskyProfileScreen(app: BlueskyApp, actor: String) {
    val profile = rememberKept("bluesky-profile:$actor") { scope -> ProfileState(app, actor, scope) }
    val state by profile.state.collectAsState()
    val follows by app.follows.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(ProfileTab.POSTS) }
    val shown = state.profile
    val pages = remember(shown, tab) { tab.read?.let(profile::pages) }
    val list = rememberLazyListState()
    val paged = pages?.state?.collectAsState()?.value ?: PagedState()
    if (pages != null) ReadsMoreNearEnd(list, paged) { pages.more() }
    val followed = shown != null && follows.orEmpty().any { it.isOf(shown.did) || it.isOf(shown.handle) }
    val share = rememberShareText()
    val noPosts = pluginString(Strings.no_posts)

    PluginScaffold(
        title = shown?.displayName ?: actor,
        onBack = app.host::back,
        actions = {
            if (shown != null) {
                IconButton(onClick = { app.openSearch(author = shown.handle, kind = BlueskySearchKind.POSTS) }) { Icon(Icons.Filled.Search, pluginString(Strings.search)) }
                IconButton(onClick = { share("https://bsky.app/profile/" + shown.handle) }) { Icon(Icons.Filled.Share, pluginString(Strings.share_link)) }
            }
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            val columns = BlueskyMediaGrid.columns(maxWidth.value)
            val refresh = {
                profile.load()
                pages?.refresh()
            }
            PullToRefreshBox(isRefreshing = state.loading && shown != null, onRefresh = { refresh() }, modifier = Modifier.fillMaxSize()) {
                LazyColumn(state = list, contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 48.dp), modifier = Modifier.fillMaxSize()) {
                    item(key = "header") { Header(app, shown, actor, followed) }
                    val problem = state.problem
                    when {
                        problem != null -> item(key = "problem") { NetworkProblemView(problem, app.network, onRetry = profile::load) }
                        state.loading && shown == null -> item(key = "loading") { LoadingRow() }
                    }
                    if (shown != null) {
                        stickyHeader(key = "tabs") { Tabs(tab) { tab = it } }
                        when {
                            tab == ProfileTab.LIKES -> likedRows(app, shown)
                            tab == ProfileTab.MEDIA && pages != null -> mediaRows(app, paged, pages, columns, noPosts)
                            pages != null -> pagedRows(paged, pages, key = { it.uri }, network = app.network, empty = noPosts) { post ->
                                PostRow(app, post, pinned = tab == ProfileTab.POSTS && post.uri == shown.pinnedPostUri)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(app: BlueskyApp, profile: BlueskyProfile?, actor: String, followed: Boolean) {
    val background = MaterialTheme.colorScheme.background
    Column(Modifier.fillMaxWidth()) {
        Box {
            AsyncImage(
                model = profile?.bannerUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(3f).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                    .clickable(enabled = profile?.bannerUrl != null) { profile?.bannerUrl?.let { app.host.openMedia(listOf(MediaItem(it)), 0) } },
            )
            Avatar(
                profile?.avatarUrl,
                80.dp,
                Modifier.align(Alignment.BottomStart).offset(x = 16.dp, y = 40.dp).border(4.dp, background, CircleShape),
                onClick = profile?.avatarUrl?.let { url -> { app.host.openMedia(listOf(MediaItem(url)), 0) } },
            )
        }
        // Beside the picture's lower half, as X places it.
        Box(Modifier.fillMaxWidth().height(52.dp).padding(start = 120.dp, end = 16.dp), contentAlignment = Alignment.CenterEnd) {
            if (profile != null) FollowButton(followed) { app.toggleFollow(profile) }
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column {
                Text(profile?.displayName ?: actor, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("@" + (profile?.handle ?: actor), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (profile != null) ProfileDetails(app, profile)
        }
        Spacer(Modifier.height(12.dp))
        Hairline()
    }
}

/** Follow in the theme's strongest colour, Unfollow outlined, as XTA's profile draws them. */
@Composable
private fun FollowButton(followed: Boolean, onClick: () -> Unit) {
    if (followed) {
        OutlinedButton(onClick = onClick) { Text(pluginString(Strings.unfollow)) }
    } else {
        val colors = MaterialTheme.colorScheme
        Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = colors.onSurface, contentColor = colors.surface)) {
            Text(pluginString(Strings.follow))
        }
    }
}

/** The description, when it joined, and its counts. */
@Composable
private fun ProfileDetails(app: BlueskyApp, profile: BlueskyProfile) {
    if (profile.description.isNotBlank()) {
        val description = remember(profile) { profile.descriptionRuns() }
        LinkedText(description, app.actions.openMention, app.actions.openTag)
    }
    profile.createdAt?.let { joined ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(pluginString(Strings.joined, monthText(joined)), style = MaterialTheme.typography.bodySmall)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Count(profile.followsCount, pluginString(Strings.count_following)) { app.host.open(BlueskyRoutes.people(profile.actor, PeopleKind.FOLLOWING)) }
        Count(profile.followersCount, pluginString(Strings.count_followers)) { app.host.open(BlueskyRoutes.people(profile.actor, PeopleKind.FOLLOWERS)) }
        val posts = profile.postsCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        Text(pluginPlural(Strings.post_count, posts, countText(profile.postsCount)), style = MaterialTheme.typography.bodySmall)
    }
}

/** A count to tap: its number in bold, what it counts after it. */
@Composable
private fun Count(n: Long, label: String, onClick: () -> Unit) {
    val strong = MaterialTheme.colorScheme.onSurface
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = strong)) { append(countText(n)) }
            append(" ")
            append(label)
        },
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.heightIn(min = 48.dp).clickable(onClick = onClick).padding(vertical = 14.dp),
    )
}

@Composable
private fun Tabs(selected: ProfileTab, onSelect: (ProfileTab) -> Unit) {
    TabRow(selectedTabIndex = selected.ordinal, containerColor = MaterialTheme.colorScheme.background, divider = { Hairline() }) {
        ProfileTab.entries.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                text = { Text(pluginString(tab.label), maxLines = 1) },
                selectedContentColor = MaterialTheme.colorScheme.onBackground,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The posts of [profile] liked on this device, under the reminder that likes stay here. */
private fun LazyListScope.likedRows(app: BlueskyApp, profile: BlueskyProfile) {
    item(key = "likes-note") { NoteText(pluginString(Strings.likes_stay_notice), Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)) }
    item(key = "likes") {
        val likes by app.likes.state.collectAsState()
        val posts by app.prefs.likedPosts.collectAsState()
        val liked = remember(likes, posts, profile) { BlueskyLocal.likedBy(BlueskyLocal.library(posts, likes.orEmpty()), profile.did, profile.handle) }
        Column {
            if (liked.isEmpty()) MessageText(pluginString(Strings.liked_empty))
            liked.forEach { post -> PostRow(app, post) }
        }
    }
}

/** The Media tab: a grid of the posts with media, [columns] across, read page by page as the list is. */
private fun LazyListScope.mediaRows(app: BlueskyApp, paged: PagedState<BlueskyPost>, pages: PagedItems<BlueskyPost>, columns: Int, empty: String) {
    val rows = BlueskyMediaGrid.rows(paged.items, columns)
    val starts = rows.associateBy { it.first().post.uri }
    pagedRows(paged, pages, key = { it.uri }, network = app.network, empty = empty) { post ->
        starts[post.uri]?.let { row -> MediaGridRow(app, row, columns) }
    }
}

/** One row of the grid; each tile opens its picture in the viewer, or its post for a video or what is covered. */
@Composable
private fun MediaGridRow(app: BlueskyApp, row: List<BlueskyMediaTile>, columns: Int) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        row.forEach { tile -> MediaTile(app, tile, Modifier.weight(1f)) }
        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun MediaTile(app: BlueskyApp, tile: BlueskyMediaTile, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val post = tile.post
    Box(
        modifier.aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(colors.surfaceContainerHighest).clickable {
            if (tile.opensPost) app.openPost(post.uri) else app.host.openMedia(post.media.map { it.toMediaItem(post.url) }, 0)
        },
    ) {
        if (tile.covered) {
            Column(Modifier.align(Alignment.Center).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(PostIcons.VisibilityOff, contentDescription = null)
                Text(
                    pluginString(if (post.isUnavailable) Strings.content_unavailable else Strings.content_warning),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        } else {
            AsyncImage(model = tile.imageUrl, contentDescription = tile.alt, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (tile.isVideo) TileBadge(Modifier.align(Alignment.Center)) { Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White) }
            if (tile.alt != null) TileBadge(Modifier.align(Alignment.BottomStart)) { Text(pluginString(Strings.alt_badge), color = Color.White, fontSize = 12.sp) }
        }
        if (tile.many) TileBadge(Modifier.align(Alignment.TopEnd)) { Icon(PostIcons.Layers, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) }
    }
}

/** A mark over a tile, white on a dark ground: a video, a picture's description, more than one picture. */
@Composable
private fun TileBadge(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.padding(6.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.54f)).padding(horizontal = 5.dp, vertical = 3.dp)) {
        content()
    }
}

/**
 * One profile, read once the screen opens, and a paged list per tab read
 * from the AppView, made the first time the tab is shown; the first page of
 * Posts starts with the post the author pinned.
 */
internal class ProfileState(private val app: BlueskyApp, private val actor: String, private val scope: CoroutineScope) {
    data class State(val profile: BlueskyProfile? = null, val loading: Boolean = true, val problem: Problem? = null)

    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable.asStateFlow()

    private val tabs = HashMap<BlueskyProfileTab, PagedItems<BlueskyPost>>()

    init {
        load()
    }

    fun load() {
        scope.launch {
            mutable.update { it.copy(loading = true, problem = null) }
            try {
                mutable.value = State(app.client.profile(actor), loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(loading = false, problem = problemOf(e)) }
            }
        }
    }

    /** [tab]'s posts, once the profile is known. */
    fun pages(tab: BlueskyProfileTab): PagedItems<BlueskyPost>? {
        val profile = state.value.profile ?: return null
        return tabs.getOrPut(tab) { PagedItems(scope, key = { it.uri }) { cursor -> read(profile, tab, cursor) } }
    }

    private suspend fun read(profile: BlueskyProfile, tab: BlueskyProfileTab, cursor: String?): Page<BlueskyPost> {
        val page = app.client.profileTab(profile, tab, cursor)
        if (cursor != null || tab != BlueskyProfileTab.POSTS) return page
        return Page(tab.withPin(page.items, pinned(profile)), page.next)
    }

    private suspend fun pinned(profile: BlueskyProfile): BlueskyPost? = try {
        app.client.pinnedPost(profile)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
