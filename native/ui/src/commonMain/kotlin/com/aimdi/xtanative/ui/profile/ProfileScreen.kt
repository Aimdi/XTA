package com.aimdi.xtanative.ui.profile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.core.x.parse.XText
import com.aimdi.xtanative.store.saved.ArchiveFilter
import com.aimdi.xtanative.ui.common.Avatar
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.ProblemRow
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.common.clickableNoRipple
import com.aimdi.xtanative.ui.common.countText
import com.aimdi.xtanative.ui.common.monthText
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.back
import com.aimdi.xtanative.ui.generated.resources.posts_account_suspended
import com.aimdi.xtanative.ui.generated.resources.posts_filter_all
import com.aimdi.xtanative.ui.generated.resources.posts_filter_bookmarks
import com.aimdi.xtanative.ui.generated.resources.posts_filter_broadcasts
import com.aimdi.xtanative.ui.generated.resources.posts_filter_likes
import com.aimdi.xtanative.ui.generated.resources.posts_filter_photos
import com.aimdi.xtanative.ui.generated.resources.posts_filter_posts
import com.aimdi.xtanative.ui.generated.resources.posts_filter_reposts
import com.aimdi.xtanative.ui.generated.resources.posts_filter_videos
import com.aimdi.xtanative.ui.generated.resources.posts_filters
import com.aimdi.xtanative.ui.generated.resources.posts_followers
import com.aimdi.xtanative.ui.generated.resources.posts_following
import com.aimdi.xtanative.ui.generated.resources.posts_joined
import com.aimdi.xtanative.ui.generated.resources.posts_no_likes
import com.aimdi.xtanative.ui.generated.resources.posts_no_posts
import com.aimdi.xtanative.ui.generated.resources.posts_no_saves
import com.aimdi.xtanative.ui.generated.resources.posts_page_not_found
import com.aimdi.xtanative.ui.generated.resources.posts_possibly_sensitive_profile
import com.aimdi.xtanative.ui.generated.resources.posts_private_profile
import com.aimdi.xtanative.ui.generated.resources.posts_protected_posts
import com.aimdi.xtanative.ui.generated.resources.posts_scroll_to_top
import com.aimdi.xtanative.ui.generated.resources.posts_tab_media
import com.aimdi.xtanative.ui.generated.resources.posts_tab_posts
import com.aimdi.xtanative.ui.generated.resources.posts_tab_replies
import com.aimdi.xtanative.ui.generated.resources.posts_tab_saved
import com.aimdi.xtanative.ui.generated.resources.posts_user_not_found
import com.aimdi.xtanative.ui.generated.resources.reader_link_share
import com.aimdi.xtanative.ui.generated.resources.search
import com.aimdi.xtanative.ui.media.MediaGridLook
import com.aimdi.xtanative.ui.media.MediaGridRows
import com.aimdi.xtanative.ui.media.mediaGrid
import com.aimdi.xtanative.ui.media.rememberMediaGridLook
import com.aimdi.xtanative.ui.media.rememberMediaGridRows
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.post.PostIcons
import com.aimdi.xtanative.ui.post.PostScopeProvider
import com.aimdi.xtanative.ui.post.RunsText
import com.aimdi.xtanative.ui.post.SensitiveGate
import com.aimdi.xtanative.ui.post.ThreadRows
import com.aimdi.xtanative.ui.post.VerifiedBadges
import com.aimdi.xtanative.ui.post.LocalPostScope
import com.aimdi.xtanative.ui.post.rememberPostPrefs
import com.aimdi.xtanative.ui.plugins.rememberShareText
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A profile (profile.dart): the header XTA draws (banner, face, feed filters
 * and following here, name and checks, bio, place, site, joined, counts
 * opening the follows lists, who of the reader's follows follows it, the
 * private note), then Posts, Replies, Media and Saved, each with its
 * filter. A protected account's posts and a sensitive profile wait behind
 * their gates. Its bar's Search opens [searchPosts] on "from:@name", its
 * Share hands the profile's link on.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProfileScreen(screenName: String, actions: PostActions, onBack: () -> Unit, searchPosts: (query: String) -> Unit = actions.search) {
    val graph = LocalGraph.current
    val vm: ProfileViewModel = viewModel(key = "profile-" + screenName.lowercase()) { ProfileViewModel(graph, screenName) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val follow by vm.follow.collectAsStateWithLifecycle()
    val session by graph.sessions.state.collectAsStateWithLifecycle()
    val share = rememberShareText()
    val shareBase = rememberPostPrefs().shareBase
    var sheet by rememberSaveable { mutableStateOf(ProfileSheet.NONE) }
    val tab = state.of(state.tab)
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val nearEnd by remember(list) {
        derivedStateOf {
            val info = list.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 5
        }
    }
    LaunchedEffect(nearEnd, tab.threads.size, tab.tiles.size, state.tab) { if (nearEnd) vm.loadMore() }
    val scrolled by remember(list) { derivedStateOf { list.firstVisibleItemIndex > 3 } }
    val user = state.user
    val shownName = user?.screenName ?: screenName

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(user?.name ?: "@$screenName", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) } },
                actions = {
                    if (user != null) {
                        IconButton(onClick = { searchPosts(ProfileHeader.searchQuery(user)) }) { Icon(Icons.Filled.Search, stringResource(Res.string.search)) }
                        IconButton(onClick = { share(ProfileHeader.shareUrl(user, shareBase)) }) { Icon(Icons.Outlined.Share, stringResource(Res.string.reader_link_share)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            if (scrolled) {
                FloatingActionButton(onClick = { scope.launch { list.scrollToItem(0) } }) {
                    Icon(PostIcons.ArrowUpward, stringResource(Res.string.posts_scroll_to_top))
                }
            }
        },
    ) { padding ->
        PostScopeProvider(profileScreenName = shownName) {
            val reveal = LocalPostScope.current?.sensitive
            val grid = rememberProfileGrid(vm, state, actions)
            val failure = state.failure
            // Padding, not content padding: the tab row sticks below the bar, not under it.
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = vm::refresh,
                modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
            ) {
                LazyColumn(
                    state = list,
                    contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 48.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "header", contentType = "header") {
                        Header(state, follow, screenName, actions, onSheet = { sheet = it })
                    }
                    val problem = state.problem
                    when {
                        failure != null -> item(key = "failure") { FailurePage(failure) }
                        state.loading && !state.complete -> item(key = "loading") { LoadingRow() }
                        problem != null -> item(key = "problem") { ProblemRow(problem, { vm.load() }) }
                    }
                    if (state.complete && user != null) {
                        stickyHeader(key = "tabs", contentType = "tabs") { Tabs(state.tab, vm::select) }
                        filterBar(state, vm)
                        val gated = state.tab != ProfileTab.SAVED && ProfileHeader.postsGated(user, session?.accounts.orEmpty().map { it.screenName })
                        when {
                            gated -> item(key = "gated") { Gated(stringResource(Res.string.posts_protected_posts, user.screenName)) }
                            // A sensitive profile's tabs wait behind XTA's gate: nothing of them is drawn until the reader chose to see it.
                            user.sensitive && reveal?.hidden == true -> item(key = "sensitive") {
                                SensitiveGate(reveal, stringResource(Res.string.posts_possibly_sensitive_profile), framed = false, modifier = Modifier.padding(vertical = 16.dp)) {}
                            }
                            else -> {
                                tabContent(state, tab, actions, grid)
                                tabFooter(state, tab, vm)
                            }
                        }
                    }
                }
            }
        }
    }
    when (sheet) {
        ProfileSheet.NONE -> Unit
        ProfileSheet.FILTERS -> FeedSettingsSheet(state, follow, vm, onDismiss = { sheet = ProfileSheet.NONE })
        ProfileSheet.NOTE -> NoteSheet(state.note, onSave = { note ->
            vm.saveNote(note)
            sheet = ProfileSheet.NONE
        }, onDismiss = { sheet = ProfileSheet.NONE })
        ProfileSheet.FOLLOWED_BY -> FollowedBySheet(follow.followedBy, actions, onDismiss = { sheet = ProfileSheet.NONE })
    }
}

