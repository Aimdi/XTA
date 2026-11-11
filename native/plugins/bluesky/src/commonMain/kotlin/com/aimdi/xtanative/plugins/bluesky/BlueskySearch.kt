package com.aimdi.xtanative.plugins.bluesky

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.networks.bluesky.BlueskyPost
import com.aimdi.xtanative.networks.bluesky.BlueskyPostFilters
import com.aimdi.xtanative.networks.bluesky.BlueskyProfile
import com.aimdi.xtanative.networks.bluesky.BlueskySearchKind
import com.aimdi.xtanative.networks.bluesky.BlueskySearchRules
import com.aimdi.xtanative.networks.bluesky.BlueskyUrls
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.swipeTabs
import com.aimdi.xtanative.ui.plugins.MessageText
import com.aimdi.xtanative.ui.plugins.PagedItems
import com.aimdi.xtanative.ui.plugins.PagedState
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.PluginSearch
import com.aimdi.xtanative.ui.plugins.ScrollsToTop
import com.aimdi.xtanative.ui.plugins.pagedRows
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.problemOf
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.post.PostIcons
import com.aimdi.xtanative.ui.search.DiscoverIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Bluesky's search under the app's own search field (Discover's chip, the
 * library's search): people, or posts latest or top, narrowed by an author
 * and a tag; a post link reads that post, a lone #tag that tag, a handle,
 * DID or profile link that account.
 */
internal class BlueskySearch(private val app: BlueskyApp) : PluginSearch() {
    override fun hint(language: String): String = Strings.search_hint.get(language)

    @Composable
    override fun Results(query: String, frame: PluginFrame) = SearchBody(app, query, author = "", BlueskySearchRules.kindFor(query), frame)

    @Composable
    override fun Landing(frame: PluginFrame, send: (String) -> Unit) = SearchLanding(app, frame, send)

    override fun sent(query: String) {
        app.launch { app.prefs.remember(query) }
    }
}

/** Before a search, as XTA's search screen lands (bluesky_search_sheet.dart): People or Posts, with what each offers until something is searched. */
@Composable
private fun SearchLanding(app: BlueskyApp, frame: PluginFrame, send: (String) -> Unit) =
    SearchBody(app, "", author = "", BlueskySearchKind.PEOPLE, frame) { kind -> Landing(app, kind, send) }

/**
 * XTA's own Bluesky search screen (bluesky_search_sheet.dart): its field,
 * People and Posts, and until something is searched, the recent searches,
 * the ways to import follows and the accounts Bluesky suggests (or, for
 * posts, what can be searched).
 */
@Composable
internal fun BlueskySearchScreen(app: BlueskyApp, initial: String, author: String, kind: BlueskySearchKind) {
    var text by rememberSaveable { mutableStateOf(initial) }
    var sent by rememberSaveable { mutableStateOf(initial.trim()) }
    var shownKind by rememberSaveable { mutableStateOf(kind) }
    val keyboard = LocalSoftwareKeyboardController.current
    val field = remember { FocusRequester() }
    // XTA's field takes the keyboard when the screen opens on nothing searched, and lets it go on a search.
    LaunchedEffect(Unit) { if (initial.isEmpty()) field.requestFocus() }
    val send = { query: String ->
        keyboard?.hide()
        text = query
        sent = query.trim()
        if (sent.isNotEmpty()) app.launch { app.prefs.remember(sent) }
    }
    PluginScaffold(title = pluginString(Strings.search), onBack = app.host::back) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text(pluginString(if (shownKind == BlueskySearchKind.PEOPLE) Strings.search_people_hint else Strings.search_link_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = { IconButton(onClick = { send(text) }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, pluginString(Strings.search)) } },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { send(text) }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).focusRequester(field),
            )
            val frame = PluginFrame(PaddingValues(bottom = padding.calculateBottomPadding()))
            SearchBody(app, sent, author, kind, frame, onKind = { shownKind = it }) { landingKind -> Landing(app, landingKind, send) }
        }
    }
}

/**
 * People or Posts for [query], the posts with their filters; with no query,
 * [landing] in their place. [onKind] hears which of them is shown.
 */
