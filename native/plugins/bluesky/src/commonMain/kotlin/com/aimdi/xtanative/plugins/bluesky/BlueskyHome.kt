package com.aimdi.xtanative.plugins.bluesky

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.networks.bluesky.Bluesky
import com.aimdi.xtanative.networks.bluesky.BlueskyPost
import com.aimdi.xtanative.networks.bluesky.BlueskyTimeline
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.plugins.MessageText
import com.aimdi.xtanative.ui.plugins.NetworkProblemView
import com.aimdi.xtanative.ui.plugins.PagedItems
import com.aimdi.xtanative.ui.plugins.PagedList
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.RefreshesOn
import com.aimdi.xtanative.ui.plugins.ScrollsToTop
import com.aimdi.xtanative.ui.plugins.pluginPlural
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.problemOf
import com.aimdi.xtanative.ui.plugins.rememberKept
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the home tab shows: Bluesky's Discover feed, or the accounts followed here. */
internal enum class HomeFeed { DISCOVER, FOLLOWING }

/** The home tab: Discover or Following, chosen above the list. */
@Composable
internal fun BlueskyHome(app: BlueskyApp, frame: PluginFrame) {
    var feed by rememberSaveable { mutableStateOf(HomeFeed.DISCOVER) }
    Column(Modifier.fillMaxSize()) {
        FeedChoice(feed) { feed = it }
        Box(Modifier.weight(1f)) {
            when (feed) {
                HomeFeed.DISCOVER -> DiscoverFeed(app, frame)
                HomeFeed.FOLLOWING -> FollowingFeed(app, frame)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedChoice(feed: HomeFeed, onChoose: (HomeFeed) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HomeFeed.entries.forEach { choice ->
            FilterChip(
                selected = feed == choice,
                onClick = { onChoose(choice) },
                label = { Text(pluginString(if (choice == HomeFeed.DISCOVER) Strings.tab_discover else Strings.tab_following)) },
            )
        }
    }
}

/** Bluesky's Discover feed ("What's Hot"), page by page, as a reader without an account reads it. */
@Composable
private fun DiscoverFeed(app: BlueskyApp, frame: PluginFrame) {
    val pages = rememberKept("bluesky-discover") { scope ->
        PagedItems<BlueskyPost>(scope, key = { it.uri }) { cursor -> app.client.feed(Bluesky.DISCOVER_FEED_URI, cursor = cursor).shown() }
    }
    RefreshesOn(frame.refresh) { pages.refresh() }
    PagedList(
        pages,
        key = { it.uri },
        network = app.network,
        empty = pluginString(Strings.feed_empty),
        contentPadding = frame.padding,
        toTop = frame.toTop,
    ) { post -> PostRow(app, post) }
}

/** The accounts followed here, merged newest first: each account's own posts, as XTA's Following reads them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FollowingFeed(app: BlueskyApp, frame: PluginFrame) {
    val timeline = rememberKept("bluesky-following") { scope -> FollowingTimeline(app, scope) }
    val state by timeline.state.collectAsState()
    val list = rememberLazyListState()
    ScrollsToTop(list, frame.toTop)
    RefreshesOn(frame.refresh) { timeline.refresh(force = true) }
    PullToRefreshBox(isRefreshing = state.loading && state.loaded, onRefresh = { timeline.refresh(force = true) }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = list, contentPadding = frame.padding, modifier = Modifier.fillMaxSize()) {
            val problem = state.problem
            when {
                state.followed == 0 && state.loaded -> item(key = "nobody") { MessageText(pluginString(Strings.following_empty)) }
                state.posts.isEmpty() && problem != null -> item(key = "problem") {
                    NetworkProblemView(problem, app.network, onRetry = { timeline.refresh(force = true) })
                }
                state.posts.isEmpty() && !state.loaded -> item(key = "loading") { LoadingRow() }
                state.posts.isEmpty() && !state.loading -> item(key = "empty") { MessageText(pluginString(Strings.feed_empty)) }
            }
            if (state.posts.isNotEmpty() && (state.partial || state.pending > 0)) {
                item(key = "notice") { FollowingNotice(state) }
            }
            items(state.posts, key = { it.uri }, contentType = { "post" }) { post -> PostRow(app, post) }
        }
    }
}

@Composable
private fun FollowingNotice(state: FollowingTimeline.State) {
    val text = when {
        state.pending > 0 -> pluginPlural(Strings.following_pending, state.pending, state.pending)
        else -> pluginString(Strings.following_partial)
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/**
 * The Following timeline: read again whenever the accounts followed here or
 * the replies and reposts settings change, and when the reader pulls. A public
 * AppView has no "my timeline", so each account's own feed is read (by
 * [com.aimdi.xtanative.networks.bluesky.BlueskyFollowing], a few at a time).
 */
internal class FollowingTimeline(private val app: BlueskyApp, private val scope: CoroutineScope) {
    data class State(
        val posts: List<BlueskyPost> = emptyList(),
        val followed: Int = 0,
        val loading: Boolean = true,
        val loaded: Boolean = false,
        val problem: Problem? = null,
        /** Some accounts failed while others were read. */
        val partial: Boolean = false,
        /** Accounts not read yet: a read asks for at most a few dozen. */
        val pending: Int = 0,
    )

    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable.asStateFlow()

    private var job: Job? = null

    init {
        scope.launch {
            app.settings.load()
            combine(app.follows.state.filterNotNull(), app.settings.state) { follows, settings -> follows.map { it.actor } to settings }
                .distinctUntilChanged()
                .collect { refresh(force = false) }
        }
        scope.launch { app.follows.load() }
    }

    fun refresh(force: Boolean) {
        val actors = app.follows.state.value?.map { it.actor } ?: return
        val settings = app.settings.state.value
        job?.cancel()
        job = scope.launch {
            mutable.update { it.copy(loading = true, followed = actors.size) }
            if (actors.isEmpty()) {
                mutable.value = State(followed = 0, loading = false, loaded = true)
                return@launch
            }
            try {
                val reader = app.following(settings.replies)
                val read = reader.read(actors, force)
                val posts = read.posts.filter { BlueskyTimeline.takes(it, settings.replies, settings.reposts) && !it.isUnavailable }
                val error = read.error
                mutable.value = State(
                    posts = posts,
                    followed = actors.size,
                    loading = false,
                    loaded = true,
                    problem = error?.let(::problemOf),
                    partial = error != null,
                    pending = reader.pending(actors),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(loading = false, loaded = true, problem = problemOf(e)) }
            }
        }
    }
}
