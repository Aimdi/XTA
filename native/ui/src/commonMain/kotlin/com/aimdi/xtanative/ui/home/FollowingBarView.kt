package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimdi.xtanative.core.feed.FeedSort
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.following
import com.aimdi.xtanative.ui.generated.resources.groups_filters
import com.aimdi.xtanative.ui.generated.resources.home_media
import com.aimdi.xtanative.ui.generated.resources.home_posts
import com.aimdi.xtanative.ui.generated.resources.home_refresh
import com.aimdi.xtanative.ui.group.GroupFeedUi
import com.aimdi.xtanative.ui.group.sortLabel
import com.aimdi.xtanative.ui.search.LoadedSearchButton
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/** What Following's bar does when its controls are used. */
internal class FollowingCalls(
    val onMedia: (Boolean) -> Unit,
    val onOrder: (FeedSort) -> Unit,
    val onFilters: () -> Unit,
    val onPicker: () -> Unit,
    val onSearchLoaded: () -> Unit,
    val onAccounts: () -> Unit,
    /** The desktop's refresh, where a mouse cannot pull the timeline down; null elsewhere. */
    val onRefresh: (() -> Unit)?,
)

/** What Following's bar shows: the feed's order and rules, Posts or Media, the picker's dot and how many accounts are off. */
internal class FollowingLook(val feed: GroupFeedUi, val mediaOnly: Boolean, val unread: Boolean, val accountsOff: Int)

/**
 * Following's one bar (home_timeline_controls.dart's HomeFollowingBar,
 * home-following-bar.md): Posts | Media at the start, then the order and
 * filters, the timeline picker with its unread dot, the search of the posts
 * loaded, Home feed accounts and, on the desktop, refresh. When the tabs do
 * not fit beside them they move closer, then to a row of their own under the
 * controls. It leaves and comes back with the scrolling as the top bar does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FollowingTopBar(look: FollowingLook, calls: FollowingCalls, behavior: TopAppBarScrollBehavior) {
    Collapsing(behavior) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val fit = FollowingBar.fit(maxWidth.value, labelsWidth(), FollowingBar.actions(desktop = calls.onRefresh != null))
            val padding = if (fit == BarFit.ONE_ROW) FollowingBar.TAB_PADDING.dp else FollowingBar.COMPACT_PADDING.dp
            if (fit == BarFit.TWO_ROWS) {
                Column {
                    Row(Modifier.fillMaxWidth().height(FollowingBar.HEIGHT.dp), horizontalArrangement = Arrangement.End) { Controls(look, calls) }
                    Box(Modifier.height(FollowingBar.HEIGHT.dp).padding(start = FollowingBar.INSET.dp)) { ModeTabs(look.mediaOnly, padding, calls.onMedia) }
                }
            } else {
                Row(Modifier.fillMaxWidth().height(FollowingBar.HEIGHT.dp).padding(start = FollowingBar.INSET.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { ModeTabs(look.mediaOnly, padding, calls.onMedia) }
                    Controls(look, calls)
                }
            }
        }
    }
}

/** Posts and Media together, measured in their bold, widest state, in dp. */
@Composable
private fun labelsWidth(): Float {
    val measurer = rememberTextMeasurer()
    val style = tabStyle(selected = true)
    val posts = stringResource(Res.string.home_posts)
    val media = stringResource(Res.string.home_media)
    val density = LocalDensity.current
    return remember(posts, media, style, density) {
        with(density) { (measurer.measure(posts, style).size.width + measurer.measure(media, style).size.width).toDp().value }
    }
}

@Composable
private fun tabStyle(selected: Boolean): TextStyle =
    MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)

@Composable
private fun ModeTabs(mediaOnly: Boolean, padding: Dp, onMedia: (Boolean) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).fillMaxHeight()) {
        ModeTab(stringResource(Res.string.home_posts), !mediaOnly, padding) { onMedia(false) }
        ModeTab(stringResource(Res.string.home_media), mediaOnly, padding) { onMedia(true) }
    }
}

