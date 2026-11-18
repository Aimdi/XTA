package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.home_switcher_title
import com.aimdi.xtanative.ui.generated.resources.home_unread
import com.aimdi.xtanative.ui.plugins.PluginClientBar
import com.aimdi.xtanative.ui.plugins.PluginHomeTab
import com.aimdi.xtanative.ui.plugins.PluginTabFit
import com.aimdi.xtanative.ui.plugins.pluginAccent
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import org.jetbrains.compose.resources.stringResource

/** The compact bar's height: one row for the network, its sections and its actions. */
private val BAR_HEIGHT = 52.dp

/** How much of the accent the network's disc is filled with. */
private const val DISC_ALPHA = 0.16f

/** What the network's button of the bar does: open the plugin switcher. */
internal class NetworkBarCalls(val onSwitcher: () -> Unit)

/**
 * A network's timeline's bar on Home (the unified plugin shell): the
 * network's button (its mark in a tinted disc, its name and a chevron),
 * then the plugin's actions and the app's ([actions]), its search and its
 * one menu. The network's own sections, handed by its plugin to [client],
 * run under it as the feed row, text tabs underlined in the accent; with
 * [sectionsBelow] they are on the bottom bar instead ([NetworkBarRules]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NetworkTopBar(
    id: String,
    ui: HomeUi,
    networks: HomeNetworks,
    client: PluginClientBar,
    anyUnread: Boolean,
    calls: NetworkBarCalls,
    bar: TopAppBarScrollBehavior,
    sectionsBelow: Boolean = false,
    actions: @Composable () -> Unit,
) {
    val content = client.content
    val accent = pluginAccent(content?.accent?.value)
    val tabs = content?.tabs?.value.orEmpty()
    Column(Modifier.background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            expandedHeight = BAR_HEIGHT,
            navigationIcon = { NetworkButton(id, ui, networks, anyUnread, calls) },
            title = {},
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
        if (tabs.size > 1 && !sectionsBelow) FeedRow(tabs, accent)
    }
}

/**
 * A network's sections as its feed row, under the bar: words in a row that
 * scrolls sideways when they do not fit, the one shown bold and underlined
 * in [color], over a hairline.
 */
@Composable
internal fun FeedRow(tabs: List<PluginHomeTab>, color: Color) {
    Row(Modifier.fillMaxWidth().height(FEED_ROW_HEIGHT).horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp)) {
        tabs.forEach { TextTab(it, color) }
    }
    Hairline()
}

/** The feed row's height. */
private val FEED_ROW_HEIGHT = 46.dp

/**
 * The network's button: its mark in a disc of the accent, its name and a
 * chevron, carrying the dot while a timeline has unread posts. It opens the
 * plugin switcher ([PluginSwitcherSheet]), where Start leads back to XTA's
 * own Home and the other networks, the grouped microblogs among them, are
 * a tap away.
 */
@Composable
private fun NetworkButton(id: String, ui: HomeUi, networks: HomeNetworks, anyUnread: Boolean, calls: NetworkBarCalls) {
    val name = networkName(id, ui)
    val label = listOfNotNull(name, stringResource(Res.string.home_unread).takeIf { anyUnread }, stringResource(Res.string.home_switcher_title)).joinToString(", ")
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .padding(start = 6.dp)
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = calls.onSwitcher)
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
            modifier = Modifier.widthIn(max = 160.dp),
        )
        Spacer(Modifier.width(2.dp))
        Icon(GroupIcons.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

private fun networkName(id: String, ui: HomeUi): String = ui.networks.firstOrNull { it.id == id }?.title ?: id

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
