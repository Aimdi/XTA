package com.aimdi.xtanative.plugins.mastodon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.mastodon.MastodonInstances
import com.aimdi.xtanative.networks.mastodon.MastodonTimeline
import com.aimdi.xtanative.networks.mastodon.MastodonTimelineFilter
import com.aimdi.xtanative.networks.mastodon.MastodonTimelineFilter.Content
import com.aimdi.xtanative.networks.mastodon.MastodonTimelineFilter.Order
import com.aimdi.xtanative.ui.plugins.PluginSwitchRow
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.post.PostIcons

internal fun orderText(order: Order): PluginText = when (order) {
    Order.FEED -> Strings.order_server
    Order.NEWEST -> Strings.order_newest
    Order.OLDEST -> Strings.order_oldest
}

private fun contentText(content: Content): PluginText = when (content) {
    Content.ALL -> Strings.content_all
    Content.MEDIA -> Strings.content_media
    Content.LINKS -> Strings.content_links
}

/** How many of [filter]'s controls are set, the order among them, as XTA's dock counts them on its Filters. */
internal fun filtersSet(filter: MastodonTimelineFilter): Int = filter.activeFilters + if (filter.order == Order.FEED) 0 else 1

/** Where the timeline's sheet names the server it reads: Local's and Federated's instance, as XTA's dock labels them. */
internal fun sourceOf(timeline: MastodonTimeline, instance: String?): String? =
    instance?.takeIf { timeline == MastodonTimeline.LOCAL || timeline == MastodonTimeline.FEDERATED }?.let(MastodonInstances::domain)

/**
 * [timeline]'s Filters in the reader's bar, as XTA's dock has them
 * (PluginDockFilterButton): a tune with how many are set, opening the sheet,
 * which names the instance that served Local and Federated.
 */
@Composable
internal fun FiltersButton(app: MastodonApp, timeline: MastodonTimeline) {
    val prefs by app.prefs.collectAsState()
    val searched by app.searched.collectAsState()
    val state by app.reader(timeline).state.collectAsState()
    val filter = app.filter(timeline, prefs, searched)
    var sheet by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { sheet = true }) {
        val set = filtersSet(filter)
        BadgedBox(badge = { if (set > 0) Badge { Text("$set") } }) { Icon(PostIcons.Tune, contentDescription = pluginString(Strings.filters)) }
    }
    if (sheet) FilterSheet(filter, sourceOf(timeline, state.instance), { app.setFilter(timeline, it) }) { sheet = false }
}

/** XTA's Filters sheet: the [source] read, the order, a search of the loaded posts, what they hold, boosts and replies, and Reset filters. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(filter: MastodonTimelineFilter, source: String?, onChange: (MastodonTimelineFilter) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(pluginString(Strings.filters), style = MaterialTheme.typography.titleLarge)
                    if (source != null) {
                        Text(source, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, pluginString(Strings.close)) }
            }
            Text(pluginString(Strings.loaded_controls), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            Text(pluginString(Strings.sort), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Order.entries.forEach { order ->
                    FilterChip(selected = filter.order == order, onClick = { onChange(filter.copy(order = order)) }, label = { Text(pluginString(orderText(order))) })
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = filter.query,
                onValueChange = { onChange(filter.copy(query = it)) },
                label = { Text(pluginString(Strings.loaded_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (filter.query.isNotEmpty()) IconButton(onClick = { onChange(filter.copy(query = "")) }) { Icon(Icons.Filled.Clear, pluginString(Strings.clear_search)) }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Content.entries.forEach { content ->
                    FilterChip(selected = filter.content == content, onClick = { onChange(filter.copy(content = content)) }, label = { Text(pluginString(contentText(content))) })
                }
            }
            PluginSwitchRow(pluginString(Strings.hide_boosts), filter.hideBoosts, { onChange(filter.copy(hideBoosts = it)) })
            PluginSwitchRow(pluginString(Strings.hide_replies), filter.hideReplies, { onChange(filter.copy(hideReplies = it)) })
            TextButton(onClick = { onChange(MastodonTimelineFilter()) }) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(pluginString(Strings.reset_filters))
            }
        }
    }
}
