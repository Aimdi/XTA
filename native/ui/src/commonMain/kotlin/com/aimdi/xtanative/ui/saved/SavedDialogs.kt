package com.aimdi.xtanative.ui.saved

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.core.x.model.XConversation
import com.aimdi.xtanative.store.saved.SavedFolder
import com.aimdi.xtanative.store.saved.SavedPost
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.cancel
import com.aimdi.xtanative.ui.generated.resources.close
import com.aimdi.xtanative.ui.generated.resources.saved_auto_download
import com.aimdi.xtanative.ui.generated.resources.saved_auto_download_description
import com.aimdi.xtanative.ui.generated.resources.saved_checking
import com.aimdi.xtanative.ui.generated.resources.saved_create
import com.aimdi.xtanative.ui.generated.resources.saved_delete
import com.aimdi.xtanative.ui.generated.resources.saved_delete_folder
import com.aimdi.xtanative.ui.generated.resources.saved_delete_folder_description
import com.aimdi.xtanative.ui.generated.resources.saved_delete_selected
import com.aimdi.xtanative.ui.generated.resources.saved_delete_selected_title
import com.aimdi.xtanative.ui.generated.resources.saved_deleted_found
import com.aimdi.xtanative.ui.generated.resources.saved_edit_folder
import com.aimdi.xtanative.ui.generated.resources.saved_find_deleted
import com.aimdi.xtanative.ui.generated.resources.saved_folder_name
import com.aimdi.xtanative.ui.generated.resources.saved_manage_folders
import com.aimdi.xtanative.ui.generated.resources.saved_new_folder
import com.aimdi.xtanative.ui.generated.resources.saved_none_deleted
import com.aimdi.xtanative.ui.generated.resources.saved_rename
import com.aimdi.xtanative.ui.generated.resources.saved_save
import com.aimdi.xtanative.ui.generated.resources.saved_some_unchecked
import com.aimdi.xtanative.ui.generated.resources.saved_tab_unfiled
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** A question with one way to go on, [confirm], and Cancel. */
@Composable
fun ConfirmDialog(title: String, text: String?, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = text?.let { { Text(it) } },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

/**
 * A new folder, or [existing] renamed (folder_picker.dart): its name, and
 * whether filing a post in it downloads its pictures.
 */
@Composable
fun FolderDialog(existing: SavedFolder?, onDismiss: () -> Unit, onDone: (name: String, autoDownload: Boolean) -> Unit) {
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var autoDownload by rememberSaveable { mutableStateOf(existing?.autoDownload ?: false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val submit = { if (name.isNotBlank()) onDone(name.trim(), autoDownload) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) Res.string.saved_new_folder else Res.string.saved_edit_folder)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(Res.string.saved_folder_name)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).clickable(role = Role.Switch) { autoDownload = !autoDownload }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(stringResource(Res.string.saved_auto_download), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(Res.string.saved_auto_download_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = autoDownload, onCheckedChange = null)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = name.isNotBlank()) {
                Text(stringResource(if (existing == null) Res.string.saved_create else Res.string.saved_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

/** Deleting a folder moves its posts to Unfiled. */
@Composable
fun DeleteFolderDialog(onDismiss: () -> Unit, onDelete: () -> Unit) {
    ConfirmDialog(
        title = stringResource(Res.string.saved_delete_folder),
        text = stringResource(Res.string.saved_delete_folder_description),
        confirm = stringResource(Res.string.saved_delete),
        onDismiss = onDismiss,
        onConfirm = onDelete,
    )
}

/** What a long press on a folder's chip offers: rename it, delete it, or manage every folder. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FolderMenuSheet(onRename: () -> Unit, onDelete: () -> Unit, onManage: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 16.dp)) {
            SheetRow(Icons.Filled.Edit, stringResource(Res.string.saved_rename), onRename)
            SheetRow(Icons.Filled.Delete, stringResource(Res.string.saved_delete), onDelete)
            SheetRow(SavedIcons.Folder, stringResource(Res.string.saved_manage_folders), onManage)
        }
    }
}

@Composable
private fun SheetRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp),
    )
}

/** Where the selected posts go: out of every folder, or into one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoveSheet(folders: List<SavedFolder>, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.padding(bottom = 16.dp)) {
            item(key = "unfiled") { SheetRow(SavedIcons.NoFolder, stringResource(Res.string.saved_tab_unfiled)) { onPick(null) } }
            items(folders, key = { it.id }) { folder -> SheetRow(SavedIcons.Folder, folder.name) { onPick(folder.id) } }
        }
    }
}

/** Removing the selected posts from the device asks first, saying how many. */
@Composable
internal fun RemoveSelectedDialog(count: Int, onDismiss: () -> Unit, onRemove: () -> Unit) {
    ConfirmDialog(
        title = stringResource(Res.string.saved_delete_selected_title),
        text = pluralStringResource(Res.plurals.saved_delete_selected, count, count),
        confirm = stringResource(Res.string.saved_delete),
        onDismiss = onDismiss,
        onConfirm = onRemove,
    )
}

/**
 * Find deleted posts (saved_cleanup.dart): each saved X post is read again,
 * gently, and those X no longer has are listed to remove; a rate limit stops
 * the scan, keeping what it did not reach.
 */
@Composable
internal fun CleanupDialog(posts: List<SavedPost>, onDismiss: () -> Unit) {
    val graph = LocalGraph.current
    val items = remember { posts.toList() }
    var scan by remember { mutableStateOf(CleanupScan(items.size)) }
    LaunchedEffect(items) {
        for (post in items) {
            val check = SavedCleanup.check(post) { id -> XConversation.of(graph.client.conversation(id), id) }
            scan = scan.after(post, check)
            if (scan.done) break
            if (SavedCleanup.isXPost(post)) delay(SavedCleanup.PAUSE_MS)
        }
        if (!scan.done) scan = scan.finished()
    }
    val done = scan.done
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(Res.string.saved_find_deleted)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (!done) {
                    LinearProgressIndicator(progress = { scan.fraction }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.size(16.dp))
                    Text("${stringResource(Res.string.saved_checking)} (${scan.checked} / ${scan.total})")
                } else {
                    Text(stringResource(if (scan.broken.isEmpty()) Res.string.saved_none_deleted else Res.string.saved_deleted_found))
                    scan.broken.forEach { post ->
                        Text(SavedCleanup.label(post), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.heightIn(min = 32.dp).padding(top = 8.dp))
                    }
                    if (scan.unreachable > 0) {
                        Text(
                            stringResource(Res.string.saved_some_unchecked),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (done && scan.broken.isNotEmpty()) {
                TextButton(onClick = {
                    val ids = scan.broken.map { it.id }
                    graph.keep { graph.saved.remove(ids) }
                    onDismiss()
                }) { Text("${stringResource(Res.string.saved_delete)} (${scan.broken.size})") }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(if (done) Res.string.close else Res.string.cancel)) }
            }
        },
        dismissButton = if (done && scan.broken.isNotEmpty()) {
            { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } }
        } else {
            null
        },
    )
}
