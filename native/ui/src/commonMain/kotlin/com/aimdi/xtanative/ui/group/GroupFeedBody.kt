package com.aimdi.xtanative.ui.group

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.core.feed.CatchUpMessage
import com.aimdi.xtanative.core.feed.FeedPause
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.ui.common.FeedSkeleton
import com.aimdi.xtanative.ui.common.PostSkeleton
import com.aimdi.xtanative.ui.common.ReadFailureNotice
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.common.ProblemRow
import com.aimdi.xtanative.ui.common.ProblemView
import com.aimdi.xtanative.ui.common.WithReadingColumn
import com.aimdi.xtanative.ui.common.readFailureText
import com.aimdi.xtanative.ui.common.savedAtText
import com.aimdi.xtanative.ui.common.scrollToTop
import com.aimdi.xtanative.ui.feed.CaughtUpDivider
import com.aimdi.xtanative.ui.feed.FeedLayout
import com.aimdi.xtanative.ui.feed.FeedMediaGrid
import com.aimdi.xtanative.ui.feed.FeedMix
import com.aimdi.xtanative.ui.feed.FeedThread
import com.aimdi.xtanative.ui.feed.FeedThreadRow
import com.aimdi.xtanative.ui.feed.MediaFoot
import com.aimdi.xtanative.ui.feed.NewPostsPill
import com.aimdi.xtanative.ui.feed.Placed
import com.aimdi.xtanative.ui.feed.PluginPostRow
import com.aimdi.xtanative.ui.feed.PluginPostsOnly
import com.aimdi.xtanative.ui.feed.SourceChips
import com.aimdi.xtanative.ui.feed.SourceLoadingHairline
import com.aimdi.xtanative.ui.feed.SourcedPost
import com.aimdi.xtanative.ui.feed.rememberFeedLayout
import com.aimdi.xtanative.ui.feed.rememberNearEnd
import com.aimdi.xtanative.ui.feed.rememberRunningPlugins
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.problem_unable_to_load_the_next_page_of_tweets
import com.aimdi.xtanative.ui.generated.resources.close
import com.aimdi.xtanative.ui.generated.resources.groups_batch_progress
import com.aimdi.xtanative.ui.generated.resources.groups_catch_up_end
import com.aimdi.xtanative.ui.generated.resources.groups_catch_up_incomplete
import com.aimdi.xtanative.ui.generated.resources.groups_catch_up_nothing_new
import com.aimdi.xtanative.ui.generated.resources.groups_catch_up_show_older
import com.aimdi.xtanative.ui.generated.resources.groups_caught_up
import com.aimdi.xtanative.ui.generated.resources.groups_feed_empty
import com.aimdi.xtanative.ui.generated.resources.groups_feed_issue
import com.aimdi.xtanative.ui.generated.resources.groups_feed_unrelated
import com.aimdi.xtanative.ui.generated.resources.groups_more_info
import com.aimdi.xtanative.ui.generated.resources.groups_never_show_again
import com.aimdi.xtanative.ui.generated.resources.groups_no_members
import com.aimdi.xtanative.ui.generated.resources.groups_retry
import com.aimdi.xtanative.ui.generated.resources.groups_zen_end
import com.aimdi.xtanative.ui.generated.resources.groups_zen_load_more
import com.aimdi.xtanative.ui.generated.resources.unable_to_load_the_group
import com.aimdi.xtanative.ui.media.LocalInlinePlayer
import com.aimdi.xtanative.ui.media.rememberInlinePlayer
import com.aimdi.xtanative.ui.pluginstore.LocalPlugins
import com.aimdi.xtanative.ui.plugins.ScrollsToTop
import com.aimdi.xtanative.ui.plugins.XtaPlugin
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import java.time.Instant
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** The link XTA gives for the unrelated-posts bug. */
private const val UNRELATED_INFO = "https://github.com/Teskann/QuaX/issues/26"

/** The key a group feed's view model is kept under: the group's id, or the Following feed's for null. */
fun groupFeedKey(groupId: String?): String = if (groupId == null) "following-feed" else "group-feed-$groupId"

/** The view model of [groupId]'s feed, the Following feed's for null, with the plugins' posts it mixes in. */
@Composable
fun groupFeedViewModel(groupId: String?): GroupFeedViewModel {
    val graph = LocalGraph.current
    val feeds = LocalPlugins.current.feeds
    return viewModel(key = groupFeedKey(groupId)) { GroupFeedViewModel(graph, groupId, feeds) }
}

