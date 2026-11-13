package com.aimdi.xtanative.plugins.substack

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.substack.SubstackCategory
import com.aimdi.xtanative.networks.substack.SubstackHomeRules
import com.aimdi.xtanative.networks.substack.SubstackHosts
import com.aimdi.xtanative.networks.substack.SubstackPost
import com.aimdi.xtanative.networks.substack.SubstackPostSearch
import com.aimdi.xtanative.networks.substack.SubstackPublication
import com.aimdi.xtanative.networks.substack.SubstackPublicationSearch
import com.aimdi.xtanative.store.settings.SubstackPrefs
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.swipeTabs
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginSearch
import com.aimdi.xtanative.ui.plugins.PluginSearchHistory
import com.aimdi.xtanative.ui.plugins.PluginText
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Substack's search, as XTA's discover screen: publications by name, handle
 * or address (or a topic's leaderboard while nothing is asked), and the posts
 * of the publications followed here; a pasted post link opens that post.
 * The app draws the field; recent searches are kept as XTA keeps them.
 */
internal class SubstackSearchUi(private val app: SubstackApp) : PluginSearch() {
    override fun hint(language: String): String = Strings.search_hint.get(language)

    @Composable
    override fun Results(query: String, frame: PluginFrame) = SearchBody(app, query.trim(), frame) {}

    /** Before a search: the recent searches, then Substack's topics with the leaderboard of the one picked. */
    @Composable
    override fun Landing(frame: PluginFrame, send: (String) -> Unit) = SearchBody(app, "", frame, send)

    override fun sent(query: String) = app.launch {
        app.host.settings.update { it.with(SubstackPrefs.searchHistory, PluginSearchHistory.remembered(it[SubstackPrefs.searchHistory], query)) }
    }
}

/** Publications or Posts for [trimmed], swiped between; with nothing asked, what to browse, [send] searching a recent search again. */
@Composable
private fun SearchBody(app: SubstackApp, trimmed: String, frame: PluginFrame, send: (String) -> Unit) {
    val direct = trimmed.isNotEmpty() && SubstackHosts.postRef(trimmed) != null
    var tab by rememberSaveable { mutableStateOf(0) }
    val notices = rememberNotices()
    WithNotices(notices) {
        Column(Modifier.fillMaxSize()) {
            if (!direct) SearchTabs(tab) { tab = it }
            LazyColumn(Modifier.weight(1f).swipeTabs(tab, if (direct) 0 else 2) { tab = it }, contentPadding = frame.padding) {
                when {
                    direct -> item(key = "direct") { DirectPost(app, trimmed, notices) }
                    trimmed.isEmpty() -> landing(app, tab, notices, send)
                    tab == 0 -> item(key = "publications-$trimmed") { PublicationResults(app, trimmed, notices) }
                    else -> item(key = "posts-$trimmed") { PostResults(app, trimmed) }
                }
            }
        }
    }
}

@Composable
private fun SearchTabs(tab: Int, onTab: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Strings.search_publications, Strings.tab_posts).forEachIndexed { index, label ->
            FilterChip(selected = tab == index, onClick = { onTab(index) }, label = { Text(pluginString(label)) }, modifier = Modifier.weight(1f))
        }
    }
}

/** What a step of a search reads next, its state kept while the screen is: the first page at once, then [step]s. */
internal class Stepped<S>(private val scope: CoroutineScope, initial: S, first: suspend () -> S) {
    private val mutable = MutableStateFlow(initial)
    val state: StateFlow<S> = mutable.asStateFlow()
    private val running = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = running.asStateFlow()

    init {
        step(first)
    }

    fun step(read: suspend () -> S) {
        if (running.value) return
        running.value = true
        scope.launch {
            try {
                mutable.value = read()
            } finally {
                running.value = false
            }
        }
    }
}

/**
 * With nothing asked (substack_search_sheet.dart): the recent searches, then
 * on Publications Substack's topics and the leaderboard of the one picked,
 * on Posts what they search.
 */
