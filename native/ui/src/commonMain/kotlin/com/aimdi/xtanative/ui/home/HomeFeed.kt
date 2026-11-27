package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.core.feed.FeedSort
import com.aimdi.xtanative.core.feed.FeedStrip
import com.aimdi.xtanative.core.session.DeckColumn
import com.aimdi.xtanative.data.LocalGraph
import com.aimdi.xtanative.store.settings.HomePrefs
import com.aimdi.xtanative.ui.common.BarFirstScaffold
import com.aimdi.xtanative.ui.common.IconTip
import com.aimdi.xtanative.ui.common.LocalParentTurnsPages
import com.aimdi.xtanative.ui.common.TipText
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.common.rememberLiveEdges
import com.aimdi.xtanative.ui.common.rememberScreenReaderOn
import com.aimdi.xtanative.ui.feed.MediaModes
import com.aimdi.xtanative.ui.feed.ToTop
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.discover_loaded_title
import com.aimdi.xtanative.ui.generated.resources.following
import com.aimdi.xtanative.ui.generated.resources.home_feed_accounts
import com.aimdi.xtanative.ui.generated.resources.home_filter_accounts
import com.aimdi.xtanative.ui.generated.resources.home_manage_plugins
import com.aimdi.xtanative.ui.generated.resources.home_more_options
import com.aimdi.xtanative.ui.generated.resources.home_networks
import com.aimdi.xtanative.ui.generated.resources.home_pin_to_bar
import com.aimdi.xtanative.ui.generated.resources.home_pin_to_bar_hint
import com.aimdi.xtanative.ui.generated.resources.home_refresh
import com.aimdi.xtanative.ui.generated.resources.home_saved
import com.aimdi.xtanative.ui.generated.resources.home_search_in
import com.aimdi.xtanative.ui.generated.resources.home_subscriptions
import com.aimdi.xtanative.ui.generated.resources.home_unread
import com.aimdi.xtanative.ui.generated.resources.home_column_for_you
import com.aimdi.xtanative.ui.generated.resources.home_x
import com.aimdi.xtanative.ui.generated.resources.home_xta_settings
import com.aimdi.xtanative.ui.group.GroupFeedBody
import com.aimdi.xtanative.ui.group.groupFeedViewModel
import com.aimdi.xtanative.ui.media.ReaderIcons
import com.aimdi.xtanative.ui.plugins.LocalHomeReading
import com.aimdi.xtanative.ui.plugins.LocalPluginClientBar
import com.aimdi.xtanative.ui.plugins.LocalShellMenu
import com.aimdi.xtanative.ui.plugins.PluginClientBar
import com.aimdi.xtanative.ui.plugins.PluginHomeTab
import com.aimdi.xtanative.ui.plugins.ReadingControlsPinRow
import com.aimdi.xtanative.ui.plugins.ShellMenu
import com.aimdi.xtanative.ui.plugins.rememberHomeReading
import com.aimdi.xtanative.ui.pluginstore.PluginRoutes
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.settings.SettingsRoutes
import com.aimdi.xtanative.ui.settings.SettingsSection
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import kotlin.coroutines.cancellation.CancellationException
import org.jetbrains.compose.resources.stringResource

/** Where the feed page's controls lead, in the shell that holds the drawer, the sheets and what opens over Home. */
internal class FeedPageCalls(
    /** The account menu opened; null while it stands beside Home ([PluginSwitcher.offersMenu]). */
    val openMenu: (() -> Unit)?,
    val openSheet: (HomeSheet) -> Unit,
    val openLayer: (HomeLayer) -> Unit,
    /** The timeline at an index of [HomeUi.tabs] shown; the one shown, back to its top. */
    val onTab: (Int) -> Unit,
    val toTitleTop: () -> Unit,
    /** Following's order picked: set, and Custom's rules opened. */
    val onOrder: (FeedSort) -> Unit,
)

