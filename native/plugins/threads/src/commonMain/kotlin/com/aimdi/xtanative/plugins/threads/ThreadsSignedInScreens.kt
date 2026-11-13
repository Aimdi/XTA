package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.Page
import com.aimdi.xtanative.networks.threads.ThreadsActivityFilter
import com.aimdi.xtanative.networks.threads.ThreadsActivityItem
import com.aimdi.xtanative.networks.threads.ThreadsActivityKind
import com.aimdi.xtanative.networks.threads.ThreadsMore
import com.aimdi.xtanative.networks.threads.ThreadsOwnFeed
import com.aimdi.xtanative.networks.threads.ThreadsPaging
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.common.agoText
import com.aimdi.xtanative.ui.plugins.Fetch
import com.aimdi.xtanative.ui.plugins.MessageText
import com.aimdi.xtanative.ui.plugins.NoticeHost
import com.aimdi.xtanative.ui.plugins.PluginAvatar
import com.aimdi.xtanative.ui.plugins.PluginFilterChip
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.RetryMessage
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.post.PostIcons
import kotlinx.coroutines.CoroutineScope

/** One of the reader's own lists of posts as far as it has been read: the first page, and those after it as the reader scrolls. */
internal class ThreadsOwnFeedModel(private val app: ThreadsApp, private val feed: ThreadsOwnFeed, scope: CoroutineScope) {
    val first = Fetch<Page<ThreadsPost>>(scope) { app.ready(); app.nextPage { app.direct.ownFeed(feed) } }
    val more = ThreadsPaging(scope, { it.shortcode ?: it.id }) { cursor ->
        app.nextPage { app.direct.ownFeed(feed, cursor).let { ThreadsMore(it.items, it.next) } }
    }
}

/**
 * A list of posts Threads keeps for the signed-in reader alone: their
 * Following feed, the posts they saved or liked on Threads. Each post a feed
 * card, page after page, pull to read again; without a session it says
 * where to sign in. Read only: a like or a bookmark here stays on the device.
 */
@Composable
internal fun ThreadsOwnFeedScreen(app: ThreadsApp, feed: ThreadsOwnFeed) {
    SignedInScaffold(app, ThreadsSignedInRules.title(feed)) { OwnFeedList(app, feed) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OwnFeedList(app: ThreadsApp, feed: ThreadsOwnFeed) {
    val model = rememberKept("threads-own-${feed.name}") { scope -> ThreadsOwnFeedModel(app, feed, scope) }
    LaunchedEffect(model) { model.first.ensure() }
    val first by model.first.state.collectAsState()
    val more by model.more.state.collectAsState()
    LaunchedEffect(first.value) { first.value?.let { model.more.start(it.items, it.next) } }
    val posts = remember(first.value, more.items) { ThreadsPostLists.shown(first.value?.items.orEmpty(), more.items) }
    val body = ThreadsPeopleView.body(signedIn = true, read = first.value != null, problem = first.problem != null, people = posts.size, typed = "")
    val state = rememberLazyListState()
    ReadsMoreNear(state, model.more, more)
    PullToRefreshBox(isRefreshing = first.loading && first.value != null, onRefresh = { model.first.load(force = true) }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
            when (body) {
                ThreadsPeopleView.Body.PROBLEM -> item(key = "problem") {
                    RetryMessage(ThreadsFailures.ownList(first.problem, currentLanguage()), onRetry = { model.first.load(force = true) })
                }
                ThreadsPeopleView.Body.LOADING -> item(key = "loading") { LoadingRow() }
                ThreadsPeopleView.Body.PEOPLE -> items(posts, key = { "post:" + (it.shortcode ?: it.id) }, contentType = { "post" }) { post -> ThreadsPostCard(app, post) }
                else -> item(key = "empty") { MessageText(pluginString(ThreadsSignedInRules.empty(feed))) }
            }
            if (body == ThreadsPeopleView.Body.PEOPLE) moreFoot("own", more) { model.more.retry() }
        }
    }
}

/** The reader's activity as far as it has been read, for one [filter]. */
internal class ThreadsActivityModel(private val app: ThreadsApp, private val filter: ThreadsActivityFilter, scope: CoroutineScope) {
    val first = Fetch<Page<ThreadsActivityItem>>(scope) { app.ready(); app.nextPage { app.direct.activity(filter) } }
    val more = ThreadsPaging(scope, { it.id }) { cursor ->
        app.nextPage { app.direct.activity(filter, cursor).let { ThreadsMore(it.items, it.next) } }
    }
}

/**
 * The signed-in reader's activity, as Threads' Activity lists it: Meta's own
 * line for each reply, mention, quote, repost, like and new follower, with
 * who did it and when, All, Replies and Mentions to choose from. A line opens
 * the post it is about, else the profile of whoever did it. Read only:
 * nothing is marked seen on Threads, answered or followed back.
 */
@Composable
internal fun ThreadsActivityScreen(app: ThreadsApp) {
    var filter by rememberSaveable { mutableStateOf(ThreadsActivityFilter.ALL) }
    SignedInScaffold(app, Strings.activity) {
        Column(Modifier.fillMaxSize()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                ThreadsActivityFilter.entries.forEach { option ->
                    PluginFilterChip(pluginString(filterLabel(option)), option == filter, onClick = { filter = option })
                }
            }
            ActivityList(app, filter)
        }
    }
}

