package com.aimdi.xtanative.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.core.feed.ContentFilter
import com.aimdi.xtanative.core.feed.FeedSort
import com.aimdi.xtanative.ui.common.BackHandling
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.common.scrollToTop
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.back
import com.aimdi.xtanative.ui.generated.resources.close
import com.aimdi.xtanative.ui.generated.resources.groups_content_default
import com.aimdi.xtanative.ui.generated.resources.groups_content_nsfw
import com.aimdi.xtanative.ui.generated.resources.groups_content_sfw
import com.aimdi.xtanative.ui.generated.resources.groups_custom
import com.aimdi.xtanative.ui.generated.resources.groups_discover
import com.aimdi.xtanative.ui.generated.resources.groups_media
import com.aimdi.xtanative.ui.generated.resources.groups_filters
import com.aimdi.xtanative.ui.generated.resources.groups_popular
import com.aimdi.xtanative.ui.generated.resources.groups_recent
import com.aimdi.xtanative.ui.generated.resources.groups_refresh
import com.aimdi.xtanative.ui.generated.resources.groups_scroll_to_top
import com.aimdi.xtanative.ui.home.DesktopShortcuts
import com.aimdi.xtanative.ui.home.RetapsToTop
import com.aimdi.xtanative.ui.feed.MediaModes
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.search.LoadedFeedSearchScreen
import com.aimdi.xtanative.ui.search.LoadedSearchButton
import com.aimdi.xtanative.ui.subscriptions.GroupEditSheet
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import com.aimdi.xtanative.ui.subscriptions.rememberFollowedRows
import com.aimdi.xtanative.ui.subscriptions.rememberPackSaver
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** What a group's screen has open over its feed. */
private enum class GroupLayer { SETTINGS, CUSTOM, SWITCHER, CREATE, LOADED_SEARCH }

