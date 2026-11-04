package com.aimdi.xtanative.plugins.booru

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.booru.BooruArtist
import com.aimdi.xtanative.networks.booru.BooruEndpoints
import com.aimdi.xtanative.networks.booru.BooruQuery
import com.aimdi.xtanative.networks.booru.BooruTagCategory
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept

/**
 * One query's posts on a page of their own, such as a tag's or a pool's
 * (booru_query_page.dart): the feed under [title], with [actions] and Download
 * all in the bar, and [header] above the posts.
 */
@Composable
private fun QueryPage(
    app: BooruApp,
    key: String,
    query: String,
    site: SiteRef?,
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
    header: @Composable () -> Unit = {},
) {
    val feed = rememberKept("booru-query:$key") { scope ->
        BooruFeed(scope, { app.client.feedKey }) { page -> app.client.search(query, page.toString(), site = app.site(site)) }.also { it.refresh() }
    }
    val state by feed.state.collectAsState()
    var downloading by remember { mutableStateOf(false) }
    PluginScaffold(title, onBack = app.host::back, actions = {
        actions()
        IconButton(onClick = { downloading = true }) { Icon(BooruIcons.DownloadAll, pluginString(Strings.download_all)) }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            Column(Modifier.fillMaxSize()) {
                header()
                FeedView(app, feed, pluginString(Strings.empty_search), PaddingValues(bottom = padding.calculateBottomPadding()))
            }
            BooruMessages(app, Modifier.align(Alignment.BottomCenter).padding(bottom = padding.calculateBottomPadding()))
        }
    }
    if (downloading) DownloadAllDialog(app, state.posts, onDone = { downloading = false })
}

/**
 * A tag's own page (booru_tag_page.dart): its posts; for an artist, their
 * other names and pages elsewhere; and the tag's follow, wiki and search.
 */
@Composable
internal fun BooruTagScreen(app: BooruApp, route: BooruRoute.Tag) = Bound(app) {
    val followed = app.follows.state.collectAsState().value.orEmpty().contains(route.tag)
    var wiki by remember { mutableStateOf(false) }
    val engine = app.site(route.site).engine
    QueryPage(
        app,
        key = "tag:${route.site?.host}:${route.tag}",
        query = route.tag,
        site = route.site,
        title = route.tag,
        actions = {
            IconButton(onClick = { app.launch { if (followed) app.follows.remove(route.tag) else app.follows.add(route.tag) } }) {
                Icon(if (followed) Icons.Filled.Check else BooruIcons.Sell, pluginString(if (followed) Strings.unfollow_tag else Strings.follow_tag))
            }
            if (BooruEndpoints.supportsWiki(engine)) IconButton(onClick = { wiki = true }) { Icon(BooruIcons.Wiki, pluginString(Strings.wiki)) }
            IconButton(onClick = { app.openSearch(route.tag) }) { Icon(Icons.Filled.Search, pluginString(Strings.search_tag)) }
        },
        header = { if (route.category == BooruTagCategory.ARTIST) ArtistLinks(app, route) },
    )
    if (wiki) WikiSheet(app, route.tag, route.site, onDismiss = { wiki = false })
}

/** A pool's posts, in the pool's own order. */
@Composable
internal fun BooruPoolScreen(app: BooruApp, route: BooruRoute.Pool) = Bound(app) {
    QueryPage(app, key = "pool:${route.site?.host}:${route.id}", query = BooruQuery.poolQuery(route.id), site = route.site, title = route.name)
}

/** The artist's other names and their pages elsewhere, once the host has answered; nothing when it keeps no entry for them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArtistLinks(app: BooruApp, route: BooruRoute.Tag) {
    val artist = produceState<BooruArtist?>(null, route) { value = attempt { app.client.artist(route.tag, app.site(route.site)) } }.value
    if (artist == null || (artist.otherNames.isEmpty() && artist.urls.isEmpty())) return
    val outside = rememberOutside()
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)) {
        if (artist.otherNames.isNotEmpty()) {
            Text(pluginString(Strings.also_known_as, artist.otherNames.joinToString(", ", transform = BooruFormats::tagName)), style = MaterialTheme.typography.bodySmall)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            artist.urls.forEach { url ->
                AssistChip(
                    onClick = { outside.open(url) },
                    label = { Text(BooruFormats.siteName(url)) },
                    leadingIcon = { Icon(XIcons.Link, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
    }
}
