package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.session.DeckGroups
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.feed.FeedPage
import com.aimdi.xtanative.ui.feed.FeedViewModel
import com.aimdi.xtanative.ui.feed.ToTop
import com.aimdi.xtanative.ui.feed.feedViewModelKey
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** The sheets Home opens over itself. */
private enum class HomeSheet { FILTER, STRIP }

/**
 * Home (XTA's home screen): the account menu behind the reader's mark, the
 * strip of timelines (Following, For you, the networks pinned there, the
 * groups) and the timeline picked, swiped or tapped to. The app opens here
 * once XTA's backup has given it an account.
 */
@Composable
fun HomeScreen(actions: PostActions, routes: HomeRoutes) {
    val graph = LocalGraph.current
    val networks = LocalHomeNetworks.current
    val vm: HomeViewModel = viewModel { HomeViewModel(graph, networks.available) }
    val loaded by vm.ui.collectAsStateWithLifecycle()
    val ui = loaded
    if (ui == null) {
        // The settings are read in a few milliseconds; until then, the theme's background.
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    var startOpened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!startOpened) {
            startOpened = true
            ui.startRoute?.let(routes::openIfExists)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val deckFits = DeckGroups.fits(maxWidth.value, minOf(maxWidth, maxHeight).value)
        HomeShell(ui, vm, networks, actions, routes, deckFits)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeShell(ui: HomeUi, vm: HomeViewModel, networks: HomeNetworks, actions: PostActions, routes: HomeRoutes, deckFits: Boolean) {
    val tabs = ui.tabs
    val pager = rememberPagerState(initialPage = ui.initialIndex.coerceIn(0, tabs.lastIndex.coerceAtLeast(0))) { tabs.size }
    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val bar = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var toTop by remember { mutableStateOf(ToTop(0, 0)) }
    var sheet by remember { mutableStateOf<HomeSheet?>(null) }

    // The tab shown stays shown when tabs come and go: a network pinned before it, a group gone.
    val latestTabs by rememberUpdatedState(tabs)
    var shownKey by rememberSaveable { mutableStateOf(tabs.getOrNull(pager.currentPage)?.key ?: HomeTab.Following.key) }
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { page ->
            val tab = latestTabs.getOrNull(page) ?: return@collect
            shownKey = tab.key
            vm.opened(tab)
        }
    }
    LaunchedEffect(tabs) {
        val index = HomeStrip.indexOf(tabs, shownKey)
        if (index != pager.currentPage) pager.scrollToPage(index)
    }
    var visited by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(shownKey) { visited = visited + shownKey }

    val current = tabs.getOrNull(pager.currentPage)
    val unread = unreadKeys(ui, visited, networks)
    val refresh = rememberRefresh(current, networks)
    fun show(index: Int) {
        if (index !in tabs.indices) return
        if (index == pager.currentPage) toTop = ToTop(index, toTop.count + 1) else scope.launch { pager.animateScrollToPage(index) }
    }
    fun go(route: String) {
        scope.launch { drawer.snapTo(DrawerValue.Closed) }
        routes.openIfExists(route)
    }
    DesktopShortcuts(
        refresh = refresh,
        turn = { by -> DesktopKeys.turned(pager.currentPage, tabs.size, by)?.let { scope.launch { pager.animateScrollToPage(it) } } },
        isOpen = { drawer.isOpen || sheet != null },
        close = {
            sheet = null
            scope.launch { drawer.close() }
        },
    )

    ModalNavigationDrawer(
        drawerState = drawer,
        // Swipes belong to the strip's pages; the drawer opens from the reader's mark, and closes with a swipe too.
        gesturesEnabled = drawer.isOpen,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.background) {
                AccountMenu(
                    ui = ui,
                    routes = routes,
                    deckFits = deckFits,
                    unread = unread,
                    onGo = ::go,
                    onGroup = { group -> openGroup(group, tabs, routes, drawer, pager, scope) },
                    onClose = { scope.launch { drawer.close() } },
                )
            }
        },
    ) {
        Scaffold(
            modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Column {
                    TopAppBar(
                        navigationIcon = { MenuButton(ui) { scope.launch { drawer.open() } } },
                        title = {
                            Text(
                                current?.let { tabLabel(it, ui) }.orEmpty(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable { toTop = ToTop(pager.currentPage, toTop.count + 1) },
                            )
                        },
                        actions = {
                            TabActions(current, ui, networks, routes, refresh, onSearch = { routes.openIfExists(HomeDestination.SEARCH.route) }, onFilter = { sheet = HomeSheet.FILTER }, onPins = vm::setTopBarPins)
                        },
                        scrollBehavior = bar,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            scrolledContainerColor = MaterialTheme.colorScheme.background,
                        ),
                    )
                    Strip(tabs, pager.currentPage, ui, networks, unread, onTab = ::show, onAdd = { sheet = HomeSheet.STRIP })
                }
            },
        ) { padding ->
            HorizontalPager(
                state = pager,
                key = { page -> tabs.getOrNull(page)?.key ?: page },
                modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
            ) { page ->
                val bottom = PaddingValues(bottom = padding.calculateBottomPadding())
                when (val tab = tabs.getOrNull(page)) {
                    HomeTab.Following -> FeedPage(null, page, toTop, actions, bottom)
                    HomeTab.ForYou -> ForYouPage(page, toTop, actions, bottom, onImport = { routes.openIfExists(HomeRoutes.IMPORT) })
                    is HomeTab.Group -> FeedPage(tab.id, page, toTop, actions, bottom)
                    is HomeTab.Network -> networks.Page(tab.id, bottom, if (toTop.page == page) toTop.count else 0)
                    null -> Unit
                }
            }
        }
    }

    when (sheet) {
        HomeSheet.FILTER -> HomeFilterSheet(
            ui,
            onApply = { draft ->
                vm.apply(draft)
                sheet = null
            },
            onDismiss = { sheet = null },
        )
        HomeSheet.STRIP -> StripSheet(
            ui,
            networks,
            routes,
            onOpen = { id ->
                sheet = null
                val key = HomeTab.Network(id).key
                val index = tabs.indexOfFirst { it.key == key }
                // A network just pinned is not on the strip yet: it is shown once it is.
                if (index >= 0) show(index) else shownKey = key
            },
            onPin = vm::pin,
            onUnpin = vm::unpin,
            onMove = vm::move,
            onDismiss = { sheet = null },
        )
        null -> Unit
    }
}

