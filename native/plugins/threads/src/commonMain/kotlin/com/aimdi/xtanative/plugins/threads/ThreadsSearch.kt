package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.threads.ThreadsDiscovery
import com.aimdi.xtanative.networks.threads.ThreadsLinks
import com.aimdi.xtanative.networks.threads.ThreadsProfile
import com.aimdi.xtanative.networks.threads.ThreadsQuery
import com.aimdi.xtanative.networks.threads.ThreadsSearchTerms
import com.aimdi.xtanative.networks.threads.ThreadsSuggestion
import com.aimdi.xtanative.store.settings.ThreadsPrefs
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.plugins.PluginAvatar
import com.aimdi.xtanative.ui.plugins.Fetch
import com.aimdi.xtanative.ui.plugins.MessageText
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginSearch
import com.aimdi.xtanative.ui.plugins.RetryMessage
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.post.PostIcons

/** Threads in the app's search: what a query names, by the rules of XTA's search sheet and results page. */
internal class ThreadsSearch(private val app: () -> ThreadsApp) : PluginSearch() {
    override fun hint(language: String): String = Strings.search_posts_hint.get(language)

    @Composable
    override fun Results(query: String, frame: PluginFrame) = QueryResults(app(), query, frame)

    /** Before a search, as XTA's search sheet (threads_search_sheet.dart): the recent searches and public accounts to try. */
    @Composable
    override fun Landing(frame: PluginFrame, send: (String) -> Unit) {
        val shown = app()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(frame.padding)) {
            BeforeSearch(shown, "", onOpenProfile = { shown.openProfile(it) }, onRecent = send)
        }
    }
}

/** Opens the page [query] names: the post, the profile, or the posts a tag or words find. */
internal fun ThreadsApp.openQuery(query: ThreadsQuery) {
    val terms = query.terms
    when {
        query is ThreadsQuery.Post -> host.open(ThreadsRoutes.post(query.link))
        query is ThreadsQuery.Profile -> openProfile(query.handle)
        terms != null -> openSearch(terms)
    }
}

/** Whether people search can be asked: only a session's cookies search people; a guest opens an exact handle. */
@Composable
internal fun searchesPeople(app: ThreadsApp): Boolean {
    val settings by app.prefs.collectAsState()
    return ThreadsSessions.of(settings).hasCookies
}

/** Whether [query] lists accounts rather than opening a page: an @handle or words, with a session. */
private fun listsPeople(query: ThreadsQuery, withSession: Boolean): Boolean =
    withSession && (query is ThreadsQuery.Profile || query is ThreadsQuery.Words)

/**
 * What [text] finds in the app's search: with a session, the accounts an
 * @handle or words find; otherwise the post, the profile, or the posts words
 * or a tag find, each as the sheet would open it.
 */
@Composable
private fun QueryResults(app: ThreadsApp, text: String, frame: PluginFrame) {
    val query = remember(text) { ThreadsQuery.of(text) } ?: return
    LaunchedEffect(text) {
        app.ready()
        query.remembered?.let(app::remember)
    }
    val terms = query.terms
    when {
        listsPeople(query, searchesPeople(app)) -> PeopleResults(app, text, frame, app::openProfile)
        query is ThreadsQuery.Post -> SheetRow(PostIcons.Forum, pluginString(Strings.thread)) { app.openQuery(query) }
        query is ThreadsQuery.Profile -> SheetRow(Icons.Filled.Person, pluginString(Strings.open_handle, query.handle)) { app.openProfile(query.handle) }
        terms != null -> ThreadsSearchBody(app, terms, frame)
    }
}

/**
 * The accounts a session's people search finds for [text], with a way to
 * the posts the words find above them. A failed search with a handle typed
 * offers that profile.
 */
@Composable
private fun PeopleResults(app: ThreadsApp, text: String, frame: PluginFrame, onOpen: (String) -> Unit, onPosts: (ThreadsSearchTerms) -> Unit = app::openSearch) {
    val fetch = rememberKept("threads-people-$text") { scope -> Fetch(scope) { app.ready(); app.direct.searchUsers(text).items } }
    LaunchedEffect(fetch) { fetch.ensure() }
    val state by fetch.state.collectAsState()
    val users = state.value
    LaunchedEffect(users) { if (users != null) app.remember(text) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = frame.padding) {
        item(key = "posts") { SheetRow(PostIcons.Article, pluginString(Strings.search_posts)) { onPosts(ThreadsSearchTerms(text.trim(), tag = false)) } }
        when {
            users == null && state.problem != null -> item(key = "problem") { FailedPeople(text, state.problem, onOpen) { fetch.load() } }
            users == null -> item(key = "loading") { LoadingRow() }
            users.isEmpty() -> item(key = "none") { MessageText(pluginString(Strings.no_matching_accounts)) }
            else -> people(app, users, onOpen)
        }
    }
}

@Composable
private fun FailedPeople(text: String, problem: Throwable?, onOpen: (String) -> Unit, onRetry: () -> Unit) {
    ThreadsLinks.normaliseHandle(text)?.let { handle -> SheetRow(Icons.Filled.Person, pluginString(Strings.open_handle, handle)) { onOpen(handle) } }
    RetryMessage(ThreadsFailures.feedText(problem, currentLanguage()), onRetry)
}

