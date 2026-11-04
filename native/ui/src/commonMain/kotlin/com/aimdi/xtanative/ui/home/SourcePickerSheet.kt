package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.close
import com.aimdi.xtanative.ui.generated.resources.home_add_timeline
import com.aimdi.xtanative.ui.generated.resources.home_groups
import com.aimdi.xtanative.ui.generated.resources.home_home
import com.aimdi.xtanative.ui.generated.resources.home_no_results
import com.aimdi.xtanative.ui.generated.resources.home_open_menu
import com.aimdi.xtanative.ui.generated.resources.home_unread
import com.aimdi.xtanative.ui.generated.resources.search
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import com.aimdi.xtanative.ui.subscriptions.GroupMark
import org.jetbrains.compose.resources.stringResource

/** What the timeline picker leads to. */
internal class PickerCalls(
    val onSource: (HomeTab) -> Unit,
    val onGroup: (FollowGroup) -> Unit,
    val onAdd: () -> Unit,
    val onMenu: () -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * The timeline picker (home_timeline_picker.dart, home_source_picker.dart),
 * Following's source button and a hold of Home on the bar: Following, For
 * you and the networks on the strip, the one shown ticked, each with its
 * unread dot, then the groups; a search once there are many; Add timeline,
 * and the way to the account menu, which Following's bar has no mark for.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SourcePickerSheet(ui: HomeUi, networks: HomeNetworks, shownKey: String?, unread: Set<String>, calls: PickerCalls) {
    var query by rememberSaveable { mutableStateOf("") }
    val sources = SourcePicker.sources(ui.tabs)
    val groups = DrawerGroups.matching(ui.session.groups, "")
    val labels = sources.associateWith { tabLabel(it, ui) }
    val shownSources = sources.filter { SourcePicker.matches(labels.getValue(it), query) }
    val shownGroups = groups.filter { SourcePicker.matches(it.name, query) }
    ModalBottomSheet(onDismissRequest = calls.onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(bottom = 8.dp)) {
            PickerTitle(calls.onDismiss)
            if (SourcePicker.hasSearch(sources.size + groups.size, query)) PickerSearch(query) { query = it }
            if (shownSources.isEmpty() && shownGroups.isEmpty()) {
                Text(stringResource(Res.string.home_no_results), modifier = Modifier.padding(16.dp))
            }
            LazyColumn(Modifier.weight(1f, fill = false).padding(horizontal = 12.dp)) {
                items(shownSources, key = { it.key }) { tab ->
                    PickerRow(labels.getValue(tab), tab.key in unread, selected = tab.key == shownKey, onClick = { calls.onSource(tab) }) { SourceMark(tab, networks) }
                }
                if (shownGroups.isNotEmpty()) {
                    item(key = "groups") {
                        Text(stringResource(Res.string.home_groups), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 8.dp))
                    }
                    items(shownGroups, key = { "group-" + it.id }) { group ->
                        PickerRow(group.name, HomeTab.Group(group.id, group.name).key in unread, selected = false, onClick = { calls.onGroup(group) }) { GroupMark(group, 24.dp) }
                    }
                }
            }
            OutlinedButton(onClick = calls.onAdd, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.home_add_timeline))
            }
            TextButton(onClick = calls.onMenu, modifier = Modifier.padding(horizontal = 16.dp)) {
                Icon(Icons.Filled.Menu, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.home_open_menu))
            }
        }
    }
}

@Composable
private fun PickerTitle(onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.home_home), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(Res.string.close)) }
    }
}

@Composable
private fun PickerSearch(query: String, onQuery: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        label = { Text(stringResource(Res.string.search)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = if (query.isEmpty()) null else ({ IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Clear, stringResource(Res.string.close)) } }),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    )
}

/** One timeline or group: its mark, its name, its unread dot, and a tick on the one shown. */
@Composable
private fun PickerRow(label: String, unread: Boolean, selected: Boolean, onClick: () -> Unit, mark: @Composable () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val unreadLabel = stringResource(Res.string.home_unread)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { this.selected = selected }
            .heightIn(min = 52.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { mark() }
        Text(
            label,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        if (unread) Box(Modifier.size(8.dp).clip(CircleShape).background(accent).semantics { contentDescription = unreadLabel })
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = accent, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun SourceMark(tab: HomeTab, networks: HomeNetworks) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    when (tab) {
        HomeTab.Following -> Icon(HomeIcons.DynamicFeed, contentDescription = null, tint = tint)
        HomeTab.ForYou -> Icon(GroupIcons.AutoAwesomeOutlined, contentDescription = null, tint = tint)
        is HomeTab.Network -> networks.Mark(tab.id, 24.dp)
        is HomeTab.Group -> Unit
    }
}
