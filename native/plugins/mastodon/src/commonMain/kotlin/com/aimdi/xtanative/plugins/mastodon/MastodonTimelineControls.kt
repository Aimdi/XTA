package com.aimdi.xtanative.plugins.mastodon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.mastodon.MastodonInstances
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

/**
 * Above a timeline, as XTA's MastodonTimelineToolbar: the instance that
 * served it, where it says, then Filters (with how many are on) and the
 * order, both applied to what is loaded.
 */
@Composable
internal fun TimelineToolbar(filter: MastodonTimelineFilter, instance: String?, onChange: (MastodonTimelineFilter) -> Unit) {
    var sheet by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        val source = instance?.let(MastodonInstances::domain)
        Text(
            source.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        TextButton(onClick = { sheet = true }) {
            BadgedBox(badge = { if (filter.activeFilters > 0) Badge { Text("${filter.activeFilters}") } }) {
                Icon(PostIcons.Tune, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(pluginString(Strings.filters))
        }
        OrderMenu(filter.order) { onChange(filter.copy(order = it)) }
    }
    if (sheet) FilterSheet(filter, onChange) { sheet = false }
}

@Composable
private fun OrderMenu(order: Order, onSelect: (Order) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text(pluginString(orderText(order)), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(4.dp))
            Icon(MastodonIcons.Sort, contentDescription = pluginString(Strings.sort), modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Order.entries.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(pluginString(orderText(choice))) },
                    onClick = {
                        open = false
                        onSelect(choice)
                    },
                    trailingIcon = { if (choice == order) Icon(Icons.Filled.Check, contentDescription = null) },
                )
            }
        }
    }
}

/** XTA's Filters sheet: the order, a search of the loaded posts, what they hold, boosts and replies, and Reset filters. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(filter: MastodonTimelineFilter, onChange: (MastodonTimelineFilter) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(pluginString(Strings.filters), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
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
