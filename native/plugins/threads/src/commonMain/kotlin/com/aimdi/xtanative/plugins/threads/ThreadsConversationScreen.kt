package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.threads.ThreadsConversation
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsReplyRow
import com.aimdi.xtanative.networks.threads.ThreadsReplyOrder
import com.aimdi.xtanative.networks.threads.ThreadsThreadView
import com.aimdi.xtanative.networks.threads.attempt
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.plugins.Fetch
import com.aimdi.xtanative.ui.plugins.Fetched
import com.aimdi.xtanative.ui.plugins.NoticeHost
import com.aimdi.xtanative.ui.plugins.PluginFeedSkeleton
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.RetryMessage
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.networks.threads.ThreadsPaging
import com.aimdi.xtanative.networks.threads.ThreadsPagingState
import com.aimdi.xtanative.networks.threads.ThreadsReplyThread
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside
import com.aimdi.xtanative.ui.plugins.rememberShareText
import com.aimdi.xtanative.ui.post.PostIcons
import com.aimdi.xtanative.ui.speech.ReadAloudButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * One conversation as it has been read, and what it showed when it was
 * last read, kept on the device, until then; each read is kept for the next
 * time it opens.
 */
internal class ThreadsConversationModel(app: ThreadsApp, url: String, seed: ThreadsPost, scope: CoroutineScope) {
    val kept = MutableStateFlow<ThreadsConversation?>(null)
    val fetch = Fetch(scope, ThreadsConversation.seed(seed)) { force ->
        app.ready()
        app.reader.conversation(seed, force).also { if (it.loaded) app.keepConversation(url, it) }
    }

    init {
        scope.launch { kept.value = app.keptConversation(url)?.value }
    }
}

/**
 * One post in its conversation (threads_thread_screen.dart): what it
 * answers, the post, the rest of its author's thread, then each reply
 * thread, read from the post's public page. The tapped card is on screen at
 * once; with posts above it, the list opens on it, what it answers a scroll up.
 * A conversation opened before shows at once what it showed then, quietly
 * "updating" while its page is read again. Where the author chained several
 * posts, the bar offers them read as one article; replies of accounts the
 * reader muted, and posts they hid, are left out.
 */
