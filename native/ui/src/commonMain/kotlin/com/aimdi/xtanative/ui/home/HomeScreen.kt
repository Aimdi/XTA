package com.aimdi.xtanative.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.session.DeckGroups
import com.aimdi.xtanative.ui.common.BackHandling
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.common.turnTo
import com.aimdi.xtanative.ui.feed.MediaModes
import com.aimdi.xtanative.ui.feed.ToTop
import com.aimdi.xtanative.ui.feed.feedViewModel
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.home_refresh
import com.aimdi.xtanative.ui.group.CustomSettingsScreen
import com.aimdi.xtanative.ui.group.FeedSettingsSheet
import com.aimdi.xtanative.ui.group.GroupFeedScreen
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.search.LoadedFeedSearchScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** The sheets Home opens over itself. */
internal enum class HomeSheet { FILTER, STRIP, PICKER, NETWORKS, FOLLOWING_FILTERS }

/** What opens over all of Home, its bar included: the search of a timeline's loaded posts, Following's Custom rules. */
internal enum class HomeLayer { LOADED_SEARCH, FOLLOWING_CUSTOM }

/** The screens of other areas that are pages of Home's bar, as the app's navigation makes them: each without a way back. */
class HomeBarScreens(
    val subscriptions: @Composable () -> Unit,
    val trends: @Composable () -> Unit,
    val saved: @Composable () -> Unit,
)

/**
 * Home (XTA's home screen): the reader's pages on the bottom bar (Settings ›
 * Home), the Feed page's strip of timelines (Following, For you, the networks
 * pinned there, the groups) and the account menu behind the reader's mark.
 * The app opens here once XTA's backup has given it an account.
 */
@Composable
fun HomeScreen(actions: PostActions, routes: HomeRoutes, screens: HomeBarScreens) {
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val deckFits = DeckGroups.fits(maxWidth.value, minOf(maxWidth, maxHeight).value)
        HomeShell(HomeParts(ui, vm, networks, actions, routes, screens), deckFits)
    }
}

/** What Home's parts are given. */
private class HomeParts(val ui: HomeUi, val vm: HomeViewModel, val networks: HomeNetworks, val actions: PostActions, val routes: HomeRoutes, val screens: HomeBarScreens)

/** Where Home stands: the bar's page, the strip's tab, the menu, the sheet and what is open over it. */
@Stable
private class HomeShellState(
    val pager: PagerState,
    val drawer: DrawerState,
    val bars: HomeBarsState,
    private val barPage: MutableState<String>,
    private val shownTab: MutableState<String>,
) {
    /** The bar's page shown, by id: it stays shown when the reader reorders the bar. */
    var barId: String
        get() = barPage.value
        set(value) {
            barPage.value = value
        }

    /** The strip's tab shown, by key: it stays shown when tabs come and go. */
    var shownKey: String
        get() = shownTab.value
        set(value) {
            shownTab.value = value
        }

    var toTop by mutableStateOf(ToTop(0, 0))
    var networkTop by mutableIntStateOf(0)
    var visited by mutableStateOf(emptySet<String>())
    var sheet by mutableStateOf<HomeSheet?>(null)
    var layer by mutableStateOf<HomeLayer?>(null)

    /** The page shown; the first when it left the bar. */
    fun page(ui: HomeUi): BarPage = ui.bar.firstOrNull { it.id == barId } ?: ui.bar.first()

    /** [page] shown, with the bars back on screen, as a page change brings them. */
    fun show(page: BarPage) {
        barId = page.id
        bars.show()
    }
}

@Composable
private fun rememberShell(ui: HomeUi): HomeShellState {
    val tabs = ui.tabs
    val pager = rememberPagerState(initialPage = ui.initialIndex.coerceIn(0, tabs.lastIndex.coerceAtLeast(0))) { tabs.size }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val bars = rememberHomeBars(ui.hideOnScroll)
    val bar = rememberSaveable { mutableStateOf(ui.bar.getOrElse(ui.initialBarIndex) { ui.bar.first() }.id) }
    val shown = rememberSaveable { mutableStateOf(tabs.getOrNull(pager.currentPage)?.key ?: HomeTab.Following.key) }
    return remember(pager, drawer, bars) { HomeShellState(pager, drawer, bars, bar, shown) }
}

