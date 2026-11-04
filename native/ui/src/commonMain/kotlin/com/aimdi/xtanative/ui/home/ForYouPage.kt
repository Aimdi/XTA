package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.ProblemRow
import com.aimdi.xtanative.ui.common.ProblemView
import com.aimdi.xtanative.ui.common.problemText
import com.aimdi.xtanative.ui.feed.ToTop
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.dismiss
import com.aimdi.xtanative.ui.generated.resources.home_for_you_empty
import com.aimdi.xtanative.ui.generated.resources.home_for_you_no_account
import com.aimdi.xtanative.ui.generated.resources.home_import
import com.aimdi.xtanative.ui.generated.resources.home_zen_end
import com.aimdi.xtanative.ui.generated.resources.home_zen_more
import com.aimdi.xtanative.ui.media.LocalInlinePlayer
import com.aimdi.xtanative.ui.media.rememberInlinePlayer
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.post.ThreadRows
import com.aimdi.xtanative.ui.post.hasVideo
import org.jetbrains.compose.resources.stringResource

/** The key For you's view model is kept under on Home. */
internal const val FOR_YOU_VIEW_MODEL = "for-you"

/** For you as a page of the strip, [page] in the pager: pulled to refresh, read on as it is scrolled. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ForYouPage(page: Int, toTop: ToTop, actions: PostActions, contentPadding: PaddingValues, onImport: () -> Unit) {
    val graph = LocalGraph.current
    val vm: ForYouViewModel = viewModel(key = FOR_YOU_VIEW_MODEL) { ForYouViewModel(graph) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    // A page swiped back into view must not answer a request it already answered.
    var answeredTap by rememberSaveable { mutableIntStateOf(toTop.count) }
    LaunchedEffect(toTop) {
        if (toTop.page == page && toTop.count > answeredTap) {
            answeredTap = toTop.count
            list.animateScrollToItem(0)
        }
    }
    var answeredRefresh by rememberSaveable { mutableIntStateOf(state.toTop) }
    LaunchedEffect(state.toTop) {
        if (state.toTop > answeredRefresh) {
            answeredRefresh = state.toTop
            list.scrollToItem(0)
        }
    }
    val session by graph.sessions.state.collectAsStateWithLifecycle()
    val inline = rememberInlinePlayer()
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { inline?.pause() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { inline?.resume() }

    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize()) {
        val problem = state.problem
        when {
            state.noAccount -> PageMessage(stringResource(Res.string.home_for_you_no_account)) {
                Button(onClick = onImport) { Text(stringResource(Res.string.home_import)) }
            }
            state.threads.isEmpty() && problem != null -> PageMessage(null) { ProblemView(problem, { vm.refresh() }) }
            state.threads.isEmpty() && state.answered && !state.refreshing -> PageMessage(stringResource(Res.string.home_for_you_empty)) {}
            // The pull indicator already says a first load is under way.
            state.threads.isEmpty() -> PageMessage(null) { if (!state.refreshing) LoadingRow() }
            else -> CompositionLocalProvider(LocalInlinePlayer provides inline.takeIf { session?.autoplay ?: true }) {
                ForYouList(state, vm, actions, list, contentPadding)
            }
        }
    }
}

@Composable
private fun ForYouList(state: ForYouUi, vm: ForYouViewModel, actions: PostActions, list: LazyListState, contentPadding: PaddingValues) {
    val nearEnd by remember(list) {
        derivedStateOf {
            val info = list.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd, state.threads.size, state.hasMore) {
        if (nearEnd) vm.loadMore()
    }
    // The first row with a video that is mostly on screen plays it, as in the Following feed.
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
        state.notice?.let { notice ->
            item(key = "notice", contentType = "notice") { RefreshNotice(notice, vm::dismissNotice) }
        }
        items(state.threads, key = { it.id }, contentType = { "thread" }) { thread ->
            ThreadRows(thread, actions, playing = thread.id == playingId)
        }
        item(key = "footer", contentType = "footer") {
            val more = state.moreProblem
            when {
                state.loadingMore -> LoadingRow()
                more != null -> ProblemRow(more, { vm.loadMore() })
                state.paused -> ZenEnd(vm::readOn)
                else -> Box(Modifier.padding(bottom = 48.dp))
            }
        }
    }
}

/** Zen mode's end of the feed, with a way to read on anyway. */
@Composable
private fun ZenEnd(onReadOn: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(Res.string.home_zen_end), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onReadOn) { Text(stringResource(Res.string.home_zen_more)) }
    }
}

/** A refresh that failed over the posts shown. */
@Composable
private fun RefreshNotice(problem: Problem, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            problemText(problem),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, stringResource(Res.string.dismiss)) }
    }
}

/** A whole-page state that can still be pulled to refresh: [text], then [action]. */
@Composable
internal fun PageMessage(text: String?, action: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (text != null) {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
        action()
    }
}
