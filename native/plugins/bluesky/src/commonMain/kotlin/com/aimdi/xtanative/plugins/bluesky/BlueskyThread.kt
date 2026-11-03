package com.aimdi.xtanative.plugins.bluesky

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.networks.bluesky.BlueskyReplies
import com.aimdi.xtanative.networks.bluesky.BlueskyReplyRow
import com.aimdi.xtanative.networks.bluesky.BlueskyThread
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.plugins.MessageText
import com.aimdi.xtanative.ui.plugins.NetworkFocalPost
import com.aimdi.xtanative.ui.plugins.NetworkProblemView
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.problemOf
import com.aimdi.xtanative.ui.plugins.rememberKept
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * An opened post, as bsky.app shows its thread: the posts it answers, root
 * first, the post itself larger, and the replies under it, each under the one
 * it answers.
 */
@Composable
internal fun BlueskyThreadScreen(app: BlueskyApp, target: PostTarget) {
    val thread = rememberKept("bluesky-thread:" + target.key) { scope -> ThreadState(app, target, scope) }
    val state by thread.state.collectAsState()
    val language = currentLanguage()
    val list = rememberLazyListState()
    val ancestors = state.thread?.ancestors.orEmpty()
    // Opened, the list starts at what the post answers: move to the post itself once.
    LaunchedEffect(ancestors.size) {
        if (ancestors.isNotEmpty() && list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0) list.scrollToItem(ancestors.size)
    }
    PluginScaffold(title = pluginString(Strings.post_title), onBack = app.host::back) { padding ->
        LazyColumn(
            state = list,
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 48.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(ancestors, key = { "above-" + it.uri }, contentType = { "post" }) { post -> PostRow(app, post, continues = true) }
            val focal = state.thread?.post
            val problem = state.problem
            when {
                focal != null && focal.isUnavailable -> item(key = "unavailable") { MessageText(pluginString(Strings.post_unavailable)) }
                focal != null -> item(key = "focal") {
                    val shown = remember(focal, language) { focal.toNetworkPost(language) }
                    NetworkFocalPost(shown, app.actions)
                }
                problem != null -> item(key = "problem") { NetworkProblemView(problem, app.network, onRetry = thread::load) }
                state.loading -> item(key = "loading") { LoadingRow() }
            }
            items(state.replies, key = { "reply-" + it.branch.post.uri }, contentType = { "post" }) { row ->
                PostRow(app, row.branch.post, indent = row.depth)
            }
        }
    }
}

/** One opened post and its conversation, read once the screen opens; [load] reads it again. */
internal class ThreadState(private val app: BlueskyApp, private val target: PostTarget, private val scope: CoroutineScope) {
    data class State(
        val thread: BlueskyThread? = null,
        /** The replies a list draws, depth first, those that may not be shown left out. */
        val replies: List<BlueskyReplyRow> = emptyList(),
        val loading: Boolean = true,
        val problem: Problem? = null,
    )

    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable.asStateFlow()

    init {
        load()
    }

    fun load() {
        scope.launch {
            mutable.update { it.copy(loading = true, problem = null) }
            try {
                val thread = app.client.postThread(target.uri(app.client))
                val rows = BlueskyReplies.visible(BlueskyReplies.branches(thread), collapsed = emptySet()).filterNot { it.branch.post.isUnavailable }
                mutable.value = State(thread.copy(ancestors = thread.ancestors.filterNot { it.isUnavailable }), rows, loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(loading = false, problem = problemOf(e)) }
            }
        }
    }
}