@Composable
private fun SearchBody(
    app: BlueskyApp,
    query: String,
    author: String,
    initialKind: BlueskySearchKind,
    frame: PluginFrame,
    onKind: (BlueskySearchKind) -> Unit = {},
    landing: (@Composable (BlueskySearchKind) -> Unit)? = null,
) {
    var kind by rememberSaveable { mutableStateOf(initialKind) }
    // A post link is read as posts, as XTA's search turns to them; otherwise the tab stays as the reader left it.
    LaunchedEffect(query) { if (BlueskyUrls.searchPostTarget(query) != null) kind = BlueskySearchKind.POSTS }
    var filters by rememberSaveable(author) { mutableStateOf(BlueskyPostFilters(author = author)) }
    LaunchedEffect(kind) { onKind(kind) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BlueskySearchKind.entries.forEach { choice ->
                FilterChip(
                    selected = kind == choice,
                    onClick = { kind = choice },
                    label = { Text(pluginString(if (choice == BlueskySearchKind.PEOPLE) Strings.search_people else Strings.search_posts)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (kind == BlueskySearchKind.POSTS) PostFiltersRow(filters) { filters = it }
        Box(Modifier.weight(1f).swipeTabs(BlueskySearchKind.entries, kind) { kind = it }) {
            when {
                query.isEmpty() -> landing?.invoke(kind)
                kind == BlueskySearchKind.PEOPLE -> PeopleResults(app, query, frame)
                else -> PostResults(app, query, filters, frame)
            }
        }
    }
}

/** Latest or Top, the filters' dialog, and the author and tag searched, each to take off. */
@Composable
private fun PostFiltersRow(filters: BlueskyPostFilters, onChange: (BlueskyPostFilters) -> Unit) {
    var dialog by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(selected = !filters.top, onClick = { onChange(filters.copy(top = false)) }, label = { Text(pluginString(Strings.search_latest)) })
        FilterChip(selected = filters.top, onClick = { onChange(filters.copy(top = true)) }, label = { Text(pluginString(Strings.search_top)) })
        AssistChip(onClick = { dialog = true }, label = { Text(pluginString(Strings.filters)) }, leadingIcon = { Icon(PostIcons.Tune, contentDescription = null) })
        if (filters.author.isNotEmpty()) RemovableChip("@" + filters.author) { onChange(filters.copy(author = "")) }
        if (filters.tag.isNotEmpty()) RemovableChip("#" + filters.tag) { onChange(filters.copy(tag = "")) }
    }
    if (dialog) FiltersDialog(filters, onDismiss = { dialog = false }) { onChange(it) }
}

@Composable
private fun RemovableChip(label: String, onRemove: () -> Unit) {
    InputChip(
        selected = false,
        onClick = onRemove,
        label = { Text(label) },
        trailingIcon = { Icon(Icons.Filled.Close, pluginString(Strings.remove)) },
    )
}

/** The author and the tag a post search is narrowed to; an author that is no account is refused. */
@Composable
private fun FiltersDialog(filters: BlueskyPostFilters, onDismiss: () -> Unit, onApply: (BlueskyPostFilters) -> Unit) {
    var author by rememberSaveable { mutableStateOf(filters.author) }
    var tag by rememberSaveable { mutableStateOf(filters.tag) }
    val checked = BlueskySearchRules.author(author)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluginString(Strings.filters)) },
        text = {
            Column {
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    singleLine = true,
                    label = { Text(pluginString(Strings.search_author)) },
                    isError = checked == null,
                    supportingText = if (checked == null) ({ Text(pluginString(Strings.search_invalid_author)) }) else null,
                )
                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it },
                    singleLine = true,
                    label = { Text(pluginString(Strings.search_tag)) },
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = checked != null, onClick = {
                onApply(filters.copy(author = checked.orEmpty(), tag = BlueskySearchRules.tag(tag)))
                onDismiss()
            }) { Text(pluginString(Strings.search)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    onApply(filters.copy(author = "", tag = ""))
                    onDismiss()
                }) { Text(pluginString(Strings.reset_filters)) }
                TextButton(onClick = onDismiss) { Text(pluginString(Strings.cancel)) }
            }
        },
    )
}

@Composable
private fun PeopleResults(app: BlueskyApp, query: String, frame: PluginFrame) {
    val server by app.prefs.appView.collectAsState()
    val pages = rememberKept("bluesky-search-people:$server:$query") { scope ->
        PagedItems<BlueskyProfile>(scope, key = { it.identity }) { cursor -> app.client.searchPeople(query, cursor) }
    }
    val follows by app.follows.state.collectAsState()
    ResultList(pages, { it.identity }, pluginString(Strings.people_empty), frame) { person ->
        PersonRow(app, person, follows.orEmpty().any { it.isOf(person.did) || it.isOf(person.handle) })
    }
}

@Composable
private fun PostResults(app: BlueskyApp, query: String, filters: BlueskyPostFilters, frame: PluginFrame) {
    val server by app.prefs.appView.collectAsState()
    val pages = rememberKept("bluesky-search-posts:$server:${filters.sort}:${filters.author}:${filters.tag}:$query") { scope ->
        PagedItems<BlueskyPost>(scope, key = { it.uri }) { cursor -> app.client.searchPostsFor(query, cursor, filters.sort, filters.author, filters.tag) }
    }
    ResultList(pages, { it.uri }, pluginString(Strings.search_nothing, query), frame) { post -> PostRow(app, post) }
}

