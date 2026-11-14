package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.threads.ThreadsDiscovery
import com.aimdi.xtanative.networks.threads.ThreadsFailure
import com.aimdi.xtanative.networks.threads.ThreadsForYouState
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.plugins.EmptyPane
import com.aimdi.xtanative.ui.plugins.PluginFeedSkeleton
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.RefreshesOn
import com.aimdi.xtanative.ui.plugins.ReportsReadingScroll
import com.aimdi.xtanative.ui.plugins.RetryMessage
import com.aimdi.xtanative.ui.plugins.RetryRow
import com.aimdi.xtanative.ui.plugins.ScrollsToTop
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.post.PostIcons
import java.time.Instant
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Threads' own recommended feed (the For you section): the pasted session's
 * timeline, or without one what threads.com shows a logged-out browser, said
 * so above it. It is painted where the reader left it, reads more as they
 * near its end, and a pull, F5 or the desktop's refresh reads it anew. Meta
 * showing a guest nothing, refusing for now, or not answering each has its
 * own words. Nothing here is liked, followed or reported as seen on Threads.
 */
@Composable
internal fun ThreadsForYouPane(app: ThreadsApp, frame: PluginFrame) {
    val restored by produceState(false, app) {
        app.restoreForYou()
        value = true
    }
    if (!restored) {
        PluginFeedSkeleton()
        return
    }
    val state by app.forYou.state.collectAsState()
    LaunchedEffect(app) {
        if (ThreadsForYouKept.readsOnOpen(app.forYou.state.value, app.forYouPlace, System.currentTimeMillis())) app.refreshForYou()
    }
    RefreshesOn(frame.refresh) { app.refreshForYou() }
    val problem = state.problem
    when {
        state.posts.isNotEmpty() -> ForYouList(app, frame, state)
        state.throttledUntil != null -> ForYouMessage(app, frame, state) { RateLimited(state.throttledUntil) }
        problem != null -> ForYouMessage(app, frame, state) { RetryMessage(pluginString(ThreadsFailures.forYou(problem)), onRetry = app::refreshForYou) }
        state.needsSignIn -> ForYouMessage(app, frame, state) { NeedsSignIn(app) }
        !state.loaded -> PluginFeedSkeleton()
        else -> ForYouMessage(app, frame, state) { EmptyPane(PostIcons.Sparkle, pluginString(Strings.no_posts)) }
    }
}

/** The posts, with what stands above and under them; the list opens where the reader stopped and tells where they are now. */
@Composable
private fun ForYouList(app: ThreadsApp, frame: PluginFrame, read: ThreadsForYouState) {
    val followed by app.accounts.state.collectAsState()
    val options by app.options.collectAsState()
    val state = remember(read, options) { read.copy(posts = options.unmuted(read.posts)) }
    val followedTopics by app.topics.state.collectAsState()
    val people = remember(state.posts, followed) { ThreadsDiscovery.peopleToFollow(state.posts, { ThreadsAccounts.follows(followed, it) }) }
    val topics = remember(state.posts, followedTopics) { app.discoveredTopics() }
    val rows = remember(state, people, topics) { ThreadsForYouRow.of(state, people, topics) }
    val list = rememberLazyListState(
        initialFirstVisibleItemIndex = remember { ThreadsForYouRow.indexOf(rows, app.forYouPlace) ?: 0 },
        initialFirstVisibleItemScrollOffset = remember { app.forYouPlace.offset.takeIf { ThreadsForYouRow.indexOf(rows, app.forYouPlace) != null } ?: 0 },
    )
    ScrollsToTop(list, frame.toTop)
    KeepsPlace(list, app::keepForYouPlace)
    ReadsMoreNearTheEnd(app, list, rows.size)
    StartsAnewAtTheTop(list, state.readAt)
    ForYouRefreshable(app, frame, state, list) {
        items(rows, key = { it.key }, contentType = { it::class }) { row ->
            when (row) {
                is ThreadsForYouRow.SessionNotice -> SessionNotice(app, row.problem)
                ThreadsForYouRow.PublicNote -> NoteText(pluginString(Strings.for_you_public), Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                is ThreadsForYouRow.Post -> ThreadsPostCard(app, row.post)
                is ThreadsForYouRow.People -> PeopleStrip(app, row.people, startOpen = true)
                is ThreadsForYouRow.Topics -> TopicStrip(app, row.topics)
                ThreadsForYouRow.LoadingMore -> LoadingRow()
                is ThreadsForYouRow.MoreProblem -> RetryRow(pluginString(ThreadsFailures.forYou(row.problem)), onRetry = app::retryMoreForYou)
                ThreadsForYouRow.End -> NoteText(pluginString(Strings.for_you_end), Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp))
            }
        }
    }
}


