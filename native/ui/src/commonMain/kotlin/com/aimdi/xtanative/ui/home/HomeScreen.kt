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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.session.DeckGroups
import com.aimdi.xtanative.ui.common.BackHandling
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.common.rememberScreenReaderOn
import com.aimdi.xtanative.ui.feed.MediaModes
import com.aimdi.xtanative.ui.feed.ToTop
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.home_refresh
import com.aimdi.xtanative.ui.group.CustomSettingsScreen
import com.aimdi.xtanative.ui.group.FeedSettingsSheet
import com.aimdi.xtanative.ui.group.GroupFeedScreen
import com.aimdi.xtanative.ui.group.groupFeedViewModel
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.search.LoadedFeedSearchScreen
import com.aimdi.xtanative.ui.settings.AvatarSheet
import com.aimdi.xtanative.ui.settings.SettingsRoutes
import com.aimdi.xtanative.ui.settings.SettingsSection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** The sheets Home opens over itself. */
internal enum class HomeSheet { FILTER, STRIP, PICKER, NETWORKS, FOLLOWING_FILTERS, AVATAR }

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
 * Home), the Feed page's timelines (Following, X and the networks pinned
 * there), turned by a drag or picked in the timeline picker, and the account
 * menu the picker leads to. The app opens here once XTA's backup has given it
 * an account.
 */