/**
 * A group feed under its bar (_feed.dart's SubscriptionGroupFeed): the posts
 * with the divider where the reader left off, catch-up mode's and zen mode's
 * ends, the batches' progress with a retry of those that failed, the
 * unrelated-posts pill, the plugins' posts among X's, and the empty
 * group's and empty feed's words; with [mediaOnly], its media alone
 * (group_media_page.dart). Every group feed is drawn so: a group's screen,
 * Home's Following, a page of the bar and the deck's columns. [toTop] grows
 * when the reader asks for the top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupFeedBody(
    state: GroupFeedUi,
    vm: GroupFeedViewModel,
    actions: PostActions,
    list: LazyListState,
    contentPadding: PaddingValues,
    mediaOnly: Boolean,
    toTop: Int = 0,
) {
    val plugin by vm.plugin.collectAsStateWithLifecycle()
    val plugins = rememberRunningPlugins()
    val media = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reduced = LocalReducedMotion.current
    val run = state.run
    val leading = if (run.stale != null && run.shown.threads.isNotEmpty()) 1 else 0
    ScrollsToTop(toTop) { (if (mediaOnly) media else list).scrollToTop(reduced) }
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
    PullToRefreshBox(isRefreshing = run.refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize().readVisibility(vm::visible)) {
        val problem = run.problem
        val threads = run.shown.threads
        when {
            state.missing -> Whole { ProblemView(Problem.Other, { vm.refresh() }, context = stringResource(Res.string.unable_to_load_the_group)) }
            // A group is empty when it has nothing from any network: the Following feed may have the plugins' alone.
            state.loaded && state.memberCount == 0 && plugin.shown.isEmpty() && !plugin.loading -> Whole { Message(stringResource(Res.string.groups_no_members)) }
            threads.isEmpty() && plugin.shown.isNotEmpty() -> PluginPostsOnly(plugin.shown, plugins, list, contentPadding, problem) { vm.refresh() }
            threads.isEmpty() && problem != null -> Whole { ProblemView(problem, { vm.refresh() }) }
            threads.isEmpty() && plugin.loading -> SkeletonPage(contentPadding)
            threads.isEmpty() && run.answered && run.shown.pause == FeedPause.CAUGHT_UP -> Whole { CaughtUpCard(run.shown.catchUpMessage, run, vm) }
            threads.isEmpty() && run.answered && !run.refreshing -> Whole { Message(stringResource(Res.string.groups_feed_empty)) }
            threads.isEmpty() -> SkeletonPage(contentPadding)
            mediaOnly -> FeedMediaGrid(
                threads,
                canLoadMore = run.shown.canLoadMore && run.moreProblem == null && !run.fromCache,
                foot = MediaFoot(run.loadingMore, run.moreProblem) { vm.loadMore() },
                actions = actions,
                list = media,
                contentPadding = contentPadding,
                onMore = vm::loadMore,
                updates = remember(vm) { vm.ui.map { it.run.shown.threads }.distinctUntilChanged() },
            )
            else -> CompositionLocalProvider(LocalInlinePlayer provides inline) {
                FeedList(state, vm, actions, list, contentPadding, FeedPlugins(buckets, plugin.shown, plugins))
            }
        }
        val progress = state.progress
        if (progress.loading && progress.total > 0) {
            LinearProgressIndicator(progress = { progress.answered.toFloat() / progress.total }, modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
        SourceLoadingHairline(plugin.loading, Modifier.align(Alignment.TopCenter))
        Column(Modifier.align(Alignment.TopCenter).padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.unrelatedNotice) UnrelatedPill(vm)
            if (progress.canRetry) RetryPill(progress.answered, progress.total, vm::retryFailed)
            if (plugin.newHeld > 0) {
                NewPostsPill(plugin.newHeld) {
                    scope.launch { list.scrollToTop(reduced) }
                    vm.revealPlugins()
                }
            }
            SourceChips(plugin, plugins, onRetry = vm::retryPlugin, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

/** The plugins' posts of a feed list: those placed above each thread ([buckets]), all of them, and who draws them. */
private class FeedPlugins(val buckets: List<List<SourcedPost>>, val shown: List<SourcedPost>, val plugins: Map<String, XtaPlugin>)

