package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.threads.ThreadsLinks
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsQuery
import com.aimdi.xtanative.networks.threads.ThreadsSearchTerms
import com.aimdi.xtanative.ui.plugins.PluginFeedSkeleton
import com.aimdi.xtanative.ui.plugins.Fetch
import com.aimdi.xtanative.ui.plugins.MessageText
import com.aimdi.xtanative.ui.plugins.NoticeHost
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.RetryMessage
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside
import com.aimdi.xtanative.ui.post.PostIcons

/** The two orders Threads' public search offers. */
internal enum class ThreadsSearchOrder { TOP, RECENT }

/**
 * What words or a tag find (threads_search_screen.dart): Top and Recent, one
 * page each, as a guest sees them. The title is a search field holding the
 * query, as on X: words or a #tag search again here, keeping the order
 * chosen; a post link, an @handle or a profile link opens its page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThreadsSearchScreen(app: ThreadsApp, start: ThreadsSearchTerms) {
    var terms by rememberSaveable(stateSaver = TermsSaver) { mutableStateOf(start) }
    var text by rememberSaveable { mutableStateOf(start.text) }
    var order by rememberSaveable { mutableStateOf(ThreadsSearchOrder.TOP) }
    val keyboard = LocalSoftwareKeyboardController.current
    val outside = rememberOpenOutside()
    val submit: () -> Unit = {
        keyboard?.hide()
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
                title = { QueryField(text, onText = { text = it }, onSubmit = submit) },
                actions = {
                    IconButton(onClick = { outside(ThreadsLinks.searchUrl(terms.query, terms.tag, order == ThreadsSearchOrder.RECENT).toString()) }) {
                        Icon(PostIcons.OpenInNew, contentDescription = pluginString(Strings.open_in_browser))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            key(terms) { ThreadsSearchBody(app, terms, PluginFrame(PaddingValues(bottom = padding.calculateBottomPadding())), order) { order = it } }
            NoticeHost(app.notices)
        }
    }
}

private val TermsSaver = Saver<ThreadsSearchTerms, List<String>>(
    save = { listOf(it.query, if (it.tag) "1" else "", it.profile.orEmpty()) },
    restore = { ThreadsSearchTerms(it[0], tag = it[1] == "1", profile = it[2].ifEmpty { null }) },
)

@Composable
private fun QueryField(text: String, onText: (String) -> Unit, onSubmit: () -> Unit) {
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
        modifier = Modifier.fillMaxWidth().padding(end = 4.dp),
    )
}

/**
 * One search's results, Top and Recent, each read when first shown; above
 * them the profile the words also spell, so a handle typed without its @
 * is one tap away.
 */
@Composable
internal fun ThreadsSearchBody(
    app: ThreadsApp,
    terms: ThreadsSearchTerms,
    frame: PluginFrame,
    order: ThreadsSearchOrder? = null,
    onOrder: ((ThreadsSearchOrder) -> Unit)? = null,
) {
    var own by rememberSaveable { mutableStateOf(ThreadsSearchOrder.TOP) }
    val shown = order ?: own
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ThreadsSearchOrder.TOP to Strings.sort_top, ThreadsSearchOrder.RECENT to Strings.recent).forEach { (option, label) ->
                FilterChip(selected = shown == option, onClick = { onOrder?.invoke(option) ?: run { own = option } }, label = { Text(pluginString(label)) })
            }
        }
        terms.profile?.let { handle -> ProfileRow(app, handle) }
        Box(Modifier.weight(1f)) { key(shown) { Results(app, terms, shown, frame) } }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Results(app: ThreadsApp, terms: ThreadsSearchTerms, order: ThreadsSearchOrder, frame: PluginFrame) {
    val fetch = rememberKept("threads-search-${terms.query}-${terms.tag}-$order") { scope ->
        Fetch<List<ThreadsPost>>(scope) { app.ready(); app.direct.searchPosts(terms.query, terms.tag, recent = order == ThreadsSearchOrder.RECENT).items.distinctBy { it.id } }
    }
    LaunchedEffect(fetch) { fetch.ensure() }
    val state by fetch.state.collectAsState()
    val posts = state.value
    val language = currentLanguage()
    val list = rememberLazyListState()
    PullToRefreshBox(isRefreshing = state.loading && posts != null, onRefresh = { fetch.load(force = true) }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = list, contentPadding = frame.padding, modifier = Modifier.fillMaxSize()) {
            when {
                posts == null && state.problem != null -> item(key = "problem") { RetryMessage(ThreadsFailures.page(state.problem, language), onRetry = { fetch.load() }) }
                posts == null -> item(key = "loading") { PluginFeedSkeleton() }
                posts.isEmpty() -> item(key = "none") { MessageText(pluginString(Strings.no_results)) }
                else -> items(posts, key = { "post:" + it.id }, contentType = { "post" }) { ThreadsPostCard(app, it) }
            }
        }
    }
}
