package com.aimdi.xtanative.ui.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimdi.xtanative.store.settings.GroupsLayout
import com.aimdi.xtanative.store.settings.GroupsOrder
import com.aimdi.xtanative.store.settings.SubscriptionsOrder
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.groups_antennas
import com.aimdi.xtanative.ui.generated.resources.groups_columns
import com.aimdi.xtanative.ui.generated.resources.groups_compact
import com.aimdi.xtanative.ui.generated.resources.groups_custom
import com.aimdi.xtanative.ui.generated.resources.groups_date_created
import com.aimdi.xtanative.ui.generated.resources.groups_date_subscribed
import com.aimdi.xtanative.ui.generated.resources.groups_deck
import com.aimdi.xtanative.ui.generated.resources.groups_find_broken
import com.aimdi.xtanative.ui.generated.resources.groups_groups
import com.aimdi.xtanative.ui.generated.resources.groups_import_list
import com.aimdi.xtanative.ui.generated.resources.groups_layout_board
import com.aimdi.xtanative.ui.generated.resources.groups_layout_list
import com.aimdi.xtanative.ui.generated.resources.groups_name
import com.aimdi.xtanative.ui.generated.resources.groups_pack_import
import com.aimdi.xtanative.ui.generated.resources.groups_reverse_order
import com.aimdi.xtanative.ui.generated.resources.groups_settings
import com.aimdi.xtanative.ui.generated.resources.groups_sort_ungrouped
import com.aimdi.xtanative.ui.generated.resources.groups_subscriptions
import com.aimdi.xtanative.ui.generated.resources.groups_username
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Where the menu's actions lead; a destination this build has no screen for yet is null and left out. */
class SubscriptionsMenuActions(
    val importPack: () -> Unit,
    val sortUngrouped: () -> Unit,
    val importList: () -> Unit,
    val findBroken: () -> Unit,
    val antennas: (() -> Unit)?,
    val deck: (() -> Unit)?,
    val settings: (() -> Unit)?,
)

/**
 * The tab's menu (subscriptions_menu_sheet.dart): how the page open is laid
 * out and ordered, and the rarer actions, grouped so the current choice shows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsMenuSheet(state: SubscriptionsUi, vm: SubscriptionsViewModel, actions: SubscriptionsMenuActions, onDismiss: () -> Unit) {
    val groups = state.tab == SubscriptionsTab.GROUPS
    fun run(action: () -> Unit) {
        onDismiss()
        action()
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
            Text(
                stringResource(if (groups) Res.string.groups_groups else Res.string.groups_subscriptions),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
            )
            if (groups) {
                LayoutRow(state.layout, vm::layOut)
                if (state.layout != GroupsLayout.LIST) ColumnsRow(state.columns, state.compact, vm::columns)
                SheetDivider()
                SortChoice(GroupIcons.SortByAlpha, Res.string.groups_name, state.groupsOrder == GroupsOrder.NAME) { vm.orderGroupsBy(GroupsOrder.NAME) }
                SortChoice(GroupIcons.ScheduleOutlined, Res.string.groups_date_created, state.groupsOrder == GroupsOrder.CREATED_AT) {
                    vm.orderGroupsBy(GroupsOrder.CREATED_AT)
                }
                SortChoice(GroupIcons.Tune, Res.string.groups_custom, state.groupsOrder == GroupsOrder.POSITION) { vm.orderGroupsBy(GroupsOrder.POSITION) }
            } else {
                SortChoice(GroupIcons.SortByAlpha, Res.string.groups_name, state.peopleOrder == SubscriptionsOrder.NAME) {
                    vm.orderPeopleBy(SubscriptionsOrder.NAME)
                }
                SortChoice(GroupIcons.AlternateEmail, Res.string.groups_username, state.peopleOrder == SubscriptionsOrder.SCREEN_NAME) {
                    vm.orderPeopleBy(SubscriptionsOrder.SCREEN_NAME)
                }
                SortChoice(GroupIcons.ScheduleOutlined, Res.string.groups_date_subscribed, state.peopleOrder == SubscriptionsOrder.CREATED_AT) {
                    vm.orderPeopleBy(SubscriptionsOrder.CREATED_AT)
                }
            }
            SwitchRow(GroupIcons.SwapVert, Res.string.groups_reverse_order, !(if (groups) state.groupsAscending else state.peopleAscending)) {
                vm.reverseOrder(groups)
            }
            SheetDivider()
            if (groups) {
                ActionRow(GroupIcons.FileDownloadOutlined, Res.string.groups_pack_import) { run(actions.importPack) }
                ActionRow(GroupIcons.AutoAwesomeOutlined, Res.string.groups_sort_ungrouped) { run(actions.sortUngrouped) }
                ActionRow(GroupIcons.ListAltOutlined, Res.string.groups_import_list) { run(actions.importList) }
            } else {
                ActionRow(GroupIcons.AutoAwesomeOutlined, Res.string.groups_sort_ungrouped) { run(actions.sortUngrouped) }
                ActionRow(GroupIcons.LinkOff, Res.string.groups_find_broken) { run(actions.findBroken) }
            }
            val antennas = actions.antennas
            val deck = actions.deck.takeIf { groups }
            val settings = actions.settings
            if (antennas != null || deck != null || settings != null) SheetDivider()
            if (antennas != null) ActionRow(GroupIcons.SensorsOutlined, Res.string.groups_antennas) { run(antennas) }
            if (deck != null) ActionRow(GroupIcons.ViewColumnOutlined, Res.string.groups_deck) { run(deck) }
            if (settings != null) ActionRow(GroupIcons.SettingsOutlined, Res.string.groups_settings) { run(settings) }
        }
    }
}

@Composable
private fun LayoutRow(layout: GroupsLayout, onPick: (GroupsLayout) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ModeButton(GroupIcons.GridView, Res.string.groups_layout_board, layout != GroupsLayout.LIST, Modifier.weight(1f)) { onPick(GroupsLayout.BOARD) }
        ModeButton(GroupIcons.ViewAgendaOutlined, Res.string.groups_layout_list, layout == GroupsLayout.LIST, Modifier.weight(1f)) { onPick(GroupsLayout.LIST) }
    }
}

@Composable
private fun ModeButton(icon: ImageVector, label: StringResource, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val tint = if (selected) accent else MaterialTheme.colorScheme.onSurface
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(if (selected) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(
            stringResource(label),
            color = tint,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun ColumnsRow(columns: Int, compact: Boolean, onPick: (Int?, Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.groups_columns), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        CountChip("2", !compact && columns != 3) { onPick(2, false) }
        CountChip("3", !compact && columns == 3, Modifier.padding(start = 8.dp)) { onPick(3, false) }
        CountChip(stringResource(Res.string.groups_compact), compact, Modifier.padding(start = 8.dp)) { onPick(null, true) }
    }
}

@Composable
private fun CountChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clip(CircleShape)
            .background(if (selected) accent else MaterialTheme.colorScheme.surfaceContainerHigh)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = if (label.length > 2) 16.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun SortChoice(icon: ImageVector, label: StringResource, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(
            stringResource(label),
            color = tint,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(start = 16.dp).weight(1f),
        )
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = tint)
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, label: StringResource, value: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = value, role = Role.Switch) { onToggle() }.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Text(stringResource(label), modifier = Modifier.padding(start = 16.dp).weight(1f))
        Switch(checked = value, onCheckedChange = null)
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: StringResource, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Text(stringResource(label), modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun SheetDivider() {
    HorizontalDivider(Modifier.padding(vertical = 10.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}