private fun LazyListScope.landing(app: SubstackApp, tab: Int, notices: Notices, send: (String) -> Unit) {
    item(key = "recent") { RecentSearches(app, send) }
    if (tab == 0) {
        item(key = "intro") { Text(pluginString(Strings.discover_intro), modifier = Modifier.padding(16.dp)) }
        item(key = "categories") { CategoryBrowser(app, notices) }
    } else {
        item(key = "posts-hint") { PostsHint(app) }
    }
}

/** What the Posts tab searches: the followed publications' articles, or a word that it needs one followed. */
@Composable
private fun PostsHint(app: SubstackApp) {
    val follows by app.publications.collectAsState()
    Column {
        Text(pluginString(Strings.search_followed_posts), modifier = Modifier.padding(16.dp))
        EmptyState(pluginString(if (follows.isNullOrEmpty()) Strings.search_no_follows else Strings.search_posts_hint))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecentSearches(app: SubstackApp, send: (String) -> Unit) {
    val recent by app.host.settings.observe(SubstackPrefs.searchHistory).collectAsState(emptyList())
    if (recent.isEmpty()) return
    Column(Modifier.padding(16.dp)) {
        Text(pluginString(Strings.recent_searches), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            recent.take(8).forEach { query -> SuggestionChip(onClick = { send(query) }, label = { Text(query) }) }
        }
        TextButton(onClick = { app.launch { app.host.settings.set(SubstackPrefs.searchHistory, emptyList()) } }) {
            Icon(Icons.Default.Delete, null, Modifier.padding(end = 8.dp))
            Text(pluginString(Strings.clear_recent_searches))
        }
    }
}

/** The topics, read once; the first is browsed until another is picked. */
internal class Categories(private val app: SubstackApp, private val scope: CoroutineScope) {
    data class State(val categories: List<SubstackCategory> = emptyList(), val loading: Boolean = true, val error: Throwable? = null)

    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable.asStateFlow()

    init {
        load()
    }

    fun load() {
        mutable.value = State(state.value.categories)
        scope.launch {
            mutable.value = try {
                State(app.client.categories(), loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                State(loading = false, error = e)
            }
        }
    }
}

@Composable
private fun CategoryBrowser(app: SubstackApp, notices: Notices) {
    val categories = rememberKept("substack-categories") { scope -> Categories(app, scope) }
    val state by categories.state.collectAsState()
    var picked by rememberSaveable { mutableStateOf<Long?>(null) }
    val error = state.error
    Column {
        when {
            state.loading -> LoadingRow()
            error != null -> FailureRow(error, categories::load)
        }
        if (state.categories.isEmpty()) return
        val category = state.categories.firstOrNull { it.id == picked } ?: state.categories.first()
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.categories.forEach { FilterChip(selected = it.id == category.id, onClick = { picked = it.id }, label = { Text(it.name) }) }
        }
        val leaderboard = rememberKept("substack-category-${category.id}") { scope ->
            val search = SubstackPublicationSearch.category(app.client, category.id)
            Stepped(scope, search.state) { search.first() } to search
        }
        PublicationPages(app, leaderboard.first, leaderboard.second, notices)
    }
}

@Composable
private fun PublicationResults(app: SubstackApp, query: String, notices: Notices) {
    val found = rememberKept("substack-find-$query") { scope ->
        val search = SubstackPublicationSearch.of(app.client, query)
        Stepped(scope, search.state) { search.first() } to search
    }
    PublicationPages(app, found.first, found.second, notices, emptyText = Strings.search_empty)
}

/** Publications found page by page, each with Follow, a failed page in its place. */
@Composable
private fun PublicationPages(
    app: SubstackApp,
    stepped: Stepped<SubstackPublicationSearch.State>,
    search: SubstackPublicationSearch,
    notices: Notices,
    emptyText: PluginText? = null,
) {
    val state by stepped.state.collectAsState()
    val busy by stepped.busy.collectAsState()
    val error = state.error
    Column {
        state.publications.forEach { PublicationTile(app, it, notices) }
        when {
            busy -> LoadingRow()
            error != null -> FailureRow(error, { stepped.step(if (state.retryMore) search::more else search::first) })
            state.publications.isEmpty() && emptyText != null -> EmptyState(pluginString(emptyText))
            state.hasMore -> LoadMoreRow(false, null, true, { stepped.step(search::more) })
        }
    }
}

/** A publication found, as XTA's SubstackDiscoveryPublicationTile: who it is, Follow on this device, and Add to group. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PublicationTile(app: SubstackApp, publication: SubstackPublication, notices: Notices) {
    val follows by app.publications.collectAsState()
    val followed = follows?.let { app.followOf(publication) } != null
    val followedText = pluginString(Strings.followed, publication.displayName)
    Row(Modifier.fillMaxWidth().clickable { app.openPublication(publication) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
        PublicationLogo(publication.logoUrl, 40.dp, shape = CircleShape)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(publication.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(SubstackHomeRules.hostOf(publication), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            publication.description?.takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            FlowRow {
                TextButton(onClick = { app.follow(publication); notices.show(followedText) }, enabled = !followed) {
                    Icon(if (followed) Icons.Default.CheckCircle else Icons.Default.Add, null, Modifier.size(18.dp).padding(end = 4.dp))
                    Text(if (followed) followedText else pluginString(Strings.follow))
                }
                GroupButton(app, publication, outlined = false)
            }
        }
    }
}

@Composable
private fun PostResults(app: SubstackApp, query: String) {
    val follows by app.publications.collectAsState()
    val followed = follows ?: return LoadingRow()
    val found = rememberKept("substack-posts-$query-${followed.size}") { scope ->
        val search = SubstackPostSearch.of(app.client, followed, query)
        Stepped(scope, search.state) { search.first() } to search
    }
    val (stepped, search) = found
    val state by stepped.state.collectAsState()
    val busy by stepped.busy.collectAsState()
    val marks = rememberMarks(app)
    Column {
        Text(pluginString(Strings.search_followed_posts), modifier = Modifier.padding(16.dp))
        if (state.failedCount > 0) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(pluginString(Strings.search_partial), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                TextButton(onClick = { stepped.step(search::retryFailed) }, enabled = !busy) { Text(pluginString(Strings.retry)) }
            }
        }
        state.posts.forEach { post: SubstackPost -> SubstackPostCard(app, post, marks, SubstackHomeRules.logoOf(followed, post)) }
        when {
            busy -> LoadingRow()
            state.posts.isEmpty() -> EmptyState(pluginString(if (followed.isEmpty()) Strings.search_no_follows else Strings.no_results))
            state.hasMore -> LoadMoreRow(false, null, true, { stepped.step(search::more) })
        }
    }
}

/** A pasted post link: its publication to follow, and the post to open. */
internal class DirectModel(private val app: SubstackApp, private val input: String, private val scope: CoroutineScope) {
    data class State(val publication: SubstackPublication? = null, val post: SubstackPost? = null, val loading: Boolean = true, val error: Throwable? = null, val postError: Throwable? = null)

    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable.asStateFlow()

    init {
        load()
    }

    fun load() {
        mutable.value = State()
        scope.launch {
            val publication = try {
                app.client.resolve(input)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.value = State(loading = false, error = e)
                return@launch
            }
            mutable.value = State(publication)
            val slug = SubstackHosts.postRef(input)?.slug ?: return@launch mutableDone(publication)
            mutable.value = try {
                State(publication, app.client.post(publication, slug), loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                State(publication, loading = false, postError = e)
            }
        }
    }

    private fun mutableDone(publication: SubstackPublication) {
        mutable.value = State(publication, loading = false)
    }
}

@Composable
private fun DirectPost(app: SubstackApp, input: String, notices: Notices) {
    val model = rememberKept("substack-direct-$input") { scope -> DirectModel(app, input, scope) }
    val state by model.state.collectAsState()
    val marks = rememberMarks(app)
    val error = state.error
    val postError = state.postError
    Column {
        state.publication?.let { PublicationTile(app, it, notices) }
        state.post?.let { SubstackPostCard(app, it, marks, state.publication?.logoUrl) }
        when {
            state.loading -> LoadingRow()
            error != null -> FailureRow(error, model::load)
            postError != null -> Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(pluginString(Strings.preview_post_failed), textAlign = TextAlign.Center)
                TextButton(onClick = model::load) { Text(pluginString(Strings.retry)) }
            }
        }
    }
}