/**
 * Results read page by page as XTA's search reads them (bluesky_search_sheet.dart):
 * "Load more results" asks for the next page, and once the last is read,
 * "No more results"; a pull searches again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T : Any> ResultList(pages: PagedItems<T>, key: (T) -> String, empty: String, frame: PluginFrame, row: @Composable (T) -> Unit) {
    val paged by pages.state.collectAsState()
    val list = rememberLazyListState()
    ScrollsToTop(list, frame.toTop)
    PullToRefreshBox(isRefreshing = paged.loading && paged.loaded, onRefresh = pages::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = list, contentPadding = frame.padding, modifier = Modifier.fillMaxSize()) {
            pagedRows(paged, pages, key, empty, { pluginString(errorText(it)) }, row)
            when (SearchFoot.of(paged)) {
                SearchFoot.MORE -> item(key = "search-more") {
                    OutlinedButton(onClick = pages::more, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text(pluginString(Strings.search_more)) }
                }
                SearchFoot.END -> item(key = "search-end") { MessageText(pluginString(Strings.search_end)) }
                SearchFoot.NONE -> Unit
            }
        }
    }
}

/** What stands under a search's results: the way to its next page, that there is none, or nothing while a page is read or failed. */
internal enum class SearchFoot {
    NONE, MORE, END;

    companion object {
        fun of(paged: PagedState<*>): SearchFoot = when {
            paged.items.isEmpty() || paged.loading || paged.loadingMore || paged.moreProblem != null -> NONE
            paged.next != null -> MORE
            else -> END
        }
    }
}

/** What the search shows before anything is searched. */
@Composable
private fun Landing(app: BlueskyApp, kind: BlueskySearchKind, onSearch: (String) -> Unit) {
    val recent by app.prefs.searches.collectAsState()
    val suggestions = rememberKept("bluesky-suggestions") { scope -> Suggestions(app, scope) }
    val suggested by suggestions.state.collectAsState()
    val follows by app.follows.state.collectAsState()
    LazyColumn(Modifier.fillMaxSize()) {
        if (recent.isNotEmpty()) item(key = "recent") { RecentSearches(app, recent, onSearch) }
        if (kind == BlueskySearchKind.POSTS) {
            item(key = "hint") { Text(pluginString(Strings.search_link_hint), modifier = Modifier.padding(24.dp)) }
        } else {
            importRows(app)
            item(key = "suggested") { SectionTitle(pluginString(Strings.suggested)) }
            val problem = suggested.problem
            when {
                problem != null -> item(key = "suggested-problem") { ProblemRow(problem, onRetry = suggestions::load) }
                suggested.loading -> item(key = "suggested-loading") { LoadingRow() }
            }
            items(suggested.people, key = { "suggested-" + it.identity }) { person ->
                PersonRow(app, person, follows.orEmpty().any { it.isOf(person.did) || it.isOf(person.handle) })
            }
        }
    }
}

@Composable
private fun RecentSearches(app: BlueskyApp, recent: List<String>, onSearch: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)) {
        Text(pluginString(Strings.recent_searches), style = MaterialTheme.typography.titleSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            recent.take(RECENT_SHOWN).forEach { query -> AssistChip(onClick = { onSearch(query) }, label = { Text(query, maxLines = 1) }) }
        }
        TextButton(onClick = { app.launch { app.prefs.clearSearches() } }) {
            Icon(Icons.Filled.Delete, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(pluginString(Strings.clear_recent_searches))
        }
    }
}

/** The three ways to copy accounts into the follows kept here. */
internal fun LazyListScope.importRows(app: BlueskyApp) {
    item(key = "import-following") { MenuRow(DiscoverIcons.PersonAdd, pluginString(Strings.import_following)) { app.host.open(BlueskyRoutes.import(ImportKind.FOLLOWING)) } }
    item(key = "import-list") { MenuRow(BlueskyIcons.ListAlt, pluginString(Strings.import_list)) { app.host.open(BlueskyRoutes.import(ImportKind.LIST)) } }
    item(key = "import-starter") { MenuRow(PostIcons.Sparkle, pluginString(Strings.import_starter)) { app.host.open(BlueskyRoutes.import(ImportKind.STARTER)) } }
}

/** The accounts the AppView suggests to a reader without an account, read once the search opens. */
internal class Suggestions(private val app: BlueskyApp, private val scope: CoroutineScope) {
    data class State(val people: List<BlueskyProfile> = emptyList(), val loading: Boolean = true, val problem: Problem? = null)

    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable.asStateFlow()

    init {
        load()
    }

    fun load() {
        scope.launch {
            mutable.value = mutable.value.copy(loading = true, problem = null)
            mutable.value = try {
                State(app.client.suggestions(limit = 20), loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                State(loading = false, problem = problemOf(e))
            }
        }
    }
}

private const val RECENT_SHOWN = 8
