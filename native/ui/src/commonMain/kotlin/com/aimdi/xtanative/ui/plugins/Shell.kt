package com.aimdi.xtanative.ui.plugins

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.IconTip
import com.aimdi.xtanative.ui.common.rememberSheetState
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.home_more_options
import com.aimdi.xtanative.ui.generated.resources.home_sort_filter
import com.aimdi.xtanative.ui.generated.resources.home_sort_filter_on
import com.aimdi.xtanative.ui.generated.resources.home_sort_reset
import com.aimdi.xtanative.ui.post.PostIcons
import org.jetbrains.compose.resources.stringResource

/**
 * A plugin's sort and filter button at the end of the shell's feed row (the
 * unified plugin shell): [onOpen] opens the plugin's sort sheet
 * ([ShellSortSheet]); while [active], the reader has changed the defaults
 * and the button is in the accent with a dot.
 */
class ShellFilter(val active: Boolean = false, val onOpen: () -> Unit)

/** The dot before the shell's subtitle: none, live (green), unread (the accent) or a problem (red). */
enum class ShellStatus { NONE, LIVE, UNREAD, PROBLEM }

internal class ShellFeedsContent(val feeds: List<PluginHomeTab>, val filter: ShellFilter?)

internal class ShellSubtitleContent(val text: String, val status: ShellStatus)

/**
 * A plugin's feeds, the shell's second row: words, the one shown bold and
 * underlined in the accent, with the plugin's [filter] at the end. Where the
 * app draws the plugin's bar (a timeline on Home, the plugin on its own) it
 * is handed to the shell, which draws it under the plugin's name; elsewhere
 * the plugin's home draws it itself, the same row.
 */
@Composable
fun ShellFeeds(feeds: List<PluginHomeTab>, filter: ShellFilter? = null, modifier: Modifier = Modifier) {
    val bar = LocalPluginClientBar.current
    if (bar == null) {
        ShellFeedRow(feeds, filter, accent = null, modifier = modifier)
        return
    }
    val content = ShellFeedsContent(feeds, filter)
    val held = remember(bar) { Held<ShellFeedsContent>() }
    SideEffect {
        held.value = content
        bar.feeds = content
    }
    DisposableEffect(bar) { onDispose { if (bar.feeds === held.value) bar.feeds = null } }
}

/** [ShellFeeds] for feeds kept as values ([feeds], the [selected] one), each with its [label]. */
@Composable
fun <T> ShellFeeds(feeds: List<T>, selected: T, onSelect: (T) -> Unit, label: @Composable (T) -> String, filter: ShellFilter? = null, modifier: Modifier = Modifier) {
    ShellFeeds(feeds.map { feed -> PluginHomeTab(PostIcons.Tune, label(feed), feed == selected) { onSelect(feed) } }, filter, modifier)
}

/**
 * What the shell's title says under the plugin's name: the account read
 * with ("u/name", "@handle") or a state ("Live · 3 tickers", "host ·
 * unreachable"), with a [status] dot. Nothing where the app does not draw
 * the plugin's bar; null [text] says nothing.
 */
@Composable
fun ShellSubtitle(text: String?, status: ShellStatus = ShellStatus.NONE) {
    val bar = LocalPluginClientBar.current ?: return
    val content = text?.takeIf { it.isNotBlank() }?.let { ShellSubtitleContent(it, status) }
    val held = remember(bar) { Held<ShellSubtitleContent>() }
    SideEffect {
        held.value = content
        bar.subtitle = content
    }
    DisposableEffect(bar) { onDispose { if (bar.subtitle === held.value) bar.subtitle = null } }
}

/**
 * How many posts the plugin has unread, the number on its avatar in the
 * app's shell (a dot alone where the plugin says nothing). Nothing where the
 * app does not draw the plugin's bar.
 */
@Composable
fun ShellUnread(count: Int) {
    val bar = LocalPluginClientBar.current ?: return
    val shown = count.coerceAtLeast(0)
    val owner = remember(bar) { Any() }
    SideEffect {
        bar.unread = shown
        bar.unreadOwner = owner
    }
    // Only its own count goes: another part of the plugin may have handed one since, even the same number.
    DisposableEffect(bar) {
        onDispose {
            if (bar.unreadOwner === owner) {
                bar.unread = null
                bar.unreadOwner = null
            }
        }
    }
}

