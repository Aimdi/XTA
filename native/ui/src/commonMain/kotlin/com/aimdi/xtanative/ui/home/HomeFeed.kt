package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.core.feed.FeedSort
import com.aimdi.xtanative.core.feed.FeedStrip
import com.aimdi.xtanative.store.settings.HomePrefs
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.LocalParentTurnsPages
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.common.rememberScreenReaderOn
import com.aimdi.xtanative.ui.feed.MediaModes
import com.aimdi.xtanative.ui.feed.ToTop
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.discover_loaded_title
import com.aimdi.xtanative.ui.generated.resources.following
import com.aimdi.xtanative.ui.generated.resources.home_feed_accounts
import com.aimdi.xtanative.ui.generated.resources.home_filter_accounts
import com.aimdi.xtanative.ui.generated.resources.home_more_options
import com.aimdi.xtanative.ui.generated.resources.home_networks
import com.aimdi.xtanative.ui.generated.resources.home_pin_to_bar
import com.aimdi.xtanative.ui.generated.resources.home_pin_to_bar_hint
import com.aimdi.xtanative.ui.generated.resources.home_refresh
import com.aimdi.xtanative.ui.generated.resources.home_saved
import com.aimdi.xtanative.ui.generated.resources.home_search_in
import com.aimdi.xtanative.ui.generated.resources.home_subscriptions
import com.aimdi.xtanative.ui.generated.resources.home_unread
import com.aimdi.xtanative.ui.generated.resources.home_x
import com.aimdi.xtanative.ui.group.GroupFeedBody
import com.aimdi.xtanative.ui.group.groupFeedViewModel
import com.aimdi.xtanative.ui.media.ReaderIcons
import com.aimdi.xtanative.ui.plugins.LocalHomeReading
import com.aimdi.xtanative.ui.plugins.ReadingControlsPinItem
import com.aimdi.xtanative.ui.plugins.rememberHomeReading
import com.aimdi.xtanative.ui.post.PostActions
import kotlin.coroutines.cancellation.CancellationException
import com.aimdi.xtanative.ui.settings.SettingsRoutes
import com.aimdi.xtanative.ui.settings.SettingsSection
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import org.jetbrains.compose.resources.stringResource