/**
 * A group's feed (group_screen.dart, _feed.dart, _feed_shell.dart): its
 * title opens the feed menu (Home's feeds, the pinned groups, every group),
 * whose groups swap in place so Back returns to wherever the first one was
 * opened from, and whose feeds go back to Home on them ([onFeed], a tab's
 * key); its bar has the search of the posts loaded, the filters, a way to
 * the top and a refresh; its chips pick the order, open Discover and show the
 * media alone. Without [onBack] it is a page of Home's bar: no way back, and
 * its title is only its name, as XTA's group pages are.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupFeedScreen(groupId: String, actions: PostActions, onBack: (() -> Unit)?, onFeed: ((String) -> Unit)? = null) {
    var shownId by rememberSaveable { mutableStateOf(groupId) }
    val vm = groupFeedViewModel(shownId)
    val state by vm.ui.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    var layer by rememberSaveable { mutableStateOf<GroupLayer?>(null) }
    var discovering by rememberSaveable(shownId) { mutableStateOf(false) }
    // A pushed group answers F5 and Ctrl+R (_feed_shell.dart); as a page of Home's bar, Home's keys refresh it.
    if (onBack != null) DesktopShortcuts(refresh = { vm.refresh() }, turn = {}, isOpen = { layer != null || discovering }, close = { if (layer != null) layer = null else discovering = false })
    RetapsToTop(list)
    var rankRequest by rememberSaveable(shownId) { mutableStateOf(0) }
    val onRank: (() -> Unit)? = if (!rememberAiReady()) {
        null
    } else {
        {
            discovering = true
            rankRequest++
        }
    }
    val mediaKeys by MediaModes.keys.collectAsStateWithLifecycle()
    val mediaOnly = shownId in mediaKeys
    BackHandling(enabled = layer == GroupLayer.CUSTOM) { layer = null }
    BackHandling(enabled = discovering && layer == null) { discovering = false }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Column {
                    GroupTopBar(state, vm, list, discovering, onBack, onFeed, open = { layer = it }, onSwitch = { id -> if (id != shownId) shownId = id }, onRank = onRank)
                    ControlBar(
                        state,
                        GroupChips(discovering, mediaOnly),
                        onSort = { sort ->
                            discovering = false
                            if (sort == FeedSort.CUSTOM && state.settings.sort == FeedSort.CUSTOM) layer = GroupLayer.CUSTOM else vm.settingsActions.setSort(sort)
                        },
                        onCustom = { layer = GroupLayer.CUSTOM },
                        onDiscover = { discovering = it },
                        onMedia = { MediaModes.set(shownId, !mediaOnly) },
                    )
                    Hairline()
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
                if (discovering) {
                    DiscoveryPane(shownId, state.group?.name.orEmpty(), actions, rankRequest = rankRequest)
                } else {
                    GroupFeedBody(state, vm, actions, list, PaddingValues(bottom = padding.calculateBottomPadding()), mediaOnly)
                }
            }
        }
        if (layer == GroupLayer.CUSTOM) CustomSettingsScreen(state, vm.settingsActions, onBack = { layer = null })
        if (layer == GroupLayer.LOADED_SEARCH) LoadedFeedSearchScreen(state.run.shown.threads, actions, onBack = { layer = null })
    }
    GroupSheets(layer, state, vm, onLayer = { layer = it }, onSwitch = { id -> if (id != shownId) shownId = id })
}

/**
 * The bar over a group's feed: back, the title and its menu, then the feed's
 * actions; while Discover is open, only a way to read it again, since the
 * feed's actions would act on the feed it hides. [onRank], with an AI set
 * up, opens Discover ranked by it (XTA's sparkle), in both.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupTopBar(
    state: GroupFeedUi,
    vm: GroupFeedViewModel,
    list: LazyListState,
    discovering: Boolean,
    onBack: (() -> Unit)?,
    onFeed: ((String) -> Unit)?,
    open: (GroupLayer) -> Unit,
    onSwitch: (String) -> Unit,
    onRank: (() -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) }
        },
        title = {
            Box {
                GroupFeedTitle(state, onClick = if (onBack != null) ({ menu = true }) else null)
                FeedSwitcherMenu(
                    expanded = menu,
                    state = state,
                    onFeed = onFeed,
                    onGroup = { id ->
                        menu = false
                        onSwitch(id)
                    },
                    onAllGroups = {
                        menu = false
                        open(GroupLayer.SWITCHER)
                    },
                    onDismiss = { menu = false },
                )
            }
        },
        actions = {
            onRank?.let { RankWithAiButton(onClick = it) }
            GroupActions(state, vm, list, discovering, open)
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun GroupActions(state: GroupFeedUi, vm: GroupFeedViewModel, list: LazyListState, discovering: Boolean, open: (GroupLayer) -> Unit) {
    val scope = rememberCoroutineScope()
    val reduced = LocalReducedMotion.current
    val groupId = state.group?.id
    if (discovering && groupId != null) {
        val discovery = discoveryViewModel(groupId)
        IconButton(onClick = discovery::load) { Icon(Icons.Filled.Refresh, stringResource(Res.string.groups_refresh)) }
        return
    }
    LoadedSearchButton { open(GroupLayer.LOADED_SEARCH) }
    IconButton(onClick = { open(GroupLayer.SETTINGS) }) { Icon(GroupIcons.Build, stringResource(Res.string.groups_filters)) }
    IconButton(onClick = { scope.launch { list.scrollToTop(reduced) } }) {
        Icon(GroupIcons.ArrowUpward, stringResource(Res.string.groups_scroll_to_top))
    }
    IconButton(onClick = { vm.refresh() }) { Icon(Icons.Filled.Refresh, stringResource(Res.string.groups_refresh)) }
}

/** The sheets a group's screen opens: its filters, the full group switcher and a new group. */
@Composable
private fun GroupSheets(layer: GroupLayer?, state: GroupFeedUi, vm: GroupFeedViewModel, onLayer: (GroupLayer?) -> Unit, onSwitch: (String) -> Unit) {
    val savePack = rememberPackSaver()
    when (layer) {
        GroupLayer.SETTINGS -> FeedSettingsSheet(state, vm.settingsActions, onCustom = { onLayer(GroupLayer.CUSTOM) }, onDismiss = { onLayer(null) })
        GroupLayer.SWITCHER -> GroupSwitcherSheet(
            state,
            onSwitch = { id ->
                onLayer(null)
                onSwitch(id)
            },
            onToggle = vm::toggleAlongside,
            onClear = vm::clearAlongside,
            onCreate = { onLayer(GroupLayer.CREATE) },
            onDismiss = { onLayer(null) },
        )
        GroupLayer.CREATE -> GroupEditSheet(state.session, rememberFollowedRows(state.session), null, vm.groupActions, savePack, onDismiss = { onLayer(null) })
        GroupLayer.CUSTOM, GroupLayer.LOADED_SEARCH, null -> Unit
    }
}

