package com.aimdi.xtanative.ui.offline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.store.offline.OfflineAvailability
import com.aimdi.xtanative.store.offline.OfflineEntry
import com.aimdi.xtanative.store.offline.OfflineStore
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.currentLocale
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.back
import com.aimdi.xtanative.ui.generated.resources.cancel
import com.aimdi.xtanative.ui.generated.resources.offline_article_detail
import com.aimdi.xtanative.ui.generated.resources.offline_available
import com.aimdi.xtanative.ui.generated.resources.offline_clear
import com.aimdi.xtanative.ui.generated.resources.offline_clear_description
import com.aimdi.xtanative.ui.generated.resources.offline_clear_question
import com.aimdi.xtanative.ui.generated.resources.offline_empty
import com.aimdi.xtanative.ui.generated.resources.offline_media_count
import com.aimdi.xtanative.ui.generated.resources.offline_partial
import com.aimdi.xtanative.ui.generated.resources.offline_remove
import com.aimdi.xtanative.ui.generated.resources.offline_sensitive_description
import com.aimdi.xtanative.ui.generated.resources.offline_sensitive_open
import com.aimdi.xtanative.ui.generated.resources.offline_show
import com.aimdi.xtanative.ui.generated.resources.offline_storage_error
import com.aimdi.xtanative.ui.generated.resources.offline_storage_limit
import com.aimdi.xtanative.ui.generated.resources.offline_storage_used
import com.aimdi.xtanative.ui.generated.resources.offline_title
import com.aimdi.xtanative.ui.generated.resources.offline_unavailable
import com.aimdi.xtanative.ui.generated.resources.retry
import com.aimdi.xtanative.ui.generated.resources.saved_open_in_browser
import com.aimdi.xtanative.ui.saved.KeepNotice
import com.aimdi.xtanative.ui.saved.KeepNotices
import com.aimdi.xtanative.ui.saved.SavedIcons
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The offline collection (offline_library_screen.dart): how much of its
 * storage is used, and the articles and saved posts' media kept to read
 * without the network. An entry opens its media (a sensitive one once the
 * reader agrees) or its article in [openArticle]; its menu removes it or
 * opens the original.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineLibraryScreen(onBack: () -> Unit, openMedia: (id: String) -> Unit, openArticle: (id: String) -> Unit) {
    val graph = LocalGraph.current
    val store = graph.offline
    val state by store.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var clearing by remember { mutableStateOf(false) }
    var revealing by remember { mutableStateOf<OfflineEntry?>(null) }
    LaunchedEffect(store) { store.load() }

    val open: (OfflineEntry) -> Unit = { entry ->
        when (OfflineRules.open(entry)) {
            OfflineOpen.NOTHING -> Unit
            OfflineOpen.MEDIA -> openMedia(entry.id)
            OfflineOpen.SENSITIVE_MEDIA -> revealing = entry
            // An article that no longer reads says so, and the library is read again from disk.
            OfflineOpen.ARTICLE -> {
                scope.launch {
                    if (store.article(entry.id) != null) {
                        openArticle(entry.id)
                    } else {
                        KeepNotices.show(KeepNotice.OfflineUnavailable)
                        store.refresh()
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.offline_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) } },
                actions = {
                    IconButton(onClick = { scope.launch { store.refresh() } }, enabled = !state.loading) { Icon(Icons.Filled.Refresh, stringResource(Res.string.retry)) }
                    IconButton(onClick = { clearing = true }, enabled = OfflineRules.canClear(state)) { Icon(SavedIcons.DeleteSweep, stringResource(Res.string.offline_clear)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            StorageHeader(state.bytes)
            if (state.storageError) {
                Text(stringResource(Res.string.offline_storage_error), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            }
            if (state.busy.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
            Box(Modifier.weight(1f)) {
                when {
                    state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.entries.isEmpty() -> Text(
                        stringResource(Res.string.offline_empty),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                    else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 16.dp)) {
                        items(state.entries, key = { it.id }) { entry ->
                            EntryRow(entry, onOpen = { open(entry) }, onRemove = { scope.launch { store.remove(entry.id) } })
                            Hairline()
                        }
                    }
                }
            }
        }
    }
    if (clearing) {
        AlertDialog(
            onDismissRequest = { clearing = false },
            title = { Text(stringResource(Res.string.offline_clear_question)) },
            text = { Text(stringResource(Res.string.offline_clear_description)) },
            confirmButton = { Button(onClick = { clearing = false; scope.launch { store.clear() } }) { Text(stringResource(Res.string.offline_clear)) } },
            dismissButton = { TextButton(onClick = { clearing = false }) { Text(stringResource(Res.string.cancel)) } },
        )
    }
    revealing?.let { entry ->
        AlertDialog(
            onDismissRequest = { revealing = null },
            title = { Text(stringResource(Res.string.offline_sensitive_open)) },
            text = { Text(stringResource(Res.string.offline_sensitive_description)) },
            confirmButton = { Button(onClick = { revealing = null; openMedia(entry.id) }) { Text(stringResource(Res.string.offline_show)) } },
            dismissButton = { TextButton(onClick = { revealing = null }) { Text(stringResource(Res.string.cancel)) } },
        )
    }
}

/** "4.2 MiB of 500 MiB used", the bar, and the limit. */
@Composable
private fun StorageHeader(bytes: Long) {
    val locale = currentLocale()
    val limit = OfflineStore.DEFAULT_STORAGE_LIMIT
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)) {
        Text(
            stringResource(Res.string.offline_storage_used, OfflineRules.bytes(bytes, locale), OfflineRules.bytes(limit, locale)),
            style = MaterialTheme.typography.titleMedium,
        )
        LinearProgressIndicator(progress = { OfflineRules.fullness(bytes, limit) }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        Text(stringResource(Res.string.offline_storage_limit, OfflineRules.bytes(limit, locale)), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EntryRow(entry: OfflineEntry, onOpen: () -> Unit, onRemove: () -> Unit) {
    val uri = LocalUriHandler.current
    val locale = currentLocale()
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().let { if (entry.canOpen) it.clickable(onClick = onOpen) else it }.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when (OfflineRules.icon(entry)) {
                OfflineIcon.SENSITIVE -> SavedIcons.VisibilityOff
                OfflineIcon.ARTICLE -> SavedIcons.Article
                OfflineIcon.MEDIA -> SavedIcons.PermMedia
            },
            contentDescription = null,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(
                entry.source + "\n" + statusText(entry.availability) + " · " + OfflineRules.bytes(entry.bytes, locale) + "\n" + detailText(OfflineRules.detail(entry)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = null) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(Res.string.offline_remove)) }, onClick = { menu = false; onRemove() })
                entry.url?.let { url ->
                    DropdownMenuItem(text = { Text(stringResource(Res.string.saved_open_in_browser)) }, onClick = { menu = false; runCatching { uri.openUri(url) } })
                }
            }
        }
    }
}

/** Available offline, partly, or not at all. */
@Composable
fun statusText(availability: OfflineAvailability): String = when (availability) {
    OfflineAvailability.AVAILABLE -> stringResource(Res.string.offline_available)
    OfflineAvailability.PARTIAL -> stringResource(Res.string.offline_partial)
    OfflineAvailability.UNAVAILABLE -> stringResource(Res.string.offline_unavailable)
}

/** How much of an entry was kept: an article's text and pictures, or a post's media files. */
@Composable
fun detailText(detail: OfflineDetail): String =
    if (detail.article) stringResource(Res.string.offline_article_detail, detail.saved, detail.total)
    else stringResource(Res.string.offline_media_count, detail.saved, detail.total)