/** The sheets a profile opens over itself. */
enum class ProfileSheet { NONE, FILTERS, NOTE, FOLLOWED_BY }

/** What a tab shows: Posts (the pinned post first), Replies, the media grid, or the device's Saved. */
private fun LazyListScope.tabContent(state: ProfileUi, tab: TabUi, actions: PostActions, grid: MediaGridRows) {
    when (state.tab) {
        ProfileTab.POSTS -> {
            val pinnedIds = state.pinned.mapTo(HashSet()) { it.id }
            items(state.pinned, key = { "pinned-" + it.id }, contentType = { "thread" }) { thread -> ThreadRows(thread, actions, pinnedIds) }
            items(tab.threads.filter { it.id !in pinnedIds }, key = { "post-" + it.id }, contentType = { "thread" }) { thread ->
                ThreadRows(thread, actions)
            }
        }
        ProfileTab.REPLIES, ProfileTab.SAVED -> items(tab.threads, key = { state.tab.name + "-" + it.id }, contentType = { "thread" }) { thread ->
            ThreadRows(thread, actions)
        }
        ProfileTab.MEDIA -> mediaGrid(grid)
    }
}

/**
 * The Media tab's grid (_media_grid.dart): the reader's layout, Livestreams
 * one 16:9 tile a row; a tap opens the viewer over every tile loaded, which
 * reads on as the reader nears the last.
 */
