package com.aimdi.xtanative.ui.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.store.saved.SavedFolder
import com.aimdi.xtanative.store.saved.SavedTabs
import com.aimdi.xtanative.store.settings.SavedPrefs
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.back
import com.aimdi.xtanative.ui.generated.resources.saved_delete
import com.aimdi.xtanative.ui.generated.resources.saved_folder_count
import com.aimdi.xtanative.ui.generated.resources.saved_hide
import com.aimdi.xtanative.ui.generated.resources.saved_manage_folders
import com.aimdi.xtanative.ui.generated.resources.saved_new_folder
import com.aimdi.xtanative.ui.generated.resources.saved_show
import com.aimdi.xtanative.ui.generated.resources.saved_tab_all
import com.aimdi.xtanative.ui.generated.resources.saved_tab_likes
import com.aimdi.xtanative.ui.generated.resources.saved_tab_notes
import com.aimdi.xtanative.ui.generated.resources.saved_tab_unfiled
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Manage folders (saved_folders_screen.dart): every tab of Saved in the
 * strip's order, dragged by its handle into another; the built-in tabs shown
 * or hidden (Notes always shows), each folder with its count, renamed with a
 * tap and deleted from its row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedFoldersScreen(onBack: () -> Unit) {
    val graph = LocalGraph.current
    val library by graph.saved.state.collectAsStateWithLifecycle()
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    LaunchedEffect(graph) {
        graph.saved.library()
        graph.settings.load()
    }
    var editing by remember { mutableStateOf<SavedFolder?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SavedFolder?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.saved_manage_folders)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) } },
                actions = { IconButton(onClick = { creating = true }) { Icon(SavedIcons.NewFolder, stringResource(Res.string.saved_new_folder)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val loaded = library
        val prefs = settings
        if (loaded != null && prefs != null) {
            val rows = FolderRow.of(loaded, prefs[SavedPrefs.tabOrder])
            ReorderableRows(
                rows = rows,
                modifier = Modifier.fillMaxSize().padding(padding),
                onMoved = { from, to ->
                    val tokens = SavedTabs.moved(rows.map { it.token }, from, to)
                    graph.keep { graph.settings.set(SavedPrefs.tabOrder, tokens) }
                },
            ) { row, handle ->
                when (row) {
                    is FolderRow.BuiltIn -> BuiltInRow(row, row.shown?.let { prefs[it] }, handle) { shown ->
                        row.shown?.let { pref -> graph.keep { graph.settings.set(pref, shown) } }
                    }
                    is FolderRow.Folder -> FolderRowView(row, handle, onRename = { editing = row.folder }, onDelete = { deleting = row.folder })
                }
            }
        }
    }
    if (creating || editing != null) {
        val folder = editing
        FolderDialog(folder, onDismiss = { creating = false; editing = null }) { name, autoDownload, upload ->
            graph.keep {
                if (folder == null) graph.saved.createFolder(name, autoDownload, upload ?: false) else graph.saved.updateFolder(folder.id, name, autoDownload, upload)
            }
            creating = false
            editing = null
        }
    }
    deleting?.let { folder ->
        DeleteFolderDialog(onDismiss = { deleting = null }) {
            graph.keep { graph.saved.deleteFolder(folder.id) }
            deleting = null
        }
    }
}

/** A built-in tab: not deletable, but [shown] or hidden in Saved's strip; Notes ([shown] null) always shows. */
@Composable
private fun BuiltInRow(row: FolderRow.BuiltIn, shown: Boolean?, handle: @Composable () -> Unit, onShown: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 24.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(builtInLabel(row.token), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (shown != null) {
            IconButton(onClick = { onShown(!shown) }) {
                Icon(
                    if (shown) SavedIcons.Visibility else SavedIcons.VisibilityOff,
                    stringResource(if (shown) Res.string.saved_hide else Res.string.saved_show),
                )
            }
        }
        handle()
    }
}

@Composable
private fun builtInLabel(token: String): String = when (token) {
    SavedTabs.ALL -> stringResource(Res.string.saved_tab_all)
    SavedTabs.UNFILED -> stringResource(Res.string.saved_tab_unfiled)
    SavedTabs.FAVORITES -> stringResource(Res.string.saved_tab_likes)
    else -> stringResource(Res.string.saved_tab_notes)
}

@Composable
private fun FolderRowView(row: FolderRow.Folder, handle: @Composable () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onRename).padding(start = 24.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.folder.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(pluralStringResource(Res.plurals.saved_folder_count, row.count, row.count), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, stringResource(Res.string.saved_delete)) }
        handle()
    }
}

/**
 * [rows] that a drag on a row's handle moves among the others, the row
 * following the finger and the others making way: [onMoved] hears where it
 * was let go. The list is short (the tabs of Saved), so it is one column.
 */
@Composable
private fun ReorderableRows(
    rows: List<FolderRow>,
    modifier: Modifier,
    onMoved: (from: Int, to: Int) -> Unit,
    row: @Composable (FolderRow, handle: @Composable () -> Unit) -> Unit,
) {
    val heights = remember { mutableStateMapOf<String, Int>() }
    val current by rememberUpdatedState(rows)
    val moved by rememberUpdatedState(onMoved)
    var dragging by remember { mutableStateOf<Int?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val heightList = rows.map { heights[it.token] ?: 0 }
    val target = dragging?.let { dropIndex(it, offset, heightList) }
    Column(modifier.verticalScroll(rememberScrollState())) {
        rows.forEachIndexed { index, item ->
            val shift = shiftOf(index, dragging, target, heightList)
            Box(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { heights[item.token] = it.height }
                    .zIndex(if (index == dragging) 1f else 0f)
                    .graphicsLayer { translationY = if (index == dragging) offset else shift }
                    .background(MaterialTheme.colorScheme.background),
            ) {
                Column {
                    row(item) {
                        Icon(
                            SavedIcons.DragHandle,
                            contentDescription = null,
                            modifier = Modifier.padding(horizontal = 8.dp).pointerInput(item.token) {
                                detectDragGestures(
                                    onDragStart = { dragging = current.indexOfFirst { it.token == item.token }.takeIf { it >= 0 }; offset = 0f },
                                    onDragEnd = {
                                        val from = dragging
                                        val to = from?.let { dropIndex(it, offset, current.map { r -> heights[r.token] ?: 0 }) }
                                        dragging = null
                                        offset = 0f
                                        if (from != null && to != null && to != from) moved(from, to)
                                    },
                                    onDragCancel = { dragging = null; offset = 0f },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        offset += amount.y
                                    },
                                )
                            },
                        )
                    }
                    Hairline()
                }
            }
        }
    }
}

/** How far a row makes way for the one dragged from [from] to [to]: by that row's height, the other way. */
private fun shiftOf(index: Int, from: Int?, to: Int?, heights: List<Int>): Float {
    if (from == null || to == null || index == from) return 0f
    val moved = heights.getOrElse(from) { 0 }.toFloat()
    return when {
        from < to && index in (from + 1)..to -> -moved
        from > to && index in to until from -> moved
        else -> 0f
    }
}