private fun filterLabel(filter: ThreadsActivityFilter): PluginText = when (filter) {
    ThreadsActivityFilter.ALL -> Strings.all
    ThreadsActivityFilter.REPLIES -> Strings.replies
    ThreadsActivityFilter.MENTIONS -> Strings.mentions
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityList(app: ThreadsApp, filter: ThreadsActivityFilter) {
    val model = rememberKept("threads-activity-${filter.name}") { scope -> ThreadsActivityModel(app, filter, scope) }
    LaunchedEffect(model) { model.first.ensure() }
    val first by model.first.state.collectAsState()
    val more by model.more.state.collectAsState()
    LaunchedEffect(first.value) { first.value?.let { model.more.start(it.items, it.next) } }
    val lines = remember(first.value, more.items) { (first.value?.items.orEmpty() + more.items).distinctBy { it.id } }
    val body = ThreadsPeopleView.body(signedIn = true, read = first.value != null, problem = first.problem != null, people = lines.size, typed = "")
    val state = rememberLazyListState()
    ReadsMoreNear(state, model.more, more)
    PullToRefreshBox(isRefreshing = first.loading && first.value != null, onRefresh = { model.first.load(force = true) }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
            when (body) {
                ThreadsPeopleView.Body.PROBLEM -> item(key = "problem") {
                    RetryMessage(ThreadsFailures.ownList(first.problem, currentLanguage()), onRetry = { model.first.load(force = true) })
                }
                ThreadsPeopleView.Body.LOADING -> item(key = "loading") { LoadingRow() }
                ThreadsPeopleView.Body.PEOPLE -> items(lines, key = { "activity:" + it.id }) { line -> ActivityRow(app, line) }
                else -> item(key = "empty") { MessageText(pluginString(Strings.activity_empty)) }
            }
            if (body == ThreadsPeopleView.Body.PEOPLE) moreFoot("activity", more) { model.more.retry() }
        }
    }
}

/** One line of activity: the face of whoever did it, Meta's words, and when, with the kind's mark. */
@Composable
private fun ActivityRow(app: ThreadsApp, line: ThreadsActivityItem) {
    val opens = ThreadsSignedInRules.opens(line)
    ListItem(
        leadingContent = { PluginAvatar(line.picture, line.handle.orEmpty(), line.handle ?: line.id, 40.dp) },
        headlineContent = { Text(line.text, maxLines = 4, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(iconOf(line.kind), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                line.at?.let { Text(agoText(it), modifier = Modifier.padding(start = 6.dp)) }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (opens != null) Modifier.clickable { app.host.open(opens) } else Modifier,
    )
    HorizontalDivider()
}

private fun iconOf(kind: ThreadsActivityKind): ImageVector = when (kind) {
    ThreadsActivityKind.REPLY -> PostIcons.Forum
    ThreadsActivityKind.MENTION -> Icons.Filled.Person
    ThreadsActivityKind.QUOTE -> PostIcons.Quote
    ThreadsActivityKind.REPOST -> XIcons.Repost
    ThreadsActivityKind.LIKE -> Icons.Filled.Favorite
    ThreadsActivityKind.FOLLOW -> ThreadsIcons.PersonAdd
    ThreadsActivityKind.OTHER -> Icons.Filled.Notifications
}

/** A signed-in reader's screen: [title] over [content], or where to sign in when there is no session. */
@Composable
private fun SignedInScaffold(app: ThreadsApp, title: PluginText, content: @Composable () -> Unit) {
    val settings by app.prefs.collectAsState()
    val signedIn = ThreadsPeopleView.signedIn(ThreadsSessions.of(settings))
    PluginScaffold(title = pluginString(title), onBack = app.host::back) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (signedIn) content() else SignedOut(app)
            NoticeHost(app.notices)
        }
    }
}