@Composable
private fun FeedList(state: GroupFeedUi, vm: GroupFeedViewModel, actions: PostActions, list: LazyListState, contentPadding: PaddingValues, mixed: FeedPlugins) {
    val run = state.run
    val threads = run.shown.threads
    val nearEnd by rememberNearEnd(list)
    LaunchedEffect(nearEnd, threads.size, run.shown.canLoadMore) { if (nearEnd) vm.loadMore() }
    val layout = rememberFeedLayout(threads, mixed.shown)
    val byKey = remember(threads) { threads.associateBy(FeedLayout::xKey) }
    val posts = remember(mixed.shown) { mixed.shown.associateBy { it.key } }
    val draw: @Composable (XThread) -> Unit = { thread -> FeedThread(thread, run.shown.folds[thread.id], actions) }
    // A stack holds X's posts and the plugins' alike.
    val drawKey: @Composable (String) -> Unit = { key ->
        val post = posts[key]
        if (post != null) PluginPostRow(post, mixed.plugins[post.source]) else byKey[key]?.let { draw(it) }
    }
    val divider = run.divider
    WithReadingColumn(contentPadding) { padding ->
        LazyColumn(state = list, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            run.stale?.let { stale -> item(key = "stale", contentType = "notice") { StaleNotice(stale, run.savedAt, vm) } }
            itemsIndexed(threads, key = { _, thread -> thread.id }, contentType = { _, _ -> "thread" }) { index, thread ->
                if (index == divider) CaughtUpDivider()
                Placed(mixed.buckets, index, mixed.plugins, layout, posts, drawKey) {
                    FeedThreadRow(layout.ofThread(index, thread), thread, actions, drawKey) { draw(thread) }
                }
            }
            item(key = "footer", contentType = "footer") {
                val more = run.moreProblem
                when {
                    run.loadingMore -> PostSkeleton()
                    more != null -> ProblemRow(more, { vm.loadMore() }, context = stringResource(Res.string.problem_unable_to_load_the_next_page_of_tweets))
                    run.shown.pause == FeedPause.CAUGHT_UP -> CaughtUpCard(run.shown.catchUpMessage, run, vm)
                    run.shown.pause == FeedPause.PAGE_CAP -> ZenEndCard(vm::continuePastPause)
                    else -> Spacer(Modifier.heightIn(min = 48.dp))
                }
            }
        }
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

/** Zen mode's pause, the feed's pages read for this visit (paginated_tweet_list.dart's _ZenFeedEndCard): a calm end, and a way to read on. */
@Composable
internal fun ZenEndCard(onLoadMore: () -> Unit) {
    val hint = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(GroupIcons.SelfImprovement, contentDescription = null, tint = hint, modifier = Modifier.size(36.dp))
        Text(stringResource(Res.string.groups_zen_end), color = hint, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
        TextButton(onClick = onLoadMore, modifier = Modifier.padding(top = 4.dp)) { Text(stringResource(Res.string.groups_zen_load_more)) }
    }
}

/**
 * The first page failed: why, how old the posts shown are (XTA's
 * StaleFeedBanner.ageLineOf), and its Retry, ⓘ and Close (ReaderFailureNotice).
 */
@Composable
private fun StaleNotice(problem: Problem, savedAt: Long?, vm: GroupFeedViewModel) {
    val age = savedAtText(savedAt?.let(Instant::ofEpochMilli))
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(readFailureText(problem), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(age, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ReadFailureNotice(problem, { vm.refresh() }, contextMessage = age, onDismiss = vm::dismissStale)
    }
}

/** The first page on its way: post-shaped bones, still pulled to refresh (XTA's TweetFeedSkeleton). */
@Composable
private fun SkeletonPage(contentPadding: PaddingValues) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = contentPadding.calculateTopPadding())) {
        FeedSkeleton()
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

/**
 * Tells [onChange] whether what it modifies can be seen, as XTA's
 * ReadVisibility: some of it inside the window (a deck column or a page of
 * the bar scrolled away is not), in an app that is not in the background,
 * and still on screen at all.
 */
@Composable
private fun Modifier.readVisibility(onChange: (Boolean) -> Unit): Modifier {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var inView by remember { mutableStateOf(true) }
    val seen = inView && lifecycle.isAtLeast(Lifecycle.State.STARTED)
    val latest by rememberUpdatedState(onChange)
    LaunchedEffect(seen) { latest(seen) }
    DisposableEffect(Unit) { onDispose { latest(false) } }
    return onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInWindow()
        inView = bounds.width > 0f && bounds.height > 0f
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
