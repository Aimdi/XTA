package com.aimdi.xtanative.plugins.booru

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.booru.BooruQuery
import com.aimdi.xtanative.networks.booru.BooruTagOperator
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.subscriptions.GroupIcons

/**
 * Posts for a search (booru_search_results.dart), with the tags they share
 * most as one-tap refinements over them, under how many the blacklist hid.
 */
@Composable
internal fun SearchResults(app: BooruApp, search: BooruSearch, results: BooruResults, padding: PaddingValues) {
    val state by search.state.collectAsState()
    FeedView(
        app,
        results.feed,
        pluginString(Strings.empty_search),
        padding,
        search = search,
        retryWhenEmpty = false,
        above = { RelatedTags(search, results, state.tags) },
    )
}

/** The host's related tags for the search where it keeps them, else the tags the results share most: a tap adds one, a long press excludes it. */
@Composable
private fun RelatedTags(search: BooruSearch, results: BooruResults, query: List<String>) {
    val fromHost by results.related.collectAsState()
    val posts by results.feed.state.collectAsState()
    val host = fromHost.orEmpty()
    val tags = if (host.isNotEmpty()) BooruQuery.unsearchedTags(host, query) else BooruQuery.relatedTags(posts.posts, query)
    if (tags.isEmpty()) return
    val hint = pluginString(Strings.related_hint)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 4.dp, end = 12.dp, top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(GroupIcons.AutoAwesomeOutlined, hint, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        tags.forEach { tag -> RelatedChip(tag, onAdd = { search.refine(tag) }, onExclude = { search.refine(BooruTagOperator.EXCLUDE.prefix + tag) }) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RelatedChip(tag: String, onAdd: () -> Unit, onExclude: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.combinedClickable(
            onClick = onAdd,
            onClickLabel = pluginString(Strings.add_to_search),
            onLongClick = onExclude,
            onLongClickLabel = pluginString(Strings.exclude_tag),
        ),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(tag, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 4.dp))
        }
    }
}
