package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.threads.ThreadsConversation
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsReplyOrder
import com.aimdi.xtanative.networks.threads.ThreadsReplyRow
import com.aimdi.xtanative.networks.threads.ThreadsThreadView
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.plugins.Fetch
import com.aimdi.xtanative.ui.plugins.Fetched
import com.aimdi.xtanative.ui.plugins.NoticeHost
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.RetryMessage
import com.aimdi.xtanative.ui.plugins.RetryRow
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginPlural
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside
import com.aimdi.xtanative.ui.plugins.rememberShareText
import com.aimdi.xtanative.ui.post.PostIcons

/**
 * One post in its conversation (threads_thread_screen.dart): what it
 * answers, the post, the rest of its author's thread, then each reply
 * thread, read from the post's public page. The tapped card is on screen at
 * once; with posts above it, the list opens on it, what it answers a scroll up.
 */
@Composable
internal fun ThreadsConversationScreen(app: ThreadsApp, route: ThreadsRoute.Post) {
    val seed = remember(route) { app.shelf.post(route.card) ?: route.stub() }
    val fetch = rememberKept("threads-post-${route.url}") { scope ->
        Fetch(scope, ThreadsConversation.seed(seed)) { force -> app.ready(); app.reader.conversation(seed, force) }
    }
    LaunchedEffect(fetch) { fetch.ensure() }
    val state by fetch.state.collectAsState()
    val conversation = state.value ?: ThreadsConversation.seed(seed)
    PluginScaffold(
        title = pluginString(Strings.thread),
        onBack = app.host::back,
        actions = { conversation.focus.url?.let { url -> ThreadActions(app, url) } },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            ConversationList(app, conversation, state, onRetry = { fetch.load(force = true) })
            NoticeHost(app.notices)
        }
    }
}

