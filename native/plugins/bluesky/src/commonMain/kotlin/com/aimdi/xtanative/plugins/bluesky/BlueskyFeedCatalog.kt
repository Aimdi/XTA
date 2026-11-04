package com.aimdi.xtanative.plugins.bluesky

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.bluesky.BlueskyFeedGenerator
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.plugins.MessageText
import com.aimdi.xtanative.ui.plugins.NetworkHeaderButton
import com.aimdi.xtanative.ui.plugins.NetworkProblemRow
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.pluginString

/**
 * Find feeds (bluesky_feeds_pane.dart's BlueskyFeedCatalogScreen): the feeds
 * pinned, the reader's own and the popular ones, or those a search finds;
 * each one pinned or unpinned here, and opened on Discover with a tap.
 */
@Composable
internal fun FeedCatalogScreen(app: BlueskyApp) {
    val feeds = app.feeds
    LaunchedEffect(feeds) { feeds.ensureLoaded() }
    val state by feeds.state.collectAsState()
    val pinned by app.prefs.pinnedFeeds.collectAsState()
    var query by rememberSaveable { mutableStateOf(state.query) }
    val catalog = state.catalog
    val entries = (if (state.query.isEmpty()) pinned + state.created else emptyList<BlueskyFeedGenerator>()).plus(catalog.items).distinctBy { it.uri }
    PluginScaffold(title = pluginString(Strings.find_feeds), onBack = app.host::back) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text(pluginString(Strings.search_feeds_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { feeds.searchCatalog(query) }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, pluginString(Strings.search)) }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { feeds.searchCatalog(query) }),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            LazyColumn(contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 24.dp), modifier = Modifier.weight(1f)) {
                items(entries, key = { it.uri }) { feed ->
                    FeedRow(feed, selected = feed.uri == state.selected.uri, pinned = pinned.any { it.uri == feed.uri }, onPin = {
                        if (pinned.any { it.uri == feed.uri }) feeds.unpin(feed.uri) else feeds.pin(feed)
                    }) {
                        feeds.open(feed.uri, feed.displayName)
                        app.host.back()
                    }
                }
                item(key = "footer") {
                    val problem = catalog.problem
                    when {
                        catalog.loading -> LoadingRow()
                        problem != null -> NetworkProblemRow(problem, app.network, onRetry = { feeds.searchCatalog(state.query, more = catalog.next != null) })
                        entries.isEmpty() -> MessageText(pluginString(Strings.no_feeds_found))
                        catalog.more -> TextButton(onClick = { feeds.searchCatalog(state.query, more = true) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Text(pluginString(Strings.load_more))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedRow(feed: BlueskyFeedGenerator, selected: Boolean, pinned: Boolean, onPin: () -> Unit, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).let { if (selected) it.background(colors.secondaryContainer) else it }
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(feed.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            feed.creatorHandle?.let { NoteText("@$it") }
            if (feed.description.isNotEmpty()) Text(feed.description, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        NetworkHeaderButton(XIcons.Pin, pluginString(if (pinned) Strings.unpin else Strings.pin), tint = if (pinned) colors.primary else colors.onSurfaceVariant, onClick = onPin)
    }
    Hairline()
}
