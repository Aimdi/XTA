package com.aimdi.xtanative.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimdi.xtanative.data.LocalGraph
import com.aimdi.xtanative.store.history.HistoryEntry
import com.aimdi.xtanative.store.history.HistoryKind
import com.aimdi.xtanative.ui.common.ColumnTopAppBar
import com.aimdi.xtanative.ui.common.IconTip
import com.aimdi.xtanative.ui.common.Scaffold
import com.aimdi.xtanative.ui.common.TipText
import com.aimdi.xtanative.ui.common.TopBarBack
import com.aimdi.xtanative.ui.common.WithReadingColumn
import com.aimdi.xtanative.ui.common.agoText
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.cancel
import com.aimdi.xtanative.ui.generated.resources.discover_no_results
import com.aimdi.xtanative.ui.generated.resources.history_clear
import com.aimdi.xtanative.ui.generated.resources.history_clear_ask
import com.aimdi.xtanative.ui.generated.resources.history_empty
import com.aimdi.xtanative.ui.generated.resources.history_keep
import com.aimdi.xtanative.ui.generated.resources.history_keep_description
import com.aimdi.xtanative.ui.generated.resources.history_remove
import com.aimdi.xtanative.ui.generated.resources.history_search
import com.aimdi.xtanative.ui.generated.resources.history_title
import com.aimdi.xtanative.ui.home.foundWithKeys
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.post.PostIcons
import com.aimdi.xtanative.ui.search.EmptyNote
import java.time.Instant
import org.jetbrains.compose.resources.stringResource

/**
 * History (Ctrl+H, and the account menu): the posts and profiles opened on
 * this device, newest first, found again by what they say, each opened again
 * through [actions] or forgotten; Clear history empties it, and the switch
 * at its top stops it being kept. Nothing of it leaves the device.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(actions: PostActions, onBack: () -> Unit) {
    val graph = LocalGraph.current
    val vm: HistoryViewModel = viewModel { HistoryViewModel(graph) }
    val state by vm.ui.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    var clearing by remember { mutableStateOf(false) }
    val title = stringResource(Res.string.history_title)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ColumnTopAppBar(
                navigationIcon = { TopBarBack(onBack) },
                title = { Text(title) },
                actions = { TextButton(onClick = { clearing = true }, enabled = state.total > 0) { Text(stringResource(Res.string.history_clear)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val list = rememberLazyListState()
        WithReadingColumn(PaddingValues(top = padding.calculateTopPadding()), list) { column ->
            LazyColumn(state = list, contentPadding = column, modifier = Modifier.fillMaxSize()) {
                item(key = "keep", contentType = "keep") { KeepRow(state.keep, vm::keep) }
                item(key = "search", contentType = "search") {
                    OutlinedTextField(
                        value = text,
                        onValueChange = {
                            text = it
                            vm.search(it)
                        },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        placeholder = { Text(stringResource(Res.string.history_search)) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).foundWithKeys(),
                    )
                }
                if (state.loaded && state.shown.isEmpty()) {
                    item(key = "empty", contentType = "empty") {
                        EmptyNote(stringResource(if (state.total == 0) Res.string.history_empty else Res.string.discover_no_results))
                    }
                }
                items(state.shown, key = { it.key }, contentType = { "entry" }) { entry ->
                    EntryRow(entry, onOpen = { open(entry, actions) }, onRemove = { vm.remove(entry) })
                }
            }
        }
    }
    if (clearing) {
        AlertDialog(
            onDismissRequest = { clearing = false },
            title = { Text(stringResource(Res.string.history_clear_ask)) },
            confirmButton = { TextButton(onClick = { clearing = false; vm.clear() }) { Text(stringResource(Res.string.history_clear)) } },
            dismissButton = { TextButton(onClick = { clearing = false }) { Text(stringResource(Res.string.cancel)) } },
        )
    }
}

private fun open(entry: HistoryEntry, actions: PostActions) = when (entry.kind) {
    HistoryKind.POST -> actions.openPost(entry.target)
    HistoryKind.PROFILE -> actions.openUser(entry.target, null)
}

@Composable
private fun KeepRow(keep: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(Res.string.history_keep)) },
        supportingContent = { Text(stringResource(Res.string.history_keep_description)) },
        trailingContent = { Switch(checked = keep, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clickable { onChange(!keep) },
    )
}

/** A post by its author and the start of its text, or a profile by its name and handle, with when it was opened. */
@Composable
private fun EntryRow(entry: HistoryEntry, onOpen: () -> Unit, onRemove: () -> Unit) {
    val opened = agoText(Instant.ofEpochMilli(entry.at))
    val who = listOf(entry.name, "@" + entry.handle).filter { it.isNotBlank() && it != "@" }.joinToString(" · ")
    ListItem(
        leadingContent = { Icon(if (entry.kind == HistoryKind.POST) PostIcons.Quote else Icons.Filled.Person, contentDescription = null) },
        headlineContent = { TipText(if (entry.kind == HistoryKind.POST && entry.text.isNotBlank()) entry.text else who, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = { TipText(if (entry.kind == HistoryKind.POST) "$who · $opened" else opened, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Box {
                val remove = stringResource(Res.string.history_remove)
                IconTip(remove) { IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, remove) } }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clickable(onClick = onOpen),
    )
}
