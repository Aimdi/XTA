package com.aimdi.xtanative.ui.plugins

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.subscriptions.GroupIcons

/** One of a plugin's own tabs (XTA's PluginHomeTab): its [icon] and [label], whether it is the one shown, and what picks it. */
class PluginHomeTab(val icon: ImageVector, val label: String, val selected: Boolean, val onSelect: () -> Unit)

/**
 * What a plugin's own tabs hand the bar of a plugin opened on its own
 * (XTA's standalone PluginHomeChrome): there the app's bar draws them as
 * icons between the plugin's mark and their [actions], so the screen has one
 * bar and not a title over a strip.
 */
class PluginClientBar {
    internal var content: PluginBarContent? by mutableStateOf(null)
}

internal class PluginBarContent(
    val tabs: State<List<PluginHomeTab>>,
    val accent: State<Color?>,
    val actions: State<@Composable RowScope.() -> Unit>,
)

/** The bar of the plugin screen being drawn, where the app draws a plugin on its own; null on Home, where the strip is the plugin's. */
val LocalPluginClientBar = staticCompositionLocalOf<PluginClientBar?> { null }

/**
 * A plugin's own tabs with its [actions] (a search, an add, its menu), as
 * XTA's PluginHomeChrome. On Home: a strip of icon and label buttons, the
 * one shown underlined in the plugin's colour ([accent], else its brand),
 * folding into a section picker when they do not fit. Opened on its own:
 * nothing here, the tabs and [actions] go into the screen's one bar
 * ([LocalPluginClientBar]).
 */
@Composable
fun PluginHomeTabs(
    tabs: List<PluginHomeTab>,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val bar = LocalPluginClientBar.current
    if (bar != null) return HandsToBar(bar, tabs, accent, actions)
    val color = pluginAccent(accent)
    Row(modifier.fillMaxWidth().heightIn(min = PluginTabFit.TARGET.dp), verticalAlignment = Alignment.CenterVertically) {
        BoxWithConstraints(Modifier.weight(1f)) {
            if (tabs.size > 1 && !labelsFit(tabs, maxWidth)) {
                PluginSectionPicker(tabs, color)
            } else {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
                    tabs.forEach { LabelledTab(it, color) }
                }
            }
        }
        actions()
    }
}

/** [PluginHomeTabs] for tabs kept as values ([tabs], the [selected] one): each with its [icon] and [label]. */
@Composable
fun <T> PluginHomeTabs(
    tabs: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    icon: (T) -> ImageVector,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    PluginHomeTabs(tabs.map { tab -> PluginHomeTab(icon(tab), label(tab), tab == selected) { onSelect(tab) } }, modifier, accent, actions)
}

@Composable
private fun HandsToBar(bar: PluginClientBar, tabs: List<PluginHomeTab>, accent: Color?, actions: @Composable RowScope.() -> Unit) {
    val content = PluginBarContent(rememberUpdatedState(tabs), rememberUpdatedState(accent), rememberUpdatedState(actions))
    val handed = remember(bar) { content }
    DisposableEffect(bar) {
        bar.content = handed
        onDispose { if (bar.content === handed) bar.content = null }
    }
}

/** Whether [tabs] fit [width] as icon and label buttons, measured in the strip's type. */
@Composable
private fun labelsFit(tabs: List<PluginHomeTab>, width: Dp): Boolean {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    val density = LocalDensity.current
    val widths = remember(tabs.map { it.label to it.selected }, style, density) {
        tabs.map { tab ->
            val measured = measurer.measure(tab.label, style.copy(fontWeight = if (tab.selected) FontWeight.Bold else FontWeight.Medium), maxLines = 1)
            with(density) { measured.size.width.toDp().value }
        }
    }
    return PluginTabFit.labelled(widths, width.value)
}

/** One tab as XTA's _TabButton: its icon and label, underlined in [color] while it is the one shown. */
@Composable
private fun LabelledTab(tab: PluginHomeTab, color: Color) {
    val foreground = if (tab.selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .defaultMinSize(minWidth = PluginTabFit.TARGET.dp, minHeight = PluginTabFit.TARGET.dp)
            .clickable(onClick = tab.onSelect)
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                selected = tab.selected
            }
            .underlined(tab.selected, color)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(tab.icon, contentDescription = null, tint = if (tab.selected) color else foreground, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(tab.label, style = MaterialTheme.typography.labelLarge, fontWeight = if (tab.selected) FontWeight.Bold else FontWeight.Medium, color = foreground, maxLines = 1)
    }
}