/** One row per account found: its face, name and handle, opening its profile, with Follow or Unfollow on this device. */
internal fun LazyListScope.people(app: ThreadsApp, users: List<ThreadsProfile>, onOpen: (String) -> Unit) {
    items(users.distinctBy { it.username }, key = { "user:" + it.username }) { user ->
        val rows by app.accounts.state.collectAsState()
        val followed = ThreadsAccounts.follows(rows, user.username)
        ListItem(
            leadingContent = { PluginAvatar(user.profilePicUrl, user.displayName, user.username, 40.dp) },
            headlineContent = { Text(user.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text("@" + user.username, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingContent = {
                TextButton(onClick = { if (followed) app.unfollow(user.username) else app.follow(ThreadsAccount.of(user), force = false) }) {
                    Text(pluginString(if (followed) Strings.unfollow else Strings.follow))
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { onOpen(user.username) },
        )
        HorizontalDivider()
    }
}

/**
 * XTA's search sheet (threads_search_sheet.dart): words search public posts,
 * a #tag opens its topic, an @handle its profile and a link its post; with a
 * session, an @handle or words list the accounts they find. Before a search,
 * the profile a typed handle names, the recent searches and public accounts
 * to try.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThreadsSearchSheet(app: ThreadsApp, initial: String?, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial.orEmpty()) }
    var listed by remember { mutableStateOf<String?>(null) }
    val withSession = searchesPeople(app)
    LaunchedEffect(Unit) { app.ready() }
    val leave = { action: () -> Unit -> onDismiss(); action() }
    val search = { typed: String ->
        val query = ThreadsQuery.of(typed)
        when {
            query == null -> Unit
            listsPeople(query, withSession) -> listed = typed.trim()
            else -> {
                query.remembered?.let(app::remember)
                leave { app.openQuery(query) }
            }
        }
    }
    LaunchedEffect(Unit) { if (initial?.isNotBlank() == true) search(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.7f).imePadding()) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; listed = null },
                singleLine = true,
                placeholder = { Text(pluginString(Strings.search_posts_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = { IconButton(onClick = { search(text) }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = pluginString(Strings.search)) } },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search(text) }),
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            )
            if (!withSession) NoteText(pluginString(Strings.search_guest_posts_hint), Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
            Box(Modifier.weight(1f)) {
                val query = listed
                if (query == null) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        BeforeSearch(app, text, onOpenProfile = { handle -> app.remember(handle); leave { app.openProfile(handle) } }) { recent ->
                            text = recent
                            search(recent)
                        }
                    }
                } else {
                    SheetPeople(app, query, leave)
                }
            }
        }
    }
}

/** The sheet's people search for [query]: a failure with a handle typed opens that profile instead, as XTA's sheet does. */
@Composable
private fun SheetPeople(app: ThreadsApp, query: String, leave: (() -> Unit) -> Unit) {
    val fetch = rememberKept("threads-people-$query") { scope -> Fetch(scope) { app.ready(); app.direct.searchUsers(query).items } }
    val state by fetch.state.collectAsState()
    val handle = ThreadsLinks.normaliseHandle(query)
    LaunchedEffect(state.problem) {
        if (state.value == null && state.problem != null && handle != null) {
            app.remember(handle)
            leave { app.openProfile(handle) }
        }
    }
    PeopleResults(app, query, PluginFrame(PaddingValues()), onOpen = { leave { app.openProfile(it) } }, onPosts = { terms -> leave { app.openSearch(terms) } })
}

/** Before a search: the profile the typed handle names, the recent searches, and public accounts to try. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BeforeSearch(app: ThreadsApp, text: String, onOpenProfile: (String) -> Unit, onRecent: (String) -> Unit) {
    val settings by app.prefs.collectAsState()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ThreadsLinks.normaliseHandle(text)?.let { handle -> SheetRow(ThreadsIcons.Mark, pluginString(Strings.open_handle, handle)) { onOpenProfile(handle) } }
        ThreadsSuggestionRows(app, text) { picked -> if (picked is ThreadsSuggestion.Account) onOpenProfile(picked.handle) else onRecent(picked.text) }
        val recent = settings[ThreadsPrefs.searchHistory].take(8)
        if (recent.isNotEmpty()) {
            Text(pluginString(Strings.recent_searches), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                recent.forEach { item -> RecentChip(item, onClick = { onRecent(item) }, onForget = { app.forget(item) }) }
            }
        }
        Text(pluginString(Strings.try_public), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThreadsDiscovery.STARTER_HANDLES.forEach { handle ->
                AssistChip(
                    onClick = { onOpenProfile(handle) },
                    label = { Text("@$handle") },
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
            }
        }
    }
}

/** A recent search, and (more than XTA's Threads sheet, as its other plugins do) a cross that forgets it. */
@Composable
private fun RecentChip(query: String, onClick: () -> Unit, onForget: () -> Unit) {
    val forget = pluginString(Strings.delete) + " " + query
    InputChip(
        selected = false,
        onClick = onClick,
        label = { Text(query) },
        trailingIcon = {
            Icon(
                Icons.Filled.Close,
                contentDescription = forget,
                modifier = Modifier.size(18.dp).clickable(onClickLabel = forget, onClick = onForget),
            )
        },
    )
}
