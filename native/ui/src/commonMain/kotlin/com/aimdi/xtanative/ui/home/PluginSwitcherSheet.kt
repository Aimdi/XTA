package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.ui.common.IconTip
import com.aimdi.xtanative.ui.common.longPressOver
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.home_add_timeline
import com.aimdi.xtanative.ui.generated.resources.home_manage_plugins
import com.aimdi.xtanative.ui.generated.resources.home_open_menu
import com.aimdi.xtanative.ui.generated.resources.home_switch_account
import com.aimdi.xtanative.ui.generated.resources.home_switcher_recent
import com.aimdi.xtanative.ui.generated.resources.home_switcher_title
import com.aimdi.xtanative.ui.generated.resources.home_unread
import com.aimdi.xtanative.ui.generated.resources.home_xta_settings
import com.aimdi.xtanative.ui.generated.resources.settings
import com.aimdi.xtanative.ui.generated.resources.settings_avatar_title
import com.aimdi.xtanative.ui.plugins.ShellSubtitleContent
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import org.jetbrains.compose.resources.stringResource

/** What the switcher sheet offers, decided apart from its sheet. */
internal object PluginSwitcher {
    /** How many networks a row of the grid holds. */
    const val PER_ROW = 4

    /** The networks among Home's [tabs], those in [recent] first in that order, then the rest in Home's. */
    fun networks(tabs: List<HomeTab>, recent: List<String>): List<String> {
        val ids = tabs.filterIsInstance<HomeTab.Network>().map { it.id }
        return recent.filter { it in ids }.distinct() + ids.filterNot { it in recent }
    }

    /** XTA's own timelines among Home's [tabs], which the sheet names as words: Following and X. */
    fun timelines(tabs: List<HomeTab>): List<HomeTab> = tabs.filter { it == HomeTab.Following || it == HomeTab.ForYou }
}

/**
 * The plugin shown, at the top of the sheet: its [subtitle], its settings
 * ([onSettings]), its own entries ([items], [ShellMenuButton]) and the
 * app's for it ([appEntries]: keep controls visible, Home's options for the
 * network, its sections in the menu).
 */
internal class SheetPlugin(
    val id: String,
    val subtitle: ShellSubtitleContent?,
    val items: (@Composable ColumnScope.(close: () -> Unit) -> Unit)?,
    val onSettings: (() -> Unit)?,
    val appEntries: @Composable ColumnScope.(close: () -> Unit) -> Unit,
    val onSwitchAccount: (() -> Unit)? = null,
)

/** Where the switcher's choices lead. */
internal class SwitcherCalls(
    /** Following or X picked. */
    val onTimeline: (HomeTab) -> Unit,
    val onGroup: (FollowGroup) -> Unit,
    val onNetwork: (String) -> Unit,
    /** Add timeline: the sheet that pins networks to Home. */
    val onAdd: () -> Unit,
    /** The account menu, which no bar has a mark for. */
    val onMenu: () -> Unit,
    val onManage: () -> Unit,
    val onSettings: () -> Unit,
    val onDismiss: () -> Unit,
    /** A long press of the way to the menu: the sheet that sets the reader's mark. */
    val onAvatar: () -> Unit = {},
)