/** Asks for the next page once the reader is within a few rows of the end. */
@Composable
private fun ReadsMoreNearTheEnd(app: ThreadsApp, list: LazyListState, rows: Int) {
    LaunchedEffect(app, list, rows) {
        snapshotFlow { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= rows - MORE_WITHIN }
            .distinctUntilChanged()
            .collect { near -> if (near) app.moreForYou() }
    }
}

private const val MORE_WITHIN = 4

/** A feed read anew is another selection: it starts at its top. The one restored, or first read, stays where it opened. */
@Composable
private fun StartsAnewAtTheTop(list: LazyListState, readAt: Instant?) {
    val first = remember { readAt }
    LaunchedEffect(readAt) {
        if (readAt != first) list.scrollToItem(0)
    }
}

/** The public page stands in for the session's own: why, and the way to the settings. */
@Composable
private fun SessionNotice(app: ThreadsApp, problem: ThreadsFailure) {
    val words = if (problem == ThreadsFailure.UNAUTHORIZED) Strings.error_unauthorized else Strings.session_parked
    ListItem(
        leadingContent = { Icon(ThreadsIcons.PauseCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer) },
        headlineContent = { Text(pluginString(words), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium) },
        supportingContent = { Text(pluginString(Strings.for_you_public), color = MaterialTheme.colorScheme.onErrorContainer) },
        trailingContent = {
            TextButton(onClick = { app.host.openSettings() }) { Text(pluginString(Strings.settings), color = MaterialTheme.colorScheme.onErrorContainer) }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.errorContainer),
    )
}

/** Meta is refusing for now: said so, with the time it named, and no button that would ask again before it. */
@Composable
private fun RateLimited(until: Instant?) {
    val language = currentLanguage()
    val time = remember(until, language) { until?.let { ThreadsFailures.time(it, language) } }
    EmptyPane(ThreadsIcons.PauseCircle, pluginString(Strings.error_throttled), detail = time?.let { pluginString(Strings.retry_after, it) })
}

/** Meta showed a guest no For you page: where a session goes, and a way to ask again. */
@Composable
private fun NeedsSignIn(app: ThreadsApp) {
    EmptyPane(ThreadsIcons.Mark, pluginString(Strings.for_you_sign_in), detail = pluginString(Strings.for_you_sign_in_detail)) {
        Button(onClick = { app.host.openSettings() }) {
            Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(pluginString(Strings.settings), modifier = Modifier.padding(start = 8.dp))
        }
        TextButton(onClick = app::refreshForYou) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(pluginString(Strings.retry), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** A message in place of the feed, on a list of its own so that it can still be pulled to ask again. */
@Composable
private fun ForYouMessage(app: ThreadsApp, frame: PluginFrame, state: ThreadsForYouState, content: @Composable () -> Unit) {
    val list = rememberLazyListState()
    ReportsReadingScroll(list)
    ForYouRefreshable(app, frame, state, list) { item(key = "message") { content() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForYouRefreshable(app: ThreadsApp, frame: PluginFrame, state: ThreadsForYouState, list: LazyListState, content: LazyListScope.() -> Unit) {
    PullToRefreshBox(isRefreshing = state.loading && (state.loaded || state.posts.isNotEmpty()), onRefresh = app::refreshForYou, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = list, contentPadding = frame.padding, modifier = Modifier.fillMaxSize(), content = content)
    }
}