/** A tab: its name, bold in the text colour when selected, with the accent's marker under it. */
@Composable
private fun ModeTab(label: String, selected: Boolean, padding: Dp, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxHeight()
            .widthIn(min = FollowingBar.TOUCH_TARGET.dp)
            .selectable(selected = selected, role = Role.Tab, onClick = { if (!selected) onClick() })
            .padding(horizontal = padding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = tabStyle(selected),
            color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        if (selected) {
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.BottomCenter) {
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primary))
            }
        }
    }
}

/** The order and filters, the picker, the search of the loaded posts, Home feed accounts and the desktop's refresh. */
@Composable
private fun Controls(look: FollowingLook, calls: FollowingCalls) {
    Row(Modifier.padding(end = FollowingBar.INSET.dp), verticalAlignment = Alignment.CenterVertically) {
        OrderButton(look.feed, calls.onOrder, calls.onFilters)
        val source = stringResource(Res.string.following)
        IconButton(onClick = calls.onPicker, modifier = Modifier.semantics { contentDescription = source }) {
            UnreadDot(look.unread) { Icon(HomeIcons.DynamicFeed, contentDescription = null) }
        }
        LoadedSearchButton(calls.onSearchLoaded)
        FeedAccountsButton(look.accountsOff, calls.onAccounts)
        calls.onRefresh?.let { refresh ->
            IconButton(onClick = refresh) { Icon(Icons.Filled.Refresh, stringResource(Res.string.home_refresh)) }
        }
    }
}

/**
 * The order and filters behind one button (home_timeline_controls.dart's
 * HomeOrderButton): Recent, Popular and Custom, then Filters; the order is
 * said, not drawn, and the count of the custom rules that are on sits on it.
 */
@Composable
private fun OrderButton(feed: GroupFeedUi, onOrder: (FeedSort) -> Unit, onFilters: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val filters = feed.activeFilters
    val label = stringResource(sortLabel(feed.settings.sort)) + " · " + stringResource(Res.string.groups_filters)
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.semantics { contentDescription = label }) {
            Counted(filters) { Icon(HomeIcons.FilterList, contentDescription = null) }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            FollowingBar.ORDERS.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(stringResource(sortLabel(sort))) },
                    leadingIcon = {
                        if (sort == feed.settings.sort) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) else Spacer(Modifier.size(24.dp))
                    },
                    trailingIcon = if (sort == FeedSort.CUSTOM) ({ Icon(GroupIcons.Tune, contentDescription = null, modifier = Modifier.size(20.dp)) }) else null,
                    onClick = {
                        open = false
                        onOrder(sort)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.groups_filters)) },
                leadingIcon = { Icon(GroupIcons.Build, contentDescription = null) },
                trailingIcon = if (filters > 0) ({ Badge { Text("$filters") } }) else null,
                onClick = {
                    open = false
                    onFilters()
                },
            )
        }
    }
}

/** [content] with [count] on its corner while it is above zero. */
@Composable
private fun Counted(count: Int, content: @Composable () -> Unit) {
    if (count <= 0) {
        content()
        return
    }
    BadgedBox(badge = { Badge(containerColor = MaterialTheme.colorScheme.primary) { Text("$count") } }) { content() }
}

/**
 * A bar that leaves with the scroll and comes back with it, as the top bar
 * does ([behavior]'s state), clear of the status bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Collapsing(behavior: TopAppBarScrollBehavior, content: @Composable () -> Unit) {
    val state = behavior.state
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(TopAppBarDefaults.windowInsets)
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                val height = (placeable.height + state.heightOffset).roundToInt().coerceIn(0, placeable.height)
                layout(placeable.width, height) { placeable.place(0, height - placeable.height) }
            },
    ) {
        Box(Modifier.onSizeChanged { state.heightOffsetLimit = -it.height.toFloat() }) { content() }
    }
}
