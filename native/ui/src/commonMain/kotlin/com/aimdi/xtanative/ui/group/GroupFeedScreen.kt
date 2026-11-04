package com.aimdi.xtanative.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.core.feed.CatchUpMessage
import com.aimdi.xtanative.core.feed.ContentFilter
import com.aimdi.xtanative.core.feed.FeedPause
import com.aimdi.xtanative.core.feed.FeedSort
import com.aimdi.xtanative.ui.common.BackHandling
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.common.ProblemRow
import com.aimdi.xtanative.ui.common.ProblemView
import com.aimdi.xtanative.ui.common.problemText
import com.aimdi.xtanative.ui.common.scrollToTop
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.back
import com.aimdi.xtanative.ui.generated.resources.close
import com.aimdi.xtanative.ui.generated.resources.dismiss
import com.aimdi.xtanative.ui.generated.resources.groups_batch_progress
import com.aimdi.xtanative.ui.generated.resources.groups_catch_up_end
import com.aimdi.xtanative.ui.generated.resources.groups_catch_up_incomplete
import com.aimdi.xtanative.ui.generated.resources.groups_catch_up_nothing_new
import com.aimdi.xtanative.ui.generated.resources.groups_catch_up_show_older
import com.aimdi.xtanative.ui.generated.resources.groups_caught_up
import com.aimdi.xtanative.ui.generated.resources.groups_content_default
import com.aimdi.xtanative.ui.generated.resources.groups_content_nsfw
import com.aimdi.xtanative.ui.generated.resources.groups_content_sfw
import com.aimdi.xtanative.ui.generated.resources.groups_custom
import com.aimdi.xtanative.ui.generated.resources.groups_discover
import com.aimdi.xtanative.ui.generated.resources.groups_feed_empty
import com.aimdi.xtanative.ui.generated.resources.groups_feed_issue
import com.aimdi.xtanative.ui.generated.resources.groups_feed_unrelated
import com.aimdi.xtanative.ui.generated.resources.groups_media
import com.aimdi.xtanative.ui.generated.resources.groups_filters
import com.aimdi.xtanative.ui.generated.resources.groups_more_info
import com.aimdi.xtanative.ui.generated.resources.groups_never_show_again
import com.aimdi.xtanative.ui.generated.resources.groups_no_members
import com.aimdi.xtanative.ui.generated.resources.groups_popular
import com.aimdi.xtanative.ui.generated.resources.groups_recent
import com.aimdi.xtanative.ui.generated.resources.groups_refresh
import com.aimdi.xtanative.ui.generated.resources.groups_retry
import com.aimdi.xtanative.ui.generated.resources.groups_scroll_to_top
import com.aimdi.xtanative.ui.generated.resources.groups_zen_end
import com.aimdi.xtanative.ui.generated.resources.groups_zen_load_more
import com.aimdi.xtanative.ui.feed.FeedMediaGrid
import com.aimdi.xtanative.ui.feed.FeedMix
import com.aimdi.xtanative.ui.feed.FeedThread
import com.aimdi.xtanative.ui.feed.MediaFoot
import com.aimdi.xtanative.ui.feed.MediaModes
import com.aimdi.xtanative.ui.feed.NewPostsPill
import com.aimdi.xtanative.ui.feed.Placed
import com.aimdi.xtanative.ui.feed.PluginPostsOnly
import com.aimdi.xtanative.ui.feed.SourceProblems
import com.aimdi.xtanative.ui.feed.SourcedPost
import com.aimdi.xtanative.ui.feed.rememberRunningPlugins
import com.aimdi.xtanative.ui.media.LocalInlinePlayer
import com.aimdi.xtanative.ui.pluginstore.LocalPlugins
import com.aimdi.xtanative.ui.plugins.XtaPlugin
import com.aimdi.xtanative.ui.media.rememberInlinePlayer
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.post.hasVideo
import com.aimdi.xtanative.ui.search.LoadedFeedSearchScreen
import com.aimdi.xtanative.ui.search.LoadedSearchButton
import com.aimdi.xtanative.ui.subscriptions.GroupEditSheet
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import com.aimdi.xtanative.ui.subscriptions.rememberPackSaver
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** The link XTA gives for the unrelated-posts bug. */
private const val UNRELATED_INFO = "https://github.com/Teskann/QuaX/issues/26"

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
    val graph = LocalGraph.current
    val feeds = LocalPlugins.current.feeds
    val vm: GroupFeedViewModel = viewModel(key = "group-feed-$shownId") { GroupFeedViewModel(graph, shownId, feeds) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    var layer by rememberSaveable { mutableStateOf<GroupLayer?>(null) }
    var discovering by rememberSaveable(shownId) { mutableStateOf(false) }
    val mediaKeys by MediaModes.keys.collectAsStateWithLifecycle()
    val mediaOnly = shownId in mediaKeys
    BackHandling(enabled = layer == GroupLayer.CUSTOM) { layer = null }
    BackHandling(enabled = discovering && layer == null) { discovering = false }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Column {
                    GroupTopBar(state, vm, list, discovering, onBack, onFeed, open = { layer = it }, onSwitch = { id -> if (id != shownId) shownId = id })
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
                    DiscoveryPane(shownId, state.group?.name.orEmpty(), actions)
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
 * feed's actions would act on the feed it hides.
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
        actions = { GroupActions(state, vm, list, discovering, open) },
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
        GroupLayer.CREATE -> GroupEditSheet(state.session, state.people, null, vm.groupActions, savePack, onDismiss = { onLayer(null) })
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

/** The feed under the chips; with [mediaOnly], its media alone (group_media_page.dart). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupFeedBody(state: GroupFeedUi, vm: GroupFeedViewModel, actions: PostActions, list: LazyListState, contentPadding: PaddingValues, mediaOnly: Boolean) {
    val plugin by vm.plugin.collectAsStateWithLifecycle()
    val plugins = rememberRunningPlugins()
    val media = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reduced = LocalReducedMotion.current
    val run = state.run
    val leading = if (run.stale != null && run.shown.threads.isNotEmpty()) 1 else 0
    LaunchedEffect(run.jump) {
        val jump = run.jump ?: return@LaunchedEffect
        list.scrollToItem(if (jump.threadIndex == 0 && jump.offset == 0) 0 else jump.threadIndex + leading, jump.offset)
        vm.jumped()
    }
    LaunchedEffect(list) {
        snapshotFlow { list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset <= 8 }.distinctUntilChanged().collect { vm.atTop(it) }
    }
    val keepPlace = { vm.keepPlace((list.firstVisibleItemIndex - leading).coerceAtLeast(0), list.firstVisibleItemScrollOffset) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { keepPlace() }
    DisposableEffect(Unit) { onDispose { keepPlace() } }
    val inline = rememberInlinePlayer()
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { inline?.pause() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { inline?.resume() }

    val buckets = remember(run.shown.threads, plugin.shown) { FeedMix.place(run.shown.threads, plugin.shown) }
    PullToRefreshBox(isRefreshing = run.refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize()) {
        val problem = run.problem
        val threads = run.shown.threads
        when {
            state.loaded && state.memberCount == 0 -> Whole { Message(stringResource(Res.string.groups_no_members)) }
            threads.isEmpty() && plugin.shown.isNotEmpty() -> PluginPostsOnly(plugin.shown, plugins, list, contentPadding, problem) { vm.refresh() }
            threads.isEmpty() && problem != null -> Whole { ProblemView(problem, { vm.refresh() }) }
            threads.isEmpty() && plugin.loading -> Whole { LoadingRow() }
            threads.isEmpty() && run.answered && run.shown.pause == FeedPause.CAUGHT_UP -> Whole { CaughtUpCard(run.shown.catchUpMessage, run, vm) }
            threads.isEmpty() && run.answered && !run.refreshing -> Whole { Message(stringResource(Res.string.groups_feed_empty)) }
            threads.isEmpty() -> Whole { if (!run.refreshing) LoadingRow() }
            mediaOnly -> FeedMediaGrid(
                threads,
                canLoadMore = run.shown.canLoadMore && run.moreProblem == null && !run.fromCache,
                foot = MediaFoot(run.loadingMore, run.moreProblem) { vm.loadMore() },
                actions = actions,
                list = media,
                contentPadding = contentPadding,
                onMore = vm::loadMore,
            )
            else -> CompositionLocalProvider(LocalInlinePlayer provides inline) {
                FeedList(state, vm, actions, list, contentPadding, buckets, plugins)
            }
        }
        val progress = state.progress
        if (progress.loading && progress.total > 0) {
            LinearProgressIndicator(progress = { progress.answered.toFloat() / progress.total }, modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
        Column(Modifier.align(Alignment.TopCenter).padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.unrelatedNotice) UnrelatedPill(vm)
            if (progress.canRetry) RetryPill(progress.answered, progress.total, vm::retryFailed)
            if (plugin.newHeld > 0) {
                NewPostsPill(plugin.newHeld) {
                    scope.launch { list.scrollToTop(reduced) }
                    vm.revealPlugins()
                }
            }
            SourceProblems(plugin, plugins, onRetry = vm::retryPlugin, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun FeedList(
    state: GroupFeedUi,
    vm: GroupFeedViewModel,
    actions: PostActions,
    list: LazyListState,
    contentPadding: PaddingValues,
    buckets: List<List<SourcedPost>>,
    plugins: Map<String, XtaPlugin>,
) {
    val run = state.run
    val threads = run.shown.threads
    val nearEnd by remember(list) {
        derivedStateOf {
            val info = list.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd, threads.size, run.shown.canLoadMore) { if (nearEnd) vm.loadMore() }
    val withVideo = remember(threads) { threads.filter { it.hasVideo }.mapTo(HashSet()) { it.id } }
    val playingId by remember(list, withVideo) {
        derivedStateOf {
            val info = list.layoutInfo
            info.visibleItemsInfo.firstOrNull { item ->
                val shown = minOf(item.offset + item.size, info.viewportEndOffset) - maxOf(item.offset, info.viewportStartOffset)
                item.key in withVideo && shown >= item.size * 0.6f
            }?.key
        }
    }
    val divider = run.divider
    LazyColumn(state = list, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        run.stale?.let { stale -> item(key = "stale", contentType = "notice") { StaleNotice(stale, vm) } }
        itemsIndexed(threads, key = { _, thread -> thread.id }, contentType = { _, _ -> "thread" }) { index, thread ->
            if (index == divider) CaughtUpDivider()
            val reason = run.shown.folds[thread.id]
            Placed(buckets, index, plugins) {
                FeedThread(thread, reason, actions, playing = thread.id == playingId)
            }
        }
        item(key = "footer", contentType = "footer") {
            val more = run.moreProblem
            when {
                run.loadingMore -> LoadingRow()
                more != null -> ProblemRow(more, { vm.loadMore() })
                run.shown.pause == FeedPause.CAUGHT_UP -> CaughtUpCard(run.shown.catchUpMessage, run, vm)
                run.shown.pause == FeedPause.PAGE_CAP -> ZenEndCard(vm::continuePastPause)
                else -> Spacer(Modifier.heightIn(min = 48.dp))
            }
        }
    }
}

/** "You're caught up", between the new posts and the ones read before. */
@Composable
private fun CaughtUpDivider() {
    val color = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = color)
        Text(stringResource(Res.string.groups_caught_up), color = color, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
        HorizontalDivider(Modifier.weight(1f), color = color)
    }
}

/**
 * The card closing a catch-up feed (caught_up_end_card.dart): built once the
 * reader reaches it, which is when the read position may move.
 */
@Composable
private fun CaughtUpCard(message: CatchUpMessage, run: FeedRun, vm: GroupFeedViewModel) {
    LaunchedEffect(Unit) { vm.caughtUp() }
    val incomplete = message == CatchUpMessage.MAY_BE_INCOMPLETE
    val hint = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(if (incomplete) GroupIcons.MoreHoriz else GroupIcons.CheckCircleOutline, contentDescription = null, tint = hint, modifier = Modifier.size(36.dp))
        Spacer(Modifier.size(12.dp))
        if (!incomplete) Text(stringResource(Res.string.groups_caught_up), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            stringResource(
                when (message) {
                    CatchUpMessage.CAUGHT_UP -> Res.string.groups_catch_up_end
                    CatchUpMessage.NOTHING_NEW -> Res.string.groups_catch_up_nothing_new
                    CatchUpMessage.MAY_BE_INCOMPLETE -> Res.string.groups_catch_up_incomplete
                },
            ),
            color = hint,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (run.shown.stops.canContinue) TextButton(onClick = vm::continuePastPause) { Text(stringResource(Res.string.groups_catch_up_show_older)) }
    }
}

/** Zen mode's pause: the feed read its pages for this visit. */
@Composable
private fun ZenEndCard(onLoadMore: () -> Unit) {
    val hint = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(GroupIcons.SelfImprovement, contentDescription = null, tint = hint, modifier = Modifier.size(36.dp))
        Text(stringResource(Res.string.groups_zen_end), color = hint, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
        TextButton(onClick = onLoadMore, modifier = Modifier.padding(top = 4.dp)) { Text(stringResource(Res.string.groups_zen_load_more)) }
    }
}

/** The first page failed: the posts shown are from before, and why it failed. */
@Composable
private fun StaleNotice(problem: com.aimdi.xtanative.data.Problem, vm: GroupFeedViewModel) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(problemText(problem), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        TextButton(onClick = { vm.refresh() }) { Text(stringResource(Res.string.groups_retry)) }
        IconButton(onClick = vm::dismissStale) { Icon(Icons.Filled.Close, stringResource(Res.string.dismiss)) }
    }
}