/** Home's timelines as the feed page shows them: the one shown, the last request for a timeline's top, and the feeds with unread posts. */
internal class TimelinesState(val index: Int, val toTop: ToTop, val unread: Set<String>)

/**
 * Home's Feed page (XTA's FeedScreen): the timeline shown (Following, X, the
 * networks pinned there) under Following's one bar while Following is shown,
 * else the timeline's own; both leave with the scroll when Theme says so. A
 * drag sideways turns to the next or the previous timeline, as their picker
 * lists them; the bar's mark opens that picker. Where the page is wide
 * enough for two ([HomeWide]), the timelines after the one shown are read
 * beside it, each a column of its own that reads on its own; a column's
 * name makes it the one shown. There, as on the Deck, Ctrl+← and Ctrl+→
 * choose the column the reading keys move ([ReadColumns]).
 */
@Composable
internal fun HomeFeedPage(
    ui: HomeUi,
    vm: HomeViewModel,
    timelines: TimelinesState,
    following: FollowingLook,
    networks: HomeNetworks,
    actions: PostActions,
    routes: HomeRoutes,
    refresh: () -> Unit,
    calls: FeedPageCalls,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val count = HomeWide.columns(maxWidth.value, ui.tabs.size)
        val beside = HomeWide.beside(ui.tabs, timelines.index, count)
        if (beside.isEmpty()) {
            ShownTimeline(ui, vm, timelines, following, networks, actions, routes, refresh, calls, Modifier.fillMaxSize())
        } else {
            val each = HomeWide.columnWidth(maxWidth.value, beside.size + 1).dp
            val shown = ui.tabs.getOrNull(timelines.index)?.key
            // Ctrl+← and Ctrl+→ choose the column the reading keys move, as on the Deck.
            ReadColumns(listOfNotNull(shown) + beside.map { it.key })
            val chosen = chosenReadColumn()
            // Beside the desktop frame's navigation, as the deck's columns: from the start edge, a line closing them
            // off where the window is wider still. Elsewhere (a tablet) centred, as before.
            val framed = LocalWideFrame.current != null
            val spare = framed && maxWidth.value - each.value * (beside.size + 1) - beside.size >= 1f
            Row(Modifier.fillMaxSize(), horizontalArrangement = if (framed) Arrangement.Start else Arrangement.Center) {
                ReadColumn(shown ?: Unit) {
                    ShownTimeline(
                        ui, vm, timelines, following, networks, actions, routes, refresh, calls,
                        Modifier.width(each).fillMaxHeight().chosenMark(shown != null && chosen == shown),
                    )
                }
                beside.forEach { tab ->
                    VerticalDivider()
                    key(tab.key) {
                        ReadColumn(tab.key) {
                            DeckColumnView(
                                columnOf(tab),
                                columnLabel(tab, ui),
                                ColumnPlace(first = false, last = false),
                                ColumnCalls(onTitle = { calls.onTab(ui.tabs.indexOf(tab)) }, menu = false),
                                toTop = 0,
                                refreshedAll = 0,
                                actions = actions,
                                modifier = Modifier.width(each).fillMaxHeight().chosenMark(chosen == tab.key),
                            )
                        }
                    }
                }
                if (spare) VerticalDivider()
            }
        }
    }
}

/** A timeline of Home as the column the deck draws it in. */
private fun columnOf(tab: HomeTab): DeckColumn = when (tab) {
    HomeTab.Following -> DeckColumn.Following
    HomeTab.ForYou -> DeckColumn.ForYou
    is HomeTab.Network -> DeckColumn.Network(tab.id)
    is HomeTab.Group -> DeckColumn.Group(tab.id)
}

