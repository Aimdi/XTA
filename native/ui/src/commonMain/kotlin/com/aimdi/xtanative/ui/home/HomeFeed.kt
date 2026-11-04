package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.core.feed.FeedSort
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.feed.FeedPage
import com.aimdi.xtanative.ui.feed.MediaModes
import com.aimdi.xtanative.ui.feed.ToTop
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.following
import com.aimdi.xtanative.ui.generated.resources.home_add_timeline
import com.aimdi.xtanative.ui.generated.resources.home_feed_accounts
import com.aimdi.xtanative.ui.generated.resources.home_for_you
import com.aimdi.xtanative.ui.generated.resources.home_more_options
import com.aimdi.xtanative.ui.generated.resources.home_open_group
import com.aimdi.xtanative.ui.generated.resources.home_open_menu
import com.aimdi.xtanative.ui.generated.resources.home_pin_to_bar
import com.aimdi.xtanative.ui.generated.resources.home_refresh
import com.aimdi.xtanative.ui.generated.resources.search
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.search.LoadedSearchButton
import org.jetbrains.compose.resources.stringResource

/** Where the feed page's controls lead, in the shell that holds the drawer, the sheets and what opens over Home. */
internal class FeedPageCalls(
    val openMenu: () -> Unit,
    val openSheet: (HomeSheet) -> Unit,
    val openLayer: (HomeLayer) -> Unit,
    val onTab: (Int) -> Unit,
    val toTitleTop: () -> Unit,
    /** Following's order picked: set, and Custom's rules opened. */
    val onOrder: (FeedSort) -> Unit,
)

/**
 * Home's Feed page (XTA's FeedScreen): the timelines of the strip, each a
 * page of its own, under Following's one bar while Following is shown, else
 * the top bar of the timeline shown; both leave with the scroll when Theme
 * says so.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeFeedPage(
    ui: HomeUi,
    vm: HomeViewModel,
    strip: StripState,
    following: FollowingLook,
    networks: HomeNetworks,
    actions: PostActions,
    routes: HomeRoutes,
    refresh: () -> Unit,
    calls: FeedPageCalls,
) {
    val tabs = ui.tabs
    val bar = if (ui.hideOnScroll) TopAppBarDefaults.enterAlwaysScrollBehavior() else TopAppBarDefaults.pinnedScrollBehavior()
    val current = tabs.getOrNull(strip.pager.currentPage)
    val desktop = LocalDesktopActions.current != null
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                if (current == HomeTab.Following) {
                    FollowingTopBar(following, followingCalls(calls, desktop, refresh), bar)
                } else {
                    TimelineTopBar(current, ui, networks, routes, refresh, calls, bar, vm::setTopBarPins)
                }
                Strip(tabs, strip.pager.currentPage, ui, networks, strip.unread, onTab = calls.onTab, onAdd = { calls.openSheet(HomeSheet.STRIP) })
            }
        },
    ) { padding ->
        HorizontalPager(
            state = strip.pager,
            key = { page -> tabs.getOrNull(page)?.key ?: page },
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
        ) { page ->
            TimelinePage(tabs.getOrNull(page), page, strip.toTop, following.mediaOnly, actions, routes, networks, PaddingValues(bottom = padding.calculateBottomPadding()))
        }
    }
}

private fun followingCalls(calls: FeedPageCalls, desktop: Boolean, refresh: () -> Unit) = FollowingCalls(
    onMedia = { MediaModes.set(FollowingBar.MEDIA_KEY, it) },
    onOrder = calls.onOrder,
    onFilters = { calls.openSheet(HomeSheet.FOLLOWING_FILTERS) },
    onPicker = { calls.openSheet(HomeSheet.PICKER) },
    onSearchLoaded = { calls.openLayer(HomeLayer.LOADED_SEARCH) },
    onAccounts = { calls.openSheet(HomeSheet.FILTER) },
    onRefresh = refresh.takeIf { desktop },
)

/** One timeline of the strip as a page of the pager; Following shows its media alone in Media. */
@Composable
private fun TimelinePage(
    tab: HomeTab?,
    page: Int,
    toTop: ToTop,
    followingMedia: Boolean,
    actions: PostActions,
    routes: HomeRoutes,
    networks: HomeNetworks,
    bottom: PaddingValues,
) {
    when (tab) {
        HomeTab.Following -> FeedPage(null, page, toTop, actions, bottom, mediaOnly = followingMedia)
        HomeTab.ForYou -> ForYouPage(page, toTop, actions, bottom, onImport = { routes.openIfExists(HomeRoutes.IMPORT) })
        is HomeTab.Group -> FeedPage(tab.id, page, toTop, actions, bottom)
        is HomeTab.Network -> networks.Page(tab.id, bottom, if (toTop.page == page) toTop.count else 0)
        null -> Unit
    }
}

/** The top bar of a timeline that is not Following: the reader's mark that opens the menu, the timeline's name, its actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimelineTopBar(
    current: HomeTab?,
    ui: HomeUi,
    networks: HomeNetworks,
    routes: HomeRoutes,
    refresh: () -> Unit,
    calls: FeedPageCalls,
    bar: TopAppBarScrollBehavior,
    onPins: (String, List<String>) -> Unit,
) {
    TopAppBar(
        navigationIcon = { MenuButton(ui, calls.openMenu) },
        title = {
            Text(
                current?.let { tabLabel(it, ui) }.orEmpty(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(onClick = calls.toTitleTop),
            )
        },
        actions = { TabActions(current, ui, networks, routes, refresh, calls, onPins) },
        scrollBehavior = bar,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
        ),
    )
}

/** The reader's mark, which opens the account menu. */
@Composable
private fun MenuButton(ui: HomeUi, onClick: () -> Unit) {
    val label = stringResource(Res.string.home_open_menu)
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
        AccountMark(ui.account, 30.dp, Modifier.clearAndSetSemantics {})
    }
}

