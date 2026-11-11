package com.aimdi.xtanative.plugins.bluesky

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import com.aimdi.xtanative.ui.plugins.EmptyPane
import com.aimdi.xtanative.ui.plugins.MessageText
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.RefreshesOn
import com.aimdi.xtanative.ui.plugins.RetryRow
import com.aimdi.xtanative.ui.plugins.ScrollsToTop
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.problemOf
import com.aimdi.xtanative.ui.post.PostIcons
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Following: the accounts followed here, merged newest first, each account's
 * own posts, as XTA's Following reads them; above them the people the feed
 * brings that are not followed yet, under them the accounts still to read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FollowingPane(app: BlueskyApp, frame: PluginFrame, onHome: Boolean, filters: ReaderFilter) {
    val timeline = app.following
    LaunchedEffect(timeline) { timeline.start() }
    val state by timeline.state.collectAsState()
    val follows by app.follows.state.collectAsState()
    val (filter, setFilter) = filters
    val shown = remember(state.posts, filter) { filter.apply(state.posts) }
    val people = remember(state.posts, follows) { BlueskyTimeline.peopleToFollow(state.posts, app.follows::follows) }
    val list = rememberLazyListState()
    ScrollsToTop(list, frame.toTop)
    // On Home the strip folds away under the third post; on the plugin's own screen it stays open above them.
    val strip: (@Composable () -> Unit)? = if (people.isEmpty()) null else { { PeopleStrip(app, people, folded = onHome) } }
    val stripAfter = if (onHome && strip != null) FollowingPlaces.STRIP_AFTER else null
    KeepsPlace(app, list, shown, state.posts, stripAfter)
    RefreshesOn(frame.refresh) { timeline.refresh(force = true) }
    PullToRefreshBox(isRefreshing = state.loading && state.loaded, onRefresh = { timeline.refresh(force = true) }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = list, contentPadding = frame.padding, modifier = Modifier.fillMaxSize()) {
            if (strip != null && stripAfter == null) item(key = "people") { strip() }
            followingStatus(app, state)
            filteredRows(app, state.posts, shown, strip.takeIf { stripAfter != null }) { setFilter(BlueskyReaderFilter()) }
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
private fun KeepsPlace(app: BlueskyApp, list: LazyListState, shown: List<BlueskyPost>, posts: List<BlueskyPost>, stripAfter: Int?) {
    val currentShown by rememberUpdatedState(shown)
    val currentPosts by rememberUpdatedState(posts)
    val currentStrip by rememberUpdatedState(stripAfter)
    var opening by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        try {
            val point = app.place.takePosition() ?: return@LaunchedEffect
            val start = withTimeoutOrNull(OPEN_WAIT_MS) {
                snapshotFlow { FollowingPlaces.start(currentShown, point, list.layoutInfo.visibleItemsInfo.map { it.key to it.index }, currentStrip) }.first { it != null }
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
            ProblemView(problem, onRetry = { app.following.refresh(force = true) })
        }
        state.posts.isEmpty() && !state.loaded -> item(key = "loading") { LoadingRow() }
        state.posts.isEmpty() && !state.loading -> item(key = "empty") { EmptyFollowing(app, nobody = false) }
        problem != null -> item(key = "refresh-failed") { RetryRow(pluginString(Strings.refresh_failed), onRetry = { app.following.refresh(force = true) }) }
    }
}

/**
 * [shown] of [posts] as rows; when the filters leave none of them, that, and
 * a way to reset them. [strip] is drawn under the third of them (after the
 * last of fewer), as XTA's Home puts the people a feed brings (headingAfter).
 */
internal fun LazyListScope.filteredRows(
    app: BlueskyApp,
    posts: List<BlueskyPost>,
    shown: List<BlueskyPost>,
    strip: (@Composable () -> Unit)? = null,
    onReset: () -> Unit,
) {
    if (posts.isNotEmpty() && shown.isEmpty()) {
        item(key = "filtered-out") {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                MessageText(pluginString(Strings.filter_empty))
                TextButton(onClick = onReset) { Text(pluginString(Strings.reset_filters)) }
            }
        }
    }
    val head = if (strip == null) shown else shown.take(FollowingPlaces.STRIP_AFTER)
    items(head, key = { it.uri }, contentType = { "post" }) { post -> PostRow(app, post) }
    if (strip == null) return
    if (shown.isNotEmpty()) item(key = "people") { strip() }
    items(shown.drop(FollowingPlaces.STRIP_AFTER), key = { it.uri }, contentType = { "post" }) { post -> PostRow(app, post) }
}

/** Following with nothing to show: why under XTA's cloud, and where to find accounts (bluesky_screen.dart's EmptyPane and its actions). */
@Composable
private fun EmptyFollowing(app: BlueskyApp, nobody: Boolean) {
    EmptyPane(BlueskyIcons.CloudOutlined, pluginString(if (nobody) Strings.following_empty else Strings.no_posts)) {
        Button(onClick = { app.openSearch(kind = BlueskySearchKind.PEOPLE) }) { IconLabel(BlueskyIcons.ExploreOutlined, pluginString(Strings.tab_discover)) }
        Spacer(Modifier.height(8.dp))
        if (nobody) {
            TextButton(onClick = { app.host.open(BlueskyRoutes.import(ImportKind.FOLLOWING)) }) { IconLabel(PostIcons.GroupAdd, pluginString(Strings.import_following)) }
            TextButton(onClick = { app.host.open(BlueskyRoutes.import(ImportKind.STARTER)) }) { IconLabel(GroupIcons.AutoAwesomeOutlined, pluginString(Strings.import_starter)) }
        } else {
            TextButton(onClick = { app.following.refresh(force = false) }) { IconLabel(GroupIcons.FileDownloadOutlined, pluginString(Strings.load_more_accounts)) }
        }
    }
}

/** A button's glyph before its words. */
@Composable
private fun IconLabel(icon: ImageVector, label: String) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(8.dp))
    Text(label)
}