/**
 * What opens the plugin's own account switcher ([onSwitch]), for the
 * plugins that keep several accounts: a long press on its avatar in the
 * app's shell, and Switch account in its plugin sheet. Nothing where the
 * app does not draw the plugin's bar.
 */
@Composable
fun ShellAccounts(onSwitch: () -> Unit) {
    val bar = LocalPluginClientBar.current ?: return
    val held = remember(bar) { Held<() -> Unit>() }
    SideEffect {
        held.value = onSwitch
        bar.switchAccount = onSwitch
    }
    DisposableEffect(bar) { onDispose { if (bar.switchAccount === held.value) bar.switchAccount = null } }
}

private class Held<T> {
    var value: T? = null
}

/** The colour of a [ShellStatus] dot; null for none. */
@Composable
internal fun ShellStatus.color(): Color? = when (this) {
    ShellStatus.NONE -> null
    ShellStatus.LIVE -> Color(0xFF00BA7C)
    ShellStatus.UNREAD -> MaterialTheme.colorScheme.primary
    ShellStatus.PROBLEM -> MaterialTheme.colorScheme.error
}

/** The shell's feed row's height. */
internal val FEED_ROW_HEIGHT = 44.dp

/**
 * The shell's second row: [feeds] as words that scroll sideways, fading at
 * the end, the one shown bold with a 26 by 3 dp line in [accent] (else the
 * app's), then [filter]'s button; a hairline under it.
 */
@Composable
internal fun ShellFeedRow(feeds: List<PluginHomeTab>, filter: ShellFilter?, accent: Color?, modifier: Modifier = Modifier) {
    val color = accent ?: MaterialTheme.colorScheme.primary
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().height(FEED_ROW_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).fillMaxHeight().fadingEnd()) {
                Row(Modifier.fillMaxHeight().horizontalScroll(rememberScrollState()).padding(start = 4.dp, end = 24.dp)) {
                    feeds.forEach { FeedWord(it, color) }
                }
            }
            if (filter != null) FilterButton(filter, color)
        }
        Hairline()
    }
}

/** The row's end fades over 24 dp, so words that go on past it show that they do. */
private fun Modifier.fadingEnd(): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
    drawContent()
    val fade = 24.dp.toPx()
    drawRect(
        Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - fade, endX = size.width),
        topLeft = Offset(size.width - fade, 0f),
        size = Size(fade, size.height),
        blendMode = BlendMode.DstIn,
    )
}

@Composable
private fun FeedWord(feed: PluginHomeTab, color: Color) {
    // The feed shown is scrolled into the row, also when it was picked another way (a swipe, the plugin itself).
    val view = remember { BringIntoViewRequester() }
    LaunchedEffect(feed.selected) { if (feed.selected) view.bringIntoView() }
    Box(
        Modifier
            .bringIntoViewRequester(view)
            .fillMaxHeight()
            .defaultMinSize(minWidth = 44.dp)
            .clickable(onClick = feed.onSelect)
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                selected = feed.selected
            }
            .underline(feed.selected, color)
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            feed.label,
            fontSize = 15.sp,
            fontWeight = if (feed.selected) FontWeight.Bold else FontWeight.Medium,
            color = if (feed.selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** A 26 by 3 dp line in [color], its top corners rounded, centred along the bottom, while [on]. */
private fun Modifier.underline(on: Boolean, color: Color): Modifier = if (!on) this else drawBehind {
    val width = 26.dp.toPx()
    val height = 3.dp.toPx()
    drawRoundRect(color, topLeft = Offset((size.width - width) / 2, size.height - height), size = Size(width, height), cornerRadius = CornerRadius(height, height))
}

@Composable
private fun FilterButton(filter: ShellFilter, color: Color) {
    val label = stringResource(if (filter.active) Res.string.home_sort_filter_on else Res.string.home_sort_filter)
    IconTip(label) { IconButton(onClick = filter.onOpen) {
        Box {
            Icon(PostIcons.Tune, label, tint = if (filter.active) color else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            if (filter.active) Box(Modifier.align(Alignment.TopEnd).size(6.dp).background(color, CircleShape))
        }
    } }
}

/**
 * The plugin's sort sheet, as the shell's filter opens it: "Sort & filter"
 * over [subtitle] (the feed it sorts), the plugin's own choices ([content],
 * with [ShellSortHeading], [ShellSortOption] and [ShellSortPills]), each
 * taking effect as it is picked, and Reset where [onReset] puts the
 * defaults back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellSortSheet(subtitle: String?, onDismiss: () -> Unit, onReset: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(sheetState = rememberSheetState(), onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.padding(horizontal = 4.dp).semantics(mergeDescendants = true) { heading() }) {
                Text(stringResource(Res.string.home_sort_filter), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
            if (onReset != null) {
                TextButton(onClick = onReset, modifier = Modifier.align(Alignment.End).heightIn(min = 44.dp)) { Text(stringResource(Res.string.home_sort_reset)) }
            }
        }
    }
}

/** A part's heading in a [ShellSortSheet] ("Sort", "Time", "Filter"). */
@Composable
fun ShellSortHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp).semantics { heading() },
    )
}

