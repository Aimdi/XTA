package com.aimdi.xtanative.plugins.reddit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.Page
import com.aimdi.xtanative.networks.reddit.RedditPost
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.plugins.MessageText
import com.aimdi.xtanative.ui.plugins.PagedItems
import com.aimdi.xtanative.ui.plugins.PagedState
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.ReadsMoreNearEnd
import com.aimdi.xtanative.ui.plugins.RefreshesOn
import com.aimdi.xtanative.ui.plugins.ScrollsToTop
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept

/** Where a list of posts comes from: a community (Reddit's rails `popular` and `all` among them), or an account. */
internal sealed interface Listing {
    val key: String

    data class Subreddit(val name: String) : Listing {
        override val key: String get() = "r/" + name.lowercase()
    }

    data class User(val name: String) : Listing {
        override val key: String get() = "u/" + name.lowercase()
    }
}

/**
 * A community's or an account's posts page by page (XTA's `RedditListingBody`),
 * in the order and with the NSFW display the reader chose, read again when
 * either changes; the next page comes as the end nears, a button stays as
 * the fallback, and the end says so.
 */
@Composable
internal fun ListingBody(app: RedditApp, listing: Listing, frame: PluginFrame) = key(listing.key) {
    val pages = rememberKept("reddit-listing:" + listing.key) { scope ->
        PagedItems<RedditPost>(scope, key = { it.id }) { cursor -> read(app, listing, cursor) }
    }
    val generation by app.generation.collectAsState()
    RefreshesOn(generation) { pages.refresh() }
    RefreshesOn(frame.refresh) { pages.refresh() }
    RedditPagedList(pages, key = { it.id }, frame = frame, empty = pluginString(Strings.no_results)) { post -> RedditPostCard(app, post) }
}

private suspend fun read(app: RedditApp, listing: Listing, cursor: String?): Page<RedditPost> = when (listing) {
    is Listing.Subreddit -> app.listing(listing.name, cursor)
    is Listing.User -> app.userPosts(listing.name, cursor)
}

/**
 * [pages] as a list that reads on as its end comes into view and is pulled
 * to read again, failures in XTA's words: [row] draws each item under
 * [header]; [empty] says there is nothing, or nothing at all when null.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T : Any> RedditPagedList(
    pages: PagedItems<T>,
    key: (T) -> String,
    frame: PluginFrame,
    empty: String?,
    header: LazyListScope.() -> Unit = {},
    row: @Composable (T) -> Unit,
) {
    val paged by pages.state.collectAsState()
    val list = rememberLazyListState()
    ScrollsToTop(list, frame.toTop)
    ReadsMoreNearEnd(list, paged) { pages.more() }
    PullToRefreshBox(isRefreshing = paged.loading && paged.loaded, onRefresh = pages::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = list, contentPadding = frame.padding, modifier = Modifier.fillMaxSize()) {
            header()
            pagedState(paged, pages::refresh, empty)
            items(paged.items, key = { "row:" + key(it) }, contentType = { "row" }) { row(it) }
            if (paged.items.isNotEmpty()) item(key = "footer") { PagedFooter(paged, pages) }
        }
    }
}

/** Why the list is empty or failed, or that it is on its way. */
private fun LazyListScope.pagedState(paged: PagedState<*>, retry: () -> Unit, empty: String?) {
    val problem = paged.problem
    when {
        paged.items.isEmpty() && problem != null -> item(key = "problem") { RedditProblemView(troubleOf(problem), detailOf(problem), retry) }
        paged.items.isEmpty() && paged.loaded && !paged.loading -> if (empty != null) item(key = "empty") { MessageText(empty) }
        paged.items.isEmpty() -> item(key = "loading") { LoadingRow() }
        problem != null -> item(key = "problem") { RedditProblemView(troubleOf(problem), detailOf(problem), retry, compact = true) }
    }
}

@Composable
private fun PagedFooter(paged: PagedState<*>, pages: PagedItems<*>) {
    val more = paged.moreProblem
    when {
        paged.loadingMore -> LoadingRow()
        more != null -> RedditProblemView(troubleOf(more), detailOf(more), pages::retryMore, compact = true)
        paged.next != null -> LoadMoreButton(pages::more)
        else -> EndOfList()
    }
}

/**
 * A community's or an account's own screen (XTA's `RedditListingScreen`):
 * its posts under its name; a community's with search within it, what it
 * says about itself, and following it on this device.
 */
@Composable
internal fun ListingScreen(app: RedditApp, listing: Listing) {
    var about by remember { mutableStateOf(false) }
    val name = when (listing) {
        is Listing.Subreddit -> listing.name
        is Listing.User -> listing.name
    }
    PluginScaffold(
        title = (if (listing is Listing.Subreddit) "r/" else "u/") + name,
        onBack = app.host::back,
        actions = {
            if (listing is Listing.Subreddit) {
                IconButton(onClick = { app.host.open(RedditRoutes.search(name)) }) { Icon(Icons.Filled.Search, pluginString(Strings.search)) }
                IconButton(onClick = { about = true }) { Icon(Icons.Outlined.Info, pluginString(Strings.about_community)) }
                FollowButton(app, name)
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            ListingBody(app, listing, PluginFrame(bottomOnly(padding)))
        }
    }
    if (about) AboutSheet(app, name) { about = false }
}

/** Follows or unfollows [subreddit] on this device, saying which it is now, wherever the list was changed. */
@Composable
internal fun FollowButton(app: RedditApp, subreddit: String) {
    val followed by app.followed.collectAsState()
    val following = followed.follows(subreddit)
    TextButton(onClick = { app.follow(subreddit, !following) }) {
        Icon(if (following) Icons.Filled.Check else Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(pluginString(if (following) Strings.unsubscribe else Strings.subscribe), modifier = Modifier.padding(start = 6.dp))
    }
}

/** The compact follow control of a search row, where the labelled one does not fit. */
@Composable
internal fun FollowIconButton(app: RedditApp, subreddit: String) {
    val followed by app.followed.collectAsState()
    val following = followed.follows(subreddit)
    IconButton(onClick = { app.follow(subreddit, !following) }) {
        Icon(if (following) Icons.Filled.Check else Icons.Filled.Add, contentDescription = pluginString(if (following) Strings.unsubscribe else Strings.subscribe))
    }
}

/** The posts saved on this device, newest first. */
@Composable
internal fun SavedScreen(app: RedditApp) {
    val saved by app.saved.collectAsState()
    val list = rememberLazyListState()
    PluginScaffold(title = pluginString(Strings.saved), onBack = app.host::back) { padding ->
        LazyColumn(state = list, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            if (saved.isEmpty()) item(key = "none") { RedditEmpty(RedditGlyphs.BookmarkBorder, pluginString(Strings.saved_empty)) }
            items(saved, key = { "post:" + it.id }) { post -> RedditPostCard(app, post) }
        }
    }
}
