package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.home_manage_plugins
import com.aimdi.xtanative.ui.generated.resources.home_switcher_recent
import com.aimdi.xtanative.ui.generated.resources.home_switcher_start
import com.aimdi.xtanative.ui.generated.resources.home_switcher_start_hint
import com.aimdi.xtanative.ui.generated.resources.home_switcher_title
import com.aimdi.xtanative.ui.generated.resources.home_unread
import com.aimdi.xtanative.ui.generated.resources.settings
import org.jetbrains.compose.resources.stringResource

/** What the plugin switcher offers, decided apart from its sheet. */
internal object PluginSwitcher {
    /** How many networks a row of the grid holds. */
    const val PER_ROW = 3

    /** The networks among Home's [tabs], those in [recent] first in that order, then the rest in Home's. */
    fun networks(tabs: List<HomeTab>, recent: List<String>): List<String> {
        val ids = tabs.filterIsInstance<HomeTab.Network>().map { it.id }
        return recent.filter { it in ids }.distinct() + ids.filterNot { it in recent }
    }
}

/** Where the switcher's choices lead. */
internal class SwitcherCalls(
    val onStart: () -> Unit,
    val onNetwork: (String) -> Unit,
    val onManage: () -> Unit,
    val onSettings: () -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * The plugin switcher, which a network's button opens (the unified plugin
 * shell): Start, back to XTA's own Home (Following, with the app's bar and
 * every timeline); the networks on Home in a grid, the recently used first,
 * the one shown ringed in the accent and each with a dot while it has
 * unread posts; then the plugin store and the settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PluginSwitcherSheet(ui: HomeUi, networks: HomeNetworks, current: String?, unread: Set<String>, calls: SwitcherCalls) {
    val ids = remember(ui.tabs, ui.recentNetworks) { PluginSwitcher.networks(ui.tabs, ui.recentNetworks) }
    val title = stringResource(Res.string.home_switcher_title)
    ModalBottomSheet(onDismissRequest = calls.onDismiss, modifier = Modifier.semantics { paneTitle = title }) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StartRow(calls.onStart)
            if (ids.isNotEmpty()) {
                Text(
                    stringResource(Res.string.home_switcher_recent),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ids.chunked(PluginSwitcher.PER_ROW).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { id ->
                                NetworkBox(id, ui, networks, shown = id == current, lit = HomeTab.Network(id).key in unread, Modifier.weight(1f)) { calls.onNetwork(id) }
                            }
                            repeat(PluginSwitcher.PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = calls.onManage, modifier = Modifier.weight(1f).heightIn(min = 44.dp)) {
                    Text(stringResource(Res.string.home_manage_plugins), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FilledTonalButton(onClick = calls.onSettings, modifier = Modifier.weight(1f).heightIn(min = 44.dp)) {
                    Text(stringResource(Res.string.settings), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Start: XTA's house in a tinted disc, its name and what it leads to. */
@Composable
private fun StartRow(onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).background(accent.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(HomeBarIcons.Home, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(Res.string.home_switcher_start), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(Res.string.home_switcher_start_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** One network of the grid: its mark in a tinted disc over its name; ringed in the accent while [shown], with a dot while [lit]. */
@Composable
private fun NetworkBox(id: String, ui: HomeUi, networks: HomeNetworks, shown: Boolean, lit: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val name = ui.networks.firstOrNull { it.id == id }?.title ?: id
    val label = listOfNotNull(name, stringResource(Res.string.home_unread).takeIf { lit }).joinToString(", ")
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .height(88.dp)
            .clip(shape)
            .background(if (shown) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.background)
            .border(1.dp, if (shown) accent else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                role = Role.Button
                selected = shown
                contentDescription = label
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(36.dp).background(accent.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                networks.Mark(id, 20.dp)
            }
            Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp))
        }
        if (lit) Box(Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 12.dp).size(7.dp).background(accent, CircleShape))
    }
}