@Composable
fun HomeScreen(actions: PostActions, routes: HomeRoutes, screens: HomeBarScreens) {
    val graph = LocalGraph.current
    val networks = LocalHomeNetworks.current
    val vm: HomeViewModel = viewModel { HomeViewModel(graph, networks.available, networks.missing) }
    val loaded by vm.ui.collectAsStateWithLifecycle()
    val failure by vm.failure.collectAsStateWithLifecycle()
    val ui = loaded
    val failed = failure
    if (ui == null && failed != null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { PagesFailed(failed, vm::resetPages) }
        return
    }
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

/** Where Home stands: the bar's page, the timeline shown, the menu, the sheet and what is open over it. */
@Stable
private class HomeShellState(
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

    /** The timeline shown, by key: it stays shown when timelines come and go. */
    var shownKey: String
        get() = shownTab.value
        set(value) {
            shownTab.value = value
        }

    var toTop by mutableStateOf(ToTop(0, 0))
    var networkTop by mutableIntStateOf(0)

    /** How often each page's place on the bar was tapped again, by the page's id. */
    var retaps by mutableStateOf(emptyMap<String, Int>())
    var sheet by mutableStateOf<HomeSheet?>(null)
    var layer by mutableStateOf<HomeLayer?>(null)

    /** The page shown; the first when it left the bar. */
    fun page(ui: HomeUi): BarPage = ui.bar.firstOrNull { it.id == barId } ?: ui.bar.first()

    /** [page] shown, with the bars back on screen, as a page change brings them. */
    fun show(page: BarPage) {
        barId = page.id
        bars.show()
    }

    /** Where the timeline shown stands among [ui]'s: Following's place when it went, as XTA falls back to Following. */
    fun index(ui: HomeUi): Int = HomeStrip.indexOf(ui.tabs, shownKey)

    /** The timeline at [index] shown, or, when it is the one shown, back at its top. */
    fun turnTo(ui: HomeUi, index: Int) {
        val tab = ui.tabs.getOrNull(index) ?: return
        if (tab.key == ui.tabs.getOrNull(index(ui))?.key) toTop = ToTop(index, toTop.count + 1) else shownKey = tab.key
    }

    fun retapped(page: BarPage) {
        retaps = retaps + (page.id to (retaps[page.id] ?: 0) + 1)
    }
}

@Composable
private fun rememberShell(ui: HomeUi): HomeShellState {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    // A screen reader keeps the bars put, as XTA's does.
    val bars = rememberHomeBars(ui.hideOnScroll && !rememberScreenReaderOn())
    val bar = rememberSaveable { mutableStateOf(ui.bar.getOrElse(ui.initialBarIndex) { ui.bar.first() }.id) }
    val shown = rememberSaveable { mutableStateOf(ui.tabs.getOrNull(ui.initialIndex)?.key ?: HomeTab.Following.key) }
    return remember(drawer, bars) { HomeShellState(drawer, bars, bar, shown) }
}

@Composable
private fun HomeShell(parts: HomeParts, deckFits: Boolean) {
    val ui = parts.ui
    val shell = rememberShell(ui)
    val scope = rememberCoroutineScope()
    val graph = LocalGraph.current
    // Following's order and rules alone: its feed is read only while it is shown, as XTA mounts it.
    val following: FollowingSettingsViewModel = viewModel { FollowingSettingsViewModel(graph) }
    KeepShownTab(shell, ui, parts.vm)
    TakeRequests(shell, ui)
    val unread = unreadKeys(ui, parts.vm, parts.networks)
    val page = shell.page(ui)
    val index = shell.index(ui)
    val refresh = rememberRefresh(page, ui.tabs.getOrNull(index), parts.networks)
    DesktopShortcuts(
        refresh = refresh,
        turn = { by -> if (page == BarPage.Feed) DesktopKeys.turned(index, ui.tabs.size, by)?.let { shell.turnTo(ui, it) } },
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
        onTab = { shell.turnTo(ui, it) },
        toTitleTop = { shell.toTop = ToTop(index, shell.toTop.count + 1) },
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
    HomeSheets(parts, shell, following, unread)
}

/** The account menu (XTA's home drawer) around Home, opened from the timeline picker. */
@Composable
private fun HomeDrawer(parts: HomeParts, shell: HomeShellState, deckFits: Boolean, unread: Set<String>, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    fun go(route: String) {
        scope.launch { shell.drawer.snapTo(DrawerValue.Closed) }
        parts.routes.openIfExists(route)
    }
    LaunchedEffect(shell.drawer.isOpen) { if (shell.drawer.isOpen) parts.vm.reloadUnread() }
    ModalNavigationDrawer(
        drawerState = shell.drawer,
        // Swipes belong to the timelines: the drawer opens from the picker or a drag from the edge, and closes with a swipe too.
        gesturesEnabled = shell.drawer.isOpen,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.background) {
                AccountMenu(
                    ui = parts.ui,
                    routes = parts.routes,
                    deckFits = deckFits,
                    unread = unread,
                    onGo = ::go,
                    onGroup = { group -> openGroup(group, parts.routes, shell.drawer, scope) },
                    onClose = { scope.launch { shell.drawer.close() } },
                    onAvatar = { shell.sheet = HomeSheet.AVATAR },
                )
            }
        },
        content = { Box(Modifier.fillMaxSize().drawerEdge(shell.drawer)) { content() } },
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
            BarPageHost(page, shell.retaps) { shown ->
                when (shown) {
                    BarPage.Feed -> HomeFeedPage(
                        ui,
                        parts.vm,
                        TimelinesState(shell.index(ui), shell.toTop, unread),
                        following,
                        parts.networks,
                        parts.actions,
                        parts.routes,
                        refresh,
                        feedCalls,
                    )
                    BarPage.Subscriptions -> parts.screens.subscriptions()
                    BarPage.Trending -> parts.screens.trends()
                    BarPage.Saved -> parts.screens.saved()
                    is BarPage.Network -> if (shown.id in ui.missing) {
                        MissingPage(onChoose = { if (parts.routes.exists(HomeDestination.SETTINGS.route)) parts.routes.open(SettingsRoutes.of(SettingsSection.HOME)) })
                    } else {
                        NetworkPage(shown.id, ui, parts.networks, shell.networkTop, refresh, parts.vm::setTopBarPins)
                    }
                    is BarPage.Group -> GroupFeedScreen(shown.groupId, parts.actions, onBack = null)
                }
            }
        }
    }
}