/** A group picked in the menu: its own screen once the app has one, else its tab. */
private fun openGroup(group: FollowGroup, tabs: List<HomeTab>, routes: HomeRoutes, drawer: DrawerState, pager: PagerState, scope: CoroutineScope) {
    if (routes.exists(HomeRoutes.GROUP)) {
        // Closed at once: an animation cut short by leaving would come back open.
        scope.launch { drawer.snapTo(DrawerValue.Closed) }
        routes.openGroup(group.id)
        return
    }
    scope.launch { drawer.close() }
    val index = tabs.indexOfFirst { it is HomeTab.Group && it.id == group.id }
    if (index >= 0) scope.launch { pager.animateScrollToPage(index) }
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
private fun tabLabel(tab: HomeTab, ui: HomeUi): String = when (tab) {
    HomeTab.Following -> stringResource(Res.string.following)
    HomeTab.ForYou -> stringResource(Res.string.home_for_you)
    is HomeTab.Group -> tab.label
    is HomeTab.Network -> ui.networks.firstOrNull { it.id == tab.id }?.title ?: tab.id
}

/**
 * The top bar's buttons for [tab]: search; for the X timelines Home feed
 * accounts; for a group its own screen once there is one; for a network its
 * pinned options and the menu of them all; on the desktop, refresh.
 */
@Composable
private fun RowScope.TabActions(
    tab: HomeTab?,
    ui: HomeUi,
    networks: HomeNetworks,
    routes: HomeRoutes,
    refresh: () -> Unit,
    onSearch: () -> Unit,
    onFilter: () -> Unit,
    onPins: (String, List<String>) -> Unit,
) {
    IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, stringResource(Res.string.search)) }
    when (tab) {
        HomeTab.Following, HomeTab.ForYou -> {
            val off = HomeFilter.turnedOff(ui.filter, ui.session.accounts, ui.session.groups)
            IconButton(onClick = onFilter) {
                OffDot(off) { Icon(Icons.Outlined.Person, stringResource(Res.string.home_feed_accounts)) }
            }
        }
        is HomeTab.Group -> if (routes.exists(HomeRoutes.GROUP)) {
            IconButton(onClick = { routes.openGroup(tab.id) }) { Icon(HomeIcons.OpenInNew, stringResource(Res.string.home_open_group)) }
        }
        is HomeTab.Network -> key(tab.id) { NetworkActions(tab.id, ui, networks, onPins) }
        null -> Unit
    }
    if (LocalDesktopActions.current != null && tab != null) {
        IconButton(onClick = refresh) { Icon(Icons.Filled.Refresh, stringResource(Res.string.home_refresh)) }
    }
}

