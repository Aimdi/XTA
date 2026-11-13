package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.threads.ThreadsLinks
import com.aimdi.xtanative.networks.threads.ThreadsPaging
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsQuery
import com.aimdi.xtanative.networks.threads.ThreadsSearchFound
import com.aimdi.xtanative.networks.threads.ThreadsSearchTerms
import com.aimdi.xtanative.ui.plugins.Fetch
import com.aimdi.xtanative.ui.plugins.MessageText
import com.aimdi.xtanative.ui.plugins.NoticeHost
import com.aimdi.xtanative.ui.plugins.PluginFeedSkeleton
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.RetryMessage
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside
import com.aimdi.xtanative.ui.post.PostIcons
import kotlinx.coroutines.launch

/**
 * The tabs of a search: Threads' two orders of its public search, and the
 * people and topics found among the top posts (a guest has no search of
 * either; a session's people search leads the people).
 */
internal enum class ThreadsSearchTab {
    TOP, RECENT, PEOPLE, TOPICS;

    val recent: Boolean get() = this == RECENT
}

/**
 * What words or a tag find (threads_search_screen.dart): Top and Recent as
 * a guest sees them, page after page where Meta names a next one. The title is a search field holding the
 * query, as on X: words or a #tag search again here, keeping the order
 * chosen; a post link, an @handle or a profile link opens its page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThreadsSearchScreen(app: ThreadsApp, start: ThreadsSearchTerms) {
    var terms by rememberSaveable(stateSaver = TermsSaver) { mutableStateOf(start) }
    var text by rememberSaveable { mutableStateOf(start.text) }
    var tab by rememberSaveable { mutableStateOf(ThreadsSearchTab.TOP) }
    var focused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val outside = rememberOpenOutside()
    val submit: () -> Unit = {
        keyboard?.hide()
        focus.clearFocus()
        ThreadsQuery.of(text)?.let { query ->
            query.remembered?.let(app::remember)
            val next = query.terms
            if (next != null) terms = next else app.openQuery(query)
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = app.host::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, pluginString(Strings.back)) } },
                title = { QueryField(text, onText = { text = it }, onSubmit = submit, onFocus = { focused = it }) },
                actions = {
                    IconButton(onClick = { outside(ThreadsLinks.searchUrl(terms.query, terms.tag, tab.recent).toString()) }) {
                        Icon(PostIcons.OpenInNew, contentDescription = pluginString(Strings.open_in_browser))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            key(terms) { ThreadsSearchBody(app, terms, PluginFrame(PaddingValues(bottom = padding.calculateBottomPadding())), tab) { tab = it } }
            if (focused && text.trim() != terms.text) {
                ThreadsSuggestionList(app, text, Modifier.fillMaxWidth()) { picked -> text = picked.text; submit() }
            }
            NoticeHost(app.notices)
        }
    }
}

private val TermsSaver = Saver<ThreadsSearchTerms, List<String>>(
    save = { listOf(it.query, if (it.tag) "1" else "", it.profile.orEmpty()) },
    restore = { ThreadsSearchTerms(it[0], tag = it[1] == "1", profile = it[2].ifEmpty { null }) },
)

@Composable
private fun QueryField(text: String, onText: (String) -> Unit, onSubmit: () -> Unit, onFocus: (Boolean) -> Unit) {
    TextField(
        value = text,
        onValueChange = onText,
        singleLine = true,
        placeholder = { Text(pluginString(Strings.search_posts_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = if (text.isEmpty()) null else { { IconButton(onClick = { onText("") }) { Icon(Icons.Filled.Clear, contentDescription = pluginString(Strings.clear)) } } },
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        shape = RoundedCornerShape(24.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier.fillMaxWidth().padding(end = 4.dp).onFocusChanged { onFocus(it.isFocused) },
    )
}

/**
 * One search's results: Top and Recent, each read when first shown, and the
 * people and topics among the top posts; above them the profile the words
 * also spell, so a handle typed without its @ is one tap away. Each tab keeps
 * its place, and tapping the chosen one again goes back to its top.
 */