/**
 * Home's one sheet, which every timeline's mark opens and a hold of Home on
 * the bar: the timeline [shown] as a profile's head is drawn (its mark, its
 * name and subtitle; for a [plugin], its settings, its account switch and
 * its own entries), the way to the account menu, XTA's timelines and the
 * groups as a row of words with Add timeline at its end, the networks on
 * Home four to a row, the recently used first, the one shown underlined in
 * the accent and each with a dot while it has unread posts; then the plugin
 * store and XTA's settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PluginSwitcherSheet(ui: HomeUi, networks: HomeNetworks, shown: HomeTab?, unread: Set<String>, calls: SwitcherCalls, plugin: SheetPlugin? = null) {
    val ids = remember(ui.tabs, ui.recentNetworks) { PluginSwitcher.networks(ui.tabs, ui.recentNetworks) }
    val title = stringResource(Res.string.home_switcher_title)
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    // Whole at once, and the page's own colour under a hairline, as the app's screens are flat.
    ModalBottomSheet(
        onDismissRequest = calls.onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = shape,
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).semantics { paneTitle = title },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
            SheetHead(shown, ui, networks, plugin)
            Column(Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
                if (plugin != null) {
                    plugin.items?.invoke(this, calls.onDismiss)
                    plugin.appEntries(this, calls.onDismiss)
                }
                MenuRow(calls)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TimelineWords(ui, shown, unread, calls)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (ids.isNotEmpty()) {
                Text(
                    stringResource(Res.string.home_switcher_recent),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
                )
                Column(Modifier.padding(horizontal = 8.dp)) {
                    ids.chunked(PluginSwitcher.PER_ROW).forEach { row ->
                        Row {
                            row.forEach { id ->
                                NetworkTile(id, ui, networks, shown = shown == HomeTab.Network(id), lit = HomeTab.Network(id).key in unread, Modifier.weight(1f)) { calls.onNetwork(id) }
                            }
                            repeat(PluginSwitcher.PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                FootButton(stringResource(Res.string.home_manage_plugins), Modifier.weight(1f), calls.onManage)
                FootButton(stringResource(Res.string.home_xta_settings), Modifier.weight(1f), calls.onSettings)
            }
        }
    }
}

/** The head, as a profile's: the mark in a ringed disc, the plugin's settings and account switch across from it, then the name and subtitle. */
@Composable
private fun SheetHead(shown: HomeTab?, ui: HomeUi, networks: HomeNetworks, plugin: SheetPlugin?) {
    val accent = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(64.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (shown != null) TimelineMark(shown, networks, 30.dp)
        }
        Spacer(Modifier.weight(1f))
        plugin?.onSettings?.let { settings ->
            IconTip(stringResource(Res.string.settings)) { IconButton(onClick = settings) { Icon(GroupIcons.Tune, stringResource(Res.string.settings)) } }
        }
        plugin?.onSwitchAccount?.let { switch ->
            TextButton(onClick = switch, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.home_switch_account), fontWeight = FontWeight.Bold, color = accent, maxLines = 1)
                Icon(GroupIcons.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp).size(18.dp))
            }
        }
    }
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(shown?.let { tabLabel(it, ui) }.orEmpty(), fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        plugin?.subtitle?.let {
            Text(it.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The way to the account menu, a row as the entries above it; a long press sets the reader's mark. */
@Composable
private fun MenuRow(calls: SwitcherCalls) {
    val avatar = stringResource(Res.string.settings_avatar_title)
    DropdownMenuItem(
        text = { Text(stringResource(Res.string.home_open_menu)) },
        leadingIcon = { Icon(Icons.Filled.Menu, contentDescription = null) },
        onClick = calls.onMenu,
        modifier = Modifier.longPressOver { calls.onAvatar() }.semantics { onLongClick(avatar) { calls.onAvatar(); true } },
    )
}

/** XTA's timelines and the groups as a row of words, the one shown in bold, each with a dot while unread; Add timeline stays at its end. */
@Composable
private fun TimelineWords(ui: HomeUi, shown: HomeTab?, unread: Set<String>, calls: SwitcherCalls) {
    val timelines = remember(ui.tabs) { PluginSwitcher.timelines(ui.tabs) }
    val groups = remember(ui.session.groups) { DrawerGroups.matching(ui.session.groups, "") }
    Row(Modifier.padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
            timelines.forEach { tab -> Word(tabLabel(tab, ui), picked = tab == shown, lit = tab.key in unread) { calls.onTimeline(tab) } }
            groups.forEach { group -> Word(group.name, picked = false, lit = HomeTab.Group(group.id, group.name).key in unread) { calls.onGroup(group) } }
        }
        IconTip(stringResource(Res.string.home_add_timeline)) { IconButton(onClick = calls.onAdd) { Icon(Icons.Filled.Add, stringResource(Res.string.home_add_timeline), tint = MaterialTheme.colorScheme.primary) } }
    }
}

@Composable
private fun Word(label: String, picked: Boolean, lit: Boolean, onClick: () -> Unit) {
    val said = listOfNotNull(label, stringResource(Res.string.home_unread).takeIf { lit }).joinToString(", ")
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp)
            .clearAndSetSemantics {
                role = Role.Tab
                selected = picked
                contentDescription = said
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            label,
            fontSize = 15.sp,
            fontWeight = if (picked) FontWeight.Bold else FontWeight.Medium,
            color = if (picked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        if (lit) Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.error, CircleShape))
    }
}

/** One network of the grid: its mark over its name, no box; its name in bold over the accent's line while [shown], a dot on the mark while [lit]. */
@Composable
private fun NetworkTile(id: String, ui: HomeUi, networks: HomeNetworks, shown: Boolean, lit: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val name = ui.networks.firstOrNull { it.id == id }?.title ?: id
    val unread = stringResource(Res.string.home_unread)
    Box(
        modifier
            .height(88.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            // One thing to a screen reader: its name as written, shown or not, and "unread" as its state.
            .semantics(mergeDescendants = true) {
                role = Role.Button
                selected = shown
                if (lit) stateDescription = unread
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                networks.Mark(id, 28.dp)
                if (lit) {
                    Box(Modifier.align(Alignment.TopEnd).size(9.dp).background(MaterialTheme.colorScheme.background, CircleShape).padding(1.5.dp).background(MaterialTheme.colorScheme.error, CircleShape))
                }
            }
            Text(
                name,
                fontSize = 13.sp,
                fontWeight = if (shown) FontWeight.Bold else FontWeight.Normal,
                color = if (shown) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        if (shown) {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).size(width = 26.dp, height = 3.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)))
        }
    }
}

/** A word of the sheet's foot, in the accent. */
@Composable
private fun FootButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp)) {
        Text(label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
