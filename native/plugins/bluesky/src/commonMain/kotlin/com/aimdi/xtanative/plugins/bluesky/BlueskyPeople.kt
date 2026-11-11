package com.aimdi.xtanative.plugins.bluesky

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.aimdi.xtanative.networks.bluesky.BlueskyFollow
import com.aimdi.xtanative.networks.bluesky.BlueskyPost
import com.aimdi.xtanative.networks.bluesky.BlueskyProfile
import com.aimdi.xtanative.networks.bluesky.BlueskyQuoteSort
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.plugins.PagedItems
import com.aimdi.xtanative.ui.plugins.PagedList
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.ReadsMoreNearEnd
import com.aimdi.xtanative.ui.plugins.pagedRows
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside
import com.aimdi.xtanative.ui.post.PostIcons

/** Whether [person] is followed here. */
private fun List<BlueskyFollow>?.has(person: BlueskyProfile): Boolean = orEmpty().any { it.isOf(person.did) || it.isOf(person.handle) }

/**
 * Who [actor] follows, or who follows them (bluesky_follows_screen.dart):
 * read page by page from the public graph, each account to open, to put
 * in the app's groups, or to follow or unfollow here.
 */
@Composable
internal fun PeopleScreen(app: BlueskyApp, actor: String, kind: PeopleKind) {
    val pages = rememberKept("bluesky-people:$kind:$actor") { scope ->
        PagedItems<BlueskyProfile>(scope, key = { it.identity }) { cursor ->
            if (kind == PeopleKind.FOLLOWING) app.client.follows(actor, cursor = cursor) else app.client.followers(actor, cursor = cursor)
        }
    }
    val follows by app.follows.state.collectAsState()
    val title = pluginString(if (kind == PeopleKind.FOLLOWING) Strings.count_following_title else Strings.count_followers_title)
    val empty = pluginString(if (kind == PeopleKind.FOLLOWING) Strings.follows_nobody else Strings.followers_nobody)
    PluginScaffold(title = title, onBack = app.host::back) { padding ->
        PagedList(
            pages,
            key = { it.identity },
            problemText = { pluginString(errorText(it)) },
            empty = empty,
            modifier = Modifier.padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
        ) { person -> PersonRow(app, person, follows.has(person), groups = true) }
    }
}

/**
 * Who reposted a post, or ([quotes]) the posts quoting it, as XTA's activity
 * screen lists them; its bar opens the post in the browser, and quotes come
 * in the order picked for the session once two are loaded.
 */
@Composable
internal fun ActivityScreen(app: BlueskyApp, uri: String, url: String, quotes: Boolean) {
    val outside = rememberOpenOutside()
    PluginScaffold(
        title = pluginString(if (quotes) Strings.quotes else Strings.reposted_by),
        onBack = app.host::back,
        actions = {
            if (url.isNotEmpty()) IconButton(onClick = { outside(url) }) { Icon(PostIcons.OpenInNew, pluginString(Strings.open_in_browser)) }
        },
    ) { padding ->
        val modifier = Modifier.padding(top = padding.calculateTopPadding())
        val bottom = PaddingValues(bottom = padding.calculateBottomPadding())
        if (quotes) QuoteList(app, uri, modifier, bottom) else RepostList(app, uri, modifier, bottom)
    }
}

@Composable
private fun RepostList(app: BlueskyApp, uri: String, modifier: Modifier, contentPadding: PaddingValues) {
    val pages = rememberKept("bluesky-reposts:$uri") { scope -> PagedItems<BlueskyProfile>(scope, key = { it.identity }) { cursor -> app.client.repostedBy(uri, cursor) } }
    val follows by app.follows.state.collectAsState()
    PagedList(pages, key = { it.identity }, problemText = { pluginString(errorText(it)) }, empty = pluginString(Strings.no_results), modifier = modifier, contentPadding = contentPadding) { person ->
        PersonRow(app, person, follows.has(person))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuoteList(app: BlueskyApp, uri: String, modifier: Modifier, contentPadding: PaddingValues) {
    val pages = rememberKept("bluesky-quotes:$uri") { scope -> PagedItems<BlueskyPost>(scope, key = { it.uri }) { cursor -> app.client.quotes(uri, cursor) } }
    val paged by pages.state.collectAsState()
    val order by app.quoteSort.collectAsState()
    val sorted = remember(paged, order) { paged.copy(items = order.sorted(paged.items)) }
    val list = rememberLazyListState()
    val empty = pluginString(Strings.no_results)
    ReadsMoreNearEnd(list, paged) { pages.more() }
    PullToRefreshBox(isRefreshing = paged.loading && paged.loaded, onRefresh = pages::refresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(state = list, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
            if (BlueskyQuoteSort.offered(paged.items)) {
                item(key = "sort") { SortMenu(order, BlueskyQuoteSort.entries, { it.label to it.icon }, onChoose = { app.quoteSort.value = it }) }
            }
            pagedRows(sorted, pages, key = { it.uri }, problemText = { pluginString(errorText(it)) }, empty = empty) { post -> PostRow(app, post) }
        }
    }
}

private val BlueskyQuoteSort.label
    get() = when (this) {
        BlueskyQuoteSort.RECENT -> Strings.sort_recent
        BlueskyQuoteSort.OLDEST -> Strings.sort_oldest
        BlueskyQuoteSort.MOST_LIKED -> Strings.sort_most_liked
    }

private val BlueskyQuoteSort.icon
    get() = when (this) {
        BlueskyQuoteSort.RECENT -> PostIcons.Schedule
        BlueskyQuoteSort.OLDEST -> PostIcons.History
        BlueskyQuoteSort.MOST_LIKED -> XIcons.Like
    }
