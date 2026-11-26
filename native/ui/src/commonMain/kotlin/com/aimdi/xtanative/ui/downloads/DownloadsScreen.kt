package com.aimdi.xtanative.ui.downloads

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.data.LocalGraph
import com.aimdi.xtanative.services.util.formatByteSize
import com.aimdi.xtanative.store.downloads.DownloadEntry
import com.aimdi.xtanative.store.downloads.DownloadStatus
import com.aimdi.xtanative.store.settings.MediaPrefs
import com.aimdi.xtanative.ui.common.ColumnTopAppBar
import com.aimdi.xtanative.ui.common.EmptyState
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.IconTip
import com.aimdi.xtanative.ui.common.TipText
import com.aimdi.xtanative.ui.common.TopBarBack
import com.aimdi.xtanative.ui.common.WithReadingColumn
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.cancel
import com.aimdi.xtanative.ui.generated.resources.downloads_cancelled
import com.aimdi.xtanative.ui.generated.resources.downloads_choose_location
import com.aimdi.xtanative.ui.generated.resources.downloads_clear_finished
import com.aimdi.xtanative.ui.generated.resources.downloads_completed
import com.aimdi.xtanative.ui.generated.resources.downloads_downloading
import com.aimdi.xtanative.ui.generated.resources.downloads_empty
import com.aimdi.xtanative.ui.generated.resources.downloads_failed
import com.aimdi.xtanative.ui.generated.resources.downloads_history_unavailable
import com.aimdi.xtanative.ui.generated.resources.downloads_interrupted
import com.aimdi.xtanative.ui.generated.resources.downloads_interrupted_desktop
import com.aimdi.xtanative.ui.generated.resources.downloads_open_file
import com.aimdi.xtanative.ui.generated.resources.downloads_progress
import com.aimdi.xtanative.ui.generated.resources.downloads_queued
import com.aimdi.xtanative.ui.generated.resources.downloads_retry_hint
import com.aimdi.xtanative.ui.generated.resources.downloads_saving
import com.aimdi.xtanative.ui.generated.resources.downloads_show_in_folder
import com.aimdi.xtanative.ui.generated.resources.downloads_title
import com.aimdi.xtanative.ui.generated.resources.retry
import com.aimdi.xtanative.ui.saved.KeepNotice
import com.aimdi.xtanative.ui.saved.KeepNotices
import com.aimdi.xtanative.ui.saved.SavedIcons
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import com.aimdi.xtanative.ui.settings.isDesktop

