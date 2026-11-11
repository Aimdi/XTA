package com.aimdi.xtanative.plugins.reddit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.reddit.RedditPost
import com.aimdi.xtanative.networks.reddit.RedditSearchSort
import com.aimdi.xtanative.networks.reddit.RedditSubredditResult
import com.aimdi.xtanative.networks.reddit.RedditUserResult
import com.aimdi.xtanative.networks.reddit.normaliseSubreddit
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.swipeTabs
import com.aimdi.xtanative.ui.plugins.PagedItems
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.PluginSearch
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept

/** Reddit's search in the app's search screen: posts, communities and accounts, three searches, since they answer different questions. */
internal class RedditSearch(private val app: RedditApp) : PluginSearch() {
    override fun hint(language: String): String = Strings.search_hint.get(language)

    @Composable
    override fun Results(query: String, frame: PluginFrame) = SearchResults(app, query, subreddit = null, frame)

    /** Its own screen, for the follow button XTA keeps beside the field while the query names a community. */
    override fun ownScreen(query: String?): String = RedditRoutes.search(null, query)
}

/**
 * What [query] finds (XTA's `RedditSearchBody`): posts, in the order chosen
 * and within [subreddit] when there is one; communities, the query itself
 * first as one to open and follow; accounts, the query itself first.
 */
@Composable
internal fun SearchResults(app: RedditApp, query: String, subreddit: String?, frame: PluginFrame) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var sort by rememberSaveable { mutableStateOf(RedditSearchSort.RELEVANCE) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background, divider = { Hairline() }) {
            listOf(Strings.posts, Strings.search_subreddits, Strings.search_users).forEachIndexed { index, label ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(pluginString(label)) },
                    selectedContentColor = MaterialTheme.colorScheme.onBackground,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(Modifier.weight(1f).swipeTabs(tab, 3) { tab = it }) {
            when (tab) {
                0 -> Column(Modifier.fillMaxSize()) {
                    SortChips(subreddit, sort) { sort = it }
                    Box(Modifier.weight(1f)) { PostResults(app, query, subreddit, sort, frame) }
                }
                1 -> CommunityResults(app, query, frame)
                else -> PeopleResults(app, query, frame)
            }
        }
    }
}

@Composable
private fun SortChips(subreddit: String?, sort: RedditSearchSort, onSort: (RedditSearchSort) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (subreddit != null) {
            AssistChip(
                onClick = {},
                label = { Text("r/$subreddit") },
                leadingIcon = { Icon(RedditGlyphs.FilterAlt, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
        }
        RedditSearchSort.entries.forEach { choice ->
            FilterChip(selected = choice == sort, onClick = { onSort(choice) }, label = { Text(searchSortLabel(choice)) })
        }
    }
}

@Composable
private fun searchSortLabel(sort: RedditSearchSort): String = pluginString(
    when (sort) {
        RedditSearchSort.RELEVANCE -> Strings.search_sort_relevance
        RedditSearchSort.TOP -> Strings.sort_top
        RedditSearchSort.NEW -> Strings.sort_new
        RedditSearchSort.COMMENTS -> Strings.search_sort_comments
    },
)

@Composable
private fun PostResults(app: RedditApp, query: String, subreddit: String?, sort: RedditSearchSort, frame: PluginFrame) = key(query, subreddit, sort) {
    val pages = rememberKept("reddit-search-posts:${subreddit.orEmpty()}:$sort:$query") { scope ->
        PagedItems<RedditPost>(scope, key = { it.id }) { app.searchPosts(query, subreddit, sort) }
    }
    RedditPagedList(pages, key = { it.id }, frame = frame, empty = pluginString(Strings.no_results)) { post -> RedditPostCard(app, post) }
}

@Composable
private fun CommunityResults(app: RedditApp, query: String, frame: PluginFrame) = key(query) {
    val pages = rememberKept("reddit-search-communities:$query") { scope ->
        PagedItems<RedditSubredditResult>(scope, key = { it.name.lowercase() }) { app.searchSubreddits(query) }
    }
    val asked = normaliseSubreddit(query)
    RedditPagedList(
        pages,
        key = { "r/" + it.name.lowercase() },
        frame = frame,
        empty = null,
        posts = false,
        header = {
            item(key = "asked") {
                NameRow(RedditGlyphs.TravelExplore, "r/$query", onClick = { app.openSubreddit(asked ?: query) }) {
                    if (asked != null) FollowIconButton(app, asked)
                }
            }
        },
    ) { result -> CommunityRow(app, result) }
}

@Composable
private fun PeopleResults(app: RedditApp, query: String, frame: PluginFrame) = key(query) {
    val pages = rememberKept("reddit-search-people:$query") { scope ->
        PagedItems<RedditUserResult>(scope, key = { it.name.lowercase() }) { app.searchUsers(query) }
    }
    RedditPagedList(
        pages,
        key = { "u/" + it.name.lowercase() },
        frame = frame,
        empty = null,
        posts = false,
        header = { item(key = "asked") { NameRow(Icons.Outlined.Person, "u/$query", onClick = { app.openUser(query) }) } },
    ) { person -> NameRow(Icons.Outlined.Person, "u/" + person.name, onClick = { app.openUser(person.name) }) }
}

@Composable
private fun CommunityRow(app: RedditApp, result: RedditSubredditResult) {
    ListItem(
        leadingContent = { SubredditAvatar(app, result.name, 40.dp, known = result.iconUrl) },
        headlineContent = { Text("r/" + result.name) },
        supportingContent = result.description?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                result.subscribers?.let { Text(it.toString(), style = MaterialTheme.typography.bodySmall) }
                FollowIconButton(app, result.name)
            }
        },
        modifier = Modifier.clickable { app.openSubreddit(result.name) },
    )
}

@Composable
private fun NameRow(icon: ImageVector, label: String, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = trailing,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/**
 * Reddit's own search screen (XTA's `RedditSearchScreen`), within a
 * community when the route names one: the field in the bar, focused while
 * nothing was asked yet, and the follow button beside it while what was
 * searched names a community; the results under the bar.
 */
@Composable
internal fun RedditSearchScreen(app: RedditApp, route: RedditRoute.Search) {
    var text by rememberSaveable { mutableStateOf(route.query.orEmpty()) }
    var sent by rememberSaveable { mutableStateOf(route.query.orEmpty().trim()) }
    val keyboard = LocalSoftwareKeyboardController.current
    val send = {
        keyboard?.hide()
        sent = text.trim()
    }
    val asked = normaliseSubreddit(sent)
    PluginScaffold(
        title = { SearchField(text, autofocus = route.query.isNullOrBlank(), onChange = { text = it }, onSend = send) },
        onBack = app.host::back,
        actions = { if (asked != null) FollowIconButton(app, asked) },
    ) { padding ->
        if (sent.isNotEmpty()) {
            Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
                SearchResults(app, sent, route.subreddit, PluginFrame(bottomOnly(padding)))
            }
        }
    }
}

@Composable
private fun SearchField(text: String, autofocus: Boolean, onChange: (String) -> Unit, onSend: () -> Unit) {
    val focus = remember { FocusRequester() }
    TextField(
        value = text,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text(pluginString(Strings.search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingIcon = { IconButton(onClick = onSend) { Icon(Icons.Filled.Search, contentDescription = pluginString(Strings.search)) } },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSend() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )
    if (autofocus) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

