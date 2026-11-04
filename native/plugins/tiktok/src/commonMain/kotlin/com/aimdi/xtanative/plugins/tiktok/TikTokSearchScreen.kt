package com.aimdi.xtanative.plugins.tiktok

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.tiktok.TikTokParser
import com.aimdi.xtanative.networks.tiktok.TikTokSearchUser
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.TikTokPrefs
import com.aimdi.xtanative.ui.common.countText
import com.aimdi.xtanative.ui.common.swipeTabs
import com.aimdi.xtanative.ui.plugins.NoticeHost
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginSearch
import com.aimdi.xtanative.ui.plugins.WithSettings
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.post.PostIcons

/** People, then videos, as the search's tabs order them. */
private enum class FoundTab { PEOPLE, VIDEOS }

/**
 * The search (tiktok_search_sheet.dart) on a screen of its own: the field,
 * People and Videos; before a search, a typed handle to open, TikTok's
 * suggestions, the recent searches, what is trending and discover's people.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TikTokSearchScreen(app: TikTokApp, initial: String) = WithSettings(app.host.settings) { settings ->
    val model = rememberKept("tiktok-search") { scope -> TikTokSearchModel(app, scope).also { if (initial.isBlank()) it.loadLanding() else it.search(initial) } }
    var text by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val run = { query: String ->
        focusManager.clearFocus()
        text = TextFieldValue(query, TextRange(query.length))
        model.search(query)
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
                            model.type(it.text)
                        },
                        singleLine = true,
                        placeholder = { Text(pluginString(Strings.search_hint)) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = { IconButton(onClick = { run(text.text) }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, pluginString(Strings.search)) } },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { run(text.text) }),
                        modifier = Modifier.fillMaxWidth().padding(end = 8.dp).focusRequester(focus),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            SearchBody(app, model, settings, text.text, PaddingValues(bottom = padding.calculateBottomPadding()), run)
            NoticeHost(app.notices)
        }
    }
}

/** TikTok's search where the app draws the field: the people and videos [query] finds. */
internal class TikTokAppSearch(private val app: TikTokApp) : PluginSearch() {
    override fun hint(language: String): String = Strings.search_hint.get(language)

    @Composable
    override fun Results(query: String, frame: PluginFrame) = WithSettings(app.host.settings) { settings ->
        val model = rememberKept("tiktok-app-search:$query") { scope -> TikTokSearchModel(app, scope).also { it.search(query) } }
        Box(Modifier.fillMaxSize()) {
            SearchBody(app, model, settings, query, frame.padding) { model.search(it) }
            NoticeHost(app.notices, Modifier.padding(frame.padding))
        }
    }

    /** Before a search, as XTA's search sheet: TikTok's suggestions, the recent searches, what is trending and discover's people. */
    @Composable
    override fun Landing(frame: PluginFrame, send: (String) -> Unit) = WithSettings(app.host.settings) { settings ->
        val model = rememberKept("tiktok-app-landing") { scope -> TikTokSearchModel(app, scope).also { it.loadLanding() } }
        Box(Modifier.fillMaxSize()) {
            SearchBody(app, model, settings, "", frame.padding, send)
            NoticeHost(app.notices, Modifier.padding(frame.padding))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBody(app: TikTokApp, model: TikTokSearchModel, settings: Settings, typed: String, padding: PaddingValues, run: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val found by model.found.collectAsState()
    val landing by model.landing.collectAsState()
    val suggestions by model.suggestions.collectAsState()
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
            FoundTab.entries.forEachIndexed { index, choice ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(pluginString(if (choice == FoundTab.PEOPLE) Strings.search_people else Strings.search_videos)) })
            }
        }
        val shown = found
        val error = shown?.error
        Box(Modifier.weight(1f).swipeTabs(tab, FoundTab.entries.size) { tab = it }) {
            when {
                shown?.loading == true || (shown == null && landing.loading) -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                error != null -> Text(pluginString(TikTokText.error(error)), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp))
                shown == null -> LandingList(app, landing, suggestions, settings, typed, padding, run)
                else -> FoundList(app, shown, FoundTab.entries[tab], suggestions, settings, padding, run)
            }
        }
    }
}

