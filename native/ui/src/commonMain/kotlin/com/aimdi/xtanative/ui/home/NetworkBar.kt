package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.home_all_timelines
import com.aimdi.xtanative.ui.generated.resources.home_networks
import com.aimdi.xtanative.ui.generated.resources.home_unread
import com.aimdi.xtanative.ui.plugins.PluginClientBar
import com.aimdi.xtanative.ui.plugins.PluginHomeTab
import com.aimdi.xtanative.ui.plugins.PluginSectionPicker
import com.aimdi.xtanative.ui.plugins.PluginTabFit
import com.aimdi.xtanative.ui.plugins.pluginAccent
import org.jetbrains.compose.resources.stringResource

/** The compact bar's height: one row for the network, its sections and its actions. */
private val BAR_HEIGHT = 52.dp

/** How much of the accent the network's disc is filled with. */
private const val DISC_ALPHA = 0.16f

/** What the network's button of the compact bar does: show another member, or open the timeline picker. */
internal class NetworkBarCalls(val onMember: (String) -> Unit, val onPicker: () -> Unit)

/**
 * A network's timeline's one bar on Home (the header's variant A): the
 * network's mark in a tinted disc with its name and a chevron, which switches
 * between Mastodon, Bluesky and Threads while they are grouped and opens the
 * timeline picker; then the network's own sections, handed by its plugin to
 * [client], as text tabs underlined in the accent (folded into its section
 * picker where they do not fit); then the plugin's actions and the app's
 * ([actions]). The plugin's own strip under it gives way, so its posts start
 * one row higher.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NetworkTopBar(
    id: String,
    ui: HomeUi,
    networks: HomeNetworks,
    client: PluginClientBar,
    unread: Set<String>,
    anyUnread: Boolean,
    calls: NetworkBarCalls,
    bar: TopAppBarScrollBehavior,
    actions: @Composable () -> Unit,
) {
    val content = client.content
    val accent = pluginAccent(content?.accent?.value)
    TopAppBar(
        expandedHeight = BAR_HEIGHT,
        navigationIcon = { NetworkButton(id, ui, networks, unread, anyUnread, calls) },
        title = {
            val tabs = content?.tabs?.value.orEmpty()
            if (tabs.isNotEmpty()) TextTabs(tabs, accent)
        },
        actions = {
            content?.actions?.value?.invoke(this)
            actions()
        },
        scrollBehavior = bar,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
        ),
    )
}

/**
 * The network's button: its mark in a disc of the accent, its name and a
 * chevron, carrying the dot while a timeline has unread posts. It opens a
 * menu of the grouped microblogs and every timeline, or, with no group to
 * switch in, the timeline picker at once.
 */
@Composable
private fun NetworkButton(id: String, ui: HomeUi, networks: HomeNetworks, unread: Set<String>, anyUnread: Boolean, calls: NetworkBarCalls) {
    val members = if (ui.microblogsGrouped && AltMicroblogging.isMember(id)) AltMicroblogging.members(ui.tabs.filterIsInstance<HomeTab.Network>().map { it.id }) else emptyList()
    var open by remember { mutableStateOf(false) }
    val name = networkName(id, ui)
    val label = listOfNotNull(name, stringResource(Res.string.home_unread).takeIf { anyUnread }, stringResource(Res.string.home_networks)).joinToString(", ")
    val accent = MaterialTheme.colorScheme.primary
    Box {
        Row(
            Modifier
                .padding(start = 6.dp)
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(22.dp))
                .clickable { if (members.size > 1) open = true else calls.onPicker() }
                .clearAndSetSemantics {
                    role = Role.DropdownList
                    contentDescription = label
                }
                .padding(start = 4.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UnreadDot(anyUnread) {
                Box(Modifier.size(28.dp).background(accent.copy(alpha = DISC_ALPHA), CircleShape), contentAlignment = Alignment.Center) {
                    networks.Mark(id, 17.dp)
                }
            }
            Spacer(Modifier.width(6.dp))
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp),
            )
            Spacer(Modifier.width(2.dp))
            Icon(GroupIcons.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            members.forEach { member ->
                val shown = member == id
                val memberName = networkName(member, ui)
                val lit = HomeTab.Network(member).key in unread
                DropdownMenuItem(
                    text = { Text(memberName) },
                    leadingIcon = { UnreadDot(lit) { networks.Mark(member, 20.dp) } },
                    trailingIcon = { if (shown) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        open = false
                        if (!shown) calls.onMember(member)
                    },
                    modifier = Modifier.semantics { selected = shown },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.home_all_timelines)) },
                onClick = {
                    open = false
                    calls.onPicker()
                },
            )
        }
    }
}

private fun networkName(id: String, ui: HomeUi): String = ui.networks.firstOrNull { it.id == id }?.title ?: id

/**
 * A plugin's sections as words, the one shown bold and underlined in
 * [color] with a rounded three dp line; folded into the plugin's section
 * picker when they do not fit the room left between the button and the
 * actions.
 */
@Composable
private fun TextTabs(tabs: List<PluginHomeTab>, color: Color) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.titleSmall
    val density = LocalDensity.current
    val widths = remember(tabs.map { it.label to it.selected }, style, density) {
        tabs.map { tab ->
            val measured = measurer.measure(tab.label, style.copy(fontWeight = FontWeight.Bold), maxLines = 1)
            with(density) { measured.size.width.toDp().value } + TAB_PADDING * 2
        }
    }
    BoxWithConstraints(Modifier.fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
        if (tabs.size > 1 && widths.sum() > maxWidth.value) {
            Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.CenterEnd) { PluginSectionPicker(tabs, color, verticalPadding = 4.dp) }
        } else {
            Row(Modifier.fillMaxHeight(), horizontalArrangement = Arrangement.End) { tabs.forEach { TextTab(it, color) } }
        }
    }
}

private const val TAB_PADDING = 10f

@Composable
private fun TextTab(tab: PluginHomeTab, color: Color) {
    Box(
        Modifier
            .fillMaxHeight()
            .defaultMinSize(minWidth = PluginTabFit.TARGET.dp)
            .clickable(onClick = tab.onSelect)
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                selected = tab.selected
            }
            .accentLine(tab.selected, color)
            .padding(horizontal = TAB_PADDING.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            tab.label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (tab.selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (tab.selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** A three dp line in [color], its ends rounded, along the bottom of what it is put on, inset six dp, while [on]. */
private fun Modifier.accentLine(on: Boolean, color: Color): Modifier = if (!on) this else drawBehind {
    val line = 3.dp.toPx()
    val inset = 6.dp.toPx()
    drawRoundRect(
        color,
        topLeft = Offset(inset, size.height - line),
        size = Size(size.width - inset * 2, line),
        cornerRadius = CornerRadius(line / 2, line / 2),
    )
}
