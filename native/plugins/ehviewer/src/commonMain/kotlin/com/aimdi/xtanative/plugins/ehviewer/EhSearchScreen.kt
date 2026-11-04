package com.aimdi.xtanative.plugins.ehviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.ehviewer.EhCategory
import com.aimdi.xtanative.networks.ehviewer.EhFeed
import com.aimdi.xtanative.networks.ehviewer.EhSearch
import com.aimdi.xtanative.networks.ehviewer.EhSearchLanguage
import com.aimdi.xtanative.networks.ehviewer.EhSearchState
import com.aimdi.xtanative.networks.ehviewer.EhTag
import com.aimdi.xtanative.store.settings.EhViewerPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.plugins.MessageText
import com.aimdi.xtanative.ui.plugins.NoticeHost
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginSearch
import com.aimdi.xtanative.ui.plugins.WithSettings
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.post.PostIcons
import com.aimdi.xtanative.ui.search.DiscoverIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** A search as one screen keeps it: the search, and the scope it and its results read in. */
internal class EhSearchPage(private val app: EhApp, private val scope: CoroutineScope) {
    val search = EhSearch(app.client, scope, remember = app::rememberSearch)

    fun run(query: String) {
        scope.launch {
            app.ready()
            search.search(query)
        }
    }

    fun refresh(feed: EhFeed) {
        scope.launch { feed.refresh() }
    }

    fun more(feed: EhFeed) {
        scope.launch { feed.loadMore() }
    }
}

/**
 * The search (eh_search_screen.dart): the site's own tag suggestions for the
 * term being typed, the category, rating and language filters, and the
 * searches run before; a changed filter runs the search again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EhSearchScreen(app: EhApp, initial: String) = WithSettings(app.host.settings) { settings ->
    val page = rememberKept("eh-search") { scope -> EhSearchPage(app, scope).also { if (initial.isNotBlank()) it.run(initial) } }
    val state by page.search.state.collectAsState()
    var text by remember { mutableStateOf(typed(initial)) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val run = { query: String ->
        focusManager.clearFocus()
        page.run(query)
    }
    LaunchedEffect(Unit) { if (initial.isBlank()) runCatching { focus.requestFocus() } }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = app.host::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, pluginString(Strings.back)) } },
                title = {
                    TextField(
                        value = text,
                        onValueChange = {
                            text = it
                            page.search.type(it.text)
                        },
                        singleLine = true,
                        placeholder = { Text(pluginString(Strings.search_hint)) },
                        trailingIcon = { IconButton(onClick = { run(text.text) }) { Icon(Icons.Filled.Search, pluginString(Strings.search)) } },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                        keyboardActions = KeyboardActions(onSearch = { run(text.text) }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            Column(Modifier.fillMaxSize()) {
                SearchFilters(page.search, state)
                HorizontalDivider()
                Box(Modifier.weight(1f)) {
                    when {
                        state.suggestions.isNotEmpty() -> Suggestions(state.suggestions) { tag ->
                            val picked = page.search.pick(tag)
                            text = TextFieldValue(picked, TextRange(picked.length))
                        }
                        state.query == null -> SearchHistory(app, settings) { query ->
                            text = TextFieldValue(query, TextRange(query.length))
                            run(query)
                        }
                        else -> page.search.results?.let { SearchResults(app, page, it, PaddingValues(bottom = padding.calculateBottomPadding())) }
                    }
                }
            }
            NoticeHost(app.notices)
        }
    }
}

/** The field as a search for [initial] opens it: the query and a space after it, ready for the next term. */
private fun typed(initial: String): TextFieldValue {
    val query = initial.trim()
    val text = if (query.isEmpty()) "" else "$query "
    return TextFieldValue(text, TextRange(text.length))
}

/** EH's search where the app draws the field (Discover's chips, the library's search): the filters and what [query] finds under them. */
internal class EhAppSearch(private val app: EhApp) : PluginSearch() {
    override fun hint(language: String): String = Strings.search_hint.get(language)

    @Composable
    override fun Results(query: String, frame: PluginFrame) = WithSettings(app.host.settings) {
        val page = rememberKept("eh-app-search:$query") { scope -> EhSearchPage(app, scope).also { it.run(query) } }
        val state by page.search.state.collectAsState()
        Column(Modifier.fillMaxSize()) {
            SearchFilters(page.search, state)
            HorizontalDivider()
            Box(Modifier.weight(1f)) { page.search.results?.let { SearchResults(app, page, it, frame.padding) } ?: LoadingRow() }
        }
    }

