package com.aimdi.xtanative.ui.plugins

import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.aimdi.xtanative.ui.search.DiscoverIcons

/**
 * One of a plugin's recent searches as XTA's plugin searches list them on
 * their landing ([PluginSearch.Landing], hn_search_sheet.dart): the history
 * mark and the query, a tap searching it again.
 */
@Composable
fun PluginRecentSearchRow(query: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        leadingContent = { Icon(DiscoverIcons.History, contentDescription = null) },
        headlineContent = { Text(query, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.clickable(onClick = onClick),
    )
}