/**
 * A page of the bar, its state kept while another is shown, as XTA keeps its
 * pages alive; it fades in, at once while motion is reduced. Each is told how
 * often its place on the bar was tapped again ([LocalBarRetap]).
 */
@Composable
private fun BarPageHost(page: BarPage, retaps: Map<String, Int>, content: @Composable (BarPage) -> Unit) {
    val holder = rememberSaveableStateHolder()
    val reduced = LocalReducedMotion.current
    Crossfade(targetState = page, animationSpec = if (reduced) snap() else tween(PAGE_FADE_MS), label = "home-page") { shown ->
        holder.SaveableStateProvider(shown.id) {
            CompositionLocalProvider(LocalBarRetap provides (retaps[shown.id] ?: 0)) { content(shown) }
        }
    }
}

private const val PAGE_FADE_MS = 150

/**
 * The bar tapped (_onBarDestination): another page is shown at once, and
 * Discover reached from a network's page searches that network; its own
 * page again goes back to the top of what it shows, and Discover, already
 * there, puts the cursor in its search.
 */
private fun barTapped(shell: HomeShellState, ui: HomeUi, index: Int) {
    val tapped = ui.bar.getOrNull(index) ?: return
    shell.bars.show()
    val from = shell.page(ui)
    if (tapped != from) {
        if (tapped == BarPage.Trending && from is BarPage.Network) HomeRequests.searchIn(from.id)
        shell.show(tapped)
        return
    }
    when (tapped) {
        BarPage.Feed -> shell.toTop = ToTop(shell.index(ui), shell.toTop.count + 1)
        is BarPage.Network -> shell.networkTop++
        else -> shell.retapped(tapped)
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

/**
 * The timeline shown stays shown as timelines come and go (a network pinned
 * before it); one that went gives way to Following, as XTA's Home does. A
 * timeline shown is remembered, and the unread marks are read again.
 */
@Composable
private fun KeepShownTab(shell: HomeShellState, ui: HomeUi, vm: HomeViewModel) {
    LaunchedEffect(ui.tabs) {
        if (ui.tabs.none { it.key == shell.shownKey }) shell.shownKey = HomeTab.Following.key
    }
    LaunchedEffect(shell.shownKey) {
        ui.tabs.firstOrNull { it.key == shell.shownKey }?.let(vm::opened)
        vm.reloadUnread()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.reloadUnread() }
}

/** A timeline another screen asked for (a group's title menu): the Feed page, on that timeline. */
@Composable
private fun TakeRequests(shell: HomeShellState, ui: HomeUi) {
    val asked by HomeRequests.tab.collectAsStateWithLifecycle()
    LaunchedEffect(asked, ui.tabs) {
        val key = asked ?: return@LaunchedEffect
        HomeRequests.taken(key)
        if (BarPage.Feed in ui.bar) shell.show(BarPage.Feed)
        if (ui.tabs.any { it.key == key }) shell.shownKey = key
    }
}

/** What opens over all of Home: the search of the shown timeline's loaded posts, and Following's Custom rules. */
@Composable
private fun HomeLayers(parts: HomeParts, shell: HomeShellState, following: FollowingSettingsViewModel) {
    when (shell.layer) {
        HomeLayer.LOADED_SEARCH -> LoadedPostsSearch(parts.ui.tabs.getOrNull(shell.index(parts.ui)), parts.actions) { shell.layer = null }
        HomeLayer.FOLLOWING_CUSTOM -> {
            val feed by following.ui.collectAsStateWithLifecycle()
            BackHandling(enabled = true) { shell.layer = null }
            CustomSettingsScreen(feed, following.actions, onBack = { shell.layer = null })
        }
        null -> Unit
    }
}

/** The search of what [tab]'s feed has loaded (XTA's LoadedSearchButton): Following's or X's. */
@Composable
private fun LoadedPostsSearch(tab: HomeTab?, actions: PostActions, onClose: () -> Unit) {
    val threads = when (tab) {
        HomeTab.ForYou -> forYouViewModel().ui.collectAsStateWithLifecycle().value.threads
        HomeTab.Following -> groupFeedViewModel(null).ui.collectAsStateWithLifecycle().value.run.shown.threads
        is HomeTab.Network, is HomeTab.Group, null -> emptyList()
    }
    LoadedFeedSearchScreen(threads, actions, onClose)
}

/** The sheets over Home: Home feed accounts, the plugin timelines, the timeline picker, the bar's networks and Following's filters. */
@Composable
private fun HomeSheets(parts: HomeParts, shell: HomeShellState, following: FollowingSettingsViewModel, unread: Set<String>) {
    val ui = parts.ui
    val scope = rememberCoroutineScope()
    val close = { shell.sheet = null }
    LaunchedEffect(shell.sheet) { if (shell.sheet == HomeSheet.PICKER) parts.vm.reloadUnread() }
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
                showTimeline(shell, ui, HomeTab.Network(id).key)
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
                    showTimeline(shell, ui, tab.key)
                },
                onGroup = { group ->
                    close()
                    openGroup(group, parts.routes, shell.drawer, scope)
                },
                onAdd = { shell.sheet = HomeSheet.STRIP },
                onMenu = {
                    close()
                    scope.launch { shell.drawer.open() }
                },
                onAvatar = { shell.sheet = HomeSheet.AVATAR },
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
        HomeSheet.AVATAR -> AvatarSheet(onDismiss = close)
        null -> Unit
    }
}

