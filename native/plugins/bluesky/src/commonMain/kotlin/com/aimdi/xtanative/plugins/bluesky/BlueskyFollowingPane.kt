package com.aimdi.xtanative.plugins.bluesky

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.networks.bluesky.BlueskyFeedPerson
import com.aimdi.xtanative.networks.bluesky.BlueskyLocal
import com.aimdi.xtanative.networks.bluesky.BlueskyPost
import com.aimdi.xtanative.networks.bluesky.BlueskyProfile
import com.aimdi.xtanative.networks.bluesky.BlueskyReaderFilter
import com.aimdi.xtanative.networks.bluesky.BlueskySearchKind
import com.aimdi.xtanative.networks.bluesky.BlueskyTimeline
import com.aimdi.xtanative.ui.common.Avatar
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.plugins.MessageText
import com.aimdi.xtanative.ui.plugins.NetworkProblemView
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.RefreshesOn
import com.aimdi.xtanative.ui.plugins.RetryRow
import com.aimdi.xtanative.ui.plugins.ScrollsToTop
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.problemOf
import com.aimdi.xtanative.ui.search.DiscoverIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Following: the accounts followed here, merged newest first, each account's
 * own posts, as XTA's Following reads them; above them the people the feed
 * brings that are not followed yet, under them the accounts still to read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FollowingPane(app: BlueskyApp, frame: PluginFrame) {
    val timeline = app.following
    LaunchedEffect(timeline) { timeline.start() }
    val state by timeline.state.collectAsState()
    val follows by app.follows.state.collectAsState()
    val (filter, setFilter) = rememberReaderFilter(app, likes = false)
    val shown = remember(state.posts, filter) { filter.apply(state.posts) }
    val people = remember(state.posts, follows) { BlueskyTimeline.peopleToFollow(state.posts, app.follows::follows) }
    val list = rememberLazyListState()
    ScrollsToTop(list, frame.toTop)
    KeepsPlace(app, list, shown, state.posts)
    RefreshesOn(frame.refresh) { timeline.refresh(force = true) }
    PullToRefreshBox(isRefreshing = state.loading && state.loaded, onRefresh = { timeline.refresh(force = true) }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = list, contentPadding = frame.padding, modifier = Modifier.fillMaxSize()) {
            item(key = "bar") { ReaderBar(filter, setFilter) }
            if (people.isNotEmpty()) item(key = "people") { PeopleStrip(app, people) }
            followingStatus(app, state)
            filteredRows(app, state.posts, shown) { setFilter(BlueskyReaderFilter()) }
            if (state.pending > 0 && state.posts.isNotEmpty()) {
                item(key = "more-accounts") { LoadMoreAccounts { timeline.refresh(force = false) } }
            }
        }
    }
}

/**
 * Following opened where it was left, the first time it is drawn this
 * session, once that post is shown; then where the list stands, kept a
 * moment after it settles, with the posts around it of all it holds.
 */
@Composable
private fun KeepsPlace(app: BlueskyApp, list: LazyListState, shown: List<BlueskyPost>, posts: List<BlueskyPost>) {
    val currentShown by rememberUpdatedState(shown)
    val currentPosts by rememberUpdatedState(posts)
    var opening by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        try {
            val point = app.place.takePosition() ?: return@LaunchedEffect
            val start = withTimeoutOrNull(OPEN_WAIT_MS) {
                snapshotFlow { FollowingPlaces.start(currentShown, point, list.layoutInfo.visibleItemsInfo.map { it.key to it.index }) }.first { it != null }
            } ?: return@LaunchedEffect
            list.scrollToItem(start.first, start.second)
        } finally {
            opening = false
        }
    }
    LaunchedEffect(opening) {
        if (opening) return@LaunchedEffect
        snapshotFlow { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }.collectLatest {
            delay(SETTLE_MS)
            val visible = list.layoutInfo.visibleItemsInfo.map { it.key to it.offset }
            FollowingPlaces.place(visible, currentPosts)?.let(app.place::remember)
        }
    }
}

private const val OPEN_WAIT_MS = 10_000L
private const val SETTLE_MS = 350L

/** Why Following shows no posts, or that its last read failed over the posts it kept. */
private fun LazyListScope.followingStatus(app: BlueskyApp, state: FollowingTimeline.State) {
    val problem = state.problem
    when {
        state.followed == 0 && state.loaded -> item(key = "nobody") { EmptyFollowing(app, nobody = true) }
        state.posts.isEmpty() && problem != null -> item(key = "problem") {
            NetworkProblemView(problem, app.network, onRetry = { app.following.refresh(force = true) })
        }
        state.posts.isEmpty() && !state.loaded -> item(key = "loading") { LoadingRow() }
        state.posts.isEmpty() && !state.loading -> item(key = "empty") { EmptyFollowing(app, nobody = false) }
        problem != null -> item(key = "refresh-failed") { RetryRow(pluginString(Strings.refresh_failed), onRetry = { app.following.refresh(force = true) }) }
    }
}

/** [shown] of [posts] as rows; when the filters leave none of them, that, and a way to reset them. */
internal fun LazyListScope.filteredRows(app: BlueskyApp, posts: List<BlueskyPost>, shown: List<BlueskyPost>, onReset: () -> Unit) {
    if (posts.isNotEmpty() && shown.isEmpty()) {
        item(key = "filtered-out") {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                MessageText(pluginString(Strings.filter_empty))
                TextButton(onClick = onReset) { Text(pluginString(Strings.reset_filters)) }
            }
        }
    }
    items(shown, key = { it.uri }, contentType = { "post" }) { post -> PostRow(app, post) }
}

