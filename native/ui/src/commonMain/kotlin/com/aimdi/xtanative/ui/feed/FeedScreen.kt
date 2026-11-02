package com.aimdi.xtanative.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.ProblemRow
import com.aimdi.xtanative.ui.common.ProblemView
import com.aimdi.xtanative.ui.common.problemText
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.dismiss
import com.aimdi.xtanative.ui.generated.resources.feed_empty
import com.aimdi.xtanative.ui.generated.resources.feed_nobody_followed
import com.aimdi.xtanative.ui.generated.resources.feed_partial
import com.aimdi.xtanative.ui.generated.resources.following
import com.aimdi.xtanative.ui.generated.resources.new_posts
import com.aimdi.xtanative.ui.generated.resources.search
import com.aimdi.xtanative.ui.generated.resources.settings
import com.aimdi.xtanative.ui.media.LocalInlinePlayer
import com.aimdi.xtanative.ui.media.rememberInlinePlayer
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.post.ThreadRows
import com.aimdi.xtanative.ui.post.hasVideo
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The top of the scroll of one tab, asked for by tapping the title. */
private data class ToTop(val page: Int, val count: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(actions: PostActions, openSearch: () -> Unit, openSettings: () -> Unit) {
    val graph = LocalGraph.current
    val session by graph.sessions.state.collectAsStateWithLifecycle()
    // Following first, then XTA's groups as tabs, in XTA's order.
    val tabs: List<FollowGroup?> = listOf<FollowGroup?>(null) + session?.feedGroups.orEmpty()
    val pager = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()
    val bar = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var toTop by remember { mutableStateOf(ToTop(0, 0)) }
    val following = stringResource(Res.string.following)

    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            tabs.getOrNull(pager.currentPage)?.let(::tabLabel) ?: following,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { toTop = ToTop(pager.currentPage, toTop.count + 1) },
                        )
                    },
                    actions = {
                        IconButton(onClick = openSearch) { Icon(Icons.Filled.Search, stringResource(Res.string.search)) }
                        IconButton(onClick = openSettings) { Icon(Icons.Filled.Settings, stringResource(Res.string.settings)) }
                    },
                    scrollBehavior = bar,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background,
                    ),
                )
                if (tabs.size > 1) {
                    ScrollableTabRow(
                        selectedTabIndex = pager.currentPage.coerceIn(0, tabs.lastIndex),
                        edgePadding = 8.dp,
                        containerColor = MaterialTheme.colorScheme.background,
                        divider = { Hairline() },
                    ) {
                        tabs.forEachIndexed { index, group ->
                            Tab(
                                selected = pager.currentPage == index,
                                onClick = {
                                    if (pager.currentPage == index) {
                                        toTop = ToTop(index, toTop.count + 1)
                                    } else {
                                        scope.launch { pager.animateScrollToPage(index) }
                                    }
                                },
                                text = { Text(group?.let(::tabLabel) ?: following, maxLines = 1) },
                                selectedContentColor = MaterialTheme.colorScheme.onBackground,
                                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        HorizontalPager(
            state = pager,
            key = { page -> tabs.getOrNull(page)?.id ?: FOLLOWING },
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
        ) { page ->
            FeedPage(tabs.getOrNull(page)?.id, page, toTop, actions, PaddingValues(bottom = padding.calculateBottomPadding()))
        }
    }
}

private const val FOLLOWING = "following"

private fun tabLabel(group: FollowGroup): String = listOfNotNull(group.emoji, group.name).joinToString(" ")

/** One tab: a feed with its own pull to refresh, place and cache. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedPage(groupId: String?, page: Int, toTop: ToTop, actions: PostActions, contentPadding: PaddingValues) {
    val graph = LocalGraph.current
    val vm: FeedViewModel = viewModel(key = "feed-" + (groupId ?: FOLLOWING)) { FeedViewModel(graph, groupId) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // A page swiped back into view must not answer a tap it already answered.
    var answeredTap by rememberSaveable { mutableIntStateOf(toTop.count) }
    LaunchedEffect(toTop) {
        if (toTop.page == page && toTop.count > answeredTap) {
            answeredTap = toTop.count
            list.animateScrollToItem(0)
        }
    }
    // Back at the last place on launch; at the top after a pull.
    LaunchedEffect(state.jump) {
        val jump = state.jump ?: return@LaunchedEffect
        val leading = if (state.partial != null) 1 else 0
        list.scrollToItem(if (jump.threadIndex == 0 && jump.offset == 0) 0 else jump.threadIndex + leading, jump.offset)
        vm.jumped()
    }
    val keepPlace = {
        val index = list.firstVisibleItemIndex - if (state.partial != null) 1 else 0
        if (index >= 0) vm.keepPlace(index, list.firstVisibleItemScrollOffset) else vm.keepPlace(0, 0)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { keepPlace() }
    DisposableEffect(Unit) { onDispose { keepPlace() } }
    val session by graph.sessions.state.collectAsStateWithLifecycle()
    val autoplay = session?.autoplay ?: true
    val inline = rememberInlinePlayer()
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { inline?.pause() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { inline?.resume() }
    val atTop by remember(list) { derivedStateOf { list.firstVisibleItemIndex == 0 } }
    LaunchedEffect(atTop) { if (atTop) vm.seenNew() }

    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize()) {
        val problem = state.problem
        when {
            state.threads.isEmpty() && problem != null -> Whole { ProblemView(problem, { vm.refresh() }) }
            state.threads.isEmpty() && state.followed == 0 -> Whole { Message(stringResource(Res.string.feed_nobody_followed)) }
            state.threads.isEmpty() && state.answered && !state.refreshing -> Whole { Message(stringResource(Res.string.feed_empty)) }
            // The pull indicator already says a first load is under way.
            state.threads.isEmpty() -> Whole { if (!state.refreshing) LoadingRow() }
            else -> CompositionLocalProvider(LocalInlinePlayer provides inline.takeIf { autoplay }) {
                FeedList(state, vm, actions, list, contentPadding)
            }
        }
        AnimatedVisibility(
            visible = state.newAbove > 0 && !atTop,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
        ) {
            NewPostsPill(state.newAbove) {
                scope.launch { list.animateScrollToItem(0) }
                vm.seenNew()
            }
        }
    }
}

/** "↑ 12 new posts": the posts a refresh put above the reader. */
@Composable
private fun NewPostsPill(count: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(
            pluralStringResource(Res.plurals.new_posts, count, count),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun FeedList(
    state: FeedUi,
    vm: FeedViewModel,
    actions: PostActions,
    list: androidx.compose.foundation.lazy.LazyListState,
    contentPadding: PaddingValues,
) {
    val nearEnd by remember(list) {
        derivedStateOf {
            val info = list.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd, state.threads.size, state.hasMore) {
        if (nearEnd) vm.loadMore()
    }
    // The first row with a video that is mostly on screen plays it.
    val withVideo = remember(state.threads) { state.threads.filter { it.hasVideo }.mapTo(HashSet()) { it.id } }
    val playingId by remember(list, withVideo) {
        derivedStateOf {
            val info = list.layoutInfo
            info.visibleItemsInfo.firstOrNull { item ->
                val shown = minOf(item.offset + item.size, info.viewportEndOffset) - maxOf(item.offset, info.viewportStartOffset)
                item.key in withVideo && shown >= item.size * 0.6f
            }?.key
        }
    }
    LazyColumn(state = list, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        state.partial?.let { partial ->
            item(key = "partial", contentType = "notice") { PartialNotice(partial, vm::dismissPartial) }
        }
        items(state.threads, key = { it.id }, contentType = { "thread" }) { thread ->
            ThreadRows(thread, actions, playing = thread.id == playingId)
        }
        item(key = "footer", contentType = "footer") {
            val more = state.moreProblem
            when {
                state.loadingMore -> LoadingRow()
                more != null -> ProblemRow(more, { vm.loadMore() })
                else -> Box(Modifier.padding(bottom = 48.dp))
            }
        }
    }
}

/** Some accounts could not be read; the rest are shown. */
@Composable
private fun PartialNotice(problem: Problem, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.feed_partial, problemText(problem)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, stringResource(Res.string.dismiss)) }
    }
}

/** A whole-screen state that can still be pulled to refresh. */
@Composable
private fun Whole(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
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
