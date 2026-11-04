package com.aimdi.xtanative.ui.group

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.store.settings.HomePrefs
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.following
import com.aimdi.xtanative.ui.generated.resources.groups_combine_clear
import com.aimdi.xtanative.ui.generated.resources.groups_combine_hint
import com.aimdi.xtanative.ui.generated.resources.groups_combined_count
import com.aimdi.xtanative.ui.generated.resources.groups_create
import com.aimdi.xtanative.ui.generated.resources.groups_groups
import com.aimdi.xtanative.ui.generated.resources.groups_member_count
import com.aimdi.xtanative.ui.generated.resources.groups_none_yet
import com.aimdi.xtanative.ui.generated.resources.groups_switch_group
import com.aimdi.xtanative.ui.generated.resources.home_x
import com.aimdi.xtanative.ui.home.HomeNetworks
import com.aimdi.xtanative.ui.home.HomeStrip
import com.aimdi.xtanative.ui.home.HomeTab
import com.aimdi.xtanative.ui.home.LocalHomeNetworks
import com.aimdi.xtanative.ui.home.StripNetwork
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import com.aimdi.xtanative.ui.subscriptions.GroupLook
import com.aimdi.xtanative.ui.subscriptions.GroupMark
import com.aimdi.xtanative.ui.subscriptions.groupColor
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A group feed's title (group_feed_title.dart, group_switcher.dart): the
 * group's mark, its name and how many accounts the feed reads, with how many
 * groups are read alongside, as a button that opens the switcher; without
 * [onClick], a page of Home's bar, it is the title alone.
 */
@Composable
fun GroupFeedTitle(state: GroupFeedUi, onClick: (() -> Unit)?) {
    val group = state.group
    val switchLabel = stringResource(Res.string.groups_switch_group)
    Row(
        Modifier.clip(RoundedCornerShape(50)).let { if (onClick != null) it.clickable(onClickLabel = switchLabel, onClick = onClick) else it }.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (group != null) {
            GroupMark(group, 32.dp)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f, fill = false)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(group?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                if (state.alsoRead.isNotEmpty()) {
                    Text("+${state.alsoRead.size}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 6.dp))
                }
                if (onClick != null) Icon(GroupIcons.ExpandMore, contentDescription = null, modifier = Modifier.padding(start = 2.dp).size(20.dp))
            }
            // An empty line while the group loads keeps the name from shifting when the count arrives.
            Text(
                if (state.loaded) pluralStringResource(Res.plurals.groups_member_count, state.memberCount, state.memberCount) else "",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The groups to hop to (group_switcher.dart), in the board's order, the one
 * open ticked. A tap opens another in place; holding one reads it alongside
 * this one instead, for as long as the app runs.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GroupSwitcherSheet(
    state: GroupFeedUi,
    onSwitch: (String) -> Unit,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val currentId = state.group?.id
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        if (state.groups.isEmpty()) {
            Text(stringResource(Res.string.groups_none_yet), modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp))
            return@ModalBottomSheet
        }
        LazyColumn(Modifier.padding(bottom = 16.dp)) {
            item(key = "hint") { CombineHint(state.alsoRead.size, onClear) }
            items(state.groups, key = { it.id }) { group ->
                SwitcherRow(group, current = group.id == currentId, combined = group.id in state.alsoRead, onClick = { onSwitch(group.id) }, onLongClick = {
                    if (group.id != currentId) onToggle(group.id)
                })
            }
            item(key = "create") {
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onCreate).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(Res.string.groups_create), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
    }
}

/** How to read groups together, and how many are. */
@Composable
private fun CombineHint(count: Int, onClear: () -> Unit) {
    if (count == 0) {
        Text(
            stringResource(Res.string.groups_combine_hint),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
        )
        return
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(GroupIcons.PlaylistAddCheck, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(Res.string.groups_combined_count, count), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp).weight(1f))
        TextButton(onClick = onClear) { Text(stringResource(Res.string.groups_combine_clear)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwitcherRow(group: FollowGroup, current: Boolean, combined: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    // Groups read together wear their own colour: which ones are combined is the thing worth seeing.
    val tint = if (current || combined) groupColor(GroupLook.seedColor(group)) else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(if (current || combined) tint.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GroupMark(group, 36.dp)
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(group.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = tint)
            Text(
                pluralStringResource(Res.plurals.groups_member_count, group.memberIds.size, group.memberIds.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            current -> Icon(Icons.Filled.Check, contentDescription = null, tint = tint)
            combined -> Icon(GroupIcons.PlaylistAddCheck, contentDescription = null, tint = tint)
            group.pinned -> Icon(GroupIcons.PushPin, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * The menu hanging from a group's title (feed_switcher_menu.dart): Home's
 * feeds (Following, For you and the networks on the strip), which [onFeed]
 * takes the reader back to Home on, the pinned groups but this one, and
 * Groups, the full switcher.
 */
@Composable
fun FeedSwitcherMenu(
    expanded: Boolean,
    state: GroupFeedUi,
    onFeed: ((String) -> Unit)?,
    onGroup: (String) -> Unit,
    onAllGroups: () -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (onFeed != null) {
            HomeFeedItems { key ->
                onDismiss()
                onFeed(key)
            }
            HorizontalDivider()
        }
        FeedSwitcher.pinned(state.groups, state.group?.id).forEach { group ->
            DropdownMenuItem(
                text = { Text(group.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { GroupMark(group, 24.dp) },
                onClick = { onGroup(group.id) },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.groups_groups)) },
            leadingIcon = { Icon(GroupIcons.FolderOutlined, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingIcon = { Icon(GroupIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            onClick = onAllGroups,
        )
    }
}

/** Following, For you and the networks pinned on Home's strip, as the menu's rows; [onPick] gets the tab's key. */
@Composable
private fun HomeFeedItems(onPick: (String) -> Unit) {
    val graph = LocalGraph.current
    val networks = LocalHomeNetworks.current
    val available by networks.available.collectAsStateWithLifecycle()
    val pins by graph.settings.observe(HomePrefs.feedStripNetworks).collectAsStateWithLifecycle(emptyList())
    val usable = available.mapTo(HashSet()) { it.id }
    HomeStrip.tabs(pins, usable::contains, emptyList()).forEach { tab ->
        DropdownMenuItem(
            text = { Text(feedLabel(tab, available), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { FeedIcon(tab, networks) },
            onClick = { onPick(tab.key) },
        )
    }
}

@Composable
private fun feedLabel(tab: HomeTab, available: List<StripNetwork>): String = when (tab) {
    HomeTab.Following -> stringResource(Res.string.following)
    HomeTab.ForYou -> stringResource(Res.string.home_x)
    is HomeTab.Network -> available.firstOrNull { it.id == tab.id }?.title ?: tab.id
    is HomeTab.Group -> tab.label
}

@Composable
private fun FeedIcon(tab: HomeTab, networks: HomeNetworks) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    when (tab) {
        HomeTab.Following -> Icon(GroupIcons.PeopleOutlined, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        HomeTab.ForYou -> Icon(GroupIcons.AutoAwesomeOutlined, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        is HomeTab.Network -> networks.Mark(tab.id, 20.dp)
        is HomeTab.Group -> Unit
    }
}