@Composable
private fun ThreadActions(app: ThreadsApp, url: String) {
    val share = rememberShareText()
    val outside = rememberOpenOutside()
    val copied = Strings.link_copied.get(currentLanguage())
    IconButton(onClick = { if (!share(url)) app.notices.tell(copied) }) { Icon(Icons.Filled.Share, contentDescription = pluginString(Strings.share_link)) }
    IconButton(onClick = { outside(url) }) { Icon(PostIcons.OpenInNew, contentDescription = pluginString(Strings.open_in_browser)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationList(app: ThreadsApp, conversation: ThreadsConversation, state: Fetched<ThreadsConversation>, onRetry: () -> Unit) {
    val order by app.replyOrder.collectAsState()
    var authorOnly by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val view = ThreadsThreadView(authorOnly, expanded.toSet(), order)
    val list = rememberLazyListState()
    LandsOnFocus(list, conversation)
    PullToRefreshBox(isRefreshing = state.loading && conversation.loaded, onRefresh = onRetry, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = list, contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
            threadRows(app, conversation)
            statusRows(conversation, state, onRetry)
            if (conversation.replies.isNotEmpty()) {
                item(key = "replies-header") { RepliesHeader(app, conversation, view, onAuthorOnly = { authorOnly = !authorOnly }) }
                replyRows(app, conversation.replyRows(view)) { chain -> expanded = expanded + chain.first().id }
            }
        }
    }
}

/** With posts above it, the opened post is brought to the top once, as X opens a reply. */
@Composable
private fun LandsOnFocus(list: LazyListState, conversation: ThreadsConversation) {
    var landed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(conversation.ancestors.size) {
        if (!landed && conversation.ancestors.isNotEmpty()) {
            landed = true
            list.scrollToItem(conversation.ancestors.size)
        }
    }
}

/** The posts it answers, the post, and its author's continuation, joined by rails. */
private fun LazyListScope.threadRows(app: ThreadsApp, conversation: ThreadsConversation) {
    if (conversation.focusIsStub && !conversation.loaded) {
        item(key = "focus-loading") { LoadingRow() }
        return
    }
    val chain = conversation.ancestors + conversation.focus + conversation.continuation
    val focusAt = conversation.ancestors.size
    itemsIndexed(chain, key = { index, post -> "chain:$index:${post.id}" }, contentType = { _, _ -> "post" }) { index, post ->
        ThreadsPostCard(
            app,
            post,
            openOnTap = index != focusAt,
            focal = index == focusAt,
            connectTop = index > 0,
            connectBottom = index < chain.size - 1,
        )
    }
}

/** While there are no replies: reading, why the page could not be read, or that it has none, with the post on the web. */
private fun LazyListScope.statusRows(conversation: ThreadsConversation, state: Fetched<ThreadsConversation>, onRetry: () -> Unit) {
    val problem = state.problem
    when {
        conversation.replies.isNotEmpty() -> {
            if (state.loading) item(key = "reloading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (problem != null) item(key = "reload-problem") { RetryRow(ThreadsFailures.page(problem, currentLanguage()), onRetry) }
        }
        state.loading -> item(key = "loading") { LoadingRow() }
        problem != null -> item(key = "problem") { RetryMessage(ThreadsFailures.page(problem, currentLanguage()), onRetry) }
        conversation.loaded -> item(key = "no-replies") { NoReplies(conversation.focus.url) }
    }
}

@Composable
private fun NoReplies(url: String?) {
    val outside = rememberOpenOutside()
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        NoteText(pluginString(Strings.replies_empty))
        if (url != null) {
            TextButton(onClick = { outside(url) }) {
                Icon(PostIcons.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(pluginString(Strings.open_in_browser), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** "Replies", the author's own replies alone when there are any, and the order, on one line that wraps on a narrow phone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepliesHeader(app: ThreadsApp, conversation: ThreadsConversation, view: ThreadsThreadView, onAuthorOnly: () -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            pluginString(Strings.replies),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        if (conversation.authorReplies.isNotEmpty()) {
            FilterChip(selected = view.authorOnly, onClick = onAuthorOnly, label = { Text(pluginString(Strings.author_replies)) })
        }
        OrderMenu(view.order) { app.replyOrder.value = it }
    }
}

/** The orders a Threads page's replies offer: Meta's own (Relevant), Recent and Oldest. */
@Composable
private fun OrderMenu(order: ThreadsReplyOrder, onOrder: (ThreadsReplyOrder) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val color = MaterialTheme.colorScheme.primary
    Box {
        TextButton(onClick = { open = true }, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(orderIcon(order), contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Text(pluginString(orderLabel(order)), color = color, modifier = Modifier.padding(start = 6.dp))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = pluginString(Strings.sort_order), tint = color)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ThreadsReplyOrder.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(pluginString(orderLabel(option))) },
                    leadingIcon = { Icon(orderIcon(option), contentDescription = null) },
                    trailingIcon = if (option == order) { { Icon(Icons.Filled.Check, contentDescription = null) } } else null,
                    onClick = {
                        open = false
                        if (option != order) onOrder(option)
                    },
                )
            }
        }
    }
}

private fun orderLabel(order: ThreadsReplyOrder): PluginText = when (order) {
    ThreadsReplyOrder.TOP -> Strings.sort_relevant
    ThreadsReplyOrder.NEWEST -> Strings.recent
    ThreadsReplyOrder.OLDEST -> Strings.sort_oldest
}

private fun orderIcon(order: ThreadsReplyOrder): ImageVector = when (order) {
    ThreadsReplyOrder.TOP -> PostIcons.Sparkle
    ThreadsReplyOrder.NEWEST -> PostIcons.Schedule
    ThreadsReplyOrder.OLDEST -> PostIcons.History
}

/** Each reply thread, its posts joined by rails, a long one cut to its first few until "Show N more replies". */
private fun LazyListScope.replyRows(app: ThreadsApp, rows: List<ThreadsReplyRow>, onExpand: (List<ThreadsPost>) -> Unit) {
    itemsIndexed(rows, key = { index, row -> "reply:$index:" + keyOf(row) }, contentType = { _, row -> if (row is ThreadsReplyRow.Post) "post" else "more" }) { _, row ->
        when (row) {
            is ThreadsReplyRow.Post -> ThreadsPostCard(app, row.post, connectTop = row.connectTop, connectBottom = row.connectBottom)
            is ThreadsReplyRow.More -> MoreReplies(row.hidden) { onExpand(row.chain) }
        }
    }
}

private fun keyOf(row: ThreadsReplyRow): String = when (row) {
    is ThreadsReplyRow.Post -> row.post.id
    is ThreadsReplyRow.More -> "more:" + row.chain.first().id
}

@Composable
private fun MoreReplies(hidden: Int, onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(start = 76.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(PostIcons.SubdirectoryArrow, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(pluginPlural(Strings.more_replies, hidden, hidden), style = MaterialTheme.typography.labelLarge, color = color, modifier = Modifier.padding(start = 8.dp))
    }
}
