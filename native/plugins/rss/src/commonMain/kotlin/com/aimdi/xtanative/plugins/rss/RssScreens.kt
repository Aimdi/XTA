package com.aimdi.xtanative.plugins.rss

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aimdi.xtanative.networks.Page
import com.aimdi.xtanative.networks.RssItem
import com.aimdi.xtanative.networks.rss.RssFeed
import com.aimdi.xtanative.ui.plugins.AddToGroupButton
import com.aimdi.xtanative.ui.plugins.AddToGroupIcon
import com.aimdi.xtanative.ui.plugins.PluginFeedSkeleton
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.RetryMessage
import com.aimdi.xtanative.ui.plugins.networkProblemText
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.problemOf
import com.aimdi.xtanative.ui.plugins.rememberKept
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What a read of one thing brought: nothing yet, its answer, or why it failed. */
internal data class Loaded<T>(val value: T? = null, val loading: Boolean = false, val error: Throwable? = null)

/** One read, kept with its screen, made again on asking. */
internal class OneRead<T>(private val scope: CoroutineScope, private val read: suspend () -> T) {
    private val mutable = MutableStateFlow(Loaded<T>())
    val state: StateFlow<Loaded<T>> = mutable.asStateFlow()
    private var job: Job? = null

    fun load() {
        job?.cancel()
        job = scope.launch {
            mutable.update { it.copy(loading = true, error = null) }
            mutable.value = try {
                Loaded(read())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Loaded(mutable.value.value, error = e)
            }
        }
    }
}

/** One feed's items (rss_feed_screen.dart), its source left out of each row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RssFeedScreen(app: RssApp, feed: RssFeed) {
    val items = rememberKept("rss-feed-${feed.id}") { scope -> OneRead<Page<RssItem>>(scope) { app.client.items(feed) }.also { it.load() } }
    val loaded by items.state.collectAsState()
    val read by app.read.collectAsState()
    PluginScaffold(title = feed.name, onBack = app.host::back, actions = { AddToGroupButton(app.host, feed.id, pluginString(Strings.add_to_group)) { app.followIfNeeded(feed) } }) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            val shown = loaded.value?.items
            val error = loaded.error
            when {
                shown == null && error != null -> RetryMessage(pluginString(Strings.load_error) + "\n" + networkProblemText(problemOf(error), NETWORK), items::load)
                shown == null -> PluginFeedSkeleton()
                else -> PullToRefreshBox(isRefreshing = loaded.loading, onRefresh = items::load, modifier = Modifier.fillMaxSize()) {
                    if (shown.isEmpty()) {
                        PullableEmpty(RssIcons.Feed, pluginString(Strings.empty), PluginFrame(PaddingValues()))
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(shown, key = { it.id }) { item -> RssItemCard(item, unread = item.id !in read, onOpen = { app.openItem(item) }, showSource = false) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Add feed (rss_add_screen.dart): a site's or a feed's address, whose feed
 * is found and followed at once, then shown with its site and description,
 * and Add to a group under it.
 */
@Composable
internal fun RssAddScreen(app: RssApp) {
    var address by rememberSaveable { mutableStateOf("") }
    val lookup = rememberKept("rss-add") { scope -> AddLookup(app, scope) }
    val state by lookup.state.collectAsState()
    PluginScaffold(title = pluginString(Strings.add), onBack = app.host::back) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(pluginString(Strings.add_hint))
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                placeholder = { Text(pluginString(Strings.add_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onDone = { lookup.follow(address) }),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            Button(onClick = { lookup.follow(address) }, enabled = !state.loading, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text(pluginString(Strings.follow))
            }
            Spacer(Modifier.height(16.dp))
            val error = state.error
            when {
                state.loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                error != null -> RetryMessage(pluginString(Strings.add_error) + "\n" + networkProblemText(problemOf(error), NETWORK), { lookup.follow(address) })
                else -> state.value?.let { feed -> FoundFeed(feed) { app.addToGroup(feed) } }
            }
        }
    }
}

@Composable
private fun FoundFeed(feed: RssFeed, onAddToGroup: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val icon = feed.iconUrl
            if (icon == null) {
                FeedMark()
            } else {
                AsyncImage(icon, null, contentScale = ContentScale.Crop, modifier = Modifier.size(40.dp).clip(CircleShape))
            }
            Column(Modifier.padding(start = 16.dp)) {
                Text(feed.name, style = MaterialTheme.typography.bodyLarge)
                Text(hostOf(feed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        feed.description?.let { Text(it, modifier = Modifier.padding(top = 12.dp)) }
        TextButton(onClick = onAddToGroup, modifier = Modifier.padding(top = 4.dp)) {
            Icon(AddToGroupIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(pluginString(Strings.add_to_group), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** The address being looked up, and the feed it was followed as: kept with the screen, as XTA's RssAddFeedStore. */
internal class AddLookup(private val app: RssApp, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(Loaded<RssFeed>())
    val state: StateFlow<Loaded<RssFeed>> = mutable.asStateFlow()

    fun follow(address: String) {
        if (mutable.value.loading) return
        scope.launch {
            mutable.value = Loaded(loading = true)
            mutable.value = try {
                val feed = app.client.lookup(address)
                app.follow(feed)
                Loaded(feed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Loaded(error = e)
            }
        }
    }
}