@Composable
private fun LandingList(app: TikTokApp, landing: TikTokLanding, suggestions: List<String>, settings: Settings, typed: String, padding: PaddingValues, run: (String) -> Unit) {
    val history = settings[TikTokPrefs.searchHistory]
    val direct = TikTokParser.handle(typed.trim())
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        if (direct != null) {
            item(key = "direct") {
                ListItem(
                    leadingContent = { Icon(Icons.Filled.Person, contentDescription = null) },
                    headlineContent = { Text("@$direct") },
                    supportingContent = { Text(pluginString(Strings.open_handle)) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier.clickable { app.openProfile(direct) },
                )
            }
        }
        if (suggestions.isNotEmpty()) item(key = "suggestions") { WordChips(pluginString(Strings.search), suggestions, run) }
        if (history.isNotEmpty()) recent(history, run)
        if (landing.trending.isNotEmpty()) item(key = "trending") { WordChips(pluginString(Strings.trending_searches), landing.trending, run) }
        if (landing.people.isNotEmpty()) {
            item(key = "suggested") { SectionHeading(pluginString(Strings.suggested)) }
            items(landing.people.take(SUGGESTED_SHOWN), key = { "user:" + it.uniqueId }) { user -> UserRow(app, user) }
        }
        if (landing.people.isEmpty() && landing.trending.isEmpty()) {
            item(key = "prompt") {
                Text(
                    pluginString(Strings.search_prompt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
        }
    }
}

private const val SUGGESTED_SHOWN = 20

/** The recent searches, a handle shown with its "@". */
private fun LazyListScope.recent(history: List<String>, run: (String) -> Unit) {
    item(key = "recent") { SectionHeading(pluginString(Strings.recent_searches)) }
    items(history, key = { "recent:$it" }) { query ->
        ListItem(
            leadingContent = { Icon(PostIcons.History, contentDescription = null) },
            headlineContent = { Text(if (TikTokParser.handle(query) == null) query else "@$query") },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            modifier = Modifier.clickable { run(query) },
        )
    }
}

/** What a search found under [tab]: TikTok's suggestions above the people, and no results said plainly. */
@Composable
private fun FoundList(app: TikTokApp, found: TikTokFound, tab: FoundTab, suggestions: List<String>, settings: Settings, padding: PaddingValues, run: (String) -> Unit) {
    val page = found.page ?: return
    val likes = remember(settings) { TikTokMarks.likes(settings).toSet() }
    val empty = if (tab == FoundTab.PEOPLE) page.users.isEmpty() else page.posts.isEmpty()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        if (suggestions.isNotEmpty() && (tab == FoundTab.PEOPLE || empty)) item(key = "suggestions") { WordChips(pluginString(Strings.search), suggestions, run) }
        when {
            empty -> item(key = "empty") { Text(pluginString(Strings.search_empty), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp)) }
            tab == FoundTab.PEOPLE -> items(page.users, key = { "user:" + it.uniqueId }) { user -> UserRow(app, user) }
            else -> items(page.posts, key = { "post:" + it.id }) { post -> TikTokPostCard(app, post, post.id in likes) }
        }
    }
}

/** A person found: their picture, name and mark, handle and followers; a tap opens them. */
@Composable
private fun UserRow(app: TikTokApp, user: TikTokSearchUser) {
    val followers = user.followerCount.takeIf { it > 0 }?.let { countText(it) }
    ListItem(
        leadingContent = { TikTokAvatar(user.avatarUrl, user.displayName, user.uniqueId, 44.dp) },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(user.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (user.verified) VerifiedMark(16.dp)
            }
        },
        supportingContent = { Text(if (followers == null) "@${user.uniqueId}" else "@${user.uniqueId} · $followers", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clickable { app.openProfile(user.uniqueId) },
    )
}
