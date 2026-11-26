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
import com.aimdi.xtanative.ui.common.IconTip
import com.aimdi.xtanative.ui.common.KeyedSheet
import com.aimdi.xtanative.ui.common.rememberSheetState
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
        KeyedSheet(sheetState = rememberSheetState(), onDismissRequest = { offline = false }) {
            OfflineSavedAction(id, content, Modifier.padding(bottom = 16.dp))
        }
    }
}

/** The note under a saved post, or a button to add one, and the offline button where there is media to keep. */
@Composable
private fun NoteLine(note: String?, offline: Boolean, onEdit: () -> Unit, onOffline: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // The note's pencil and the add button's glyph both line up with the post's text column.
    Row(Modifier.fillMaxWidth().padding(start = SavedNoteLook.ROW_START.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            if (!note.isNullOrEmpty()) {
                SavedNoteBox(note, onEdit)
            } else {
                TextButton(onClick = onEdit, modifier = Modifier.padding(start = SavedNoteLook.BUTTON_START.dp).heightIn(min = 48.dp)) {
                    Icon(SavedIcons.NoteAdd, contentDescription = null, tint = muted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.saved_add_note), color = muted)
                }
            }
        }
        if (offline) IconTip(stringResource(Res.string.offline_title)) { IconButton(onClick = onOffline) { Icon(SavedIcons.OfflinePinOutline, stringResource(Res.string.offline_title)) } }
    }
}

/**
 * A saved post's [note] as a box [onEdit] edits: the pencil beside its first
 * line, the whole centred in the 48 dp it takes at least, and far enough in
 * from the box's edges that the desktop's lit block, inset from a wide row's
 * sides, keeps a margin round them. Saved's rows and the opened post show it.
 */
@Composable
internal fun SavedNoteBox(note: String, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onEdit)
            .padding(horizontal = SavedNoteLook.PADDING.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row {
            Icon(SavedIcons.EditNote, stringResource(Res.string.saved_edit_note), tint = muted, modifier = Modifier.padding(top = 1.dp).size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(note, maxLines = 3, overflow = TextOverflow.Ellipsis, color = muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * Where a saved post's note line sits, in dp: the note box from [ROW_START]
 * with its pencil [PADDING] in, and the add button nudged [BUTTON_START] in
 * so its glyph after the button's own 12 dp lands at the same place, the
 * post's text column ([com.aimdi.xtanative.ui.post.PostGeometry.COLUMN_START]).
 */
internal object SavedNoteLook {
    const val PADDING = 16f
    const val ROW_START = 52f
    const val BUTTON_START = 4f

    /** Material's text button's own start padding. */
    const val TEXT_BUTTON_PADDING = 12f
}