@Composable
private fun HomeShell(parts: HomeParts, deckFits: Boolean) {
    val ui = parts.ui
    val shell = rememberShell(ui)
    val scope = rememberCoroutineScope()
    val reduced = LocalReducedMotion.current
    val graph = LocalGraph.current
    val following: FollowingSettingsViewModel = viewModel { FollowingSettingsViewModel(graph) }
    KeepShownTab(shell, ui.tabs, parts.vm)
    TakeRequests(shell, ui)
    val unread = unreadKeys(ui, shell.visited, parts.networks)
    val page = shell.page(ui)
    val refresh = rememberRefresh(page, ui.tabs.getOrNull(shell.pager.currentPage), parts.networks)
    fun showTab(index: Int) {
        if (index !in ui.tabs.indices) return
        if (index == shell.pager.currentPage) shell.toTop = ToTop(index, shell.toTop.count + 1) else scope.launch { shell.pager.turnTo(index, reduced) }
    }
    DesktopShortcuts(
        refresh = refresh,
        turn = { by -> if (page == BarPage.Feed) DesktopKeys.turned(shell.pager.currentPage, ui.tabs.size, by)?.let { scope.launch { shell.pager.turnTo(it, reduced) } } },
        isOpen = { shell.drawer.isOpen || shell.sheet != null || shell.layer != null },
        close = {
            shell.sheet = null
            shell.layer = null
            scope.launch { shell.drawer.close() }
        },
    )
    val feedCalls = FeedPageCalls(
        openMenu = { scope.launch { shell.drawer.open() } },
        openSheet = { shell.sheet = it },
        openLayer = { shell.layer = it },
        onTab = ::showTab,
        toTitleTop = { shell.toTop = ToTop(shell.pager.currentPage, shell.toTop.count + 1) },
        onOrder = { sort ->
            following.actions.setSort(sort)
            if (FollowingBar.opensCustom(sort)) shell.layer = HomeLayer.FOLLOWING_CUSTOM
        },
    )
    Box(Modifier.fillMaxSize()) {
        HomeDrawer(parts, shell, deckFits, unread) {
            HomeBody(parts, shell, page, followingLook(following, ui, unread), unread, refresh, feedCalls)
        }
        HomeLayers(parts, shell, following)
    }
    HomeSheets(parts, shell, following, unread, onShowTab = ::showTab)
}

/** The account menu (XTA's home drawer) around Home, opened from the reader's mark or the timeline picker. */
@Composable
private fun HomeDrawer(parts: HomeParts, shell: HomeShellState, deckFits: Boolean, unread: Set<String>, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val reduced = LocalReducedMotion.current
    fun go(route: String) {
        scope.launch { shell.drawer.snapTo(DrawerValue.Closed) }
        parts.routes.openIfExists(route)
    }
    ModalNavigationDrawer(
        drawerState = shell.drawer,
        // Swipes belong to the strip's pages; the drawer opens from the reader's mark, and closes with a swipe too.
        gesturesEnabled = shell.drawer.isOpen,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.background) {
                AccountMenu(
                    ui = parts.ui,
                    routes = parts.routes,
                    deckFits = deckFits,
                    unread = unread,
                    onGo = ::go,
                    onGroup = { group -> openGroup(group, parts.ui.tabs, parts.routes, shell.drawer, scope) { index -> shell.pager.turnTo(index, reduced) } },
                    onClose = { scope.launch { shell.drawer.close() } },
                )
            }
        },
        content = content,
    )
}

