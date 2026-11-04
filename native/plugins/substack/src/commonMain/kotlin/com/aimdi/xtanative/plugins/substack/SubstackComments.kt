package com.aimdi.xtanative.plugins.substack

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.substack.SubstackComment
import com.aimdi.xtanative.networks.substack.SubstackCommentOrder
import com.aimdi.xtanative.networks.substack.SubstackCommentRow
import com.aimdi.xtanative.networks.substack.SubstackComments
import com.aimdi.xtanative.networks.substack.SubstackLinks
import com.aimdi.xtanative.networks.substack.SubstackPost
import com.aimdi.xtanative.networks.substack.SubstackPostLink
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.agoText
import com.aimdi.xtanative.ui.plugins.LinkedText
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.TextRun
import com.aimdi.xtanative.ui.plugins.pluginPlural
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A post's discussion as XTA's SubstackCommentsStore holds it: read once, then searched, sorted and folded on the device. */
internal class CommentsModel(private val app: SubstackApp, val post: SubstackPost, private val scope: CoroutineScope) {
    data class State(
        val comments: List<SubstackComment> = emptyList(),
        val collapsed: Set<String> = emptySet(),
        val order: SubstackCommentOrder = SubstackCommentOrder.ORIGINAL,
        val query: String = "",
        val loading: Boolean = false,
        val error: Throwable? = null,
    ) {
        val rows: List<SubstackCommentRow> get() = SubstackComments.rows(comments, collapsed, order, query)
    }

    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable.asStateFlow()
    private var job: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        job?.cancel()
        mutable.update { it.copy(loading = true, error = null) }
        job = scope.launch {
            try {
                val comments = app.client.commentsOf(post)
                val ids = comments.mapTo(HashSet()) { it.id }
                mutable.update { it.copy(comments = comments, collapsed = it.collapsed.filterTo(HashSet()) { id -> id in ids }, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(loading = false, error = e) }
            }
        }
    }

    fun search(query: String) = mutable.update { it.copy(query = query, collapsed = emptySet()) }

    fun sort(order: SubstackCommentOrder) = mutable.update { it.copy(order = order) }

    fun toggle(id: String) = mutable.update { it.copy(collapsed = if (id in it.collapsed) it.collapsed - id else it.collapsed + id) }

    /** Every branch opened, or every one with replies folded. */
    fun expandAll(expanded: Boolean) = mutable.update { state ->
        state.copy(collapsed = if (expanded) emptySet() else SubstackComments.rows(state.comments, emptySet(), state.order, state.query).filter { it.descendants > 0 }.mapTo(HashSet()) { it.comment.id })
    }
}

