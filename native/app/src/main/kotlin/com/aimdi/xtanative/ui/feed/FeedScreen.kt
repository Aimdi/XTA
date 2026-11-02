package com.aimdi.xtanative.ui.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.R
import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.ProblemRow
import com.aimdi.xtanative.ui.common.ProblemView
import com.aimdi.xtanative.ui.common.problemText
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.post.ThreadRows
import kotlinx.coroutines.launch

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
    val following = stringResource(R.string.following)

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
                        IconButton(onClick = openSearch) { Icon(Icons.Filled.Search, stringResource(R.string.search)) }
                        IconButton(onClick = openSettings) { Icon(Icons.Filled.Settings, stringResource(R.string.settings)) }
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
@Composable
private fun FeedPage(groupId: String?, page: Int, toTop: ToTop, actions: PostActions, contentPadding: PaddingValues) {
    val graph = LocalGraph.current
    val vm: FeedViewModel = viewModel(key = "feed-" + (groupId ?: FOLLOWING)) { FeedViewModel(graph, groupId) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    // A page swiped back into view must not answer a tap it already answered.
    var answeredTap by rememberSaveable { mutableIntStateOf(toTop.count) }
    LaunchedEffect(toTop) {
        if (toTop.page == page && toTop.count > answeredTap) {
            answeredTap = toTop.count
            list.animateScrollToItem(0)
        }
    }
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        val problem = state.problem
        when {
            state.threads.isEmpty() && problem != null -> Whole { ProblemView(problem, vm::refresh) }
            state.threads.isEmpty() && state.followed == 0 -> Whole { Message(stringResource(R.string.feed_nobody_followed)) }
            state.threads.isEmpty() && state.answered && !state.refreshing -> Whole { Message(stringResource(R.string.feed_empty)) }
            // The pull indicator already says a first load is under way.
            state.threads.isEmpty() -> Whole { if (!state.refreshing) LoadingRow() }
            else -> FeedList(state, vm, actions, list, contentPadding)
        }
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
    LazyColumn(state = list, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        state.partial?.let { partial ->
            item(key = "partial", contentType = "notice") { PartialNotice(partial, vm::dismissPartial) }
        }
        items(state.threads, key = { it.id }, contentType = { "thread" }) { thread ->
            ThreadRows(thread, actions)
        }
        item(key = "footer", contentType = "footer") {
            val more = state.moreProblem
            when {
                state.loadingMore -> LoadingRow()
                more != null -> ProblemRow(more, vm::loadMore)
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
            stringResource(R.string.feed_partial, problemText(problem)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, stringResource(R.string.dismiss)) }
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