@Composable
private fun LoadMoreAccounts(onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
        TextButton(onClick = onClick) { IconLabel(GroupIcons.FileDownloadOutlined, pluginString(Strings.load_more_accounts)) }
    }
}

/**
 * The authors the feed brings that are not followed here, reposted ones
 * first (plugin_feed_people.dart): each to open or follow. [folded], as on
 * Home, they wait under a line that counts them until the reader opens it.
 */
@Composable
private fun PeopleStrip(app: BlueskyApp, people: List<BlueskyFeedPerson>, folded: Boolean) {
    if (!folded) {
        Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp)) {
            Text(pluginString(Strings.from_feed), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
            PeopleRow(app, people, Modifier.padding(top = 8.dp), start = 16.dp)
        }
        return
    }
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { open = !open }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BadgedBox(badge = { Badge { Text(people.size.toString()) } }) { Icon(GroupIcons.PeopleOutlined, contentDescription = null, modifier = Modifier.size(20.dp)) }
            Text(
                pluginString(Strings.from_feed),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            )
            Icon(if (open) GroupIcons.ExpandLess else GroupIcons.ExpandMore, contentDescription = null)
        }
        if (open) PeopleRow(app, people, Modifier.padding(bottom = 4.dp), start = 12.dp)
    }
}

/** The people side by side, each a chip that opens the profile and Follow after it. */
@Composable
private fun PeopleRow(app: BlueskyApp, people: List<BlueskyFeedPerson>, modifier: Modifier, start: Dp) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = start, end = 16.dp),
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

/** Liked: the posts liked on this device, newest first, with the same filters as Following; a pull reads them from the device again. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LikedPane(app: BlueskyApp, frame: PluginFrame, filters: ReaderFilter) {
    val likes by app.likes.state.collectAsState()
    val posts by app.prefs.likedPosts.collectAsState()
    val library = remember(posts, likes) { BlueskyLocal.library(posts, likes.orEmpty()) }
    val (filter, setFilter) = filters
    val shown = remember(library, filter) { filter.apply(library) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var reading by remember { mutableStateOf(false) }
    ScrollsToTop(list, frame.toTop)
    val reload = {
        reading = true
        scope.launch {
            try {
                app.likes.reload()
            } finally {
                reading = false
            }
        }
        Unit
    }
    PullToRefreshBox(isRefreshing = reading, onRefresh = reload, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = list, contentPadding = frame.padding, modifier = Modifier.fillMaxSize()) {
            if (library.isEmpty()) {
                item(key = "empty") { LikedEmpty() }
            } else {
                filteredRows(app, library, shown) { setFilter(BlueskyReaderFilter()) }
            }
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
 * here or the AppView change, and when the reader asks; the first time this
 * session it shows the posts kept where it was left ([BlueskyPlace]) instead
 * of reading, as XTA does. A public AppView has
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
        if (started) {
            // Shown again after a read that failed: it is tried again, as XTA's feed store does on entry.
            if (mutable.value.problem != null && !mutable.value.loading) refresh(force = false)
            return
        }
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
                // The posts kept where Following was left are shown as they were, without reading every account again: a pull reads.
                if (left.isNotEmpty()) {
                    mutable.value = State(left, actors.size, loading = false, loaded = true, pending = app.reader.pending(actors))
                    return@launch
                }
            }
            mutable.update { it.copy(loading = true, followed = actors.size) }
            if (actors.isEmpty()) {
                mutable.value = State(followed = 0, loading = false, loaded = true)
                return@launch
            }
            try {
                val read = app.reader.read(actors, force)
                // A read that brought nothing but a failure leaves what is on screen, as XTA's feed store does.
                val posts = if (read.posts.isEmpty() && read.error != null) mutable.value.posts else read.posts
                mutable.value = State(posts, actors.size, loading = false, loaded = true, read.error?.let(::problemOf), app.reader.pending(actors))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(loading = false, loaded = true, problem = problemOf(e)) }
            }
        }
    }
}
