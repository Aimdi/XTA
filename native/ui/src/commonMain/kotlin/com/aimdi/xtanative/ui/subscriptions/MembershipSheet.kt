package com.aimdi.xtanative.ui.subscriptions

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.groups_add_to_group
import com.aimdi.xtanative.ui.generated.resources.groups_none_yet
import com.aimdi.xtanative.ui.generated.resources.groups_sheet_all
import com.aimdi.xtanative.ui.generated.resources.groups_sheet_create
import com.aimdi.xtanative.ui.generated.resources.groups_sheet_in_many
import com.aimdi.xtanative.ui.generated.resources.groups_sheet_in_none
import com.aimdi.xtanative.ui.generated.resources.groups_sheet_in_one
import com.aimdi.xtanative.ui.generated.resources.groups_sheet_in_two
import com.aimdi.xtanative.ui.generated.resources.groups_sheet_matches
import com.aimdi.xtanative.ui.generated.resources.groups_sheet_no_match
import com.aimdi.xtanative.ui.generated.resources.groups_sheet_recent
import com.aimdi.xtanative.ui.generated.resources.groups_sheet_search_or_create
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Which groups an account is in (group_membership_sheet.dart): the groups as
 * chips that tick with a tap, a search that can make a group, and a line that
 * says where the account is. The choice applies as the sheet closes, however
 * it is closed: [onDone] hears the groups chosen, or null when they are the
 * ones it opened with. [create] makes a group and answers its id.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GroupMembershipSheet(
    groups: List<FollowGroup>,
    selected: Set<String>,
    recent: List<String>,
    create: suspend (String) -> String,
    onDone: (Set<String>?) -> Unit,
) {
    val openOrder = remember { GroupPicker.openOrder(groups, selected, recent) }
    val shown = remember { GroupPicker.shownFor(selected) }
    var chosen by remember { mutableStateOf(selected) }
    var view by remember { mutableStateOf(PickView()) }
    val scope = rememberCoroutineScope()
    val ordered = GroupPicker.ordered(groups, openOrder, view.createdIds)
    val chips = GroupPicker.chips(ordered, view, shown)

    ModalBottomSheet(onDismissRequest = { onDone(GroupPicker.answer(selected, chosen)) }, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.imePadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.groups_add_to_group), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (chips.hasMore) {
                    TextButton(onClick = { view = view.showingAll() }) {
                        Text(stringResource(Res.string.groups_sheet_all))
                        Icon(GroupIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
            }
            TextField(
                value = view.query,
                onValueChange = { view = view.searching(it) },
                singleLine = true,
                placeholder = { Text(stringResource(Res.string.groups_sheet_search_or_create)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            )
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                sectionLabel(groups.isEmpty(), chips)?.let { label ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 8.dp),
                    )
                }
                if (chips.canCreate) {
                    CreateRow(stringResource(Res.string.groups_sheet_create, view.query.trim())) {
                        val name = view.query.trim()
                        scope.launch {
                            val id = create(name)
                            view = view.created(id)
                            chosen = chosen + id
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    chips.groups.forEach { group ->
                        FilterChip(
                            selected = group.id in chosen,
                            onClick = { chosen = GroupPicker.toggled(chosen, group.id) },
                            label = { Text(group.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { GroupMark(group, 24.dp) },
                        )
                    }
                }
                Spacer(Modifier.size(8.dp))
            }
            StatusLine(GroupPicker.status(ordered, chosen))
        }
    }
}

@Composable
private fun sectionLabel(noGroups: Boolean, chips: PickChips): String? = when {
    noGroups && chips.section == PickSection.RECENT -> stringResource(Res.string.groups_none_yet)
    chips.groups.isEmpty() && chips.section != PickSection.NO_MATCH -> null
    else -> when (chips.section) {
        PickSection.RECENT -> stringResource(Res.string.groups_sheet_recent)
        PickSection.ALL -> stringResource(Res.string.groups_sheet_all)
        PickSection.MATCHES -> stringResource(Res.string.groups_sheet_matches)
        PickSection.NO_MATCH -> stringResource(Res.string.groups_sheet_no_match)
    }
}

/** "Create “Japan” and add", for a typed name no group has yet. */
@Composable
private fun CreateRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StatusLine(status: PickStatus) {
    val text = when (status) {
        PickStatus.None -> stringResource(Res.string.groups_sheet_in_none)
        is PickStatus.One -> stringResource(Res.string.groups_sheet_in_one, status.name)
        is PickStatus.Two -> stringResource(Res.string.groups_sheet_in_two, status.first, status.second)
        is PickStatus.Many -> pluralStringResource(Res.plurals.groups_sheet_in_many, status.count, status.count)
    }
    val placed = status != PickStatus.None
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (placed) GroupIcons.CheckCircleOutline else GroupIcons.CircleOutlined,
            contentDescription = null,
            tint = if (placed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (placed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
