package com.aimdi.xtanative.ui.pluginstore

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.data.plugins.RunningPlugin
import com.aimdi.xtanative.store.search.RecentSearches
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.SearchPillShape
import com.aimdi.xtanative.ui.common.searchPillColors
import com.aimdi.xtanative.ui.plugins.LocalMediaSave
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.MessageText
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.back
import com.aimdi.xtanative.ui.generated.resources.plugin_not_running
import com.aimdi.xtanative.ui.generated.resources.search
import com.aimdi.xtanative.ui.generated.resources.settings
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.PluginSearch
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.search.RecentSearchesBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** The running plugin [id], following installs and uninstalls; null while it is not running. */
@Composable
internal fun runningPlugin(id: String): RunningPlugin? {
    val running by LocalPlugins.current.running.collectAsStateWithLifecycle()
    return running.firstOrNull { it.plugin.id == id }
}

/** [content], a plugin's, with a long press on a photo's tile saving it through plugin [id]'s downloads ([LocalMediaSave]). */
@Composable
internal fun SavesMediaOf(id: String, content: @Composable () -> Unit) {
    val plugins = LocalPlugins.current
    val save = remember(plugins, id) { { item: MediaItem -> plugins.download(id, item) } }
    CompositionLocalProvider(LocalMediaSave provides save, content = content)
}

/** A plugin opened on its own, from the store or pinned: its client, else its home tab, under its name. */
@Composable
fun PluginClientScreen(id: String, onBack: () -> Unit, onSearch: () -> Unit, onSettings: () -> Unit) {
    val plugin = runningPlugin(id)?.plugin
    PluginScaffold(
        title = plugin?.title(currentLanguage()) ?: id,
        onBack = onBack,
        actions = {
            if (plugin?.search != null) IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, stringResource(Res.string.search)) }
            if (plugin?.settings != null) IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, stringResource(Res.string.settings)) }
        },
    ) { padding ->
        val body = plugin?.clientTab ?: plugin?.homeTab
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            if (body != null) SavesMediaOf(id) { body(PluginFrame(PaddingValues(bottom = padding.calculateBottomPadding()))) } else NotRunning()
        }
    }
}

/** A plugin's settings, under its settings' title ("Booru settings"; its name for most). */
@Composable
fun PluginSettingsScreen(id: String, onBack: () -> Unit) {
    val plugin = runningPlugin(id)?.plugin
    val language = currentLanguage()
    val title = plugin?.let { runCatching { it.settingsTitle(language) }.getOrNull() ?: it.title(language) } ?: id
    PluginScaffold(title = title, onBack = onBack) { padding ->
        val body = plugin?.settings
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            if (body != null) body(PluginFrame(PaddingValues(bottom = padding.calculateBottomPadding()))) else NotRunning()
        }
    }
}

/** One of a plugin's own screens: the plugin draws it whole, its bar included. */
@Composable
fun PluginRouteScreen(id: String, route: String, onBack: () -> Unit) {
    val plugin = runningPlugin(id)?.plugin
    val screen = remember(plugin, route) { plugin?.screen(route) }
    if (screen != null) {
        SavesMediaOf(id) { screen() }
    } else {
        PluginScaffold(title = plugin?.title(currentLanguage()) ?: id, onBack = onBack) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) { NotRunning() }
        }
    }
}

/**
 * A plugin's search: the app's field above; below, what the plugin finds for
 * the query sent, and until one is, what it lands on ([PluginSearch.Landing]),
 * under its recent searches where they are the app's.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginSearchScreen(id: String, initial: String?, onBack: () -> Unit) {
    val search = runningPlugin(id)?.plugin?.search
    var text by rememberSaveable { mutableStateOf(initial.orEmpty()) }
    var sent by rememberSaveable { mutableStateOf(initial.orEmpty().trim()) }
    var told by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val recents = rememberSearchRecents(id, search)
    val send: (String) -> Unit = { query ->
        keyboard?.hide()
        text = query
        sent = query.trim()
        recents.sent(sent)
    }
    LaunchedEffect(Unit) {
        if (initial.isNullOrBlank()) runCatching { focus.requestFocus() } else if (!told) recents.sent(sent)
        told = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SearchBar(text, search, { text = it }, { send(text) }, focus, onBack) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            val frame = PluginFrame(PaddingValues(bottom = padding.calculateBottomPadding()))
            when {
                search == null -> NotRunning()
                sent.isNotEmpty() -> SavesMediaOf(id) { search.Results(sent, frame) }
                else -> Column(Modifier.fillMaxSize()) {
                    recents.Bar(send)
                    Box(Modifier.weight(1f)) { SavesMediaOf(id) { search.Landing(frame, send) } }
                }
            }
        }
    }
}

/** The search screen's bar: back, and the field with the plugin's placeholder, sending [onSend] from the keyboard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(text: String, search: PluginSearch?, onText: (String) -> Unit, onSend: () -> Unit, focus: FocusRequester, onBack: () -> Unit) {
    val language = currentLanguage()
    TopAppBar(
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) } },
        title = {
            TextField(
                value = text,
                onValueChange = onText,
                singleLine = true,
                placeholder = { Text(search?.let { runCatching { it.hint(language) }.getOrNull() } ?: stringResource(Res.string.search)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSend() }),
                shape = SearchPillShape,
                colors = searchPillColors(),
                modifier = Modifier.fillMaxWidth().padding(end = 12.dp).focusRequester(focus),
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/**
 * Where a plugin's search screen keeps what is sent: the plugin's own
 * history ([PluginSearch.sent]), or the app's recent searches under its id
 * where they are the app's ([PluginSearch.sharesRecentSearches]), listed in
 * [Bar] while nothing is sent.
 */
private class SearchRecents(
    private val id: String,
    private val search: PluginSearch?,
    private val shared: Boolean,
    private val recents: RecentSearches,
    private val scope: CoroutineScope,
) {
    fun sent(query: String) {
        val search = search
        if (query.isEmpty() || search == null) return
        runCatching { search.sent(query) }
        if (shared) scope.launch { recents.remember(id, query) }
    }

    @Composable
    fun Bar(send: (String) -> Unit) {
        if (!shared) return
        val recent by remember(this) { recents.of(id) }.collectAsStateWithLifecycle(emptyList())
        RecentSearchesBar(recent, onSelect = send, onRemove = { scope.launch { recents.remove(id, it) } }, onClear = { scope.launch { recents.clear(id) } })
    }
}

@Composable
private fun rememberSearchRecents(id: String, search: PluginSearch?): SearchRecents {
    val recents = LocalGraph.current.recentSearches
    val scope = rememberCoroutineScope()
    return remember(id, search, recents, scope) {
        SearchRecents(id, search, search?.let { runCatching { it.sharesRecentSearches }.getOrDefault(false) } == true, recents, scope)
    }
}

@Composable
private fun NotRunning() {
    MessageText(stringResource(Res.string.plugin_not_running))
}
