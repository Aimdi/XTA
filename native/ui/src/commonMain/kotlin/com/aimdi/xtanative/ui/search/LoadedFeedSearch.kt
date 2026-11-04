package com.aimdi.xtanative.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.back
import com.aimdi.xtanative.ui.generated.resources.discover_loaded_hint
import com.aimdi.xtanative.ui.generated.resources.discover_loaded_title
import com.aimdi.xtanative.ui.generated.resources.discover_no_results
import com.aimdi.xtanative.ui.generated.resources.discover_search
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.post.ThreadRows
import org.jetbrains.compose.resources.stringResource

/**
 * XTA's search of a feed's loaded posts: typing filters [threads], what the
 * feed has already shown, by text, links and authors. Nothing is asked of X,
 * and scrolling loads nothing more. A feed opens it from its bar with the
 * rows it holds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoadedFeedSearchScreen(threads: List<XThread>, actions: PostActions, onBack: () -> Unit) {
    val index = remember(threads) { LoadedFeed.Index(threads) }
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(index, query) { index.matching(query) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) }
                },
                title = { Text(stringResource(Res.string.discover_loaded_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                label = { Text(stringResource(Res.string.discover_search)) },
                supportingText = { Text(stringResource(Res.string.discover_loaded_hint)) },
                modifier = Modifier.fillMaxWidth().padding(12.dp).focusRequester(focus),
            )
            if (matches.isEmpty()) {
                EmptyNote(stringResource(Res.string.discover_no_results))
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(matches, key = { it.id }, contentType = { "thread" }) { thread -> ThreadRows(thread, actions) }
                }
            }
        }
    }
}
