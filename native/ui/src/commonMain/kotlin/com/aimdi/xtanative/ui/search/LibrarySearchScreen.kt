package com.aimdi.xtanative.ui.search

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.data.plugins.Destination
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.back
import com.aimdi.xtanative.ui.generated.resources.discover_all
import com.aimdi.xtanative.ui.generated.resources.discover_no_results
import com.aimdi.xtanative.ui.generated.resources.discover_search_x
import com.aimdi.xtanative.ui.generated.resources.following
import com.aimdi.xtanative.ui.generated.resources.groups_groups
import com.aimdi.xtanative.ui.generated.resources.offline_title
import com.aimdi.xtanative.ui.generated.resources.retry
import com.aimdi.xtanative.ui.generated.resources.saved_note_author
import com.aimdi.xtanative.ui.generated.resources.saved_search_library
import com.aimdi.xtanative.ui.generated.resources.saved_title
import com.aimdi.xtanative.ui.pluginstore.LocalPlugins
import com.aimdi.xtanative.ui.plugins.currentLanguage
import org.jetbrains.compose.resources.stringResource

/**
 * XTA's "Search your library" (reader_search_screen.dart): the field, All and
 * a chip for each kind of thing, with the query typed X's search and each
 * plugin's a tap away, and the results, each opened in its own screen
 * through [open]. Coming back reads the library again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySearchScreen(initialQuery: String?, open: (LibraryTarget) -> Unit, onBack: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initialQuery.orEmpty()) }
    val graph = LocalGraph.current
    val vm: LibrarySearchViewModel = viewModel { LibrarySearchViewModel(graph, text) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val results = remember(state.documents, state.query, state.kind) { state.results }
    var shownBefore by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (shownBefore) vm.load() else shownBefore = true }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val title = stringResource(Res.string.saved_search_library)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) } },
                title = { Text(title) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    vm.type(it)
                },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text(title) },
                modifier = Modifier.fillMaxWidth().padding(12.dp).focusRequester(focus),
            )
            KindChips(state.kind, vm::pick)
            if (state.query.isNotBlank()) ElsewhereChips(state.query.trim(), open)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (state.saysNoResults(results)) {
                    EmptyNote(stringResource(Res.string.discover_no_results))
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(results, key = { it.id }, contentType = { "result" }) { result ->
                            ResultRow(result) {
                                vm.opening(result)
                                open(result.target)
                            }
                        }
                    }
                }
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                if (state.failed) RetryPill(vm::load, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
            }
        }
    }
}

/** All, then a chip for each kind of thing, in XTA's order. */
@Composable
private fun KindChips(picked: LibraryKind?, onPick: (LibraryKind?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterChip(selected = picked == null, onClick = { onPick(null) }, label = { Text(stringResource(Res.string.discover_all)) })
        LibraryKind.entries.forEach { kind ->
            FilterChip(selected = picked == kind, onClick = { onPick(kind) }, label = { Text(kindLabel(kind)) })
        }
    }
}

/** What was typed, searched on X or in a plugin that searches, as XTA offers it under the chips. */
@Composable
private fun ElsewhereChips(query: String, open: (LibraryTarget) -> Unit) {
    val plugins = LocalPlugins.current
    val language = currentLanguage()
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AssistChip(
            onClick = { open(LibraryTarget.Search(query)) },
            label = { Text(stringResource(Res.string.discover_search_x)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        searchablePlugins().forEach { plugin ->
            AssistChip(
                onClick = { plugins.navigator.go(Destination.PluginSearch(plugin.id, query)) },
                label = { Text(plugin.title(language)) },
                leadingIcon = { PluginChipMark(plugin) },
            )
        }
    }
}

@Composable
private fun ResultRow(result: LibraryDocument, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(result.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(kindLabel(result.kind) + " · " + LibrarySearch.preview(result.text), maxLines = 3, overflow = TextOverflow.Ellipsis)
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** A read that failed: Try again, floating over what was read. */
@Composable
private fun RetryPill(onRetry: () -> Unit, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 3.dp, modifier = modifier) {
        TextButton(onClick = onRetry, modifier = Modifier.padding(horizontal = 8.dp)) { Text(stringResource(Res.string.retry)) }
    }
}

@Composable
private fun kindLabel(kind: LibraryKind): String = stringResource(
    when (kind) {
        LibraryKind.SAVED -> Res.string.saved_title
        LibraryKind.NOTE -> Res.string.saved_note_author
        LibraryKind.ACCOUNT -> Res.string.following
        LibraryKind.GROUP -> Res.string.groups_groups
        LibraryKind.ARTICLE -> Res.string.offline_title
    },
)