/** The bar's page shown, above the bar, which slips away as the page is scrolled down. */
@Composable
private fun HomeBody(parts: HomeParts, shell: HomeShellState, page: BarPage, following: FollowingLook, unread: Set<String>, refresh: () -> Unit, feedCalls: FeedPageCalls) {
    val ui = parts.ui
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            HomeBottomBar(
                ui,
                ui.bar.indexOf(page),
                shell.bars,
                parts.networks,
                onPage = { index -> barTapped(shell, ui, index) },
                onNetworks = { shell.sheet = HomeSheet.NETWORKS },
                onHoldHome = { shell.sheet = HomeSheet.PICKER },
            )
        },
    ) { padding ->
        val bottom = PaddingValues(bottom = padding.calculateBottomPadding())
        Box(Modifier.fillMaxSize().padding(bottom).consumeWindowInsets(bottom).nestedScroll(shell.bars.connection)) {
            BarPageHost(page) { shown ->
                when (shown) {
                    BarPage.Feed -> HomeFeedPage(ui, parts.vm, StripState(shell.pager, shell.toTop, unread), following, parts.networks, parts.actions, parts.routes, refresh, feedCalls)
                    BarPage.Subscriptions -> parts.screens.subscriptions()
                    BarPage.Trending -> parts.screens.trends()
                    BarPage.Saved -> parts.screens.saved()
                    is BarPage.Network -> NetworkPage(shown.id, ui, parts.networks, shell.networkTop, refresh, parts.vm::setTopBarPins)
                    is BarPage.Group -> GroupFeedScreen(shown.groupId, parts.actions, onBack = null)
                }
            }
        }
    }
}

/**
 * A page of the bar, its state kept while another is shown, as XTA keeps its
 * pages alive; it fades in, at once while motion is reduced.
 */
@Composable
private fun BarPageHost(page: BarPage, content: @Composable (BarPage) -> Unit) {
    val holder = rememberSaveableStateHolder()
    val reduced = LocalReducedMotion.current
    Crossfade(targetState = page, animationSpec = if (reduced) snap() else tween(PAGE_FADE_MS), label = "home-page") { shown ->
        holder.SaveableStateProvider(shown.id) { content(shown) }
    }
}

private const val PAGE_FADE_MS = 150

/**
 * The bar tapped (_onBarDestination): another page is shown at once; its own
 * page again goes back to the top of the timeline shown.
 */
private fun barTapped(shell: HomeShellState, ui: HomeUi, index: Int) {
    val tapped = ui.bar.getOrNull(index) ?: return
    shell.bars.show()
    if (tapped != shell.page(ui)) {
        shell.show(tapped)
        return
    }
    when (tapped) {
        BarPage.Feed -> shell.toTop = ToTop(shell.pager.currentPage, shell.toTop.count + 1)
        is BarPage.Network -> shell.networkTop++
        else -> Unit
    }
}

/** A network as a page of the bar: its name and options over its timeline. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetworkPage(id: String, ui: HomeUi, networks: HomeNetworks, toTop: Int, refresh: () -> Unit, onPins: (String, List<String>) -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(ui.networks.firstOrNull { it.id == id }?.title ?: id, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    key(id) { NetworkActions(id, ui, networks, onPins) }
                    if (LocalDesktopActions.current != null) IconButton(onClick = refresh) { Icon(Icons.Filled.Refresh, stringResource(Res.string.home_refresh)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            networks.Page(id, PaddingValues(bottom = padding.calculateBottomPadding()), toTop)
        }
    }
}

/** What Following's bar shows. */
@Composable
private fun followingLook(following: FollowingSettingsViewModel, ui: HomeUi, unread: Set<String>): FollowingLook {
    val feed by following.ui.collectAsStateWithLifecycle()
    val media by MediaModes.keys.collectAsStateWithLifecycle()
    return FollowingLook(
        feed = feed,
        mediaOnly = FollowingBar.MEDIA_KEY in media,
        unread = SourcePicker.sources(ui.tabs).any { it.key in unread },
        accountsOff = HomeFilter.turnedOff(ui.filter, ui.session.accounts, ui.session.groups),
    )
}

/** The strip's tab shown stays shown as tabs come and go (a network pinned before it, a group gone); a tab opened is remembered. */
@Composable
private fun KeepShownTab(shell: HomeShellState, tabs: List<HomeTab>, vm: HomeViewModel) {
    val latestTabs by rememberUpdatedState(tabs)
    LaunchedEffect(shell.pager) {
        snapshotFlow { shell.pager.currentPage }.collect { page ->
            val tab = latestTabs.getOrNull(page) ?: return@collect
            shell.shownKey = tab.key
            vm.opened(tab)
        }
    }
    LaunchedEffect(tabs) {
        val index = HomeStrip.indexOf(tabs, shell.shownKey)
        if (index != shell.pager.currentPage) shell.pager.scrollToPage(index)
    }
    LaunchedEffect(shell.shownKey) { shell.visited = shell.visited + shell.shownKey }
}

