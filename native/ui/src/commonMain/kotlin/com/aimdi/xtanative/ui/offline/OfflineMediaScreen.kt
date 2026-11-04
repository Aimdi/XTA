package com.aimdi.xtanative.ui.offline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.store.offline.OfflineEntry
import com.aimdi.xtanative.store.offline.OfflineFile
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.back
import com.aimdi.xtanative.ui.generated.resources.offline_media_label
import com.aimdi.xtanative.ui.generated.resources.offline_unavailable
import com.aimdi.xtanative.ui.saved.ZoomableImage
import java.io.File
import org.jetbrains.compose.resources.stringResource

/** The kept pictures and videos of a saved post, one per page (offline_media_screen.dart); a video plays only on its own page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineMediaScreen(id: String, onBack: () -> Unit) {
    val graph = LocalGraph.current
    val state by graph.offline.state.collectAsStateWithLifecycle()
    LaunchedEffect(graph) { graph.offline.load() }
    val entry = state.entry(id)
    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text(entry?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(Color.Black)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                entry == null || entry.files.isEmpty() -> Unavailable()
                else -> Pages(entry)
            }
        }
    }
}

@Composable
private fun Pages(entry: OfflineEntry) {
    val pager = rememberPagerState { entry.files.size }
    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { entry.files[it].name }) { page ->
        val label = stringResource(Res.string.offline_media_label, page + 1)
        Box(Modifier.fillMaxSize().semantics { contentDescription = label }) {
            MediaPage(entry, entry.files[page], active = pager.currentPage == page)
        }
    }
}

/** One kept file, read back from the library; it says so when it is no longer there whole. */
@Composable
private fun MediaPage(entry: OfflineEntry, media: OfflineFile, active: Boolean) {
    val graph = LocalGraph.current
    var file by remember(media.name) { mutableStateOf<File?>(null) }
    var read by remember(media.name) { mutableStateOf(false) }
    LaunchedEffect(media.name) {
        file = graph.offline.mediaFile(entry, media)
        read = true
    }
    val kept = file
    when {
        !read -> Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
        kept == null -> Unavailable()
        media.video -> OfflineVideo(kept, active, Modifier.fillMaxSize())
        else -> ZoomableImage(kept, Modifier.fillMaxSize())
    }
}

@Composable
private fun Unavailable() {
    Box(Modifier.fillMaxSize()) {
        Text(stringResource(Res.string.offline_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Center))
    }
}
