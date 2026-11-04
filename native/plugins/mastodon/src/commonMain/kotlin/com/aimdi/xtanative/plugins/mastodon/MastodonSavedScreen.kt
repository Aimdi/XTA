package com.aimdi.xtanative.plugins.mastodon

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.mastodon.MastodonSnapshot
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.plugins.MessageText
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.pluginString

/** What was saved from Mastodon on this device, newest first: XTA's Saved with its Mastodon source picked. */
@Composable
internal fun MastodonSavedScreen(app: MastodonApp) {
    val graph = LocalGraph.current
    LaunchedEffect(graph) { graph.saved.library() }
    val library by graph.saved.state.collectAsState()
    val posts = remember(library) { library?.posts.orEmpty().mapNotNull { MastodonSnapshot.fromArchive(it.content) } }
    PluginScaffold(title = pluginString(Strings.saved), onBack = app.host::back) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 32.dp),
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
        ) {
            when {
                library == null -> item(key = "loading") { LoadingRow() }
                posts.isEmpty() -> item(key = "empty") { MessageText(pluginString(Strings.saved_empty)) }
            }
            items(posts, key = { it.url }, contentType = { "post" }) { post -> MastodonPostRow(app, post) }
        }
    }
}
