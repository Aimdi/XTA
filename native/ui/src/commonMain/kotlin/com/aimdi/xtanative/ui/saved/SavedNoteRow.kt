package com.aimdi.xtanative.ui.saved

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.store.offline.OfflineSaved
import com.aimdi.xtanative.store.saved.SavedContent
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.offline_title
import com.aimdi.xtanative.ui.generated.resources.saved_add_note
import com.aimdi.xtanative.ui.generated.resources.saved_edit_note
import com.aimdi.xtanative.ui.offline.OfflineSavedAction
import org.jetbrains.compose.resources.stringResource

/**
 * Under saved post [id]: its [note], or a button to add one, [onEdit] editing
 * it, and the offline button where [content] has media to keep, opening its
 * sheet. A plugin's own saved list draws it too ([com.aimdi.xtanative.ui.plugins.PluginSavedScreen]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SavedNoteRow(id: String, note: String?, content: SavedContent, onEdit: () -> Unit) {
    var offline by remember { mutableStateOf(false) }
    NoteLine(note, OfflineSaved.hasMedia(content), onEdit = onEdit, onOffline = { offline = true })
    if (offline) {
        ModalBottomSheet(onDismissRequest = { offline = false }) {
            OfflineSavedAction(id, content, Modifier.padding(bottom = 16.dp))
        }
    }
}

/** The note under a saved post, or a button to add one, and the offline button where there is media to keep. */
@Composable
private fun NoteLine(note: String?, offline: Boolean, onEdit: () -> Unit, onOffline: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(start = 60.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            if (!note.isNullOrEmpty()) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onEdit).padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Icon(SavedIcons.EditNote, stringResource(Res.string.saved_edit_note), tint = muted, modifier = Modifier.padding(top = 2.dp).size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(note, maxLines = 3, overflow = TextOverflow.Ellipsis, color = muted, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                TextButton(onClick = onEdit, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(SavedIcons.NoteAdd, contentDescription = null, tint = muted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.saved_add_note), color = muted)
                }
            }
        }
        if (offline) IconButton(onClick = onOffline) { Icon(SavedIcons.OfflinePinOutline, stringResource(Res.string.offline_title)) }
    }
}