/** Where the feed page's controls lead, in the shell that holds the drawer, the sheets and what opens over Home. */
internal class FeedPageCalls(
    val openMenu: () -> Unit,
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
 * lists them; the bar's mark opens that picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val tabs = ui.tabs
    val reader = rememberScreenReaderOn()
    val bar = if (ui.hideOnScroll && !reader) TopAppBarDefaults.enterAlwaysScrollBehavior() else TopAppBarDefaults.pinnedScrollBehavior()
    val current = tabs.getOrNull(timelines.index)
    val desktop = LocalDesktopActions.current != null
    val anyUnread = SourcePicker.sources(tabs).any { it.key in timelines.unread }
    val labels = tabs.map { tabLabel(it, ui) }
    val holder = rememberSaveableStateHolder()
    val reading = rememberHomeReading()
    val graph = LocalGraph.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val source = (current as? HomeTab.Network)?.id
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
    Scaffold(
        modifier = Modifier.nestedScroll(reading.connection).nestedScroll(bar.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (current == HomeTab.Following) {
                FollowingTopBar(following, followingCalls(calls, desktop, refresh), bar)
            } else {
                TimelineTopBar(current, TopBarLook(ui, anyUnread, timelines.unread), networks, routes, refresh, calls, bar, vm::setTopBarPins)
            }
        },
    ) { padding ->
        SwipesTimelines(timelines.index, labels, onTurn = calls.onTab, modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            if (current != null) {
                holder.SaveableStateProvider(current.key) {
                    val toTop = if (timelines.toTop.page == timelines.index) timelines.toTop.count else 0
                    // Home turns its timelines with a sideways drag: the tabs of a network's page leave it to Home.
                    CompositionLocalProvider(LocalParentTurnsPages provides true) {
                        TimelinePage(current, toTop, following.mediaOnly, actions, networks, PaddingValues(bottom = padding.calculateBottomPadding()))
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
 * The top bar of a timeline that is not Following (plugin_compact_header.dart):
 * its mark, which opens the timeline picker and carries the unread dot of
 * any timeline; its name, or, for Mastodon, Bluesky and Threads while they
 * are grouped, the switch between them; its actions.
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
    TopAppBar(
        navigationIcon = { if (current != null) PickerMark(current, ui, networks, look.anyUnread) { calls.openSheet(HomeSheet.PICKER) } },
        title = {
            if (current is HomeTab.Network && ui.microblogsGrouped && AltMicroblogging.isMember(current.id)) {
                MicroblogSwitcher(ui, networks, current.id, look.unread) { id -> calls.onTab(ui.tabs.indexOf(HomeTab.Network(id))) }
            } else {
                Text(
                    current?.let { tabLabel(it, ui) }.orEmpty(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(onClick = calls.toTitleTop),
                )
            }
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
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
        // The label says the dot already.
        Box(Modifier.clearAndSetSemantics {}) { UnreadDot(unread) { TimelineMark(tab, networks, 24.dp) } }
    }
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

/**
 * Mastodon, Bluesky and Threads side by side (alt_microblogging_selector.dart's
 * compact row): the one shown lit, each with its unread dot; a tap shows another.
 */
@Composable
private fun MicroblogSwitcher(ui: HomeUi, networks: HomeNetworks, shown: String, unread: Set<String>, onPick: (String) -> Unit) {
    val members = AltMicroblogging.members(ui.tabs.filterIsInstance<HomeTab.Network>().map { it.id })
    val unreadLabel = stringResource(Res.string.home_unread)
    Row(verticalAlignment = Alignment.CenterVertically) {
        members.forEach { id ->
            val selected = id == shown
            val name = ui.networks.firstOrNull { it.id == id }?.title ?: id
            val lit = HomeTab.Network(id).key in unread
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background)
                    .clickable(onClick = { if (!selected) onPick(id) })
                    .semantics(mergeDescendants = true) {
                        role = Role.Tab
                        this.selected = selected
                        contentDescription = if (lit) "$name, $unreadLabel" else name
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (lit) BadgedBox(badge = { Badge() }) { networks.Mark(id, 22.dp) } else networks.Mark(id, 22.dp)
            }
        }
    }
}

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
            IconButton(onClick = { routes.openIfExists(HomeDestination.SEARCH.route) }) { Icon(Icons.Filled.Search, stringResource(Res.string.home_search_in, x)) }
            val off = HomeFilter.turnedOff(ui.filter, ui.session.accounts, ui.session.groups)
            PinnedOptions(FeedStrip.FOR_YOU, xOptions(routes, refresh, calls), ui.topBarPins[FeedStrip.FOR_YOU].orEmpty(), onPins, attention = off > 0)
        }
        is HomeTab.Network -> {
            key(tab.id) { NetworkActions(tab.id, ui, networks, onPins) }
            if (LocalDesktopActions.current != null) IconButton(onClick = refresh) { Icon(Icons.Filled.Refresh, stringResource(Res.string.home_refresh)) }
        }
        HomeTab.Following, is HomeTab.Group, null -> Unit
    }
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
    IconButton(onClick = onClick) {
        OffDot(off) { Icon(Icons.Outlined.Person, stringResource(Res.string.home_feed_accounts)) }
    }
}

/** A network's options pinned to the bar, in pin order, and the menu of them all, where each can be pinned (XTA's top-bar pins). */
@Composable
internal fun NetworkActions(id: String, ui: HomeUi, networks: HomeNetworks, onPins: (String, List<String>) -> Unit) {
    PinnedOptions(id, networks.options(id), ui.topBarPins[id].orEmpty(), onPins)
}

/**
 * [options] of the timeline [id]: those in [pins] on the bar, in pin order,
 * then the menu of them all, where each can be pinned; the menu's button
 * carries a dot while one of them wants the reader's [attention].
 */
@Composable
private fun PinnedOptions(id: String, options: List<NetworkOption>, pins: List<String>, onPins: (String, List<String>) -> Unit, attention: Boolean = false) {
    if (options.isEmpty()) return
    TopBarPins.shown(options, pins) { it.id }.forEach { option ->
        IconButton(onClick = option.onClick) { Icon(option.icon, option.label) }
    }
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            val more = stringResource(Res.string.home_more_options)
            if (attention) BadgedBox(badge = { Badge() }) { Icon(Icons.Filled.MoreVert, more) } else Icon(Icons.Filled.MoreVert, more)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val pinLabel = stringResource(Res.string.home_pin_to_bar)
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
            ReadingControlsPinItem()
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