/** Following with nothing to show: why, and where to find accounts (XTA's empty pane and its actions). */
@Composable
private fun EmptyFollowing(app: BlueskyApp, nobody: Boolean) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            pluginString(if (nobody) Strings.following_empty else Strings.no_posts),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = { app.openSearch(kind = BlueskySearchKind.PEOPLE) }) { Text(pluginString(Strings.tab_discover)) }
        if (nobody) {
            TextButton(onClick = { app.host.open(BlueskyRoutes.import(ImportKind.FOLLOWING)) }) { Text(pluginString(Strings.import_following)) }
            TextButton(onClick = { app.host.open(BlueskyRoutes.import(ImportKind.STARTER)) }) { Text(pluginString(Strings.import_starter)) }
        } else {
            TextButton(onClick = { app.following.refresh(force = false) }) { Text(pluginString(Strings.load_more_accounts)) }
        }
    }
}

@Composable
private fun LoadMoreAccounts(onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
        TextButton(onClick = onClick) {
            Icon(DiscoverIcons.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(pluginString(Strings.load_more_accounts))
        }
    }
}

/** The authors the feed brings that are not followed here, reposted ones first (plugin_feed_people.dart): each to open or follow. */
@Composable
private fun PeopleStrip(app: BlueskyApp, people: List<BlueskyFeedPerson>) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)) {
        Text(
            pluginString(Strings.from_feed),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            people.forEach { person ->
                AssistChip(
                    onClick = { app.openProfile(person.handle) },
                    label = { Text("@" + person.handle, maxLines = 1) },
                    leadingIcon = { Avatar(person.avatarUrl, 20.dp) },
                )
                TextButton(onClick = { app.toggleFollow(BlueskyProfile(did = "", handle = person.handle, displayName = person.name, avatarUrl = person.avatarUrl)) }) {
                    Text(pluginString(Strings.follow))
                }
            }
        }
    }
}

/** Liked: the posts liked on this device, newest first, with the same filters as Following. */
@Composable
internal fun LikedPane(app: BlueskyApp, frame: PluginFrame) {
    val likes by app.likes.state.collectAsState()
    val posts by app.prefs.likedPosts.collectAsState()
    val library = remember(posts, likes) { BlueskyLocal.library(posts, likes.orEmpty()) }
    val (filter, setFilter) = rememberReaderFilter(app, likes = true)
    val shown = remember(library, filter) { filter.apply(library) }
    val list = rememberLazyListState()
    ScrollsToTop(list, frame.toTop)
    LazyColumn(state = list, contentPadding = frame.padding, modifier = Modifier.fillMaxSize()) {
        if (library.isEmpty()) {
            item(key = "empty") { LikedEmpty() }
        } else {
            item(key = "bar") { ReaderBar(filter, setFilter) }
            filteredRows(app, library, shown) { setFilter(BlueskyReaderFilter()) }
        }
    }
}

@Composable
private fun LikedEmpty() {
    Column(Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.FavoriteBorder, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(16.dp))
        Text(pluginString(Strings.liked_empty), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}

/**
 * The Following timeline, shared by the plugin's screens as XTA's feed store
 * is: read the first time it is shown, then whenever the accounts followed
 * here or the AppView change, and when the reader asks; its first read
 * this session starts from the posts kept where it was left ([BlueskyPlace]). A public AppView has
 * no "my timeline", so each account's own feed is read (with its replies) by
 * [com.aimdi.xtanative.networks.bluesky.BlueskyFollowing], a few dozen
 * accounts a read; the filters hide what the reader does not want.
 */
internal class FollowingTimeline(private val app: BlueskyApp, private val scope: CoroutineScope) {
    data class State(
        val posts: List<BlueskyPost> = emptyList(),
        val followed: Int = 0,
        val loading: Boolean = true,
        val loaded: Boolean = false,
        /** Why the last read failed, whole or for some accounts. */
        val problem: Problem? = null,
        /** Accounts not read yet: a read asks for at most a few dozen. */
        val pending: Int = 0,
    )

    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable.asStateFlow()

    private var job: Job? = null

    @Volatile
    private var started = false

    /** Reads the timeline once it is first shown, and again on each change of what it reads. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(app.follows.state.filterNotNull(), app.prefs.appView) { follows, server -> follows.map { it.actor } to server }
                .distinctUntilChanged()
                .collect { refresh(force = false) }
        }
        scope.launch { app.follows.load() }
    }

    fun refresh(force: Boolean) {
        val actors = app.follows.state.value?.map { it.actor } ?: return
        job?.cancel()
        job = scope.launch {
            if (!force && mutable.value.posts.isEmpty()) {
                val left = app.place.takePosts(app.follows::follows)
                if (left.isNotEmpty()) mutable.update { it.copy(posts = left) }
            }
            mutable.update { it.copy(loading = true, followed = actors.size) }
            if (actors.isEmpty()) {
                mutable.value = State(followed = 0, loading = false, loaded = true)
                return@launch
            }
            try {
                val read = app.reader.read(actors, force)
                mutable.value = State(read.posts, actors.size, loading = false, loaded = true, read.error?.let(::problemOf), app.reader.pending(actors))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(loading = false, loaded = true, problem = problemOf(e)) }
            }
        }
    }
}