/** A timeline another screen asked for (a group's title menu): the Feed page, on that tab. */
@Composable
private fun TakeRequests(shell: HomeShellState, ui: HomeUi) {
    val asked by HomeRequests.tab.collectAsStateWithLifecycle()
    LaunchedEffect(asked, ui.tabs) {
        val key = asked ?: return@LaunchedEffect
        HomeRequests.taken(key)
        if (BarPage.Feed in ui.bar) shell.show(BarPage.Feed)
        shell.shownKey = key
        shell.pager.scrollToPage(HomeStrip.indexOf(ui.tabs, key))
    }
}

/** What opens over all of Home: the search of the shown timeline's loaded posts, and Following's Custom rules. */
@Composable
private fun HomeLayers(parts: HomeParts, shell: HomeShellState, following: FollowingSettingsViewModel) {
    when (shell.layer) {
        HomeLayer.LOADED_SEARCH -> LoadedPostsSearch(parts.ui.tabs.getOrNull(shell.pager.currentPage), parts.actions) { shell.layer = null }
        HomeLayer.FOLLOWING_CUSTOM -> {
            val feed by following.ui.collectAsStateWithLifecycle()
            BackHandling(enabled = true) { shell.layer = null }
            CustomSettingsScreen(feed, following.actions, onBack = { shell.layer = null })
        }
        null -> Unit
    }
}

/** The search of what [tab]'s feed has loaded (XTA's LoadedSearchButton): Following's, For you's or a group's. */
@Composable
private fun LoadedPostsSearch(tab: HomeTab?, actions: PostActions, onClose: () -> Unit) {
    val threads = when (tab) {
        HomeTab.ForYou -> forYouViewModel().ui.collectAsStateWithLifecycle().value.threads
        HomeTab.Following, is HomeTab.Group -> feedViewModel((tab as? HomeTab.Group)?.id).ui.collectAsStateWithLifecycle().value.threads
        is HomeTab.Network, null -> emptyList()
    }
    LoadedFeedSearchScreen(threads, actions, onClose)
}

/** The sheets over Home: Home feed accounts, the strip's timelines, the timeline picker, the bar's networks and Following's filters. */
@Composable
private fun HomeSheets(parts: HomeParts, shell: HomeShellState, following: FollowingSettingsViewModel, unread: Set<String>, onShowTab: (Int) -> Unit) {
    val ui = parts.ui
    val scope = rememberCoroutineScope()
    val reduced = LocalReducedMotion.current
    val close = { shell.sheet = null }
    when (shell.sheet) {
        HomeSheet.FILTER -> HomeFilterSheet(ui, onApply = { draft ->
            parts.vm.apply(draft)
            close()
        }, onDismiss = close)
        HomeSheet.STRIP -> StripSheet(
            ui,
            parts.networks,
            parts.routes,
            onOpen = { id ->
                close()
                showFeedTab(shell, ui, HomeTab.Network(id).key, onShowTab)
            },
            onPin = parts.vm::pin,
            onUnpin = parts.vm::unpin,
            onMove = parts.vm::move,
            onDismiss = close,
        )
        HomeSheet.PICKER -> SourcePickerSheet(
            ui,
            parts.networks,
            shell.shownKey,
            unread,
            PickerCalls(
                onSource = { tab ->
                    close()
                    showFeedTab(shell, ui, tab.key, onShowTab)
                },
                onGroup = { group ->
                    close()
                    openGroup(group, ui.tabs, parts.routes, shell.drawer, scope) { index -> shell.pager.turnTo(index, reduced) }
                },
                onAdd = { shell.sheet = HomeSheet.STRIP },
                onMenu = {
                    close()
                    scope.launch { shell.drawer.open() }
                },
                onDismiss = close,
            ),
        )
        HomeSheet.NETWORKS -> NetworksSheet(
            ui,
            parts.networks,
            current = (shell.page(ui) as? BarPage.Network)?.id,
            onPick = { id ->
                close()
                parts.vm.touched(id)
                ui.bar.indexOf(BarPage.Network(id)).takeIf { it >= 0 }?.let { barTapped(shell, ui, it) }
            },
            onAdd = { shell.sheet = HomeSheet.STRIP },
            onDismiss = close,
        )
        HomeSheet.FOLLOWING_FILTERS -> {
            val feed by following.ui.collectAsStateWithLifecycle()
            FeedSettingsSheet(feed, following.actions, onCustom = {
                close()
                shell.layer = HomeLayer.FOLLOWING_CUSTOM
            }, onDismiss = close, following = true)
        }
        null -> Unit
    }
}