/** The timeline shown, under its bar: the whole page, or the first column of a wide one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShownTimeline(
    ui: HomeUi,
    vm: HomeViewModel,
    timelines: TimelinesState,
    following: FollowingLook,
    networks: HomeNetworks,
    actions: PostActions,
    routes: HomeRoutes,
    refresh: () -> Unit,
    calls: FeedPageCalls,
    modifier: Modifier,
) {
    val tabs = ui.tabs
    val reader = rememberScreenReaderOn()
    val bar = if (ui.hideOnScroll && !reader) TopAppBarDefaults.enterAlwaysScrollBehavior() else TopAppBarDefaults.pinnedScrollBehavior()
    val current = tabs.getOrNull(timelines.index)
    val desktop = LocalDesktopActions.current != null
    val anyUnread = tabs.any { it !is HomeTab.Group &&  it.key in timelines.unread }
    val labels = tabs.map { tabLabel(it, ui) }
    val holder = rememberSaveableStateHolder()
    val client = remember(current?.key) { PluginClientBar() }
    val reading = rememberHomeReading()
    val graph = LocalGraph.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val source = (current as? HomeTab.Network)?.id
    val takeover = LocalNetworkBarTakeover.current
    // The bar's house, while a network has it, leads back to Following and so to the app's own bar.
    val toFollowing by rememberUpdatedState({ calls.onTab(tabs.indexOf(HomeTab.Following).coerceAtLeast(0)) })
    DisposableEffect(takeover, source, client) {
        takeover?.show(NetworkBarRules.FEED, source, client.takeIf { source != null }, onHome = { toFollowing() }, house = false)
        onDispose { takeover?.show(NetworkBarRules.FEED, null, null) }
    }
    val sectionsBelow = takeover?.on(BarPage.Feed)?.network == source && source != null
    val pinned = settings?.get(HomePrefs.keepControlsVisible) == true
    val keyboard = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    reading.active = source != null
    reading.protected = reader || keyboard
    if (source != null) reading.bind(source, pinned)
    reading.persist = { value ->
        try {
            graph.settings.set(HomePrefs.keepControlsVisible, value)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }
    CompositionLocalProvider(LocalHomeReading provides reading.takeIf { source != null }) {
    // On the desktop Tab walks the bar before the timeline under it, as the eye reads them.
    BarFirstScaffold(
        barFirst = desktop,
        modifier = modifier.nestedScroll(reading.connection).nestedScroll(bar.nestedScrollConnection),
        topBar = {
            if (current == HomeTab.Following) {
                FollowingTopBar(following, followingCalls(calls, desktop, refresh), bar)
            } else if (current is HomeTab.Network) {
                val extras = takeover?.on(BarPage.Feed)?.takeIf { it.network == current.id }?.split?.more.orEmpty()
                val shell = remember(current.id) { ShellMenu(current.id) }
                var sheet by remember { mutableStateOf(false) }
                CompositionLocalProvider(LocalShellMenu provides shell) {
                    NetworkTopBar(
                        current.id,
                        ui,
                        networks,
                        client,
                        HomeTab.Network(current.id).key in timelines.unread,
                        NetworkBarCalls(onAvatar = { sheet = true }, onTitle = calls.toTitleTop),
                        bar,
                        sectionsBelow = sectionsBelow,
                    ) {
                        // Only the search and what the reader pinned stay on the bar; the rest is in the plugin sheet.
                        key(current.id) { NetworkActions(current.id, ui, networks, vm::setTopBarPins, routes, extras, menu = false) }
                        if (desktop) IconTip(stringResource(Res.string.home_refresh)) { IconButton(onClick = refresh) { Icon(Icons.Filled.Refresh, stringResource(Res.string.home_refresh)) } }
                    }
                }
                if (sheet) {
                    LaunchedEffect(Unit) { vm.reloadUnread() }
                    PluginSheet(current.id, ui, networks, client, shell, extras, timelines.unread, routes, calls, vm::setTopBarPins) { sheet = false }
                }
            } else {
                TimelineTopBar(current, TopBarLook(ui, anyUnread, timelines.unread), networks, routes, refresh, calls, bar, vm::setTopBarPins)
            }
        },
    ) { padding ->
        // The bar's height changes at every frame of its slide: the page is measured with it, not composed again.
        val (top, bottom) = rememberLiveEdges(padding)
        SwipesTimelines(timelines.index, labels, onTurn = calls.onTab, modifier = Modifier.fillMaxSize().padding(top)) {
            if (current != null) {
                holder.SaveableStateProvider(current.key) {
                    val toTop = if (timelines.toTop.page == timelines.index) timelines.toTop.count else 0
                    // Home turns its timelines with a sideways drag: the tabs of a network's page leave it to Home.
                    // A network hands its own sections and actions to Home's one bar (NetworkTopBar).
                    CompositionLocalProvider(LocalParentTurnsPages provides true, LocalPluginClientBar provides client.takeIf { current is HomeTab.Network }) {
                        TimelinePage(current, toTop, following.mediaOnly, actions, networks, bottom)
                    }
                }
            }
        }
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

/** One timeline of Home; [toTop] grows when the reader asks for its top. Following shows its media alone in Media. */
@Composable
private fun TimelinePage(
    tab: HomeTab,
    toTop: Int,
    followingMedia: Boolean,
    actions: PostActions,
    networks: HomeNetworks,
    bottom: PaddingValues,
) {
    when (tab) {
        HomeTab.Following -> {
            val vm = groupFeedViewModel(null)
            val state by vm.ui.collectAsStateWithLifecycle()
            GroupFeedBody(state, vm, actions, rememberLazyListState(), bottom, mediaOnly = followingMedia, toTop = toTop)
        }
        HomeTab.ForYou -> ForYouPage(toTop, actions, bottom)
        is HomeTab.Network -> networks.Page(tab.id, bottom, toTop)
        is HomeTab.Group -> Unit
    }
}