/** One choice of a [ShellSortSheet]'s part, a radio button with its [label]. */
@Composable
fun ShellSortOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.RadioButton
                this.selected = selected
            }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** A [ShellSortSheet]'s choices as pills in rows ("Today", "Week"…), the [selected] one outlined and written in the accent, in bold; [selected] null for none. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShellSortPills(labels: List<String>, selected: Int?, onSelect: (Int) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    FlowRow(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            val on = index == selected
            val shape = RoundedCornerShape(18.dp)
            Box(
                Modifier
                    .heightIn(min = 36.dp)
                    .clip(shape)
                    .border(1.dp, if (on) accent else MaterialTheme.colorScheme.outlineVariant, shape)
                    .clickable { onSelect(index) }
                    .semantics {
                        role = Role.RadioButton
                        this.selected = on
                    }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.Bold else null, color = if (on) accent else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/**
 * The plugin's own entries of the shell (its saved posts, accounts, add,
 * mark all read, its settings…), as a plugin hands them with
 * [ShellMenuButton]. Home draws them in the plugin sheet the plugin's
 * avatar opens; it makes one of these for each network shown.
 */
@Stable
internal class ShellMenu(val network: String) {
    /** The plugin's entries, while its [ShellMenuButton] is drawn. */
    var items: (@Composable ColumnScope.(close: () -> Unit) -> Unit)? by mutableStateOf(null)

    /** Whether the plugin hands entries of its own. */
    val drawnByPlugin: Boolean get() = items != null
}

/** Where a network's bar is drawn on Home, the shell's entries of the plugin shown; null elsewhere. */
internal val LocalShellMenu = staticCompositionLocalOf<ShellMenu?> { null }

/**
 * A plugin's own entries ([items]: its saved posts, accounts, add, mark all
 * read, its settings…). On Home they are in the plugin sheet its avatar
 * opens (the unified plugin shell, which has no ⋮), above the app's;
 * elsewhere this is a ⋮ menu of them. [items] get what closes the menu;
 * [attention] puts a dot on the ⋮.
 */
@Composable
fun ShellMenuButton(attention: Boolean = false, items: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    val shell = LocalShellMenu.current
    if (shell != null) {
        val held = remember(shell) { Held<@Composable ColumnScope.(close: () -> Unit) -> Unit>() }
        SideEffect {
            held.value = items
            shell.items = items
        }
        // Only its own entries go: another button of the plugin may have handed some since.
        DisposableEffect(shell) { onDispose { if (shell.items === held.value) shell.items = null } }
        return
    }
    var open by remember { mutableStateOf(false) }
    val close = { open = false }
    Box {
        IconTip(stringResource(Res.string.home_more_options)) { IconButton(onClick = { open = true }) {
            val more = stringResource(Res.string.home_more_options)
            if (attention) BadgedBox(badge = { Badge() }) { Icon(Icons.Filled.MoreVert, more) } else Icon(Icons.Filled.MoreVert, more)
        } }
        DropdownMenu(expanded = open, onDismissRequest = close) { items(close) }
    }
}
