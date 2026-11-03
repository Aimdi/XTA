package com.aimdi.xtanative.plugins.bluesky

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.bluesky.BlueskyPost
import com.aimdi.xtanative.networks.bluesky.BlueskyProfile
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.plugins.PagedItems
import com.aimdi.xtanative.ui.plugins.PagedList
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginSearch
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept

/** What the search shows: posts (latest or top), or people. */
internal enum class SearchTab { POSTS, PEOPLE }

/**
 * Bluesky's search, as XTA's search sheet asks for it: posts, latest or top
 * (a post link reads that post, a lone #tag that tag), and people (a handle,
 * a DID or a profile link reads that account).
 */
internal class BlueskySearch(private val app: BlueskyApp) : PluginSearch() {
    override fun hint(language: String): String = Strings.search_hint.get(language)

    @Composable
    override fun Results(query: String, frame: PluginFrame) = SearchResults(app, query, frame)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchResults(app: BlueskyApp, query: String, frame: PluginFrame) {
    var tab by rememberSaveable { mutableStateOf(SearchTab.POSTS) }
    var top by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab.ordinal, containerColor = MaterialTheme.colorScheme.background, divider = { Hairline() }) {
            SearchTab.entries.forEach { choice ->
                Tab(
                    selected = tab == choice,
                    onClick = { tab = choice },
                    text = { Text(pluginString(if (choice == SearchTab.POSTS) Strings.search_posts else Strings.search_people)) },
                    selectedContentColor = MaterialTheme.colorScheme.onBackground,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                SearchTab.POSTS -> Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !top, onClick = { top = false }, label = { Text(pluginString(Strings.search_latest)) })
                        FilterChip(selected = top, onClick = { top = true }, label = { Text(pluginString(Strings.search_top)) })
                    }
                    Box(Modifier.weight(1f)) { PostResults(app, query, top, frame) }
                }
                SearchTab.PEOPLE -> PeopleResults(app, query, frame)
            }
        }
    }
}

@Composable
private fun PostResults(app: BlueskyApp, query: String, top: Boolean, frame: PluginFrame) {
    val sort = if (top) "top" else "latest"
    val pages = rememberKept("bluesky-search-posts:$sort:$query") { scope ->
        PagedItems<BlueskyPost>(scope, key = { it.uri }) { cursor -> app.client.searchPostsFor(query, cursor, sort).shown() }
    }
    PagedList(
        pages,
        key = { it.uri },
        network = app.network,
        empty = pluginString(Strings.search_nothing, query),
        contentPadding = frame.padding,
        toTop = frame.toTop,
    ) { post -> PostRow(app, post) }
}

@Composable
private fun PeopleResults(app: BlueskyApp, query: String, frame: PluginFrame) {
    val pages = rememberKept("bluesky-search-people:$query") { scope ->
        PagedItems<BlueskyProfile>(scope, key = { it.identity }) { cursor -> app.client.searchPeople(query, cursor) }
    }
    PagedList(
        pages,
        key = { it.identity },
        network = app.network,
        empty = pluginString(Strings.people_empty),
        contentPadding = frame.padding,
        toTop = frame.toTop,
    ) { person -> PersonRow(app, person) }
}