/** What the chips show besides the order: Discover open, and the media alone. */
private data class GroupChips(val discovering: Boolean, val mediaOnly: Boolean)

/**
 * The chips (group_chrome.dart): Recent, Popular, Custom with how many rules
 * are on, Discover, Media and the content filter when it filters. While
 * Discover is open it is the only chip, and closing it goes back to the feed.
 */
@Composable
private fun ControlBar(
    state: GroupFeedUi,
    chips: GroupChips,
    onSort: (FeedSort) -> Unit,
    onCustom: () -> Unit,
    onDiscover: (Boolean) -> Unit,
    onMedia: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (chips.discovering) {
            DiscoverChip(selected = true) { onDiscover(false) }
        } else {
            OrderChips(state, onSort)
            DiscoverChip(selected = false) { onDiscover(true) }
            MediaChip(chips.mediaOnly, onMedia)
            state.contentChip?.let { filter ->
                FilterChip(
                    selected = true,
                    onClick = onCustom,
                    label = { Text(contentFilterLabel(filter)) },
                    leadingIcon = { Icon(GroupIcons.FilterAltOutlined, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
    }
}

@Composable
private fun OrderChips(state: GroupFeedUi, onSort: (FeedSort) -> Unit) {
    val sort = state.settings.sort
    OrderChip(stringResource(Res.string.groups_recent), sort == FeedSort.RECENT) { onSort(FeedSort.RECENT) }
    OrderChip(stringResource(Res.string.groups_popular), sort == FeedSort.POPULAR) { onSort(FeedSort.POPULAR) }
    val filters = state.activeFilters
    val label = stringResource(Res.string.groups_custom)
    OrderChip(if (filters > 0) "$label ($filters)" else label, sort == FeedSort.CUSTOM) { onSort(FeedSort.CUSTOM) }
}

/** Discover's chip: a way into it, or, while it is open, its name with the way back to the feed. */
@Composable
private fun DiscoverChip(selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(Res.string.groups_discover), fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) },
        leadingIcon = { Icon(if (selected) DiscoveryIcons.Explore else DiscoveryIcons.ExploreOutlined, contentDescription = null, modifier = Modifier.size(18.dp)) },
        trailingIcon = if (selected) {
            { Icon(Icons.Filled.Close, stringResource(Res.string.close), modifier = Modifier.size(18.dp)) }
        } else {
            null
        },
    )
}

/** Media: the feed's photos and videos alone, as a grid. */
@Composable
private fun MediaChip(selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(Res.string.groups_media), fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) },
        leadingIcon = { Icon(if (selected) DiscoveryIcons.PhotoLibrary else GroupIcons.PhotoLibraryOutlined, contentDescription = null, modifier = Modifier.size(18.dp)) },
    )
}

@Composable
private fun OrderChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) },
    )
}

@Composable
fun contentFilterLabel(filter: ContentFilter): String = stringResource(
    when (filter) {
        ContentFilter.SFW -> Res.string.groups_content_sfw
        ContentFilter.NSFW -> Res.string.groups_content_nsfw
        ContentFilter.DEFAULT -> Res.string.groups_content_default
    },
)
