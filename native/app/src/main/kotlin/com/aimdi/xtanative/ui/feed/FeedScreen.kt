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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.R
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.ProblemRow
import com.aimdi.xtanative.ui.common.ProblemView
import com.aimdi.xtanative.ui.common.problemText
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.post.ThreadRows
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(actions: PostActions, openSearch: () -> Unit, openSettings: () -> Unit) {
    val graph = LocalGraph.current
    val vm: FeedViewModel = viewModel { FeedViewModel(graph) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val bar = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.following),
                        modifier = Modifier.clickable { scope.launch { list.animateScrollToItem(0) } },
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
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = vm::refresh,
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
        ) {
            val problem = state.problem
            when {
                state.threads.isEmpty() && problem != null -> Whole { ProblemView(problem, vm::refresh) }
                state.threads.isEmpty() && state.followed == 0 -> Whole { Message(stringResource(R.string.feed_nobody_followed)) }
                state.threads.isEmpty() && state.answered && !state.refreshing -> Whole { Message(stringResource(R.string.feed_empty)) }
                // The pull indicator already says a first load is under way.
                state.threads.isEmpty() -> Whole { if (!state.refreshing) LoadingRow() }
                else -> FeedList(state, vm, actions, list, PaddingValues(bottom = padding.calculateBottomPadding()))
            }
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
