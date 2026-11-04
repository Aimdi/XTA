package com.aimdi.xtanative.plugins.instagram

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.instagram.InstagramDiscovery
import com.aimdi.xtanative.networks.instagram.InstagramParser
import com.aimdi.xtanative.networks.instagram.InstagramSearchUser
import com.aimdi.xtanative.store.settings.InstagramPrefs
import com.aimdi.xtanative.ui.common.Avatar
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.plugins.EmptyPane
import com.aimdi.xtanative.ui.plugins.Fetch
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginSearch
import com.aimdi.xtanative.ui.plugins.RetryMessage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.post.PostIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Instagram in the app's search: the accounts people search finds (instagram_discover_search.dart), a typed @handle opening its profile. */
internal class InstagramSearch(private val app: () -> InstagramApp) : PluginSearch() {
    override fun hint(language: String): String = Strings.search_hint.get(language)

    @Composable
    override fun Results(query: String, frame: PluginFrame) = DiscoverResults(app(), query, frame)
}

@Composable
private fun DiscoverResults(app: InstagramApp, query: String, frame: PluginFrame) {
    val fetch = rememberKept("instagram-discover-$query") { scope -> Fetch(scope) { app.client().searchUsers(query) } }
    LaunchedEffect(fetch) { fetch.ensure() }
    val state by fetch.state.collectAsState()
    val users = state.value
    val handle = InstagramParser.handle(query)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = frame.padding) {
        handle?.let { item(key = "handle") { HandleRow(it) { app.openProfile(it) } } }
        when {
            users == null && state.problem != null -> item(key = "problem") { RetryMessage(pluginString(InstagramFailures.of(state.problem)), onRetry = { fetch.load() }) }
            users == null -> item(key = "loading") { LoadingRow() }
            users.isEmpty() -> item(key = "none") { EmptyPane(Icons.Filled.Search, pluginString(Strings.search_empty)) }
            else -> people(users) { app.openProfile(it) }
        }
    }
}

/** "@handle — Open profile". */
@Composable
private fun HandleRow(handle: String, onOpen: () -> Unit) {
    ListItem(
        leadingContent = { Icon(Icons.Filled.Person, contentDescription = null) },
        headlineContent = { Text("@$handle") },
        supportingContent = { Text(pluginString(Strings.open_handle)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onOpen),
    )
}

/** One row per account found: its face, handle and name, a lock when private. */
private fun LazyListScope.people(users: List<InstagramSearchUser>, onOpen: (String) -> Unit) {
    items(users.distinctBy { it.username }, key = { "user:" + it.username }) { user ->
        ListItem(
            leadingContent = { Avatar(user.avatarUrl, 40.dp) },
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("@" + user.username, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (user.isVerified) Icon(XIcons.Verified, contentDescription = null, tint = Color.Unspecified, modifier = Modifier.padding(start = 4.dp).size(16.dp))
                }
            },
            supportingContent = user.fullName.takeIf { it.isNotEmpty() }?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            trailingContent = if (user.isPrivate) { { Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp)) } } else null,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { onOpen(user.username) },
        )
    }
}

/** What a search in the sheet stands at. */
private sealed interface SheetSearch {
    data object None : SheetSearch
    data object Searching : SheetSearch
    data class Found(val users: List<InstagramSearchUser>) : SheetSearch
    data class Failed(val error: Throwable) : SheetSearch
}

/**
 * XTA's search sheet (instagram_search_sheet.dart): a guest's typed @handle
 * opens its profile, as a guest cannot search people; with a session, names
 * and handles search people, a failure with a handle typed opening it
 * still. Before a search, the recent ones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InstagramSearchSheet(app: InstagramApp, initial: String?, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial.orEmpty()) }
    var search by remember { mutableStateOf<SheetSearch>(SheetSearch.None) }
    val settings by app.prefs.collectAsState()
    val guest = !app.hasSession(settings)
    val scope = rememberCoroutineScope()
    val open = { handle: String -> onDismiss(); app.openProfile(handle) }
    val run = { typed: String ->
        val query = typed.trim()
        val handle = InstagramParser.handle(query)
        when {
            query.isEmpty() -> Unit
            InstagramDiscovery.opensHandle(!guest, query) && handle != null -> { app.remember(handle); open(handle) }
            else -> {
                search = SheetSearch.Searching
                scope.launch {
                    search = try {
                        SheetSearch.Found(app.client().searchUsers(query)).also { app.remember(query) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (handle != null) {
                            app.remember(handle)
                            open(handle)
                        }
                        SheetSearch.Failed(e)
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { if (initial?.isNotBlank() == true) run(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.7f).imePadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text(pluginString(Strings.search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = { IconButton(onClick = { run(text) }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = pluginString(Strings.search)) } },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { run(text) }),
                modifier = Modifier.fillMaxWidth(),
            )
            if (search == SheetSearch.Searching) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (guest) Text(pluginString(Strings.search_guest_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
            InstagramParser.handle(text)?.let { handle -> HandleRow(handle) { open(handle) } }
            Box(Modifier.weight(1f)) { SheetBody(app, search, onOpen = open, onRetry = { run(text) }) { recent -> text = recent; run(recent) } }
        }
    }
}

@Composable
private fun SheetBody(app: InstagramApp, search: SheetSearch, onOpen: (String) -> Unit, onRetry: () -> Unit, onRecent: (String) -> Unit) {
    when (search) {
        is SheetSearch.Found -> if (search.users.isEmpty()) Recent(app, onRecent) else LazyColumn(Modifier.fillMaxSize()) { people(search.users, onOpen) }
        is SheetSearch.Failed -> RetryMessage(pluginString(InstagramFailures.of(search.error)), onRetry = onRetry)
        SheetSearch.Searching -> Unit
        SheetSearch.None -> Recent(app, onRecent)
    }
}

/** The recent searches, cleared with Delete; without any, what can be searched. */
@Composable
private fun Recent(app: InstagramApp, onRecent: (String) -> Unit) {
    val settings by app.prefs.collectAsState()
    val history = settings[InstagramPrefs.searchHistory]
    if (history.isEmpty()) {
        Text(pluginString(Strings.search_empty), modifier = Modifier.padding(top = 16.dp))
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "title") {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(pluginString(Strings.recent_searches), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = app::clearHistory) { Text(pluginString(Strings.delete)) }
            }
        }
        items(history, key = { "recent:$it" }) { query ->
            ListItem(
                leadingContent = { Icon(PostIcons.History, contentDescription = null) },
                headlineContent = { Text(query) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onRecent(query) },
            )
        }
    }
}
