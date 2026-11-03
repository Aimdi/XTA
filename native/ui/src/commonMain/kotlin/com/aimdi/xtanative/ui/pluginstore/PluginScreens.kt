package com.aimdi.xtanative.ui.pluginstore

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.data.plugins.RunningPlugin
import com.aimdi.xtanative.ui.plugins.MessageText
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.back
import com.aimdi.xtanative.ui.generated.resources.plugin_not_running
import com.aimdi.xtanative.ui.generated.resources.search
import com.aimdi.xtanative.ui.generated.resources.settings
import com.aimdi.xtanative.ui.plugins.PluginFrame
import com.aimdi.xtanative.ui.plugins.currentLanguage
import org.jetbrains.compose.resources.stringResource

/** The running plugin [id], following installs and uninstalls; null while it is not running. */
@Composable
internal fun runningPlugin(id: String): RunningPlugin? {
    val running by LocalPlugins.current.running.collectAsStateWithLifecycle()
    return running.firstOrNull { it.plugin.id == id }
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
            if (body != null) body(PluginFrame(PaddingValues(bottom = padding.calculateBottomPadding()))) else NotRunning()
        }
    }
}

/** A plugin's settings, under its name. */
@Composable
fun PluginSettingsScreen(id: String, onBack: () -> Unit) {
    val plugin = runningPlugin(id)?.plugin
    PluginScaffold(title = plugin?.title(currentLanguage()) ?: id, onBack = onBack) { padding ->
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
        screen()
    } else {
        PluginScaffold(title = plugin?.title(currentLanguage()) ?: id, onBack = onBack) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) { NotRunning() }
        }
    }
}

/** A plugin's search: the app's field above, what the plugin finds for the query sent below. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginSearchScreen(id: String, initial: String?, onBack: () -> Unit) {
    val search = runningPlugin(id)?.plugin?.search
    val language = currentLanguage()
    var text by rememberSaveable { mutableStateOf(initial.orEmpty()) }
    var sent by rememberSaveable { mutableStateOf(initial.orEmpty().trim()) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (initial.isNullOrBlank()) runCatching { focus.requestFocus() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) } },
                title = {
                    TextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        placeholder = { Text(search?.hint(language) ?: stringResource(Res.string.search)) },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            keyboard?.hide()
                            sent = text.trim()
                        }),
                        shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(end = 12.dp).focusRequester(focus),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            when {
                search == null -> NotRunning()
                sent.isNotEmpty() -> search.Results(sent, PluginFrame(PaddingValues(bottom = padding.calculateBottomPadding())))
            }
        }
    }
}

@Composable
private fun NotRunning() {
    MessageText(stringResource(Res.string.plugin_not_running))
}