/** The strip (XTA's feed strip): the timelines' tabs, each with its unread dot, then the button that adds a timeline. */
@Composable
private fun Strip(
    tabs: List<HomeTab>,
    selected: Int,
    ui: HomeUi,
    networks: HomeNetworks,
    unread: Set<String>,
    onTab: (Int) -> Unit,
    onAdd: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScrollableTabRow(
                selectedTabIndex = selected.coerceIn(0, tabs.lastIndex.coerceAtLeast(0)),
                edgePadding = 8.dp,
                containerColor = MaterialTheme.colorScheme.background,
                divider = {},
                modifier = Modifier.weight(1f),
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = index == selected,
                        onClick = { onTab(index) },
                        text = { TabTitle(tab, ui, networks, tab.key in unread) },
                        selectedContentColor = MaterialTheme.colorScheme.onBackground,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, stringResource(Res.string.home_add_timeline)) }
        }
        Hairline()
    }
}

@Composable
private fun TabTitle(tab: HomeTab, ui: HomeUi, networks: HomeNetworks, unread: Boolean) {
    UnreadDot(unread) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (tab is HomeTab.Network) {
                networks.Mark(tab.id, 18.dp)
                Spacer(Modifier.width(6.dp))
            }
            Text(tabLabel(tab, ui), maxLines = 1)
        }
    }
}

@Composable
internal fun tabLabel(tab: HomeTab, ui: HomeUi): String = when (tab) {
    HomeTab.Following -> stringResource(Res.string.following)
    HomeTab.ForYou -> stringResource(Res.string.home_for_you)
    is HomeTab.Group -> tab.label
    is HomeTab.Network -> ui.networks.firstOrNull { it.id == tab.id }?.title ?: tab.id
}

/**
 * The top bar's buttons for [tab]: search; for For you the search of its
 * loaded posts and Home feed accounts; for a group the search of its loaded
 * posts and its own screen once there is one; for a network its pinned
 * options and the menu of them all; on the desktop, refresh.
 */
@Composable
private fun RowScope.TabActions(
    tab: HomeTab?,
    ui: HomeUi,
    networks: HomeNetworks,
    routes: HomeRoutes,
    refresh: () -> Unit,
    calls: FeedPageCalls,
    onPins: (String, List<String>) -> Unit,
) {
    IconButton(onClick = { routes.openIfExists(HomeDestination.SEARCH.route) }) { Icon(Icons.Filled.Search, stringResource(Res.string.search)) }
    when (tab) {
        HomeTab.Following, HomeTab.ForYou -> {
            LoadedSearchButton { calls.openLayer(HomeLayer.LOADED_SEARCH) }
            FeedAccountsButton(HomeFilter.turnedOff(ui.filter, ui.session.accounts, ui.session.groups)) { calls.openSheet(HomeSheet.FILTER) }
        }
        is HomeTab.Group -> {
            LoadedSearchButton { calls.openLayer(HomeLayer.LOADED_SEARCH) }
            if (routes.exists(HomeRoutes.GROUP)) {
                IconButton(onClick = { routes.openGroup(tab.id) }) { Icon(HomeIcons.OpenInNew, stringResource(Res.string.home_open_group)) }
            }
        }
        is HomeTab.Network -> key(tab.id) { NetworkActions(tab.id, ui, networks, onPins) }
        null -> Unit
    }
    if (LocalDesktopActions.current != null && tab != null) {
        IconButton(onClick = refresh) { Icon(Icons.Filled.Refresh, stringResource(Res.string.home_refresh)) }
    }
}

/** Home feed accounts (homeFeedAccountsButton): a person, with a dot while accounts or groups are turned off. */
@Composable
internal fun FeedAccountsButton(off: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        OffDot(off) { Icon(Icons.Outlined.Person, stringResource(Res.string.home_feed_accounts)) }
    }
}

/** A network's options pinned to the bar, in pin order, and the menu of them all, where each can be pinned (XTA's top-bar pins). */
@Composable
internal fun NetworkActions(id: String, ui: HomeUi, networks: HomeNetworks, onPins: (String, List<String>) -> Unit) {
    val options = networks.options(id)
    if (options.isEmpty()) return
    val pins = ui.topBarPins[id].orEmpty()
    TopBarPins.shown(options, pins) { it.id }.forEach { option ->
        IconButton(onClick = option.onClick) { Icon(option.icon, option.label) }
    }
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, stringResource(Res.string.home_more_options)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val pinLabel = stringResource(Res.string.home_pin_to_bar)
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    leadingIcon = { Icon(option.icon, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { onPins(id, TopBarPins.toggled(pins, option.id)) }) {
                            Icon(
                                XIcons.Pin,
                                pinLabel,
                                tint = if (option.id in pins) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                    onClick = {
                        open = false
                        option.onClick()
                    },
                )
            }
        }
    }
}

/** The strip as the feed page shows it: its pager, the last tap asking for a timeline's top, and the tabs with their unread dot. */
internal class StripState(val pager: PagerState, val toTop: ToTop, val unread: Set<String>)