/** A two dp line in [color] under what it is put on, while [on]. */
private fun Modifier.underlined(on: Boolean, color: Color): Modifier = if (!on) this else drawBehind {
    val line = 2.dp.toPx()
    drawRect(color, topLeft = Offset(0f, size.height - line), size = Size(size.width, line))
}

/**
 * A plugin's tabs as icons alone, each a touch target sharing the width, the
 * one shown underlined in [color] (XTA's PluginCompactTabs): a standalone
 * bar's navigation. Folds into [PluginSectionPicker] when they do not fit.
 */
@Composable
fun PluginCompactTabs(tabs: List<PluginHomeTab>, color: Color, modifier: Modifier = Modifier) {
    if (tabs.isEmpty()) return
    BoxWithConstraints(modifier) {
        if (!PluginTabFit.icons(tabs.size, maxWidth.value)) {
            return@BoxWithConstraints PluginSectionPicker(tabs, color, verticalPadding = 4.dp, iconOnly = !PluginTabFit.namesSection(maxWidth.value))
        }
        Row(Modifier.fillMaxWidth()) {
            tabs.forEach { tab ->
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = PluginTabFit.TARGET.dp)
                        .clickable(onClick = tab.onSelect)
                        .semantics {
                            role = Role.Tab
                            selected = tab.selected
                            contentDescription = tab.label
                        }
                        .underlined(tab.selected, color),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(tab.icon, contentDescription = null, tint = if (tab.selected) color else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

/**
 * A plugin's tabs folded into one labelled button (XTA's
 * PluginSectionPicker): the section shown, its icon in [color], opening a
 * menu of them all with the one shown ticked. With [iconOnly], the icon
 * alone, the section's name its description.
 */
@Composable
fun PluginSectionPicker(tabs: List<PluginHomeTab>, color: Color, modifier: Modifier = Modifier, verticalPadding: Dp = 8.dp, iconOnly: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    val current = tabs.firstOrNull { it.selected }
    Box(modifier) {
        Row(
            Modifier
                .defaultMinSize(minWidth = PluginTabFit.TARGET.dp, minHeight = PluginTabFit.TARGET.dp)
                .clickable { open = true }
                .semantics(mergeDescendants = true) {
                    role = Role.DropdownList
                    current?.label?.let { contentDescription = it }
                }
                .padding(horizontal = if (iconOnly) 4.dp else 12.dp, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(current?.icon ?: Icons.Filled.Menu, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            if (!iconOnly) {
                Spacer(Modifier.width(8.dp))
                Text(
                    current?.label.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(4.dp))
            }
            Icon(GroupIcons.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(if (iconOnly) 16.dp else 20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            tabs.forEach { tab ->
                DropdownMenuItem(
                    text = { Text(tab.label) },
                    leadingIcon = { Icon(tab.icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    trailingIcon = { if (tab.selected) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        open = false
                        tab.onSelect()
                    },
                    modifier = Modifier.semantics { selected = tab.selected },
                )
            }
        }
    }
}

/**
 * The one bar of a plugin opened on its own (XTA's standalone
 * PluginHomeChrome): back, the plugin's [mark] named [title], then its tabs
 * as icons where it handed some to [bar], else [title]; then the plugin's
 * actions, else [fallbackActions] (the app's search and settings for a
 * plugin that draws no tabs of its own).
 */
@Composable
internal fun PluginStandaloneBar(
    bar: PluginClientBar,
    title: String,
    mark: @Composable () -> Unit,
    back: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    fallbackActions: @Composable RowScope.() -> Unit = {},
) {
    val content = bar.content
    Row(modifier.fillMaxWidth().heightIn(min = STANDALONE_BAR.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        back()
        Box(Modifier.width(40.dp).semantics { contentDescription = title }, contentAlignment = Alignment.Center) { mark() }
        val tabs = content?.tabs?.value.orEmpty()
        if (tabs.isEmpty()) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
        } else {
            PluginCompactTabs(tabs, pluginAccent(content?.accent?.value), Modifier.weight(1f))
        }
        if (content != null) content.actions.value(this) else fallbackActions()
    }
}

/** The standalone bar's height, XTA's pluginToolbarHeight at the default text size. */
private const val STANDALONE_BAR = 52f