/**
 * Downloads (downloads_screen.dart): the ones running or waiting first,
 * oldest first, then the ended ones, newest first, each with its progress
 * and what can be done with it (cancel, try again, open the file), and
 * Clear completed history. A history that could not be saved says so.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val graph = LocalGraph.current
    val queue = graph.downloads
    val state by queue.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val showInFolder = rememberShowInFolder()
    LaunchedEffect(queue) {
        queue.initialize()
        queue.setConcurrency(graph.settings.load()[MediaPrefs.downloadConcurrency])
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ColumnTopAppBar(
                title = { Text(stringResource(Res.string.downloads_title)) },
                navigationIcon = { TopBarBack(onBack) },
                actions = {
                    IconTip(stringResource(Res.string.downloads_clear_finished)) { IconButton(onClick = { scope.launch { queue.clearFinished() } }, enabled = hasClearableHistory(state.entries)) {
                        Icon(SavedIcons.PlaylistRemove, stringResource(Res.string.downloads_clear_finished))
                    } }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            if (state.storageError) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.downloads_history_unavailable), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { scope.launch { queue.retryHistory() } }) { Text(stringResource(Res.string.retry)) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    !state.ready -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.entries.isEmpty() -> EmptyState(SavedIcons.Download, stringResource(Res.string.downloads_empty))
                    else -> rememberLazyListState().let { readList -> WithReadingColumn(PaddingValues(bottom = padding.calculateBottomPadding() + 16.dp), readList) { column -> LazyColumn(state = readList, contentPadding = column, modifier = Modifier.fillMaxSize()) {
                        items(state.shown, key = { it.id }) { entry ->
                            DownloadRowView(
                                entry,
                                onCancel = { scope.launch { queue.cancel(entry.id) } },
                                onRetry = { scope.launch { queue.retry(entry.id) } },
                                onOpen = {
                                    scope.launch {
                                        val saved = entry.savedUri ?: return@launch
                                        if (!graph.platformFiles.open(saved, entry.fileName)) KeepNotices.show(KeepNotice.OpenFailed)
                                    }
                                },
                                // The desktop's file manager, the file picked out in its folder.
                                onShowInFolder = showInFolder?.let { show ->
                                    {
                                        scope.launch {
                                            val saved = entry.savedUri ?: return@launch
                                            if (!show(saved)) KeepNotices.show(KeepNotice.OpenFailed)
                                        }
                                    }
                                },
                            )
                            Hairline()
                        }
                    } } }
                }
            }
        }
    }
}

@Composable
private fun DownloadRowView(entry: DownloadEntry, onCancel: () -> Unit, onRetry: () -> Unit, onOpen: () -> Unit, onShowInFolder: (() -> Unit)? = null) {
    val row = DownloadRow.of(entry)
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)) {
        Icon(
            when (row.icon) {
                DownloadIcon.DONE -> SavedIcons.DownloadDone
                DownloadIcon.ERROR -> SavedIcons.ErrorOutline
                DownloadIcon.CANCELLED -> SavedIcons.Cancelled
                DownloadIcon.DOWNLOAD -> SavedIcons.Download
            },
            contentDescription = null,
            modifier = Modifier.padding(top = 4.dp, end = 12.dp).size(22.dp),
        )
        Column(Modifier.weight(1f)) {
            TipText(entry.fileName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.size(3.dp))
            Text(statusText(entry.status), style = MaterialTheme.typography.bodySmall)
            if (row.showsProgress) {
                Spacer(Modifier.size(8.dp))
                val fraction = row.fraction
                if (fraction != null) LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.size(4.dp))
                Text(
                    stringResource(Res.string.downloads_progress, formatByteSize(entry.received), entry.total?.let(::formatByteSize) ?: "—"),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (row.retryHint) Text(stringResource(Res.string.downloads_retry_hint), style = MaterialTheme.typography.bodySmall)
        }
        if (row.canCancel) IconTip(stringResource(Res.string.cancel)) { IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, stringResource(Res.string.cancel)) } }
        if (row.canRetry) IconTip(stringResource(Res.string.retry)) { IconButton(onClick = onRetry) { Icon(Icons.Filled.Refresh, stringResource(Res.string.retry)) } }
        if (row.canOpen && onShowInFolder != null) {
            IconTip(stringResource(Res.string.downloads_show_in_folder)) { IconButton(onClick = onShowInFolder) { Icon(SavedIcons.FolderOpen, stringResource(Res.string.downloads_show_in_folder)) } }
        }
        if (row.canOpen) IconTip(stringResource(Res.string.downloads_open_file)) { IconButton(onClick = onOpen) { Icon(SavedIcons.OpenInNew, stringResource(Res.string.downloads_open_file)) } }
        Spacer(Modifier.width(0.dp))
    }
}

/** What a download's row says it is doing. */
@Composable
private fun statusText(status: DownloadStatus): String = when (status) {
    DownloadStatus.QUEUED -> stringResource(Res.string.downloads_queued)
    DownloadStatus.DOWNLOADING -> stringResource(Res.string.downloads_downloading)
    DownloadStatus.CHOOSING_LOCATION -> stringResource(Res.string.downloads_choose_location)
    DownloadStatus.SAVING -> stringResource(Res.string.downloads_saving)
    DownloadStatus.COMPLETED -> stringResource(Res.string.downloads_completed)
    DownloadStatus.FAILED -> stringResource(Res.string.downloads_failed)
    DownloadStatus.CANCELLED -> stringResource(Res.string.downloads_cancelled)
    DownloadStatus.INTERRUPTED -> stringResource(if (isDesktop) Res.string.downloads_interrupted_desktop else Res.string.downloads_interrupted)
}