/** What a timeline's top bar shows of Home: the reader's settings, whether any timeline has unread posts, and which feeds do. */
private class TopBarLook(val ui: HomeUi, val anyUnread: Boolean, val unread: Set<String>)

/**
 * The top bar of X's For you and a group (plugin_compact_header.dart): its
 * mark, which opens the timeline picker and carries the unread dot of any
 * timeline; its name; its actions. A network's timeline has its own
 * ([NetworkTopBar]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimelineTopBar(
    current: HomeTab?,
    look: TopBarLook,
    networks: HomeNetworks,
    routes: HomeRoutes,
    refresh: () -> Unit,
    calls: FeedPageCalls,
    bar: TopAppBarScrollBehavior,
    onPins: (String, List<String>) -> Unit,
) {
    val ui = look.ui
    // In the desktop's frame as low as Following's bar and the columns' heads beside it, the name as theirs.
    val framed = LocalWideFrame.current != null
    TopAppBar(
        expandedHeight = if (framed) FollowingBar.HEIGHT.dp else TopAppBarDefaults.TopAppBarExpandedHeight,
        navigationIcon = { if (current != null) PickerMark(current, ui, networks, look.anyUnread) { calls.openSheet(HomeSheet.PICKER) } },
        title = {
            TipText(
                current?.let { tabLabel(it, ui) }.orEmpty(),
                style = if (framed) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold) else LocalTextStyle.current,
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

/** The timeline's mark as the picker's button (HomeTimelineTitle): said as the timeline's name, with the dot while a timeline has unread posts. */
@Composable
private fun PickerMark(tab: HomeTab, ui: HomeUi, networks: HomeNetworks, unread: Boolean, onClick: () -> Unit) {
    val label = listOfNotNull(tabLabel(tab, ui), stringResource(Res.string.home_unread).takeIf { unread }, stringResource(Res.string.home_networks)).joinToString(", ")
    val opener = switcherButton("timeline", onClick)
    IconTip(label) { IconButton(onClick = opener.onClick, modifier = opener.modifier.semantics { contentDescription = label }) {
        // The label says the dot already.
        Box(Modifier.clearAndSetSemantics {}) { UnreadDot(unread) { TimelineMark(tab, networks, 24.dp) } }
    } }
}