/** A network's options pinned to the bar, in pin order, and the menu of them all, where each can be pinned (XTA's top-bar pins). */
@Composable
private fun NetworkActions(id: String, ui: HomeUi, networks: HomeNetworks, onPins: (String, List<String>) -> Unit) {
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

/**
 * The tabs with the unread dot. A Following or group feed already opened
 * says whether it put posts above the reader they have not seen; a feed not
 * opened yet is not read for it, as reading it would ask X. Networks answer
 * for themselves.
 */
@Composable
private fun unreadKeys(ui: HomeUi, visited: Set<String>, networks: HomeNetworks): Set<String> {
    val graph = LocalGraph.current
    return buildSet {
        ui.tabs.forEach { tab ->
            val lit = key(tab.key) {
                when (tab) {
                    HomeTab.Following, is HomeTab.Group -> tab.key in visited && feedUnread(tab, ui, graph)
                    is HomeTab.Network -> networks.unread(tab.id)
                    HomeTab.ForYou -> false
                }
            }
            if (lit) add(tab.key)
        }
    }
}

@Composable
private fun feedUnread(tab: HomeTab, ui: HomeUi, graph: AppGraph): Boolean {
    val groupId = (tab as? HomeTab.Group)?.id
    val vm: FeedViewModel = viewModel(key = feedViewModelKey(groupId)) { FeedViewModel(graph, groupId) }
    // Only the count above the reader: the feed's other changes need not redraw Home.
    val newAbove by remember(vm) { vm.ui.map { it.newAbove }.distinctUntilChanged() }.collectAsStateWithLifecycle(vm.ui.value.newAbove)
    return HomeUnread.unread(HomeUnread.tracks(ui.session, tab, ui.readingPosition), newAbove)
}

/** What refreshing [tab] does: its feed read again, the desktop's button and F5. */
@Composable
private fun rememberRefresh(tab: HomeTab?, networks: HomeNetworks): () -> Unit {
    val graph = LocalGraph.current
    return key(tab?.key) {
        when (tab) {
            HomeTab.Following, is HomeTab.Group -> {
                val groupId = (tab as? HomeTab.Group)?.id
                val vm: FeedViewModel = viewModel(key = feedViewModelKey(groupId)) { FeedViewModel(graph, groupId) }
                remember(vm) { { vm.refresh() } }
            }
            HomeTab.ForYou -> {
                val vm: ForYouViewModel = viewModel(key = FOR_YOU_VIEW_MODEL) { ForYouViewModel(graph) }
                remember(vm) { { vm.refresh() } }
            }
            is HomeTab.Network -> remember(tab.id) { { networks.refresh(tab.id) } }
            null -> remember { {} }
        }
    }
}
