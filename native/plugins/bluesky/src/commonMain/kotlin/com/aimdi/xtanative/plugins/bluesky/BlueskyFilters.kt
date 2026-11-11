package com.aimdi.xtanative.plugins.bluesky

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.bluesky.BlueskyReaderFilter
import com.aimdi.xtanative.networks.bluesky.BlueskyReading
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.post.PostIcons

/** A list's filter and how to change it. */
internal typealias ReaderFilter = Pair<BlueskyReaderFilter, (BlueskyReaderFilter) -> Unit>

/** The filter of the Following list, or ([likes]) of the Liked one, and how to change it; its words are this screen's, the rest is kept. */
@Composable
internal fun rememberReaderFilter(app: BlueskyApp, likes: Boolean): ReaderFilter {
    val reading by app.prefs.reading.collectAsState()
    var query by rememberSaveable(likes) { mutableStateOf("") }
    val kept = if (likes) reading.likes else reading.following
    val change: (BlueskyReaderFilter) -> Unit = { next ->
        query = next.query
        app.launch { app.prefs.reading { it.with(likes, next.copy(query = "")) } }
    }
    return kept.copy(query = query) to change
}

private fun BlueskyReading.with(likes: Boolean, filter: BlueskyReaderFilter): BlueskyReading =
    if (likes) copy(likes = filter) else copy(following = filter)

/** How many of a filter's controls are set, for the badge on Filters. */
private fun BlueskyReaderFilter.setCount(): Int =
    listOf(query.isNotBlank(), content != BlueskyReaderFilter.Content.ALL, hideReposts, hideReplies, order != BlueskyReaderFilter.Order.FEED).count { it }

internal val BlueskyReaderFilter.Order.label: PluginText
    get() = when (this) {
        BlueskyReaderFilter.Order.FEED -> Strings.order_feed
        BlueskyReaderFilter.Order.NEWEST -> Strings.order_newest
        BlueskyReaderFilter.Order.OLDEST -> Strings.order_oldest
    }

private val BlueskyReaderFilter.Content.label: PluginText
    get() = when (this) {
        BlueskyReaderFilter.Content.ALL -> Strings.content_all
        BlueskyReaderFilter.Content.IMAGES -> Strings.content_photos
        BlueskyReaderFilter.Content.VIDEOS -> Strings.content_videos
        BlueskyReaderFilter.Content.LINKS -> Strings.content_links
    }

/**
 * Filters in the tab's bar, as XTA's dock has it (PluginDockFilterButton):
 * a tune with how many are set, opening the sheet. They apply to what is loaded only.
 */
@Composable
internal fun FiltersButton(filter: BlueskyReaderFilter, onChange: (BlueskyReaderFilter) -> Unit) {
    var sheet by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { sheet = true }) {
        val set = filter.setCount()
        BadgedBox(badge = { if (set > 0) Badge { Text("$set") } }) { Icon(PostIcons.Tune, contentDescription = pluginString(Strings.filters)) }
    }
    if (sheet) FiltersSheet(filter, onChange) { sheet = false }
}

/** The filters themselves: the order, words to find, what kind of post, reposts and replies, and a reset. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FiltersSheet(filter: BlueskyReaderFilter, onChange: (BlueskyReaderFilter) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(pluginString(Strings.filters), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, pluginString(Strings.close)) }
            }
            NoteText(pluginString(Strings.loaded_controls))
            Text(pluginString(Strings.sort_posts), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            ChipRow(BlueskyReaderFilter.Order.entries, filter.order, { it.label }) { onChange(filter.copy(order = it)) }
            OutlinedTextField(
                value = filter.query,
                onValueChange = { onChange(filter.copy(query = it)) },
                singleLine = true,
                label = { Text(pluginString(Strings.loaded_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (filter.query.isNotEmpty()) IconButton(onClick = { onChange(filter.copy(query = "")) }) { Icon(Icons.Filled.Clear, pluginString(Strings.clear_search)) }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
            ChipRow(BlueskyReaderFilter.Content.entries, filter.content, { it.label }) { onChange(filter.copy(content = it)) }
            SwitchLine(pluginString(Strings.hide_reposts), filter.hideReposts) { onChange(filter.copy(hideReposts = it)) }
            SwitchLine(pluginString(Strings.hide_replies), filter.hideReplies) { onChange(filter.copy(hideReplies = it)) }
            TextButton(onClick = { onChange(BlueskyReaderFilter()) }) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(pluginString(Strings.reset_filters))
            }
        }
    }
}

@Composable
internal fun <T> ChipRow(choices: List<T>, chosen: T, label: (T) -> PluginText, onChoose: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { choice ->
            FilterChip(selected = choice == chosen, onClick = { onChoose(choice) }, label = { Text(pluginString(label(choice)), maxLines = 1) })
        }
    }
}

/** A switch with its label, the whole line a target. */
@Composable
internal fun SwitchLine(label: String, checked: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    Row(modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
