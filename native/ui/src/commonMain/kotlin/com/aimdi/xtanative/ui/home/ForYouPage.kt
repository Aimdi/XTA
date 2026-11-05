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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.ui.common.FeedSkeleton
import com.aimdi.xtanative.ui.common.PostSkeleton
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.common.ProblemRow
import com.aimdi.xtanative.ui.common.ProblemView
import com.aimdi.xtanative.ui.common.WithReadingColumn
import com.aimdi.xtanative.ui.common.problemText
import com.aimdi.xtanative.ui.common.scrollToTop
import com.aimdi.xtanative.ui.feed.feedThreads
import com.aimdi.xtanative.ui.feed.rememberFeedLayout
import com.aimdi.xtanative.ui.feed.rememberNearEnd
import com.aimdi.xtanative.ui.feed.rememberPlayingId
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.dismiss
import com.aimdi.xtanative.ui.generated.resources.home_for_you_empty
import com.aimdi.xtanative.ui.group.ZenEndCard
import com.aimdi.xtanative.ui.media.LocalInlinePlayer
import com.aimdi.xtanative.ui.media.rememberInlinePlayer
import com.aimdi.xtanative.ui.plugins.ScrollsToTop
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.post.ThreadRows
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.stringResource

/** The key For you's view model is kept under on Home. */
internal const val FOR_YOU_VIEW_MODEL = "for-you"

/** For you's view model. */
@Composable
internal fun forYouViewModel(): ForYouViewModel {
    val graph = LocalGraph.current
    return viewModel(key = FOR_YOU_VIEW_MODEL) { ForYouViewModel(graph) }
}

/**
 * For you as one of Home's timelines: pulled to refresh, read on as it is
 * scrolled, opened at "You're caught up" when there is something new above
 * the posts read before. [toTop] grows when the reader asks for the top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ForYouPage(toTop: Int, actions: PostActions, contentPadding: PaddingValues) {
    val vm = forYouViewModel()
    val state by vm.ui.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    val reduced = LocalReducedMotion.current
    ScrollsToTop(toTop) { list.scrollToTop(reduced) }
    var answeredRefresh by rememberSaveable { mutableIntStateOf(state.toTop) }
    LaunchedEffect(state.toTop) {
        if (state.toTop > answeredRefresh) {
            answeredRefresh = state.toTop
            list.scrollToItem(0)
        }
    }
    LaunchedEffect(state.jump) {
        val index = state.jump ?: return@LaunchedEffect
        list.scrollToItem(index + if (state.notice != null) 1 else 0)
        vm.jumped()
    }
    LaunchedEffect(list) {
        snapshotFlow { list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset <= 8 }.distinctUntilChanged().collect { vm.atTop(it) }
    }
    val inline = rememberInlinePlayer()
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { inline?.pause() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { inline?.resume() }

    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize()) {
        val problem = state.problem
        when {
            // XTA's no-account screen, whose Add account signs in.
            state.noAccount -> PageMessage(null) { ProblemView(Problem.NoAccount, { vm.refresh() }) }
            state.threads.isEmpty() && problem != null -> PageMessage(null) { ProblemView(problem, { vm.refresh() }) }
            state.threads.isEmpty() && state.answered && !state.refreshing -> PageMessage(stringResource(Res.string.home_for_you_empty)) {}
            state.threads.isEmpty() -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = contentPadding.calculateTopPadding())) { FeedSkeleton() }
            else -> CompositionLocalProvider(LocalInlinePlayer provides inline) {
                ForYouList(state, vm, actions, list, contentPadding)
            }
        }
    }
}

@Composable
private fun ForYouList(state: ForYouUi, vm: ForYouViewModel, actions: PostActions, list: LazyListState, contentPadding: PaddingValues) {
    val nearEnd by rememberNearEnd(list)
    LaunchedEffect(nearEnd, state.threads.size, state.hasMore) {
        if (nearEnd) vm.loadMore()
    }
    // The first row with a video that is mostly on screen plays it, as in the group feeds.
    val playingId by rememberPlayingId(list, state.threads)
    val layout = rememberFeedLayout(state.threads)
    val divider = state.divider
    WithReadingColumn(contentPadding) { padding ->
        LazyColumn(state = list, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            state.notice?.let { notice ->
                item(key = "notice", contentType = "notice") { RefreshNotice(notice, vm::dismissNotice) }
            }
            feedThreads(state.threads, layout, actions, divider = divider) { thread -> ThreadRows(thread, actions, playing = thread.id == playingId) }
            item(key = "footer", contentType = "footer") {
                val more = state.moreProblem
                when {
                    state.loadingMore -> PostSkeleton()
                    more != null -> ProblemRow(more, { vm.loadMore() })
                    state.paused -> ZenEndCard(vm::readOn)
                    else -> Box(Modifier.padding(bottom = 48.dp))
                }
            }
        }
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
