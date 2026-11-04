package com.aimdi.xtanative.plugins.booru

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.booru.BooruPost
import com.aimdi.xtanative.networks.booru.BooruTagCategory
import com.aimdi.xtanative.networks.booru.BooruTagGroup
import com.aimdi.xtanative.networks.booru.BooruTagGroups
import com.aimdi.xtanative.networks.booru.BooruTagInfo
import com.aimdi.xtanative.networks.booru.BooruTagSort
import com.aimdi.xtanative.store.settings.BooruPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.plugins.pluginPlural
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.subscriptions.GroupIcons

/** A tag of a post, opened for its actions. */
internal data class OpenedTag(val tag: String, val category: BooruTagCategory?)

/**
 * A post's tags grouped by kind as tinted chips with their post counts
 * (booru_tag_list.dart), followed ones checked and hidden ones marked; sorted
 * by name, or by use once the host sent counts. A tap searches the tag (an
 * artist's, character's or series' opens its page); a long press offers the rest.
 */
@Composable
internal fun TagSection(app: BooruApp, viewer: ViewerState, post: BooruPost, info: Map<String, BooruTagInfo>, settings: Settings) {
    val counted = info.values.any { it.postCount != null }
    val sort = if (counted) BooruConfig.tagSort(settings) else BooruTagSort.NAME
    val groups = remember(post.tags, info, sort) { BooruTagGroups.of(post.tags, info, sort) }
    if (groups.isEmpty()) return
    val followed = app.follows.state.collectAsState().value.orEmpty().toSet()
    val muted = BooruConfig.muted(settings)
    var opened by remember(post.key) { mutableStateOf<OpenedTag?>(null) }
    val site = app.siteOf(post)
    Column(Modifier.fillMaxWidth()) {
        TagsHeader(sort, counted) { next -> app.update { it.with(BooruPrefs.tagSort, BooruConfig.stored(next)) } }
        groups.forEach { group ->
            TagGroup(group, info, followed, muted, onTap = { tag -> app.openTag(tag, group.category, site) }, onHold = { tag -> opened = OpenedTag(tag, group.category) })
        }
    }
    opened?.let { tag -> TagActions(app, tag, viewer.search, site, onDismiss = { opened = null }) }
}

@Composable
private fun TagsHeader(sort: BooruTagSort, counted: Boolean, onSort: (BooruTagSort) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(pluginString(Strings.tags), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (counted) {
            val next = if (sort == BooruTagSort.NAME) BooruTagSort.COUNT else BooruTagSort.NAME
            TextButton(onClick = { onSort(next) }) {
                Icon(GroupIcons.SwapVert, pluginString(Strings.tag_sort), modifier = Modifier.size(18.dp))
                Text(pluginString(if (sort == BooruTagSort.NAME) Strings.tag_sort_name else Strings.tag_sort_count), Modifier.padding(start = 8.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagGroup(
    group: BooruTagGroup,
    info: Map<String, BooruTagInfo>,
    followed: Set<String>,
    muted: Set<String>,
    onTap: (String) -> Unit,
    onHold: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp)) {
        if (group.category != null) {
            Text(
                kindLabel(group.category),
                style = MaterialTheme.typography.titleSmall,
                color = tagColor(group.category) ?: MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            group.tags.forEach { tag -> PostTagChip(tag, group.category, info[tag]?.postCount, tag in followed, tag in muted, onTap, onHold) }
        }
    }
}

@Composable
private fun PostTagChip(tag: String, category: BooruTagCategory?, count: Int?, followed: Boolean, muted: Boolean, onTap: (String) -> Unit, onHold: (String) -> Unit) {
    val name = BooruFormats.tagName(tag)
    val locale = readerLocale()
    TagChip(
        label = name,
        category = category,
        onClick = { onTap(tag) },
        detail = count?.let { BooruFormats.compactCount(it.toLong(), locale) },
        marker = when {
            followed -> Icons.Filled.Check
            muted -> GroupIcons.VisibilityOffOutlined
            else -> null
        },
        description = count?.let { "$name, " + pluginPlural(Strings.tag_count, it, it) } ?: name,
        onLongClick = { onHold(tag) },
        longClickLabel = pluginString(Strings.tag_long_press_hint),
    )
}
