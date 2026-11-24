package com.aimdi.xtanative.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.common.IconTip
import com.aimdi.xtanative.ui.common.TipText
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.discover_clear_recent_searches
import com.aimdi.xtanative.ui.generated.resources.discover_delete
import com.aimdi.xtanative.ui.generated.resources.discover_recent_searches
import org.jetbrains.compose.resources.stringResource

/**
 * XTA's recent searches bar: a chip per search, newest first, searched again
 * with a tap or taken away with its cross, and a button clearing them all.
 * Nothing shows while there are none. The one [highlighted] from the
 * search field's keys ([pickedFromRecent]) shows selected, scrolled into view.
 */
@Composable
fun RecentSearchesBar(
    queries: List<String>,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Int? = null,
) {
    if (queries.isEmpty()) return
    val row = rememberLazyListState()
    LaunchedEffect(highlighted) { highlighted?.let { row.animateScrollToItem(it) } }
    val recent = stringResource(Res.string.discover_recent_searches)
    val delete = stringResource(Res.string.discover_delete)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LazyRow(
            modifier = Modifier.weight(1f),
            state = row,
            contentPadding = PaddingValues(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(queries, key = { _, query -> query }) { index, query ->
                InputChip(
                    selected = index == highlighted,
                    onClick = { onSelect(query) },
                    label = { TipText(query, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(DiscoverIcons.History, contentDescription = recent, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = delete,
                            modifier = Modifier.size(18.dp).clickable { onRemove(query) },
                        )
                    },
                )
            }
        }
        IconTip(stringResource(Res.string.discover_clear_recent_searches)) { IconButton(onClick = onClear) {
            Icon(DiscoverIcons.DeleteSweep, contentDescription = stringResource(Res.string.discover_clear_recent_searches))
        } }
    }
}
