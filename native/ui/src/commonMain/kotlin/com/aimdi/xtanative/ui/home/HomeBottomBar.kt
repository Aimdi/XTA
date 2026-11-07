package com.aimdi.xtanative.ui.home

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.home_add_timeline
import com.aimdi.xtanative.ui.generated.resources.home_alt_microblogging
import com.aimdi.xtanative.ui.generated.resources.home_discover
import com.aimdi.xtanative.ui.generated.resources.home_home
import com.aimdi.xtanative.ui.generated.resources.home_networks
import com.aimdi.xtanative.ui.generated.resources.home_networks_all
import com.aimdi.xtanative.ui.generated.resources.home_networks_recent
import com.aimdi.xtanative.ui.generated.resources.home_saved
import com.aimdi.xtanative.ui.generated.resources.home_subscriptions
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import com.aimdi.xtanative.ui.subscriptions.GroupMark
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The bar's height above the system's gesture area, with or without labels (kHomeNavigationHeight). */
private val BAR_HEIGHT = 64.dp

/** How much of the page's colour the bar is drawn in (kHomeNavigationBarOpacity). */
private const val BAR_OPACITY = 0.88f

/** A scroll that stops without a fling (a mouse wheel) settles the bars after this long. */
private const val SETTLE_AFTER_MS = 180L

/**
 * Home's bars slipping away as the reader scrolls a page down and coming back
 * as they scroll up (home_navigation_visibility.dart's store, [BarHiding]):
 * every page's scrolling reaches [connection]. With Theme's switch off they
 * stay; they come back on a page change and a tap of the bar.
 */
@Stable
internal class HomeBarsState(private val scope: CoroutineScope, density: Density) {
    private val hiding = with(density) { BarHiding(BarHiding.HIDE_AFTER_DP.dp.toPx(), BarHiding.SHOW_AFTER_DP.dp.toPx()) }
    private var motion: Job? = null

    var enabled = true
    var reduced = false

    /** The bar's whole height, in pixels: how far it travels to leave. */
    var travel = 0f

    /** 0 on screen, 1 gone. */
    var hidden by mutableFloatStateOf(0f)
        private set

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (!enabled || available.y == 0f) return Offset.Zero
            motion?.cancel()
            hiding.scrolled(-available.y, travel)
            hidden = hiding.hidden
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (!enabled) return Offset.Zero
            // Pulled on past the top or the end: the bars belong on screen there.
            if (available.y != 0f) show() else settleSoon()
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (enabled) settle()
            return Velocity.Zero
        }
    }

    fun show() {
        hiding.shown()
        moveTo(0f)
    }

    private fun settleSoon() {
        motion?.cancel()
        motion = scope.launch {
            delay(SETTLE_AFTER_MS)
            settle()
        }
    }

    private fun settle() = moveTo(hiding.resting(travel))

    private fun moveTo(target: Float) {
        motion?.cancel()
        if (reduced || hidden == target) {
            hidden = target
            hiding.moved(target)
            return
        }
        val distance = abs(target - hidden)
        motion = scope.launch {
            animate(hidden, target, animationSpec = tween(BarHiding.settleMillis(distance))) { value, _ ->
                hidden = value
                hiding.moved(value)
            }
        }
    }

}

@Composable
internal fun rememberHomeBars(enabled: Boolean): HomeBarsState {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val state = remember(scope, density) { HomeBarsState(scope, density) }
    val reduced = LocalReducedMotion.current
    SideEffect { state.reduced = reduced }
    LaunchedEffect(state, enabled) {
        state.enabled = enabled
        if (!enabled) state.show()
    }
    return state
}

/** The bar's place on screen: it slides off the bottom edge as it hides, and the pages above it grow into the room it leaves. */
private fun Modifier.hiding(state: HomeBarsState): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val away = (state.hidden * placeable.height).roundToInt()
    layout(placeable.width, placeable.height - away) { placeable.place(0, 0) }
}

/**
 * Home's bottom bar (home_chrome.dart's HomeNavigationBar): the reader's
 * pages flat across the bottom in the page's colour with a hairline along
 * its top, the selected one's icon filled, with labels when Theme says so.
 * Networks beyond the first stand behind one Networks place. Holding Home
 * opens the timeline picker; a swipe across the bar turns its pages.
 */
@Composable
internal fun HomeBottomBar(
    ui: HomeUi,
    page: Int,
    state: HomeBarsState,
    networks: HomeNetworks,
    onPage: (Int) -> Unit,
    onNetworks: () -> Unit,
    onHoldHome: () -> Unit,
) {
    val slots = remember(ui.bar, ui.recentNetworks) { HomeBar.slots(ui.bar, ui.recentNetworks) }
    val selected = HomeBar.slotOf(slots, page)
    Column(
        Modifier
            .hiding(state)
            .onSizeChanged { state.travel = it.height.toFloat() }
            .background(MaterialTheme.colorScheme.background.copy(alpha = BAR_OPACITY))
            .barSwipe(page, ui.bar.size, onPage),
    ) {
        Hairline()
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).height(BAR_HEIGHT)) {
            slots.forEachIndexed { index, slot ->
                when (slot) {
                    BarSlot.Networks -> BarItem(stringResource(Res.string.home_networks), index == selected, ui.navigationLabels, onClick = onNetworks) { on ->
                        BarIcon(if (on) HomeIcons.Public else HomeIcons.PublicOutlined)
                    }
                    is BarSlot.Page -> {
                        val barPage = ui.bar[slot.index]
                        BarItem(barLabel(barPage, ui), index == selected, ui.navigationLabels, onClick = { onPage(slot.index) }, onHold = onHoldHome.takeIf { barPage == BarPage.Feed }) { on ->
                            BarPageIcon(barPage, on, ui, networks)
                        }
                    }
                }
            }
        }
    }
}