/** A timeline's mark: Following's stacked posts, X's own mark, a network's. */
@Composable
internal fun TimelineMark(tab: HomeTab, networks: HomeNetworks, size: Dp) {
    val tint = MaterialTheme.colorScheme.onSurface
    when (tab) {
        HomeTab.Following -> Icon(HomeIcons.DynamicFeed, contentDescription = null, tint = tint, modifier = Modifier.size(size))
        HomeTab.ForYou -> Icon(ReaderIcons.XMark, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.85f))
        is HomeTab.Network -> networks.Mark(tab.id, size)
        is HomeTab.Group -> Unit
    }
}

/** What a timeline's column beside the one shown calls it: as the deck's columns do, X's For you by that name beside X's mark. */
@Composable
private fun columnLabel(tab: HomeTab, ui: HomeUi): String =
    if (tab == HomeTab.ForYou) stringResource(Res.string.home_column_for_you) else tabLabel(tab, ui)

/** What the strip, the picker and the menus call a timeline: Following, X, a network by its title. */
@Composable
internal fun tabLabel(tab: HomeTab, ui: HomeUi): String = when (tab) {
    HomeTab.Following -> stringResource(Res.string.following)
    HomeTab.ForYou -> stringResource(Res.string.home_x)
    is HomeTab.Group -> tab.label
    is HomeTab.Network -> ui.networks.firstOrNull { it.id == tab.id }?.title ?: tab.id
}

/**
 * The top bar's buttons for [tab]: for X, Search X, then the options it
 * pinned and the menu of them all (x_reader_chrome.dart: Subscriptions,
 * Saved and Accounts; the search of the loaded posts, Home feed accounts and
 * Refresh); for a network its pinned options and the menu of them all, and
 * on the desktop, refresh.
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
    when (tab) {
        HomeTab.ForYou -> {
            val x = stringResource(Res.string.home_x)
            IconTip(stringResource(Res.string.home_search_in, x)) { IconButton(onClick = { routes.openIfExists(HomeDestination.SEARCH.route) }) { Icon(Icons.Filled.Search, stringResource(Res.string.home_search_in, x)) } }
            val off = HomeFilter.turnedOff(ui.filter, ui.session.accounts, ui.session.groups)
            PinnedOptions(FeedStrip.FOR_YOU, xOptions(routes, refresh, calls), ui.topBarPins[FeedStrip.FOR_YOU].orEmpty(), onPins, attention = off > 0)
        }
        is HomeTab.Network -> {
            key(tab.id) { NetworkActions(tab.id, ui, networks, onPins) }
            if (LocalDesktopActions.current != null) IconTip(stringResource(Res.string.home_refresh)) { IconButton(onClick = refresh) { Icon(Icons.Filled.Refresh, stringResource(Res.string.home_refresh)) } }
        }
        HomeTab.Following, is HomeTab.Group, null -> Unit
    }
}

/**
 * Network [id]'s sheet ([PluginSwitcherSheet]): the plugin's own entries
 * ([shell]) and the app's for it, then what the sheet offers every timeline.
 */