@Composable
private fun rememberProfileGrid(vm: ProfileViewModel, state: ProfileUi, actions: PostActions): MediaGridRows {
    val updates = remember(vm) { vm.ui.map { it.of(ProfileTab.MEDIA).tiles }.distinctUntilChanged() }
    val look = if (state.mediaFilter == MediaFilter.BROADCASTS) MediaGridLook.BROADCASTS else rememberMediaGridLook()
    return rememberMediaGridRows(state.of(ProfileTab.MEDIA).tiles, actions, updates, vm::loadMore, look)
}

private fun LazyListScope.tabFooter(state: ProfileUi, tab: TabUi, vm: ProfileViewModel) {
    item(key = "footer-" + state.tab.name) {
        val more = tab.moreProblem
        val failed = tab.problem
        val empty = tab.loaded && tab.threads.isEmpty() && tab.tiles.isEmpty() && (state.tab != ProfileTab.POSTS || state.pinned.isEmpty())
        when {
            tab.loading || tab.loadingMore -> LoadingRow()
            failed != null -> ProblemRow(failed, { vm.select(state.tab) })
            more != null -> ProblemRow(more, { vm.loadMore() })
            empty -> Gated(
                stringResource(
                    when {
                        state.tab != ProfileTab.SAVED -> Res.string.posts_no_posts
                        state.archiveFilter == ArchiveFilter.LIKES -> Res.string.posts_no_likes
                        else -> Res.string.posts_no_saves
                    },
                ),
                icon = null,
            )
        }
    }
}

/** The filter right above the tab it narrows: Posts (all, own posts, reposts), Media and Saved; Replies has none. */
private fun LazyListScope.filterBar(state: ProfileUi, vm: ProfileViewModel) {
    item(key = "filter-" + state.tab.name, contentType = "filter") {
        when (state.tab) {
            ProfileTab.POSTS -> FilterBar(
                state.postsFilter,
                listOf(
                    Filter(PostsFilter.ALL, PostIcons.Layers, Res.string.posts_filter_all),
                    Filter(PostsFilter.POSTS, XIcons.Reply, Res.string.posts_filter_posts),
                    Filter(PostsFilter.REPOSTS, XIcons.Repost, Res.string.posts_filter_reposts),
                ),
                vm::selectPosts,
            )
            ProfileTab.MEDIA -> FilterBar(
                state.mediaFilter,
                listOf(
                    Filter(MediaFilter.ALL, PostIcons.Layers, Res.string.posts_filter_all),
                    Filter(MediaFilter.PHOTOS, PostIcons.Image, Res.string.posts_filter_photos),
                    Filter(MediaFilter.VIDEOS, PostIcons.Videocam, Res.string.posts_filter_videos),
                    Filter(MediaFilter.BROADCASTS, PostIcons.Sensors, Res.string.posts_filter_broadcasts),
                ),
                vm::selectMedia,
            )
            ProfileTab.SAVED -> FilterBar(
                state.archiveFilter,
                listOf(
                    Filter(ArchiveFilter.ALL, PostIcons.Layers, Res.string.posts_filter_all),
                    Filter(ArchiveFilter.LIKES, Icons.Filled.FavoriteBorder, Res.string.posts_filter_likes),
                    Filter(ArchiveFilter.BOOKMARKS, PostIcons.Bookmark, Res.string.posts_filter_bookmarks),
                ),
                vm::selectArchive,
            )
            ProfileTab.REPLIES -> Unit
        }
    }
}

private class Filter<T>(val value: T, val icon: ImageVector, val label: StringResource)