/** A swipe across the bar turns to the page before or after (reader_swipe_navigation.dart). */
@Composable
private fun Modifier.barSwipe(page: Int, count: Int, onPage: (Int) -> Unit): Modifier {
    val density = LocalDensity.current.density
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var distance by remember { mutableFloatStateOf(0f) }
    val drag = rememberDraggableState { delta -> distance += delta }
    return draggable(
        drag,
        Orientation.Horizontal,
        onDragStarted = { distance = 0f },
        onDragStopped = { velocity ->
            val next = HomeBar.swiped(page, count, distance / density, velocity / density, rtl)
            if (next != page) onPage(next)
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.BarItem(label: String, selected: Boolean, labels: Boolean, onClick: () -> Unit, onHold: (() -> Unit)? = null, icon: @Composable (Boolean) -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .combinedClickable(role = Role.Tab, onClick = onClick, onLongClick = onHold)
            .semantics(mergeDescendants = true) {
                this.selected = selected
                if (!labels) contentDescription = label
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) { icon(selected) }
        if (labels) {
            Text(
                label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 2.dp),
            )
        }
    }
}

@Composable
private fun BarIcon(vector: ImageVector) {
    Icon(vector, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(26.dp))
}

@Composable
private fun BarPageIcon(page: BarPage, selected: Boolean, ui: HomeUi, networks: HomeNetworks) {
    when (page) {
        BarPage.Feed -> BarIcon(if (selected) Icons.Filled.Home else Icons.Outlined.Home)
        BarPage.Subscriptions -> BarIcon(if (selected) HomeIcons.People else HomeIcons.PeopleOutlined)
        BarPage.Trending -> BarIcon(if (selected) Icons.Filled.Search else Icons.Outlined.Search)
        BarPage.Saved -> BarIcon(if (selected) HomeIcons.BookmarkFilled else HomeIcons.Bookmark)
        is BarPage.Network -> if (page.id in ui.missing) BarIcon(Icons.Outlined.Warning) else networks.Mark(page.id, 22.dp)
        is BarPage.Group -> ui.session.groups.firstOrNull { it.id == page.groupId }?.let { GroupMark(it, 24.dp) }
    }
}

/** What the bar and Settings › Home call a page: Home's four by name, a network by its title, a group by its name. */
@Composable
internal fun barLabel(page: BarPage, ui: HomeUi): String = when (page) {
    BarPage.Feed -> stringResource(Res.string.home_home)
    BarPage.Subscriptions -> stringResource(Res.string.home_subscriptions)
    BarPage.Trending -> stringResource(Res.string.home_discover)
    BarPage.Saved -> stringResource(Res.string.home_saved)
    is BarPage.Network -> ui.networks.firstOrNull { it.id == page.id }?.title ?: page.id
    is BarPage.Group -> ui.session.groups.firstOrNull { it.id == page.groupId }?.name ?: page.groupId
}

/**
 * The networks on the bar (network_switcher.dart's sheet): those opened
 * lately, then all the others, the one open ticked, with Mastodon, Bluesky
 * and Threads under one entry that opens on them while they are grouped; and
 * Add timeline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NetworksSheet(ui: HomeUi, networks: HomeNetworks, current: String?, onPick: (String) -> Unit, onAdd: () -> Unit, onDismiss: () -> Unit) {
    val onBar = HomeBar.networks(ui.bar)
    val (recent, rest) = AltMicroblogging.sections(onBar, ui.recentNetworks, ui.microblogsGrouped)
    val sheet = NetworkRows(ui, networks, current, AltMicroblogging.members(onBar), onPick)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        LazyColumn(Modifier.padding(bottom = 16.dp)) {
            item(key = "title") {
                Text(stringResource(Res.string.home_networks), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp))
            }
            networkSection("recent", recent, Res.string.home_networks_recent, sheet)
            networkSection("all", rest, Res.string.home_networks_all, sheet)
            item(key = "add") {
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onAdd).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(stringResource(Res.string.home_add_timeline), modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
    }
}

/** What the Networks sheet draws its rows with: the networks' names and marks, the one open, the microblogs among them, and what a tap opens. */
private class NetworkRows(val ui: HomeUi, val networks: HomeNetworks, val current: String?, val microblogs: List<String>, val onPick: (String) -> Unit)

private fun LazyListScope.networkSection(key: String, ids: List<String>, title: StringResource, rows: NetworkRows) {
    if (ids.isEmpty()) return
    item(key = "section-$key") {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
    }
    items(ids, key = { "$key-$it" }) { id ->
        if (id == AltMicroblogging.SECTION) MicroblogsEntry(rows) else NetworkRow(id, rows)
    }
}

@Composable
private fun NetworkRow(id: String, rows: NetworkRows, indent: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable { rows.onPick(id) }
            .semantics { selected = id == rows.current }
            .padding(start = if (indent) 32.dp else 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        rows.networks.Mark(id, 28.dp)
        Text(
            rows.ui.networks.firstOrNull { it.id == id }?.title ?: id,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
        )
        if (id == rows.current) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

/** Mastodon, Bluesky and Threads under one entry, open while one of them is shown. */
@Composable
private fun MicroblogsEntry(rows: NetworkRows) {
    var open by rememberSaveable { mutableStateOf(rows.current in rows.microblogs) }
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { open = !open }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            MicroblogMark(rows.networks, 28.dp)
            Text(stringResource(Res.string.home_alt_microblogging), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 16.dp))
            Icon(if (open) GroupIcons.ExpandLess else GroupIcons.ExpandMore, contentDescription = null)
        }
        if (open) rows.microblogs.forEach { id -> NetworkRow(id, rows, indent = true) }
    }
}
