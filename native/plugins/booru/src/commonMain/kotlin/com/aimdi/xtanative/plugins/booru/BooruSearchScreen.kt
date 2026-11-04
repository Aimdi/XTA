package com.aimdi.xtanative.plugins.booru

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.booru.BooruMetatags
import com.aimdi.xtanative.networks.booru.BooruQuery
import com.aimdi.xtanative.store.settings.BooruPrefs
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** A search of the site in use, its results read in [scope]: what the search screen and the app's search field both run. */
internal fun newSearch(app: BooruApp, scope: CoroutineScope, initial: String): BooruSearch = BooruSearch(
    scope,
    suggest = { prefix -> app.client.suggestTags(prefix) },
    metatags = { input ->
        val settings = app.current()
        BooruMetatags.suggestions(BooruConfig.engine(settings), input, BooruConfig.host(settings), BooruConfig.maxRating(settings))
    },
    remember = { query -> app.update { it.with(BooruPrefs.searchHistory, BooruHistory.remembered(it[BooruPrefs.searchHistory], query)) } },
    results = { query, tags ->
        val related = MutableStateFlow<List<String>?>(null)
        scope.launch { related.value = attempt { app.client.relatedTags(BooruQuery.plainTags(tags)) }.orEmpty() }
        BooruResults(query, tags, BooruFeed(scope, { app.client.feedKey }) { page -> app.client.search(query, page.toString()) }, related)
    },
    initialQuery = initial,
)

/**
 * The tag search (booru_search_screen.dart): tags added one by one as chips,
 * with the host's suggestions as one types, quick operators, the engine's
 * metatags, saved and recent searches; every search that runs is kept in the
 * history, and a search can be saved to Following. Opened on [route]'s query,
 * it runs it at once, with [BooruRoute.Search.edit] back in the field.
 */
@Composable
internal fun BooruSearchScreen(app: BooruApp, route: BooruRoute.Search) = Bound(app) {
    val search = rememberKept("booru-search:${route.query}:${route.edit}") { scope ->
        newSearch(app, scope, route.query).also { opened ->
            if (route.query.isNotBlank()) opened.search()
            route.edit?.let(opened::reopen)
        }
    }
    val state by search.state.collectAsState()
    val results by search.current.collectAsState()
    val field = remember { mutableStateOf(route.edit.orEmpty()) }
    var downloading by remember { mutableStateOf(false) }
    // Back closes the editor over the results first, as XTA's does; the system's back leaves the screen.
    val back: () -> Unit = {
        val closed = search.closeEditor()
        if (closed) field.value = ""
        if (!closed) app.host.back()
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, pluginString(Strings.back)) }
                    TagField(search, state, field, autofocus = route.query.isBlank(), modifier = Modifier.weight(1f))
                    SaveSearchButton(app, state.text)
                    if (state.showsResults && results != null) {
                        IconButton(onClick = { downloading = true }) { Icon(BooruIcons.DownloadAll, pluginString(Strings.download_all)) }
                    }
                }
                Hairline()
                SearchBody(app, search, state, results, field)
            }
            BooruMessages(app, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
    }
    val shown = results
    if (downloading && shown != null) DownloadAllDialog(app, shown.feed.state.value.posts, onDone = { downloading = false })
}

@Composable
private fun SearchBody(app: BooruApp, search: BooruSearch, state: BooruSearchState, results: BooruResults?, field: MutableState<String>) {
    when {
        state.showsResults && results != null -> SearchResults(app, search, results, PaddingValues(bottom = 24.dp))
        !state.editing -> LoadingRow()
        else -> SearchLanding(app, search, state, onField = { field.value = it })
    }
}

/** The star of the query in the field: a saved search feeds Following. */
@Composable
internal fun SaveSearchButton(app: BooruApp, text: String) {
    val query = BooruQuery.normalise(text)
    val saved = query != null && app.follows.state.collectAsState().value.orEmpty().contains(query)
    val message = pluginString(Strings.search_saved, pluginString(Strings.tab_following))
    IconButton(
        enabled = query != null,
        onClick = {
            if (query != null) {
                app.launch {
                    if (saved) {
                        app.follows.remove(query)
                    } else {
                        app.follows.add(query)
                        app.tell(message)
                    }
                }
            }
        },
    ) {
        Icon(if (saved) Icons.Filled.Star else Icons.Outlined.Star, pluginString(if (saved) Strings.unsave_search else Strings.save_search))
    }
}
