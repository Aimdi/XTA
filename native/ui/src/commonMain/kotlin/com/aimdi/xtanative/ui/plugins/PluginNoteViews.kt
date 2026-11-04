package com.aimdi.xtanative.ui.plugins

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.saved_add_note
import com.aimdi.xtanative.ui.saved.Keepable
import com.aimdi.xtanative.ui.saved.NoteEditorDialog
import com.aimdi.xtanative.ui.saved.NoteRules
import com.aimdi.xtanative.ui.saved.NoteTarget
import com.aimdi.xtanative.ui.saved.SavedIcons
import org.jetbrains.compose.resources.stringResource

/** The entry under the bookmark in a post's sheet (plugin_post_actions.dart), in Saved's words. */
@Composable
fun PluginNoteMenuRow(onClick: () -> Unit, modifier: Modifier = Modifier) =
    PluginMenuRow(SavedIcons.EditNote, stringResource(Res.string.saved_add_note), onClick, modifier)

/** The note under [item] in Saved, written in Saved's editor; keeping it saves a post not saved yet. */
@Composable
fun PluginNoteEditor(item: Keepable, onDismiss: () -> Unit) {
    val graph = LocalGraph.current
    val notes = remember(graph) { PluginNotes(graph.saved, graph.settings) }
    val target by produceState<NoteTarget.Text?>(null, item.id) {
        value = NoteTarget.Text(notes.noteOf(item.id), NoteRules.savedNoteKey(item.id))
    }
    target?.let { NoteEditorDialog(it, onDismiss, saveText = { note -> notes.keep(item, note) }) }
}
