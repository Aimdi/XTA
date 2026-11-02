package com.aimdi.xtanative.ui.search

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.core.format.Formats
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.ProblemRow
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.back
import com.aimdi.xtanative.ui.generated.resources.search_go_to
import com.aimdi.xtanative.ui.generated.resources.search_latest
import com.aimdi.xtanative.ui.generated.resources.search_nothing
import com.aimdi.xtanative.ui.generated.resources.search_top
import com.aimdi.xtanative.ui.generated.resources.search_x
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.post.ThreadRows
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(initial: String?, actions: PostActions, onBack: () -> Unit) {
    val graph = LocalGraph.current
    val vm: SearchViewModel = viewModel { SearchViewModel(graph, initial) }
    val state by vm.ui.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf(initial.orEmpty()) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val list = rememberLazyListState()
    val nearEnd by remember(list) {
        derivedStateOf {
            val info = list.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 5
        }
    }
    LaunchedEffect(nearEnd, state.results.size) { if (nearEnd) vm.loadMore() }
    LaunchedEffect(Unit) { if (initial.isNullOrBlank()) focus.requestFocus() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) }
                    },
                    title = {
                        TextField(
                            value = text,
                            onValueChange = { text = it },
                            singleLine = true,
                            placeholder = { Text(stringResource(Res.string.search_x)) },
                            leadingIcon = { Icon(Icons.Filled.Search, null) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                keyboard?.hide()
                                vm.search(text)
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
                if (state.searched) {
                    TabRow(selectedTabIndex = state.tab.ordinal, containerColor = MaterialTheme.colorScheme.background) {
                        SearchTab.entries.forEach { tab ->
                            Tab(
                                selected = state.tab == tab,
                                onClick = { vm.select(tab) },
                                text = { Text(stringResource(if (tab == SearchTab.TOP) Res.string.search_top else Res.string.search_latest)) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = list,
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 48.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            val handle = Formats.handleOf(text)
            if (handle != null) item(key = "go-to") { GoToRow(handle) { actions.openProfile(handle) } }
            val problem = state.problem
            when {
                state.loading -> item(key = "loading") { LoadingRow() }
                problem != null -> item(key = "problem") { ProblemRow(problem, { vm.search(state.query) }) }
                state.searched && state.results.isEmpty() -> item(key = "empty") {
                    Text(
                        stringResource(Res.string.search_nothing, state.query),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
            }
            items(state.results, key = { it.id }, contentType = { "thread" }) { thread -> ThreadRows(thread, actions) }
            item(key = "footer") {
                val more = state.moreProblem
                when {
                    state.loadingMore -> LoadingRow()
                    more != null -> ProblemRow(more, vm::loadMore)
                }
            }
        }
    }
}

/** A profile by its handle: it opens without a search, which needs an X account. */
@Composable
private fun GoToRow(handle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(Res.string.search_go_to, handle), modifier = Modifier.padding(start = 16.dp))
    }
}