@Composable
internal fun ThreadsConversationScreen(app: ThreadsApp, route: ThreadsRoute.Post) {
    val seed = remember(route) { app.shelf.post(route.card) ?: route.stub() }
    val model = rememberKept("threads-post-${route.url}") { scope -> ThreadsConversationModel(app, route.url, seed, scope) }
    val fetch = model.fetch
    LaunchedEffect(fetch) { fetch.ensure() }
    val state by fetch.state.collectAsState()
    val kept by model.kept.collectAsState()
    val more = rememberKept("threads-post-more-${route.url}") { scope ->
        ThreadsPaging<ThreadsReplyThread>(scope, { it.posts.first().id }) { cursor ->
            val focus = fetch.state.value.value?.focus ?: seed
            app.nextPage { app.direct.moreReplyThreads(focus.url ?: route.url, focus.id, cursor) }
        }
    }
    val paged by more.state.collectAsState()
    LaunchedEffect(state.value) {
        state.value?.takeIf { it.loaded }?.let { read -> more.start(read.replies.map(::ThreadsReplyThread), read.repliesNext) }
    }
    val shown = ThreadsConversationView.shown(state.value, kept, seed)
    val options by app.options.collectAsState()
    val conversation = remember(shown, paged.items, options) { ThreadsConversationView.quieted(shown.withMore(paged.items), options) }
    val article = remember(conversation) { ThreadsArticles.of(conversation) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val reading = ThreadsArticles.reading(asked, article)
    PluginScaffold(
        title = pluginString(Strings.thread),
        onBack = app.host::back,
        actions = {
            if (article != null) ArticleSwitch(reading) { asked = !reading }
            if (conversation.loaded) {
                ReadAloudButton("${pluginString(Strings.thread)} · @${conversation.focus.handle}", text = { if (reading) article?.spokenText().orEmpty() else conversation.spokenText() })
            }
            conversation.focus.url?.let { url -> ThreadActions(app, url) }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (reading && article != null) {
                ThreadsArticleView(app, article)
            } else {
                ConversationList(app, conversation, state, ThreadsMoreReplies(more, paged), onRetry = { fetch.load(force = true) })
            }
            NoticeHost(app.notices)
        }
    }
}

/** The bar's switch between an author's thread read as one article ([ThreadsArticleView]) and the conversation with its replies. */
@Composable
private fun ArticleSwitch(reading: Boolean, onSwitch: () -> Unit) {
    IconButton(onClick = onSwitch) {
        if (reading) {
            Icon(PostIcons.Forum, contentDescription = pluginString(Strings.show_conversation))
        } else {
            Icon(PostIcons.Article, contentDescription = pluginString(Strings.read_as_article))
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

/** The conversation's list; a picture opened in it is viewed among all the pictures shown in it. */
@Composable
private fun ConversationList(app: ThreadsApp, read: ThreadsConversation, state: Fetched<ThreadsConversation>, more: ThreadsMoreReplies, onRetry: () -> Unit) {
    val order by app.replyOrder.collectAsState()
    var authorOnly by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(emptyList<String>()) }
    // The replies read in place under an unfinished thread, by the thread: only once Threads' own read of them is known.
    var deeper by remember(read.focus.id) { mutableStateOf(emptyMap<String, ThreadsReplyThread>()) }
    val conversation = remember(read, deeper) { read.withDeeper(deeper) }
    val scope = rememberCoroutineScope()
    val view = ThreadsThreadView(authorOnly, expanded.toSet(), order)
    val rows = remember(conversation, view) { conversation.replyRows(view) }
    val gallery = remember(conversation, rows) { conversation.ancestors + conversation.focus + conversation.continuation + rows.mapNotNull { (it as? ThreadsReplyRow.Post)?.post } }
    val list = rememberLazyListState()
    LandsOnFocus(list, conversation)
    ReadsMoreNear(list, more.paging, more.state)
    CompositionLocalProvider(LocalThreadsGallery provides gallery) {
        RefreshableConversation(
            app, conversation, state, more, list, view, rows, onRetry,
            onAuthorOnly = { authorOnly = !authorOnly },
            onExpand = { chain -> expanded = expanded + chain.first().id },
            onCollapse = { chain -> expanded = expanded - chain.first().id },
            onContinue = { row ->
                val last = row.continuesAt
                val under = row.chain.first().id
                when {
                    last == null -> Unit
                    !app.direct.readsDeeperReplies -> app.openPost(last)
                    else -> scope.launch {
                        val brought = attempt { app.nextPage { app.direct.deeperReplies(last) } }
                        val all = brought?.let { deeper[under]?.then(it) ?: it }
                        if (all != null && read.withDeeper(deeper).brings(all)) {
                            deeper = deeper + (under to all)
                            expanded = expanded + under
                        } else {
                            app.openPost(last)
                        }
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefreshableConversation(
    app: ThreadsApp,
    conversation: ThreadsConversation,
    state: Fetched<ThreadsConversation>,
    more: ThreadsMoreReplies,
    list: LazyListState,
    view: ThreadsThreadView,
    rows: List<ThreadsReplyRow>,
    onRetry: () -> Unit,
    onAuthorOnly: () -> Unit,
    onExpand: (List<ThreadsPost>) -> Unit,
    onCollapse: (List<ThreadsPost>) -> Unit,
    onContinue: (ThreadsReplyRow.OnWeb) -> Unit,
) {
    // Pulling shows its own spinner; what was kept shown while the page is read first says "updating" instead.
    val fresh = state.value?.loaded == true
    PullToRefreshBox(isRefreshing = state.loading && fresh, onRefresh = onRetry, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = list, contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
            threadRows(app, conversation)
            statusRows(conversation, state, onRetry)
            if (conversation.replies.isNotEmpty()) {
                item(key = "replies-header") { RepliesHeader(app, conversation, view, onAuthorOnly) }
                replyRows(app, rows, onExpand, onCollapse, byAuthor = conversation::byAuthor, onContinue = onContinue)
            }
            moreFoot("replies", more.state) { more.paging.retry() }
            if (conversation.moreReplies && (more.state.onWeb || !more.state.paged)) moreRepliesOnWeb(conversation.focus.url)
            relatedRows(app, conversation.related)
        }
        if (conversation.replies.isNotEmpty()) {
            ReloadStatus(state.loading, state.problem?.let { ThreadsFailures.post(it, currentLanguage()) }, onRetry, updating = !fresh)
        }
    }
}

/** The reply threads after the page's own, and where reading them stands. */
private class ThreadsMoreReplies(val paging: ThreadsPaging<ThreadsReplyThread>, val state: ThreadsPagingState<ThreadsReplyThread>)

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
        item(key = "focus-loading") { PluginFeedSkeleton(count = 1) }
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

/** While there are no replies: reading, why the page could not be read, or that it has none, with the post on the web. With replies, [ReloadStatus] says it. */
private fun LazyListScope.statusRows(conversation: ThreadsConversation, state: Fetched<ThreadsConversation>, onRetry: () -> Unit) {
    val problem = state.problem
    when {
        conversation.replies.isNotEmpty() -> Unit
        state.loading -> item(key = "loading") { LoadingRow() }
        problem != null -> item(key = "problem") { RetryMessage(ThreadsFailures.post(problem, currentLanguage()), onRetry) }
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

/** Which conversation the screen shows while its page is read. */
internal object ThreadsConversationView {
    /** The page as read; until then what was kept of it last time; else the tapped card (or the link's stub) alone. */
    fun shown(read: ThreadsConversation?, kept: ThreadsConversation?, seed: ThreadsPost): ThreadsConversation = when {
        read?.loaded == true -> read
        kept != null -> kept
        else -> read ?: ThreadsConversation.seed(seed)
    }

    /**
     * [conversation] without what the reader muted or hid on this device
     * ([ThreadsFeedOptions.replies]): among its replies and the posts Threads
     * suggests beside it. The post that was opened, what it answers and its
     * author's own thread stay whole: the reader asked for them.
     */
    fun quieted(conversation: ThreadsConversation, options: ThreadsFeedOptions): ThreadsConversation =
        if (!options.hidesSome) conversation else conversation.copy(replies = options.replies(conversation.replies), related = options.unhidden(conversation.related))
}
