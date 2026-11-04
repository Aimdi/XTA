package com.aimdi.xtanative.ui.antenna

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import com.aimdi.xtanative.ui.common.WithReadingColumn
import com.aimdi.xtanative.ui.feed.feedThreads
import com.aimdi.xtanative.ui.feed.rememberFeedLayout
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.back
import com.aimdi.xtanative.ui.generated.resources.discover_no_posts_match
import com.aimdi.xtanative.ui.generated.resources.discover_no_results
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.search.EmptyNote
import com.aimdi.xtanative.ui.search.LoadMoreNearEnd
import com.aimdi.xtanative.ui.search.pageStatus
import org.jetbrains.compose.resources.stringResource

/** An antenna's feed, as XTA shows it: the newest posts with its terms, under its name. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AntennaFeedScreen(id: String, actions: PostActions, onBack: () -> Unit) {
    val graph = LocalGraph.current
    val vm: AntennaFeedViewModel = viewModel { AntennaFeedViewModel(graph, id) }
    val antenna by vm.antenna.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val posts by vm.posts.state.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    LoadMoreNearEnd(list, posts, vm::more)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) }
                },
                title = { Text(antenna?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        when {
            !loaded -> LoadingRow(Modifier.padding(top = padding.calculateTopPadding()))
            // Deleted since it was opened.
            antenna == null -> EmptyNote(stringResource(Res.string.discover_no_results), modifier = Modifier.padding(top = padding.calculateTopPadding()))
            else -> WithReadingColumn(padding) { column ->
                val layout = rememberFeedLayout(posts.items)
                LazyColumn(state = list, contentPadding = column, modifier = Modifier.fillMaxSize()) {
                    feedThreads(posts.items, layout, actions)
                    pageStatus(posts, Res.string.discover_no_posts_match, vm::retry, vm::more)
                }
            }
        }
    }
}
