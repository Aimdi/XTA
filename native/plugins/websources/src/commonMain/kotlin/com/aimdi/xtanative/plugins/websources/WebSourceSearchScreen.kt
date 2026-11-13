package com.aimdi.xtanative.plugins.websources

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import com.aimdi.xtanative.networks.websources.WebSourceDefinition
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

/** The results of the latest query, as a list of their own. */
internal class SearchResults(private val app: WebSourcesApp, private val source: WebSourceDefinition, private val scope: CoroutineScope) {
    val shown = MutableStateFlow<WebSourceFeed?>(null)

    fun search(query: String) {
        val search = source.search ?: return
        if (query.isBlank()) return
        shown.value = WebSourceFeed(app.client, source, search, scope, query = query.trim())
    }
}

/** A source's search (web_source_search_screen.dart), for a source whose file names one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WebSourceSearchScreen(app: WebSourcesApp, source: WebSourceDefinition) {
    val results = rememberKept("websource-search-${source.id}") { scope -> SearchResults(app, source, scope) }
    val shown by results.shown.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(pluginString(Strings.search_in, source.name)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { results.search(query) }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                },
                navigationIcon = { BackButton(app) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            shown?.let { feed -> key(feed) { WebSourceListPane(app, feed) } }
        }
    }
}