/** A post's discussion, as XTA's SubstackCommentsScreen: searched, sorted and folded here; written comments open on the site. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubstackCommentsScreen(app: SubstackApp, link: SubstackPostLink, key: String?) {
    val model = rememberKept("substack-comments-${link.publicationBase}/${link.slug}") { scope -> CommentsModel(app, app.postFor(link, key), scope) }
    val state by model.state.collectAsState()
    val outside = rememberOpenOutside()
    val url = SubstackLinks.webUrl(model.post.canonicalUrl) ?: SubstackLinks.webUrl("${model.post.publicationBaseUrl}/p/${model.post.slug}")
    PluginScaffold(
        title = pluginString(Strings.comments),
        onBack = app.host::back,
        actions = {
            IconButton(onClick = { url?.let(outside) }, enabled = url != null) { Icon(SubstackIcons.OpenInNew, pluginString(Strings.open_in_browser)) }
            BranchesMenu(enabled = state.comments.isNotEmpty(), model::expandAll)
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.loading && state.comments.isNotEmpty(), onRefresh = model::refresh, modifier = Modifier.fillMaxSize().padding(padding)) {
            Discussion(model, state)
        }
    }
}

@Composable
private fun Discussion(model: CommentsModel, state: CommentsModel.State) {
    val rows = state.rows
    val error = state.error
    SelectionContainer {
        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "title") { Text(model.post.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) }
            if (state.comments.isNotEmpty()) item(key = "controls") { CommentControls(model, state) }
            when {
                state.loading && state.comments.isEmpty() -> item(key = "loading") { LoadingRow() }
                error != null -> item(key = "error") { FailureRow(error, model::refresh) }
                rows.isEmpty() && !state.loading -> item(key = "empty") { EmptyState(pluginString(if (state.query.isBlank()) Strings.no_comments else Strings.comment_no_match)) }
            }
            if (state.loading && state.comments.isNotEmpty()) item(key = "reloading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            items(rows, key = { "comment-" + it.comment.id }) { row -> CommentRow(row) { model.toggle(row.comment.id) } }
        }
    }
}

@Composable
private fun CommentControls(model: CommentsModel, state: CommentsModel.State) {
    var sorting by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = model::search,
            label = { Text(pluginString(Strings.comment_search)) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = if (state.query.isEmpty()) null else ({ IconButton(onClick = { model.search("") }) { Icon(Icons.Default.Clear, pluginString(Strings.clear_search)) } }),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.align(Alignment.End)) {
            TextButton(onClick = { sorting = true }) {
                Icon(SubstackIcons.Sort, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(pluginString(commentOrderLabel(state.order)))
            }
            DropdownMenu(expanded = sorting, onDismissRequest = { sorting = false }) {
                SubstackCommentOrder.entries.forEach { order ->
                    DropdownMenuItem(
                        text = { Text(pluginString(commentOrderLabel(order))) },
                        leadingIcon = { if (order == state.order) Icon(Icons.Default.Check, null) else Spacer(Modifier.size(24.dp)) },
                        onClick = { sorting = false; model.sort(order) },
                    )
                }
            }
        }
    }
}

private fun commentOrderLabel(order: SubstackCommentOrder): PluginText = when (order) {
    SubstackCommentOrder.ORIGINAL -> Strings.sort_default
    SubstackCommentOrder.NEWEST -> Strings.order_newest
    SubstackCommentOrder.OLDEST -> Strings.order_oldest
}

@Composable
private fun BranchesMenu(enabled: Boolean, onExpand: (Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, enabled = enabled) { Icon(Icons.Default.MoreVert, pluginString(Strings.more_options)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(pluginString(Strings.expand_all)) }, onClick = { open = false; onExpand(true) })
            DropdownMenuItem(text = { Text(pluginString(Strings.collapse_all)) }, onClick = { open = false; onExpand(false) })
        }
    }
}

/** One comment, indented under what it answers, its replies folded or not. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommentRow(row: SubstackCommentRow, onToggle: () -> Unit) {
    val comment = row.comment
    val runs = remember(comment.body) { SubstackLinks.textParts(comment.body).map { part -> part.url?.let { TextRun.Link(part.text, it) } ?: TextRun.Plain(part.text) } }
    Row(Modifier.padding(start = (16 + 12 * row.depth.coerceIn(0, 2)).dp, end = 16.dp, top = 12.dp).height(IntrinsicSize.Min)) {
        if (row.depth > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
        Column(Modifier.padding(start = if (row.depth > 0) 8.dp else 0.dp).weight(1f)) {
            if (row.contextOnly) Text(pluginString(Strings.thread_context), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(verticalArrangement = Arrangement.Center) {
                Text(comment.author?.takeIf { it.isNotBlank() } ?: pluginString(Strings.unknown), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                comment.at?.let { Text("  " + agoText(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            LinkedText(runs, onMention = {}, onTag = {}, modifier = Modifier.padding(top = 6.dp))
            if (row.descendants > 0) {
                TextButton(onClick = onToggle) {
                    Icon(if (row.collapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp, null)
                    Text(pluginString(if (row.collapsed) Strings.show else Strings.hide) + " · " + pluginPlural(Strings.comment_count, row.descendants, row.descendants))
                }
            }
            HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