@Composable
internal fun ThreadsSearchBody(
    app: ThreadsApp,
    terms: ThreadsSearchTerms,
    frame: PluginFrame,
    tab: ThreadsSearchTab? = null,
    onTab: ((ThreadsSearchTab) -> Unit)? = null,
) {
    var own by rememberSaveable { mutableStateOf(ThreadsSearchTab.TOP) }
    val shown = tab ?: own
    val lists = ThreadsSearchTab.entries.associateWith { rememberSaveable(it.name, saver = LazyListState.Saver) { LazyListState() } }
    val scope = rememberCoroutineScope()
    val choose = { option: ThreadsSearchTab ->
        when {
            option == shown -> scope.launch { lists.getValue(option).animateScrollToItem(0) }
            onTab != null -> onTab(option)
            else -> own = option
        }
        Unit
    }
    Column(Modifier.fillMaxSize()) {
        TrendHeader(app, terms)
        SearchTabs(shown, choose)
        terms.profile?.let { handle -> ProfileRow(app, handle) }
        Box(Modifier.weight(1f)) { key(shown) { Results(app, terms, shown, frame, lists.getValue(shown)) } }
    }
}

@Composable
private fun SearchTabs(shown: ThreadsSearchTab, onChoose: (ThreadsSearchTab) -> Unit) {
    val labels = listOf(Strings.sort_top, Strings.recent, Strings.search_people, Strings.search_topics)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ThreadsSearchTab.entries.zip(labels).forEach { (option, label) ->
            FilterChip(selected = shown == option, onClick = { onChoose(option) }, label = { Text(pluginString(label)) })
        }
    }
}

@Composable
private fun ProfileRow(app: ThreadsApp, handle: String) {
    ListItem(
        leadingContent = { Icon(Icons.Filled.Person, contentDescription = null) },
        headlineContent = { Text(pluginString(Strings.open_handle, handle)) },
        trailingContent = { Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { app.openProfile(handle) },
    )
}

/** The page Threads' public search gives for [terms], newest first with [recent]; People and Topics share Top's. */
@Composable
private fun rememberSearchFound(app: ThreadsApp, terms: ThreadsSearchTerms, recent: Boolean): Fetch<ThreadsSearchFound> {
    val fetch = rememberKept("threads-search-${terms.query}-${terms.tag}-$recent") { scope ->
        Fetch(scope) { app.ready(); app.direct.searchFound(terms.query, terms.tag, recent) }
    }
    LaunchedEffect(fetch) { fetch.ensure() }
    return fetch
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Results(app: ThreadsApp, terms: ThreadsSearchTerms, tab: ThreadsSearchTab, frame: PluginFrame, list: LazyListState) {
    val fetch = rememberSearchFound(app, terms, tab.recent)
    val state by fetch.state.collectAsState()
    val found = state.value
    val sessionPeople = if (tab == ThreadsSearchTab.PEOPLE) rememberSessionPeople(app, terms) else emptyList()
    val more = rememberKept("threads-search-more-${terms.query}-${terms.tag}-${tab.recent}") { scope ->
        ThreadsPaging(scope, ThreadsPost::id) { cursor -> app.nextPage { app.direct.moreFound(terms.query, terms.tag, tab.recent, cursor) } }
    }
    val paged by more.state.collectAsState()
    val postsTab = tab == ThreadsSearchTab.TOP || tab == ThreadsSearchTab.RECENT
    LaunchedEffect(found) { found?.let { more.start(it.posts, it.next) } }
    if (postsTab) ReadsMoreNear(list, more, paged)
    val language = currentLanguage()
    PullToRefreshBox(isRefreshing = state.loading && found != null, onRefresh = { fetch.load(force = true) }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = list, contentPadding = frame.padding, modifier = Modifier.fillMaxSize()) {
            when {
                found == null && state.problem != null -> item(key = "problem") { RetryMessage(ThreadsFailures.search(state.problem, language), onRetry = { fetch.load() }) }
                found == null -> item(key = "loading") { PluginFeedSkeleton() }
                tab == ThreadsSearchTab.PEOPLE -> foundPeople(app, terms, found, sessionPeople)
                tab == ThreadsSearchTab.TOPICS -> foundTopics(app, terms, found)
                else -> {
                    foundPosts(app, terms, found.copy(posts = found.posts + paged.items))
                    moreFoot("search", paged, more::retry)
                }
            }
        }
    }
}

private fun LazyListScope.foundPosts(app: ThreadsApp, terms: ThreadsSearchTerms, found: ThreadsSearchFound) {
    if (terms.tag) item(key = "topic") { ThreadsTopicHeader(app, found.topic(terms.query)) }
    if (found.posts.isEmpty()) item(key = "none") { MessageText(pluginString(Strings.no_results)) }
    items(found.posts, key = { "post:" + it.id }, contentType = { "post" }) { ThreadsPostCard(app, it) }
}