/** The Feed page on the timeline [key]: back to its top when it is shown; a network just pinned is shown once it is on Home. */
private fun showTimeline(shell: HomeShellState, ui: HomeUi, key: String) {
    if (BarPage.Feed in ui.bar) shell.show(BarPage.Feed)
    val index = ui.tabs.indexOfFirst { it.key == key }
    if (index >= 0) shell.turnTo(ui, index) else shell.shownKey = key
}

/** A group picked in the menu or the picker: its own screen, as XTA opens it. */
private fun openGroup(group: FollowGroup, routes: HomeRoutes, drawer: DrawerState, scope: CoroutineScope) {
    // Closed at once: an animation cut short by leaving would come back open.
    scope.launch { drawer.snapTo(DrawerValue.Closed) }
    routes.openGroup(group.id)
}

/**
 * The feeds with the unread dot: Following, X and the groups by their read
 * marks, as XTA's unread store reads them; networks answer for themselves.
 */
@Composable
private fun unreadKeys(ui: HomeUi, vm: HomeViewModel, networks: HomeNetworks): Set<String> {
    val feeds by vm.unread.collectAsStateWithLifecycle()
    val lit = ui.tabs.filterIsInstance<HomeTab.Network>().filter { tab -> key(tab.id) { networks.unread(tab.id) } }
    return if (lit.isEmpty()) feeds else feeds + lit.map { it.key }
}

/** What refreshing the page shown does: its timeline read again, the desktop's button and F5. */
@Composable
private fun rememberRefresh(page: BarPage, tab: HomeTab?, networks: HomeNetworks): () -> Unit = key(page.id, tab?.key) {
    when (page) {
        BarPage.Feed -> rememberTabRefresh(tab, networks)
        is BarPage.Network -> remember(page.id) { { networks.refresh(page.id) } }
        is BarPage.Group -> {
            val vm = groupFeedViewModel(page.groupId)
            remember(vm) { { vm.refresh() } }
        }
        BarPage.Subscriptions, BarPage.Trending, BarPage.Saved -> remember { {} }
    }
}

@Composable
private fun rememberTabRefresh(tab: HomeTab?, networks: HomeNetworks): () -> Unit = when (tab) {
    HomeTab.Following -> {
        val vm = groupFeedViewModel(null)
        remember(vm) { { vm.refresh() } }
    }
    HomeTab.ForYou -> {
        val vm = forYouViewModel()
        remember(vm) { { vm.refresh() } }
    }
    is HomeTab.Network -> remember(tab.id) { { networks.refresh(tab.id) } }
    is HomeTab.Group, null -> remember { {} }
}
