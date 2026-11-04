package com.aimdi.xtanative.plugins.booru

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
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
import com.aimdi.xtanative.networks.booru.BooruQuery
import com.aimdi.xtanative.networks.booru.BooruTagSuggestion
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginSearch
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept

/**
 * Booru's search where the app draws the field (Discover's chips, Home's and
 * the store's search): the query's posts as the tag search shows them, its
 * tags as chips that open the tag search to be changed, the star that saves
 * it, Download all, and the host's tag suggestions for the last tag typed
 * when it is not a tag of the site's (`miku` finds `hatsune_miku`).
 */
internal class BooruFieldSearch(private val app: BooruApp) : PluginSearch() {
    override fun hint(language: String): String = Strings.search_hint.get(language)

    @Composable
    override fun Results(query: String, frame: PluginFrame) = FieldResults(app, query, frame)
}

@Composable
private fun FieldResults(app: BooruApp, query: String, frame: PluginFrame) = Bound(app) {
    val search = rememberKept("booru-field-search:$query") { scope -> newSearch(app, scope, query).also { it.search() } }
    val state by search.state.collectAsState()
    val results by search.current.collectAsState()
    var downloading by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            QueryRow(app, state, onDownload = { downloading = true })
            TypedTagSuggestions(app, search, state.tags)
            results?.let { SearchResults(app, search, it, frame.padding) }
        }
        BooruMessages(app, Modifier.align(Alignment.BottomCenter).padding(frame.padding))
    }
    val shown = results
    if (downloading && shown != null) DownloadAllDialog(app, shown.feed.state.value.posts, onDone = { downloading = false })
}

/** The search's tags, each opening the tag search with it back in the field; the star and Download all at the end. */
@Composable
private fun QueryRow(app: BooruApp, state: BooruSearchState, onDownload: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.tags.forEach { tag ->
                AssistChip(onClick = { app.openSearch(state.query, edit = tag) }, label = { Text(tokenText(tag, state.categories[tag])) })
            }
        }
        SaveSearchButton(app, state.text)
        IconButton(onClick = onDownload) { Icon(BooruIcons.DownloadAll, pluginString(Strings.download_all)) }
    }
}

/**
 * The host's suggestions for the last tag of the search, when the site has
 * no tag by that name: the typed word was a part of a tag's name, another
 * name for it or a typo. A suggestion takes its place and searches again.
 */
@Composable
private fun TypedTagSuggestions(app: BooruApp, search: BooruSearch, tags: List<String>) {
    val last = tags.lastOrNull()
    val prefix = last?.let(BooruQuery::suggestionPrefix)
    val found = produceState(emptyList<BooruTagSuggestion>(), prefix) {
        value = prefix?.let { attempt { app.client.suggestTags(it) } }.orEmpty()
    }.value
    if (last == null || prefix == null || found.isEmpty() || found.any { it.name.equals(prefix, ignoreCase = true) }) return
    val locale = readerLocale()
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 4.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Search, pluginString(Strings.suggestions_hint), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        found.take(SUGGESTED).forEach { suggestion ->
            TagChip(
                label = suggestion.alias?.let { "$it → ${suggestion.name}" } ?: suggestion.name,
                category = suggestion.category,
                onClick = { search.run(BooruQuery.text(tags.dropLast(1) + BooruQuery.tokenFor(last, suggestion.name))) },
                detail = suggestion.postCount?.let { BooruFormats.compactCount(it.toLong(), locale) },
            )
        }
    }
}

private const val SUGGESTED = 8