/** The Feed page on the strip's tab [key]: a network just pinned is not on the strip yet, and is shown once it is. */
private fun showFeedTab(shell: HomeShellState, ui: HomeUi, key: String, onShowTab: (Int) -> Unit) {
    if (BarPage.Feed in ui.bar) shell.show(BarPage.Feed)
    val index = ui.tabs.indexOfFirst { it.key == key }
    if (index >= 0) onShowTab(index) else shell.shownKey = key
}

/** A group picked in the menu or the picker: its own screen once the app has one, else its tab, which [showTab] turns to. */
private fun openGroup(
    group: FollowGroup,
    tabs: List<HomeTab>,
    routes: HomeRoutes,
    drawer: DrawerState,
    scope: CoroutineScope,
    showTab: suspend (Int) -> Unit,
) {
    if (routes.exists(HomeRoutes.GROUP)) {
        // Closed at once: an animation cut short by leaving would come back open.
        scope.launch { drawer.snapTo(DrawerValue.Closed) }
        routes.openGroup(group.id)
        return
    }
    scope.launch { drawer.close() }
    val index = tabs.indexOfFirst { it is HomeTab.Group && it.id == group.id }
    if (index >= 0) scope.launch { showTab(index) }
}

/**
 * The tabs with the unread dot. A Following or group feed already opened
 * says whether it put posts above the reader they have not seen; a feed not
 * opened yet is not read for it, as reading it would ask X. Networks answer
 * for themselves.
 */
@Composable
private fun unreadKeys(ui: HomeUi, visited: Set<String>, networks: HomeNetworks): Set<String> {
    return buildSet {
        ui.tabs.forEach { tab ->
            val lit = key(tab.key) {
                when (tab) {
                    HomeTab.Following, is HomeTab.Group -> tab.key in visited && feedUnread(tab, ui)
                    is HomeTab.Network -> networks.unread(tab.id)
                    HomeTab.ForYou -> false
                }
            }
            if (lit) add(tab.key)
        }
    }
}

@Composable
private fun feedUnread(tab: HomeTab, ui: HomeUi): Boolean {
    val groupId = (tab as? HomeTab.Group)?.id
    val vm = feedViewModel(groupId)
    // Only the count above the reader: the feed's other changes need not redraw Home.
    val newAbove by remember(vm) { vm.ui.map { it.newAbove }.distinctUntilChanged() }.collectAsStateWithLifecycle(vm.ui.value.newAbove)
    return HomeUnread.unread(HomeUnread.tracks(ui.session, tab, ui.readingPosition), newAbove)
}

/** What refreshing the page shown does: its timeline read again, the desktop's button and F5. */
@Composable
private fun rememberRefresh(page: BarPage, tab: HomeTab?, networks: HomeNetworks): () -> Unit = key(page.id, tab?.key) {
    when (page) {
        BarPage.Feed -> rememberTabRefresh(tab, networks)
        is BarPage.Network -> remember(page.id) { { networks.refresh(page.id) } }
        is BarPage.Group, BarPage.Subscriptions, BarPage.Trending, BarPage.Saved -> remember { {} }
    }
}

@Composable
private fun rememberTabRefresh(tab: HomeTab?, networks: HomeNetworks): () -> Unit = when (tab) {
    HomeTab.Following, is HomeTab.Group -> {
        val vm = feedViewModel((tab as? HomeTab.Group)?.id)
        remember(vm) { { vm.refresh() } }
    }
    HomeTab.ForYou -> {
        val vm = forYouViewModel()
        remember(vm) { { vm.refresh() } }
    }
    is HomeTab.Network -> remember(tab.id) { { networks.refresh(tab.id) } }
    null -> remember { {} }
}