@Composable
private fun PluginSheet(
    id: String,
    ui: HomeUi,
    networks: HomeNetworks,
    client: PluginClientBar,
    shell: ShellMenu,
    extras: List<PluginHomeTab>,
    unread: Set<String>,
    routes: HomeRoutes,
    calls: FeedPageCalls,
    onPins: (String, List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val tabs = ui.tabs
    val settings = PluginRoutes.settings(id).takeIf { routes.exists(PluginRoutes.SETTINGS) }
    PluginSwitcherSheet(
        ui,
        networks,
        shown = HomeTab.Network(id),
        unread = unread,
        calls = SwitcherCalls(
            onTimeline = { tab ->
                onDismiss()
                tabs.indexOf(tab).takeIf { it >= 0 }?.let(calls.onTab)
            },
            onGroup = { group ->
                onDismiss()
                routes.openGroup(group.id)
            },
            onNetwork = { other ->
                onDismiss()
                tabs.indexOf(HomeTab.Network(other)).takeIf { it >= 0 }?.let(calls.onTab)
            },
            onAdd = {
                onDismiss()
                calls.openSheet(HomeSheet.STRIP)
            },
            onMenu = calls.openMenu?.let { open ->
                {
                    onDismiss()
                    open()
                }
            },
            onManage = {
                onDismiss()
                routes.openIfExists(HomeDestination.PLUGINS.route)
            },
            onSettings = {
                onDismiss()
                routes.openIfExists(HomeDestination.SETTINGS.route)
            },
            onDismiss = onDismiss,
            onAvatar = {
                onDismiss()
                calls.openSheet(HomeSheet.AVATAR)
            },
        ),
        plugin = SheetPlugin(
            id,
            client.subtitle,
            // The plugin's entries see its shell as its page does.
            shell.items?.let { items -> { close -> CompositionLocalProvider(LocalPluginClientBar provides client) { items(this, close) } } },
            settings?.let { route -> { onDismiss(); routes.open(route) } },
            appEntries = { close -> ShellEntries(id, ui, networks, extras, null, onPins, close) },
            onSwitchAccount = client.switchAccount?.let { switch -> { onDismiss(); switch() } },
        ),
    )
}

/** X's options, by the ids its pins keep: its library, the reader's accounts, and the loaded posts' search, Home feed accounts and Refresh. */
@Composable
private fun xOptions(routes: HomeRoutes, refresh: () -> Unit, calls: FeedPageCalls): List<NetworkOption> {
    val subscriptions = stringResource(Res.string.home_subscriptions)
    val saved = stringResource(Res.string.home_saved)
    val accounts = stringResource(Res.string.home_filter_accounts)
    val loaded = stringResource(Res.string.discover_loaded_title)
    val feedAccounts = stringResource(Res.string.home_feed_accounts)
    val again = stringResource(Res.string.home_refresh)
    return listOf(
        NetworkOption("subscriptions", subscriptions, HomeIcons.PeopleOutlined) { routes.openIfExists(HomeDestination.SUBSCRIPTIONS.route) },
        NetworkOption("saved", saved, HomeIcons.Bookmark) { routes.openIfExists(HomeDestination.SAVED.route) },
        NetworkOption("accounts", accounts, HomeIcons.ManageAccounts) {
            if (routes.exists(HomeDestination.SETTINGS.route)) routes.open(SettingsRoutes.of(SettingsSection.ACCOUNT))
        },
        NetworkOption("loaded-search", loaded, GroupIcons.ManageSearch) { calls.openLayer(HomeLayer.LOADED_SEARCH) },
        NetworkOption("feed-accounts", feedAccounts, Icons.Outlined.Person) { calls.openSheet(HomeSheet.FILTER) },
        NetworkOption("refresh", again, Icons.Filled.Refresh, refresh),
    )
}

/** Home feed accounts (homeFeedAccountsButton): a person, with a dot while accounts or groups are turned off. */
@Composable
internal fun FeedAccountsButton(off: Int, onClick: () -> Unit) {
    IconTip(stringResource(Res.string.home_feed_accounts)) { IconButton(onClick = onClick) {
        OffDot(off) { Icon(Icons.Outlined.Person, stringResource(Res.string.home_feed_accounts)) }
    } }
}

/**
 * A network's options pinned to the bar, in pin order, and, unless its
 * plugin draws the one menu itself ([menu] false, [ShellMenu]), the menu of
 * them all, where each can be pinned, with the app's entries after them:
 * the network's sections that are in its menu ([extras]), the plugin store
 * and XTA's settings.
 */
@Composable
internal fun NetworkActions(
    id: String,
    ui: HomeUi,
    networks: HomeNetworks,
    onPins: (String, List<String>) -> Unit,
    routes: HomeRoutes? = null,
    extras: List<PluginHomeTab> = emptyList(),
    menu: Boolean = true,
) {
    PinnedOptions(id, networks.options(id), ui.topBarPins[id].orEmpty(), onPins, menu = menu) { close -> AppEntries(extras, routes, close) }
}

/**
 * The app's entries of a network's one menu, under XTA's name in its
 * plugin's ([ShellMenuButton]): keep controls visible, the network's
 * options as Home offers them (each can be pinned to the bar), then its
 * sections in the menu, the plugin store and XTA's settings.
 */
@Composable
internal fun ColumnScope.ShellEntries(id: String, ui: HomeUi, networks: HomeNetworks, extras: List<PluginHomeTab>, routes: HomeRoutes?, onPins: (String, List<String>) -> Unit, close: () -> Unit) {
    ReadingControlsPinRow()
    OptionItems(id, networks.options(id), ui.topBarPins[id].orEmpty(), onPins, close)
    AppEntries(extras, routes, close)
}

/** The network's sections in its menu, then the plugin store and XTA's settings, where the app has them. */
@Composable
private fun AppEntries(extras: List<PluginHomeTab>, routes: HomeRoutes?, close: () -> Unit) {
    extras.forEach { section ->
        DropdownMenuItem(
            text = { Text(section.label, fontWeight = if (section.selected) FontWeight.Bold else null) },
            leadingIcon = { Icon(section.icon, contentDescription = null) },
            onClick = {
                close()
                section.onSelect()
            },
        )
    }
    if (routes == null) return
    if (routes.exists(HomeDestination.PLUGINS.route)) {
        DropdownMenuItem(text = { Text(stringResource(Res.string.home_manage_plugins)) }, onClick = {
            close()
            routes.open(HomeDestination.PLUGINS.route)
        })
    }
    if (routes.exists(HomeDestination.SETTINGS.route)) {
        DropdownMenuItem(text = { Text(stringResource(Res.string.home_xta_settings)) }, onClick = {
            close()
            routes.open(HomeDestination.SETTINGS.route)
        })
    }
}

/**
 * [options] of the timeline [id]: those in [pins] on the bar, in pin order,
 * then, with [menu], the menu of them all, where each can be pinned, and
 * [more] after them; the menu's button carries a dot while one of them
 * wants the reader's [attention].
 */
@Composable
private fun PinnedOptions(
    id: String,
    options: List<NetworkOption>,
    pins: List<String>,
    onPins: (String, List<String>) -> Unit,
    attention: Boolean = false,
    menu: Boolean = true,
    more: @Composable ColumnScope.(close: () -> Unit) -> Unit = {},
) {
    TopBarPins.shown(options, pins) { it.id }.forEach { option ->
        IconTip(option.label) { IconButton(onClick = option.onClick) { Icon(option.icon, option.label) } }
    }
    if (!menu) return
    var open by remember { mutableStateOf(false) }
    Box {
        val label = stringResource(Res.string.home_more_options)
        IconTip(label) {
            IconButton(onClick = { open = true }) {
                if (attention) BadgedBox(badge = { Badge() }) { Icon(Icons.Filled.MoreVert, label) } else Icon(Icons.Filled.MoreVert, label)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (options.isNotEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(Res.string.home_pin_to_bar_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = {},
                    enabled = false,
                )
            }
            ReadingControlsPinRow()
            OptionItems(id, options, pins, onPins) { open = false }
            more { open = false }
        }
    }
}

/** Each of [options] as a menu row, its pin beside it. */
@Composable
private fun OptionItems(id: String, options: List<NetworkOption>, pins: List<String>, onPins: (String, List<String>) -> Unit, close: () -> Unit) {
    val pinLabel = stringResource(Res.string.home_pin_to_bar)
    options.forEach { option ->
        DropdownMenuItem(
            text = { Text(option.label) },
            leadingIcon = { Icon(option.icon, contentDescription = null) },
            trailingIcon = {
                IconTip(pinLabel) { IconButton(onClick = { onPins(id, TopBarPins.toggled(pins, option.id)) }) {
                    Icon(
                        XIcons.Pin,
                        pinLabel,
                        tint = if (option.id in pins) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                } }
            },
            onClick = {
                close()
                option.onClick()
            },
        )
    }
}
