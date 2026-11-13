package com.aimdi.xtanative.plugins.substack

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.substack.SubstackFeedFilter
import com.aimdi.xtanative.networks.substack.SubstackHomeRules
import com.aimdi.xtanative.networks.substack.SubstackLoadedOptions
import com.aimdi.xtanative.networks.substack.SubstackOrder
import com.aimdi.xtanative.networks.substack.SubstackPost
import com.aimdi.xtanative.networks.substack.SubstackPublication
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.pluginPlural
import com.aimdi.xtanative.ui.plugins.pluginString
import androidx.compose.foundation.layout.PaddingValues

/**
 * The row above a feed's posts, as XTA's SubstackReadingToolbar: the
 * followed publications (a picker that opens one), a warning when some
 * failed, and the feed's search and filters, which only narrow what is loaded.
 */
@Composable
internal fun ReadingToolbar(app: SubstackApp, state: HomeState, pane: HomePane, followed: List<SubstackPublication>) {
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var failure by rememberSaveable { mutableStateOf(false) }
    val feed by state.feed.state.collectAsState()
    val options = state.controls.options(pane)
    val filter = state.controls.filter.takeIf { pane == HomePane.HOME }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            TextButton(onClick = { sheet = PICKER }) {
                Icon(SubstackIcons.Newspaper, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(pluginString(Strings.library_following), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (feed.failed) {
            IconButton(onClick = { failure = true }) { Icon(Icons.Outlined.Warning, failureLabel(feed.failedCount, feed.refreshError), tint = MaterialTheme.colorScheme.error) }
        }
        Marked(options.searching) { IconButton(onClick = { sheet = SEARCH }) { Icon(Icons.Default.Search, pluginString(Strings.search)) } }
        Marked(SubstackHomeRules.filtered(options, filter)) { IconButton(onClick = { sheet = FILTERS }) { Icon(SubstackIcons.Tune, pluginString(Strings.filters)) } }
    }
    when (sheet) {
        PICKER -> PublicationPicker(app, followed, feed.posts) { sheet = null }
        SEARCH, FILTERS -> ReadingOptions(state, pane, autofocus = sheet == SEARCH) { sheet = null }
    }
    if (failure) {
        AlertDialog(
            onDismissRequest = { failure = false },
            title = { Text(failureLabel(feed.failedCount, feed.refreshError)) },
            confirmButton = { TextButton(onClick = { failure = false; state.feed.refresh(force = true) }, enabled = !feed.refreshing) { Text(pluginString(Strings.retry)) } },
            dismissButton = { TextButton(onClick = { failure = false }) { Text(pluginString(Strings.close)) } },
        )
    }
}

/** What failed, in a line: how many publications, else why the refresh did. */
@Composable
private fun failureLabel(failedCount: Int, error: Throwable?): String = when {
    failedCount > 0 -> pluginPlural(Strings.partial_error, failedCount, failedCount)
    error != null -> problemText(error)
    else -> pluginString(Strings.load_error)
}

@Composable
private fun Marked(on: Boolean, content: @Composable () -> Unit) {
    if (!on) return content()
    BadgedBox(badge = { Badge(containerColor = MaterialTheme.colorScheme.primary) }) { content() }
}

/** The feed's search, order and (on Home) content filter, as XTA's reading options sheet. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ReadingOptions(state: HomeState, pane: HomePane, autofocus: Boolean, onDismiss: () -> Unit) {
    val controls = state.controls
    val options = controls.options(pane)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            SheetHeading(pluginString(Strings.filters), onDismiss)
            LoadedControls(options, autofocus) { controls.configure(pane, it) }
            if (pane == HomePane.HOME) {
                FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubstackFeedFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = controls.filter == filter,
                            onClick = { controls.filter = filter },
                            label = { Text(pluginString(filterLabel(filter))) },
                            leadingIcon = if (controls.filter == filter) ({ Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }) else null,
                        )
                    }
                }
            }
            TextButton(onClick = { controls.reset(pane) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Icon(Icons.Default.Clear, null, Modifier.padding(end = 8.dp))
                Text(pluginString(Strings.reset_filters))
            }
        }
    }
}

/** A search over loaded posts and its order, as XTA's SubstackLoadedControls. */
@Composable
internal fun LoadedControls(options: SubstackLoadedOptions, autofocus: Boolean, onChange: (SubstackLoadedOptions) -> Unit) {
    val focus = remember { FocusRequester() }
    var sorting by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = options.query,
            onValueChange = { onChange(options.copy(query = it)) },
            placeholder = { Text(pluginString(Strings.loaded_search)) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = if (options.query.isEmpty()) null else ({ IconButton(onClick = { onChange(options.copy(query = "")) }) { Icon(Icons.Default.Clear, pluginString(Strings.clear_search)) } }),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.weight(1f).focusRequester(focus),
        )
        Box {
            IconButton(onClick = { sorting = true }) { Icon(SubstackIcons.Sort, pluginString(Strings.sort)) }
            DropdownMenu(expanded = sorting, onDismissRequest = { sorting = false }) {
                SubstackOrder.entries.forEach { order ->
                    DropdownMenuItem(
                        text = { Text(pluginString(orderLabel(order))) },
                        leadingIcon = { if (order == options.order) Icon(Icons.Default.Check, null) else Spacer(Modifier.size(24.dp)) },
                        onClick = { sorting = false; onChange(options.copy(order = order)) },
                    )
                }
            }
        }
    }
    if (autofocus) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/** The followed publications, each marked when it has unread posts loaded; picking one opens its page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PublicationPicker(app: SubstackApp, followed: List<SubstackPublication>, posts: List<SubstackPost>, onDismiss: () -> Unit) {
    val read by app.read.state.collectAsState()
    val pins by app.pins.state.collectAsState()
    val unread = remember(posts, read) { SubstackHomeRules.withUnread(posts, read) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetHeading(pluginString(Strings.library_following), onDismiss)
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(followed, key = { it.id + "\n" + it.baseUrl }) { publication ->
                val marked = publication.baseUrl.lowercase() in unread
                Row(
                    Modifier.fillMaxWidth().clickable { onDismiss(); app.openPublication(publication) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BadgedBox(badge = { if (marked) Badge(containerColor = MaterialTheme.colorScheme.primary) }) { PublicationLogo(publication.logoUrl, 36.dp) }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(publication.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (marked) Text(pluginString(Strings.filter_unread), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (publication.id in pins) Icon(SubstackIcons.PushPin, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

internal fun filterLabel(filter: SubstackFeedFilter): PluginText = when (filter) {
    SubstackFeedFilter.ALL -> Strings.filter_all
    SubstackFeedFilter.UNREAD -> Strings.filter_unread
    SubstackFeedFilter.SAVED -> Strings.library_saved
    SubstackFeedFilter.FREE -> Strings.filter_free
    SubstackFeedFilter.PAID -> Strings.filter_paid
    SubstackFeedFilter.PODCAST -> Strings.filter_podcast
    SubstackFeedFilter.VIDEO -> Strings.videos
}

internal fun orderLabel(order: SubstackOrder): PluginText = when (order) {
    SubstackOrder.NEWEST -> Strings.order_newest
    SubstackOrder.OLDEST -> Strings.order_oldest
    SubstackOrder.POPULAR -> Strings.order_popular
}

private const val PICKER = "picker"
private const val SEARCH = "search"
private const val FILTERS = "filters"
