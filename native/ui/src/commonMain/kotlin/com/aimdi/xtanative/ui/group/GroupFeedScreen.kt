package com.aimdi.xtanative.ui.group

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.core.feed.CatchUpMessage
import com.aimdi.xtanative.core.feed.ContentFilter
import com.aimdi.xtanative.core.feed.FeedPause
import com.aimdi.xtanative.core.feed.FeedSort
import com.aimdi.xtanative.core.x.model.XThread
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
import com.aimdi.xtanative.ui.generated.resources.groups_feed_empty
import com.aimdi.xtanative.ui.generated.resources.groups_feed_issue
import com.aimdi.xtanative.ui.generated.resources.groups_feed_unrelated
import com.aimdi.xtanative.ui.generated.resources.groups_filters
import com.aimdi.xtanative.ui.generated.resources.groups_fold_hide_again
import com.aimdi.xtanative.ui.generated.resources.groups_fold_matched
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
import com.aimdi.xtanative.ui.media.LocalInlinePlayer
import com.aimdi.xtanative.ui.media.rememberInlinePlayer
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.post.ThreadRows
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

/**
 * A group's feed (group_screen.dart, _feed.dart, _feed_shell.dart): its
 * title opens the group switcher, which swaps the group in place so Back
 * returns to wherever the first one was opened from; its bar has the search
 * of the posts loaded, the filters, a way to the top and a refresh; its
 * chips pick the order.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupFeedScreen(groupId: String, actions: PostActions, onBack: () -> Unit) {
    var shownId by rememberSaveable { mutableStateOf(groupId) }
    val graph = LocalGraph.current
    val vm: GroupFeedViewModel = viewModel(key = "group-feed-$shownId") { GroupFeedViewModel(graph, shownId) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var settings by rememberSaveable { mutableStateOf(false) }
    var custom by rememberSaveable { mutableStateOf(false) }
    var switcher by rememberSaveable { mutableStateOf(false) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var searchingLoaded by rememberSaveable { mutableStateOf(false) }
    val savePack = rememberPackSaver()
    val reduced = LocalReducedMotion.current
    BackHandling(enabled = custom) { custom = false }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Column {
                    TopAppBar(
                        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) } },
                        title = { GroupFeedTitle(state, onClick = { switcher = true }) },
                        actions = {
                            LoadedSearchButton { searchingLoaded = true }
                            IconButton(onClick = { settings = true }) { Icon(GroupIcons.Build, stringResource(Res.string.groups_filters)) }
                            IconButton(onClick = { scope.launch { list.scrollToTop(reduced) } }) {
                                Icon(GroupIcons.ArrowUpward, stringResource(Res.string.groups_scroll_to_top))
                            }
                            IconButton(onClick = { vm.refresh() }) { Icon(Icons.Filled.Refresh, stringResource(Res.string.groups_refresh)) }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    )
                    ControlBar(state, onSort = { sort ->
                        if (sort == FeedSort.CUSTOM && state.settings.sort == FeedSort.CUSTOM) custom = true else vm.settingsActions.setSort(sort)
                    }, onCustom = { custom = true })
                    Hairline()
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
                GroupFeedBody(state, vm, actions, list, PaddingValues(bottom = padding.calculateBottomPadding()))
            }
        }
        if (custom) CustomSettingsScreen(state, vm.settingsActions, onBack = { custom = false })
        if (searchingLoaded) LoadedFeedSearchScreen(state.run.shown.threads, actions, onBack = { searchingLoaded = false })
    }

    if (settings) {
        FeedSettingsSheet(state, vm.settingsActions, onCustom = {
            settings = false
            custom = true
        }, onDismiss = { settings = false })
    }
    if (switcher) {
        GroupSwitcherSheet(
            state,
            onSwitch = { id ->
                switcher = false
                if (id != shownId) shownId = id
            },
            onToggle = vm::toggleAlongside,
            onClear = vm::clearAlongside,
            onCreate = {
                switcher = false
                creating = true
            },
            onDismiss = { switcher = false },
        )
    }
    if (creating) GroupEditSheet(state.session, state.people, null, vm.groupActions, savePack, onDismiss = { creating = false })
}

/** The order chips (group_chrome.dart): Recent, Popular, Custom with how many rules are on, and the content filter when it filters. */
@Composable
private fun ControlBar(state: GroupFeedUi, onSort: (FeedSort) -> Unit, onCustom: () -> Unit) {
    val sort = state.settings.sort
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OrderChip(stringResource(Res.string.groups_recent), sort == FeedSort.RECENT) { onSort(FeedSort.RECENT) }
        OrderChip(stringResource(Res.string.groups_popular), sort == FeedSort.POPULAR) { onSort(FeedSort.POPULAR) }
        val filters = state.activeFilters
        val label = stringResource(Res.string.groups_custom)
        OrderChip(if (filters > 0) "$label ($filters)" else label, sort == FeedSort.CUSTOM) { onSort(FeedSort.CUSTOM) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupFeedBody(state: GroupFeedUi, vm: GroupFeedViewModel, actions: PostActions, list: LazyListState, contentPadding: PaddingValues) {
    val graph = LocalGraph.current
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
    val session by graph.sessions.state.collectAsStateWithLifecycle()
    val inline = rememberInlinePlayer()
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { inline?.pause() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { inline?.resume() }

    PullToRefreshBox(isRefreshing = run.refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize()) {
        val problem = run.problem
        val threads = run.shown.threads
        when {
            state.loaded && state.memberCount == 0 -> Whole { Message(stringResource(Res.string.groups_no_members)) }
            threads.isEmpty() && problem != null -> Whole { ProblemView(problem, { vm.refresh() }) }
            threads.isEmpty() && run.answered && run.shown.pause == FeedPause.CAUGHT_UP -> Whole { CaughtUpCard(run.shown.catchUpMessage, run, vm) }
            threads.isEmpty() && run.answered && !run.refreshing -> Whole { Message(stringResource(Res.string.groups_feed_empty)) }
            threads.isEmpty() -> Whole { if (!run.refreshing) LoadingRow() }
            else -> CompositionLocalProvider(LocalInlinePlayer provides inline.takeIf { session?.autoplay ?: true }) {
                FeedList(state, vm, actions, list, contentPadding)
            }
        }
        val progress = state.progress
        if (progress.loading && progress.total > 0) {
            LinearProgressIndicator(progress = { progress.answered.toFloat() / progress.total }, modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
        Column(Modifier.align(Alignment.TopCenter).padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.unrelatedNotice) UnrelatedPill(vm)
            if (progress.canRetry) RetryPill(progress.answered, progress.total, vm::retryFailed)
        }
    }
}

@Composable
private fun FeedList(state: GroupFeedUi, vm: GroupFeedViewModel, actions: PostActions, list: LazyListState, contentPadding: PaddingValues) {
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
            if (reason != null) FoldedThread(thread, reason, actions, thread.id == playingId) else ThreadRows(thread, actions, playing = thread.id == playingId)
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

/** A thread folded behind why (folded_chain.dart): a tap shows it, and hides it again. */
@Composable
private fun FoldedThread(thread: XThread, reason: String, actions: PostActions, playing: Boolean) {
    var open by rememberSaveable(thread.id) { mutableStateOf(false) }
    Column(Modifier.animateContentSize()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { open = !open }.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val accent = if (open) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Icon(if (open) GroupIcons.ExpandLess else GroupIcons.VisibilityOffOutlined, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (open) stringResource(Res.string.groups_fold_hide_again) else stringResource(Res.string.groups_fold_matched, reason),
                style = if (open) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall,
                color = accent,
                modifier = Modifier.weight(1f),
            )
            if (!open) Icon(GroupIcons.ExpandMore, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        }
        if (open) ThreadRows(thread, actions, playing = playing) else Hairline()
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
