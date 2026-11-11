package com.aimdi.xtanative.plugins.mastodon

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.mastodon.MastodonReplyOrder
import com.aimdi.xtanative.networks.mastodon.MastodonReplyRow
import com.aimdi.xtanative.networks.mastodon.MastodonThreadReader
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.common.scrollToTop
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.RetryRow
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside
import com.aimdi.xtanative.ui.post.PostIcons
import kotlinx.coroutines.launch

/**
 * An opened post and its public conversation (mastodon_thread_screen.dart):
 * the posts it answers on request, the post itself marked, and its replies as
 * a tree to fold, all of them or the author's own, oldest first, most recent
 * first or most liked first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MastodonThreadScreen(app: MastodonApp, target: MastodonTarget) {
    val reader = rememberKept("mastodon-thread:" + target.url) { scope ->
        MastodonThreadReader(app.client, { app.candidates(target.acct) }, app.seed(target), app.replyOrder, scope).also { it.refresh() }
    }
    val state by reader.state.collectAsState()
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reduced = LocalReducedMotion.current
    val openOutside = rememberOpenOutside()
    PluginScaffold(
        title = pluginString(Strings.thread),
        onBack = app.host::back,
        actions = {
            IconButton(onClick = {
                reader.focusSelected()
                scope.launch { list.scrollToTop(reduced) }
            }) { Icon(MastodonIcons.ToTop, pluginString(Strings.thread_selected)) }
            IconButton(onClick = { openOutside(state.thread.status.url) }) { Icon(PostIcons.OpenInNew, pluginString(Strings.open_in_browser)) }
            BranchesMenu(state.thread.descendants.isNotEmpty(), reader::setAllExpanded)
        },
    ) { padding ->
        val hasReplies = state.thread.descendants.isNotEmpty()
        PullToRefreshBox(isRefreshing = state.loading && hasReplies, onRefresh = reader::refresh, modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            LazyColumn(state = list, contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 32.dp), modifier = Modifier.fillMaxSize()) {
                threadRows(app, state, reader)
            }
        }
    }
}

private fun LazyListScope.threadRows(app: MastodonApp, state: MastodonThreadReader.State, reader: MastodonThreadReader) {
    val ancestors = state.ancestors
    if (ancestors.isNotEmpty()) item(key = "context") { AncestorsToggle(ancestors.last().acct, state.ancestorsOpen, reader::toggleAncestors) }
    if (state.ancestorsOpen) items(ancestors, key = { "above:" + it.url }) { MastodonPostRow(app, it) }
    item(key = "selected") { SelectedPost(app, state) }
    val hasReplies = state.thread.descendants.isNotEmpty()
    val error = state.error
    when {
        state.loading && !hasReplies -> item(key = "loading") { LoadingRow() }
        error != null -> item(key = "problem") { RetryRow(pluginString(problemText(error)), reader::refresh) }
    }
    if (hasReplies) item(key = "filters") { ThreadFilters(app, state, reader) }
    val replies = state.replies
    if (replies.isNotEmpty()) {
        item(key = "replies") { Text(pluginString(Strings.replies), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) }
    } else if (!state.loading && error == null) {
        item(key = "no-replies") { NoReplies(state.authorOnly) }
    }
    items(replies, key = { "reply:" + it.post.url }) { row -> ReplyRow(app, row, reader::toggle) }
}

/** The opened post, marked by a line at its start. */
@Composable
private fun SelectedPost(app: MastodonApp, state: MastodonThreadReader.State) {
    val mark = MaterialTheme.colorScheme.primary
    MastodonPostRow(app, state.thread.status, selected = true, modifier = Modifier.drawBehind { drawRect(mark, size = Size(3.dp.toPx(), size.height)) })
}

/** "Replying to @…": the posts above, shown or put away. */
@Composable
private fun AncestorsToggle(acct: String, open: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(PostIcons.SubdirectoryArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(pluginString(Strings.replying_to), style = MaterialTheme.typography.bodyLarge)
            Text("@$acct", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
    }
}

/** All replies or the author's thread, and their order. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThreadFilters(app: MastodonApp, state: MastodonThreadReader.State, reader: MastodonThreadReader) {
    FlowRow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
        FilterChip(selected = !state.authorOnly, onClick = { reader.selectAuthor(false) }, label = { Text(pluginString(Strings.thread_all)) })
        FilterChip(selected = state.authorOnly, onClick = { reader.selectAuthor(true) }, label = { Text(pluginString(Strings.thread_author)) })
        OrderChoice(MastodonReplyOrder.entries, state.order, ::replyOrderText) { order ->
            app.replyOrder = order
            reader.selectOrder(order)
        }
    }
}

private fun replyOrderText(order: MastodonReplyOrder): PluginText = when (order) {
    MastodonReplyOrder.OLDEST -> Strings.sort_oldest
    MastodonReplyOrder.RECENT -> Strings.sort_recent
    MastodonReplyOrder.MOST_LIKED -> Strings.sort_most_liked
}

/** A menu of orders, named by [label], the one [selected] shown on its button. */
@Composable
internal fun <T> OrderChoice(options: List<T>, selected: T, label: (T) -> PluginText, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Icon(MastodonIcons.Sort, contentDescription = pluginString(Strings.sort_order), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(pluginString(label(selected)))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(pluginString(label(option))) }, onClick = {
                    open = false
                    onSelect(option)
                })
            }
        }
    }
}

@Composable
private fun NoReplies(authorOnly: Boolean) {
    Text(
        pluginString(if (authorOnly) Strings.thread_no_author_replies else Strings.thread_no_replies),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(24.dp),
    )
}

/** A reply under the one it answers, two levels of indent at most, its own replies folded with Hide and back with Show. */
@Composable
private fun ReplyRow(app: MastodonApp, row: MastodonReplyRow, onToggle: (String) -> Unit) {
    val line = MaterialTheme.colorScheme.outlineVariant
    val nested = row.depth > 0
    Column(
        Modifier
            .padding(start = 12.dp * row.depth.coerceIn(0, 2))
            .let { if (nested) it.drawBehind { drawRect(line, size = Size(2.dp.toPx(), size.height)) } else it },
    ) {
        if (row.contextOnly) {
            Text(
                pluginString(Strings.thread_context),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
        }
        MastodonPostRow(app, row.post)
        if (row.descendants > 0) {
            TextButton(onClick = { onToggle(row.post.id) }, modifier = Modifier.padding(start = 8.dp)) {
                Icon(if (row.collapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(pluginString(if (row.collapsed) Strings.show else Strings.hide) + " · " + row.descendants)
            }
        }
    }
}

/** Every reply unfolded, or every reply folded. */
@Composable
private fun BranchesMenu(enabled: Boolean, onExpand: (Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, enabled = enabled) { Icon(Icons.Filled.MoreVert, pluginString(Strings.more)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(pluginString(Strings.thread_expand_all)) }, onClick = {
                open = false
                onExpand(true)
            })
            DropdownMenuItem(text = { Text(pluginString(Strings.thread_collapse_all)) }, onClick = {
                open = false
                onExpand(false)
            })
        }
    }
}