/** Every choice an icon in a row, the chosen one on a quiet pill, each named for a screen reader (ProfileFilterBar). */
@Composable
private fun <T> FilterBar(selected: T, options: List<Filter<T>>, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { option ->
            val chosen = option.value == selected
            IconButton(
                onClick = { if (!chosen) onSelect(option.value) },
                modifier = Modifier.clip(CircleShape).background(if (chosen) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent),
            ) {
                Icon(
                    option.icon,
                    stringResource(option.label),
                    tint = if (chosen) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun Tabs(selected: ProfileTab, onSelect: (ProfileTab) -> Unit) {
    TabRow(selectedTabIndex = selected.ordinal, containerColor = MaterialTheme.colorScheme.background, divider = { Hairline() }) {
        ProfileTab.entries.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                text = {
                    Text(
                        stringResource(
                            when (tab) {
                                ProfileTab.POSTS -> Res.string.posts_tab_posts
                                ProfileTab.REPLIES -> Res.string.posts_tab_replies
                                ProfileTab.MEDIA -> Res.string.posts_tab_media
                                ProfileTab.SAVED -> Res.string.posts_tab_saved
                            },
                        ),
                        maxLines = 1,
                    )
                },
                selectedContentColor = MaterialTheme.colorScheme.onBackground,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A tab with nothing to show, and why: a protected account's posts, or nothing yet. */
@Composable
private fun Gated(message: String, icon: ImageVector? = Icons.Filled.Lock) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        icon?.let { Icon(it, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** A profile X will not show, as X said: the page itself, since a retry cannot change it. */
@Composable
private fun FailurePage(failure: ProfileFailure) {
    Gated(
        stringResource(
            when (failure) {
                ProfileFailure.PRIVATE -> Res.string.posts_private_profile
                ProfileFailure.NOT_FOUND -> Res.string.posts_user_not_found
                ProfileFailure.SUSPENDED -> Res.string.posts_account_suspended
                ProfileFailure.PAGE_NOT_FOUND -> Res.string.posts_page_not_found
            },
        ),
        icon = if (failure == ProfileFailure.PRIVATE) Icons.Filled.Lock else Icons.Filled.Search,
    )
}

@Composable
private fun Header(state: ProfileUi, follow: FollowUi, screenName: String, actions: PostActions, onSheet: (ProfileSheet) -> Unit) {
    val user = state.user
    val background = MaterialTheme.colorScheme.background
    Column(Modifier.fillMaxWidth()) {
        Box {
            val banner = user?.bannerUrl
            val owner = user?.screenName ?: screenName
            AsyncImage(
                model = banner?.let(ProfileHeader::bannerUrl),
                contentDescription = user?.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .let { if (banner != null) it.clickable { actions.openImage(banner, owner) } else it },
            )
            Avatar(
                user?.largeAvatarUrl,
                80.dp,
                Modifier.align(Alignment.BottomStart).offset(x = 16.dp, y = 40.dp).border(4.dp, background, CircleShape),
                onClick = user?.avatarUrl?.let { avatar -> { actions.openImage(ProfileHeader.fullAvatarUrl(avatar), owner) } },
            )
        }
        // Beside the avatar's lower half, as X places it: the feed filters, then following here.
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(start = 112.dp, end = 16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (user != null && state.complete) {
                IconButton(onClick = { onSheet(ProfileSheet.FILTERS) }) { Icon(PostIcons.Tune, stringResource(Res.string.posts_filters)) }
                ProfileFollowButton(user)
            }
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Identity(user, screenName)
            if (!user?.description.isNullOrBlank()) {
                val bio = remember(user) { user?.let(XText::bio).orEmpty() }
                SelectionContainer { RunsText(bio, actions) }
            }
            if (state.complete && user != null) {
                Meta(user, actions)
                Counts(user, actions)
                FollowedByLine(follow.followedBy) { onSheet(ProfileSheet.FOLLOWED_BY) }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (state.complete && user != null) {
            Hairline()
            NoteRow(state.note) { onSheet(ProfileSheet.NOTE) }
        }
        Hairline()
    }
}

/** The name with its checks and, for a protected account, the lock; the handle under it. */
@Composable
private fun Identity(user: XUser?, screenName: String) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                user?.name ?: screenName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (user != null) VerifiedBadges(user, 20.dp, Modifier.padding(start = 4.dp))
            if (user?.protected == true) {
                Icon(Icons.Filled.Lock, stringResource(Res.string.posts_private_profile), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp).size(17.dp))
            }
        }
        Text("@" + (user?.screenName ?: screenName), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Where, which site, and since when. */
@Composable
private fun Meta(user: XUser, actions: PostActions) {
    val joined = user.createdAt?.let { stringResource(Res.string.posts_joined, monthText(it)) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        user.location?.let { MetaLine(Icons.Filled.LocationOn, it) }
        user.website?.let { site -> MetaLine(XIcons.Link, ProfileHeader.websiteLabel(site), link = true) { actions.openLink(site) } }
        joined?.let { MetaLine(Icons.Filled.DateRange, it) }
    }
}

@Composable
private fun MetaLine(icon: ImageVector, text: String, link: Boolean = false, onClick: (() -> Unit)? = null) {
    val grey = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, modifier = if (onClick != null) Modifier.clickableNoRipple(onClick) else Modifier) {
        Icon(icon, null, tint = grey, modifier = Modifier.size(16.dp))
        Text(
            text,
            color = if (link) MaterialTheme.colorScheme.primary else grey,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/** Following and followers, each opening its list. */
@Composable
private fun Counts(user: XUser, actions: PostActions) {
    val strong = MaterialTheme.colorScheme.onSurface
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        listOfNotNull(
            user.following?.let { Triple(it, Res.string.posts_following, false) },
            user.followers?.let { Triple(it, Res.string.posts_followers, true) },
        ).forEach { (n, label, followers) ->
            val number = countText(n)
            val word = stringResource(label)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = strong)) { append(number) }
                    append(" ")
                    append(word)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickableNoRipple { actions.openFollows(user.screenName, followers) },
            )
        }
    }
}