/** A pill floating over the posts. */
@Composable
private fun Pill(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 3.dp) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

/** Some sources could not be read: how many were, and a retry of the rest. */
@Composable
private fun RetryPill(answered: Int, total: Int, onRetry: () -> Unit) {
    Pill {
        Text(stringResource(Res.string.groups_batch_progress, answered, total), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
        TextButton(onClick = onRetry) { Text(stringResource(Res.string.groups_retry)) }
    }
}

/** X answered with posts from outside the group (unrelated_posts.dart): a pill over them, and why. */
@Composable
private fun UnrelatedPill(vm: GroupFeedViewModel) {
    var explaining by remember { mutableStateOf(false) }
    Pill {
        Icon(GroupIcons.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 8.dp).size(18.dp))
        Text(stringResource(Res.string.groups_feed_issue), modifier = Modifier.padding(start = 8.dp))
        IconButton(onClick = { explaining = true }) { Icon(GroupIcons.InfoOutline, stringResource(Res.string.groups_more_info)) }
    }
    if (explaining) {
        var never by remember { mutableStateOf(false) }
        val uri = LocalUriHandler.current
        val close = {
            explaining = false
            if (never) {
                vm.settingsActions.hideUnrelatedWarnings()
                vm.dismissUnrelated()
            }
        }
        AlertDialog(
            onDismissRequest = close,
            icon = { Icon(GroupIcons.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(Res.string.groups_feed_issue)) },
            text = {
                Column {
                    Text(stringResource(Res.string.groups_feed_unrelated))
                    Row(Modifier.padding(top = 16.dp).clickable { never = !never }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = never, onCheckedChange = { never = it })
                        Text(stringResource(Res.string.groups_never_show_again))
                    }
                }
            },
            confirmButton = { TextButton(onClick = close) { Text(stringResource(Res.string.close)) } },
            dismissButton = {
                TextButton(onClick = {
                    runCatching { uri.openUri(UNRELATED_INFO) }
                    close()
                }) { Text(stringResource(Res.string.groups_more_info)) }
            },
        )
    }
}

/** A whole-screen state that can still be pulled to refresh. */
@Composable
private fun Whole(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        content()
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 32.dp),
    )
}