    /** Before a search, as XTA's search screen: the searches run before, each to run again or forget. */
    @Composable
    override fun Landing(frame: PluginFrame, send: (String) -> Unit) = WithSettings(app.host.settings) { settings ->
        Box(Modifier.fillMaxSize().padding(frame.padding)) { SearchHistory(app, settings, send) }
    }
}

/** Category, minimum rating and language, each a tap away. */
@Composable
private fun SearchFilters(search: EhSearch, state: EhSearchState) {
    val filters = state.filters
    Column(Modifier.fillMaxWidth()) {
        FilterRow {
            EhCategory.entries.forEach { category ->
                FilterChip(
                    selected = category in filters.categories,
                    onClick = { search.toggleCategory(category) },
                    label = { Text(category.label) },
                    leadingIcon = { Box(Modifier.size(12.dp).clip(CircleShape).background(categoryColor(category))) },
                )
            }
        }
        FilterRow {
            Text(pluginString(Strings.min_rating), style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.CenterVertically))
            (2..5).forEach { stars ->
                val spoken = pluginString(Strings.rating, "$stars")
                FilterChip(
                    selected = filters.minRating == stars,
                    onClick = { search.setMinRating(stars) },
                    label = { Text("$stars", Modifier.semantics { contentDescription = spoken }) },
                    leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
            EhSearchLanguage.entries.forEach { language ->
                FilterChip(selected = filters.language == language, onClick = { search.setLanguage(language) }, label = { Text(pluginString(EhText.language(language))) })
            }
        }
    }
}

@Composable
private fun FilterRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** The site's tags for the term being typed, in their namespaces' colours. */
@Composable
private fun Suggestions(tags: List<EhTag>, onPick: (EhTag) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(tags, key = { it.raw }) { tag ->
            ListItem(
                headlineContent = { Text(tag.name, color = namespaceColor(tag.namespace) ?: MaterialTheme.colorScheme.onSurface) },
                trailingContent = {
                    Text(pluginString(EhText.namespace(tag.namespace)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.clickable { onPick(tag) },
            )
        }
    }
}

/** The searches run before, newest first, each to run again or forget; all of them cleared at once. */
@Composable
private fun SearchHistory(app: EhApp, settings: Settings, onRun: (String) -> Unit) {
    val queries = settings[EhViewerPrefs.searchHistory]
    LazyColumn(Modifier.fillMaxSize()) {
        if (queries.isNotEmpty()) {
            item(key = "title") {
                ListItem(
                    headlineContent = { Text(pluginString(Strings.tab_history), style = MaterialTheme.typography.titleSmall) },
                    trailingContent = { IconButton(onClick = app::clearSearches) { Icon(DiscoverIcons.DeleteSweep, pluginString(Strings.clear_history)) } },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                )
            }
        }
        items(queries, key = { "q:$it" }) { query ->
            ListItem(
                headlineContent = { Text(query) },
                leadingContent = { Icon(PostIcons.History, contentDescription = null) },
                trailingContent = { IconButton(onClick = { app.forgetSearch(query) }) { Icon(Icons.Filled.Close, pluginString(Strings.delete)) } },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.clickable { onRun(query) },
            )
        }
    }
}

/** What the search found: a spinner while its first page loads, why it failed, that it found nothing, or the galleries. */
@Composable
private fun SearchResults(app: EhApp, page: EhSearchPage, feed: EhFeed, padding: PaddingValues) = key(feed) {
    val state by feed.state.collectAsState()
    val grid = rememberLazyGridState()
    val error = state.error
    when {
        error != null && !state.loading -> EhProblem(error, { page.refresh(feed) })
        // A first page not read yet still has its more to come.
        state.galleries.isEmpty() && (state.loading || state.hasMore) -> LoadingRow()
        state.galleries.isEmpty() -> MessageText(pluginString(Strings.empty_search))
        else -> GalleryGrid(
            app,
            state.galleries,
            padding,
            grid,
            refreshing = state.loading,
            onRefresh = { page.refresh(feed) },
            loadingMore = state.loadingMore,
            onNearEnd = { page.more(feed) },
        )
    }
}
