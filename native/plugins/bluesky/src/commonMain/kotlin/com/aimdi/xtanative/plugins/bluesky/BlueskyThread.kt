package com.aimdi.xtanative.plugins.bluesky

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.networks.bluesky.BlueskyPost
import com.aimdi.xtanative.networks.bluesky.BlueskyReplies
import com.aimdi.xtanative.networks.bluesky.BlueskyReplyRow
import com.aimdi.xtanative.networks.bluesky.BlueskyReplySort
import com.aimdi.xtanative.networks.bluesky.BlueskyThread
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.common.scrollToTop
import com.aimdi.xtanative.ui.plugins.NetworkPostActions
import com.aimdi.xtanative.ui.plugins.NetworkProblemRow
import com.aimdi.xtanative.ui.plugins.NetworkProblemView
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.problemOf
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside
import com.aimdi.xtanative.ui.post.PostIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * An opened post, as XTA's Bluesky thread shows it (bluesky_thread_screen.dart):
 * what it replies to folded under "Replying to", the post itself marked,
 * and its replies under it, each under the one it answers and each branch
 * folding; all of them or the author's own, in the order picked for the
 * session; a pull reads it all again. Its bar goes back to the post, to the
 * browser, and folds or unfolds every branch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BlueskyThreadScreen(app: BlueskyApp, target: PostTarget) {
    val thread = rememberKept("bluesky-thread:" + target.key) { scope -> ThreadState(app, target, scope) }
    val state by thread.state.collectAsState()
    val order by app.replySort.collectAsState()
    val rows = remember(state, order) { thread.rows(order) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reduced = LocalReducedMotion.current
    PluginScaffold(
        title = pluginString(Strings.thread),
        onBack = app.host::back,
        actions = {
            IconButton(onClick = {
                thread.closeContext()
                scope.launch { list.scrollToTop(reduced) }
            }) { Icon(PostIcons.ArrowUpward, pluginString(Strings.back_to_selected)) }
            val outside = rememberOpenOutside()
            state.thread?.post?.let { post -> IconButton(onClick = { outside(post.url) }) { Icon(PostIcons.OpenInNew, pluginString(Strings.open_in_browser)) } }
            BranchesMenu(enabled = rows.isNotEmpty() || state.collapsed.isNotEmpty()) { expand -> thread.setAllExpanded(expand, order) }
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.loading && state.thread != null, onRefresh = thread::load, modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            LazyColumn(state = list, contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 48.dp), modifier = Modifier.fillMaxSize()) {
                threadHead(app, thread, state)
                if (state.thread?.replies.orEmpty().isNotEmpty()) item(key = "controls") { Controls(app, state.authorOnly, order, thread::selectAuthor) }
                if (!state.loading && state.problem == null && state.thread != null && rows.isEmpty()) {
                    item(key = "no-replies") { EmptyReplies(if (state.authorOnly) Strings.no_author_replies else Strings.no_replies) }
                }
                items(rows, key = { "reply-" + it.branch.post.uri }, contentType = { "reply" }) { row -> ReplyRow(app, row, thread::toggle) }
            }
        }
    }
}

/** The context above the post, folded until asked for, the post itself, and why it is not there yet or was not read again. */
private fun LazyListScope.threadHead(app: BlueskyApp, thread: ThreadState, state: ThreadState.State) {
    val ancestors = state.thread?.ancestors.orEmpty()
    if (ancestors.isNotEmpty()) item(key = "context") { ContextToggle(ancestors.last().handle, state.contextOpen, thread::toggleContext) }
    if (state.contextOpen) items(ancestors, key = { "above-" + it.uri }, contentType = { "post" }) { post -> PostRow(app, post, continues = true) }
    val focal = state.thread?.post
    val problem = state.problem
    when {
        focal != null -> item(key = "focal") { SelectedPost(app, focal) }
        problem != null -> item(key = "problem") { NetworkProblemView(problem, app.network, onRetry = thread::load) }
        state.loading -> item(key = "loading") { LoadingRow() }
    }
    // A reload that failed leaves what was read on screen, saying so under the post.
    if (focal != null && problem != null) item(key = "reload-problem") { NetworkProblemRow(problem, app.network, onRetry = thread::load) }
}

/** "Replying to @…", folding open the posts the opened one answers. */
@Composable
private fun ContextToggle(parent: String, open: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(PostIcons.SubdirectoryArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(pluginString(Strings.replying_to), style = MaterialTheme.typography.bodyLarge)
            Text("@$parent", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
    }
}

/** The opened post, marked by a line along its start, opening nothing more itself (its quote still opens). */
@Composable
private fun SelectedPost(app: BlueskyApp, post: BlueskyPost) {
    val actions = remember(app, post) {
        NetworkPostActions(
            open = { opened -> if (opened.id != post.uri) app.actions.open(opened) },
            openAuthor = app.actions.openAuthor,
            openMention = app.actions.openMention,
            openTag = app.actions.openTag,
            openMedia = app.actions.openMedia,
        )
    }
    PostRow(app, post, modifier = Modifier.startLine(3.dp, MaterialTheme.colorScheme.primary), actions = actions)
}

/** A line [width] wide along the start of what it draws. */
private fun Modifier.startLine(width: Dp, color: Color): Modifier = drawBehind {
    val x = width.toPx() / 2
    drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = width.toPx())
}

/** All replies or the author's own, and their order. */
@Composable
private fun Controls(app: BlueskyApp, authorOnly: Boolean, order: BlueskyReplySort, onAuthor: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(selected = !authorOnly, onClick = { onAuthor(false) }, label = { Text(pluginString(Strings.all)) })
        FilterChip(selected = authorOnly, onClick = { onAuthor(true) }, label = { Text(pluginString(Strings.author_thread)) })
        SortMenu(order, BlueskyReplySort.entries, { it.label to it.icon }) { app.replySort.value = it }
    }
}

internal val BlueskyReplySort.label: PluginText
    get() = when (this) {
        BlueskyReplySort.RELEVANT -> Strings.sort_relevant
        BlueskyReplySort.RECENT -> Strings.sort_recent
        BlueskyReplySort.OLDEST -> Strings.sort_oldest
        BlueskyReplySort.MOST_LIKED -> Strings.sort_most_liked
    }

private val BlueskyReplySort.icon: ImageVector
    get() = when (this) {
        BlueskyReplySort.RELEVANT -> PostIcons.Sparkle
        BlueskyReplySort.RECENT -> PostIcons.Schedule
        BlueskyReplySort.OLDEST -> PostIcons.History
        BlueskyReplySort.MOST_LIKED -> XIcons.Like
    }

/** The order chosen, opening the others (sort_menu_button.dart's SortMenuButton). */
@Composable
internal fun <T> SortMenu(chosen: T, choices: List<T>, look: (T) -> Pair<PluginText, ImageVector>, onChoose: (T) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        val (label, icon) = look(chosen)
        TextButton(onClick = { open = true }) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(pluginString(label), maxLines = 1)
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = pluginString(Strings.sort_order), modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { choice ->
                val (text, glyph) = look(choice)
                DropdownMenuItem(
                    text = { Text(pluginString(text)) },
                    leadingIcon = { Icon(glyph, contentDescription = null) },
                    onClick = {
                        open = false
                        if (choice != chosen) onChoose(choice)
                    },
                )
            }
        }
    }
}

@Composable
private fun BranchesMenu(enabled: Boolean, onExpand: (Boolean) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, enabled = enabled) { Icon(Icons.Filled.MoreVert, pluginString(Strings.more_options)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(pluginString(Strings.expand_all)) }, onClick = {
                open = false
                onExpand(true)
            })
            DropdownMenuItem(text = { Text(pluginString(Strings.collapse_all)) }, onClick = {
                open = false
                onExpand(false)
            })
        }
    }
}

@Composable
private fun EmptyReplies(text: PluginText) {
    Text(
        pluginString(text),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(24.dp),
    )
}

/** A reply, moved in under the one it answers, with a line along its start; "Reply context" over one kept only to lead to the author's. */
@Composable
private fun ReplyRow(app: BlueskyApp, row: BlueskyReplyRow, onToggle: (String) -> Unit) {
    val branch = row.branch
    val line = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxWidth().padding(start = 8.dp + 12.dp * row.depth.coerceIn(0, 2)).startLine(2.dp, line)) {
        if (branch.contextOnly) {
            Text(
                pluginString(Strings.reply_context),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
        }
        PostRow(app, branch.post)
        if (branch.children.isNotEmpty()) {
            TextButton(onClick = { onToggle(branch.post.uri) }, modifier = Modifier.padding(start = 8.dp)) {
                Icon(if (row.collapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(pluginString(Strings.branch_toggle, pluginString(if (row.collapsed) Strings.show else Strings.hide), branch.descendants))
            }
        }
    }
}

/**
 * One opened post and its conversation (bluesky_thread_store.dart): read
 * once the screen opens, [load] reads it again; what is folded, whether the
 * context above is open and whether only the author's replies are shown are
 * this screen's.
 */
internal class ThreadState(private val app: BlueskyApp, private val target: PostTarget, private val scope: CoroutineScope) {
    data class State(
        val thread: BlueskyThread? = null,
        val collapsed: Set<String> = emptySet(),
        val contextOpen: Boolean = false,
        val authorOnly: Boolean = false,
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
                val known = thread.replies.mapTo(HashSet()) { it.uri }
                mutable.update { it.copy(thread = thread, collapsed = it.collapsed intersect known, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(loading = false, problem = problemOf(e)) }
            }
        }
    }

    /** The replies a list draws, in [order], depth first, without what sits under a folded one. */
    fun rows(order: BlueskyReplySort): List<BlueskyReplyRow> {
        val now = mutable.value
        val thread = now.thread ?: return emptyList()
        return BlueskyReplies.visible(BlueskyReplies.branches(thread, now.authorOnly, order), now.collapsed)
    }

    fun toggle(uri: String) = mutable.update { it.copy(collapsed = if (uri in it.collapsed) it.collapsed - uri else it.collapsed + uri) }

    fun toggleContext() = mutable.update { it.copy(contextOpen = !it.contextOpen) }

    fun closeContext() = mutable.update { it.copy(contextOpen = false) }

    fun selectAuthor(only: Boolean) = mutable.update { if (it.authorOnly == only) it else it.copy(authorOnly = only, collapsed = emptySet()) }

    /** Every branch open, or every one with replies folded. */
    fun setAllExpanded(expanded: Boolean, order: BlueskyReplySort) {
        val now = mutable.value
        val thread = now.thread ?: return
        val folded = if (expanded) emptySet() else BlueskyReplies.foldable(BlueskyReplies.branches(thread, now.authorOnly, order))
        mutable.update { it.copy(collapsed = folded) }
    }
}
