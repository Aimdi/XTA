package com.aimdi.xtanative.ui.saved

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.aimdi.xtanative.store.saved.LocalPost
import com.aimdi.xtanative.store.saved.LocalPosts
import com.aimdi.xtanative.store.saved.NoteEditor
import com.aimdi.xtanative.store.saved.NoteMedia
import com.aimdi.xtanative.store.saved.SavedContent
import com.aimdi.xtanative.ui.common.IconTip
import com.aimdi.xtanative.ui.common.TipText
import com.aimdi.xtanative.ui.common.destructiveTextColors
import com.aimdi.xtanative.ui.common.dialogDefault
import com.aimdi.xtanative.ui.common.shortcutKey
import com.aimdi.xtanative.ui.common.tabLeavesField
import com.aimdi.xtanative.ui.downloads.PickedFile
import com.aimdi.xtanative.ui.downloads.rememberClipboardPicture
import com.aimdi.xtanative.ui.downloads.rememberFilePicker
import com.aimdi.xtanative.ui.downloads.takesDroppedFiles
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.cancel
import com.aimdi.xtanative.ui.generated.resources.close
import com.aimdi.xtanative.ui.generated.resources.saved_add_note
import com.aimdi.xtanative.ui.generated.resources.saved_discard
import com.aimdi.xtanative.ui.generated.resources.saved_discard_message
import com.aimdi.xtanative.ui.generated.resources.saved_discard_title
import com.aimdi.xtanative.ui.generated.resources.saved_draft_restored
import com.aimdi.xtanative.ui.generated.resources.saved_edit_note
import com.aimdi.xtanative.ui.generated.resources.saved_new_note
import com.aimdi.xtanative.ui.generated.resources.saved_note_attach
import com.aimdi.xtanative.ui.generated.resources.saved_note_attach_error
import com.aimdi.xtanative.ui.generated.resources.saved_note_hint
import com.aimdi.xtanative.ui.generated.resources.saved_note_on_device
import com.aimdi.xtanative.ui.generated.resources.saved_note_remove_media
import com.aimdi.xtanative.ui.generated.resources.saved_note_reply_title
import com.aimdi.xtanative.ui.generated.resources.saved_note_replying_to
import com.aimdi.xtanative.ui.generated.resources.saved_note_save_error
import com.aimdi.xtanative.ui.generated.resources.saved_notice_notes
import com.aimdi.xtanative.ui.generated.resources.saved_save
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/**
 * A note editor over the whole window (note_editor_frame.dart): a note of
 * Notes, with its files, the note it answers and the post it quotes
 * (local_post_compose.dart), or the text under a saved post or a highlight
 * (saved_note_editor.dart). What is typed is kept as a draft until it is
 * saved or discarded; closing with changes asks first. A note never leaves
 * the device. [onSaved] hears the note kept; [saveText] stores a text note.
 */
@Composable
fun NoteEditorDialog(
    target: NoteTarget,
    onDismiss: () -> Unit,
    onSaved: (LocalPost?) -> Unit = {},
    saveText: (suspend (String?) -> Unit)? = null,
) {
    val keeper = LocalKeeper.current
    val scope = rememberCoroutineScope()
    val note = target as? NoteTarget.Note
    val newId = remember { note?.editing?.id ?: UUID.randomUUID().toString() }
    val editor = remember { editorFor(target, newId, keeper) }
    val state by editor.state.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf(editor.state.value.body) }
    var confirming by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val pick = rememberFilePicker(images = false, title = stringResource(Res.string.saved_note_attach))

    LaunchedEffect(editor) {
        editor.restoreDraft()
        text = editor.state.value.body
        runCatching { focus.requestFocus() }
    }
    DisposableEffect(editor) {
        onDispose {
            editor.close()
            if (editor.state.value.discarded && note != null) dropNewFiles(keeper, editor.postId ?: newId, editor.initialMedia)
        }
    }

    val close = {
        when (NoteRules.onClose(editor.state.value, editor.dirty)) {
            NoteClose.WAIT -> Unit
            NoteClose.CLOSE -> onDismiss()
            NoteClose.CONFIRM -> confirming = true
        }
    }
    val save: () -> Unit = {
        scope.launch {
            if (note != null) {
                var kept: LocalPost? = null
                val saved = editor.save {
                    val now = editor.state.value
                    kept = keeper.localPosts.save(
                        body = now.body,
                        media = now.media,
                        id = editor.postId ?: newId,
                        quotedId = note.quotedId ?: note.editing?.quotedId,
                        quoted = note.quoted ?: note.editing?.quoted,
                        replyTo = note.replyTo?.id ?: note.editing?.replyTo,
                    )
                }
                if (saved) {
                    onSaved(kept)
                    onDismiss()
                }
            } else {
                val body = editor.state.value.body.trim()
                if (editor.save { saveText?.invoke(body.ifEmpty { null }) }) onDismiss()
            }
        }
    }

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        // Ctrl+S and Ctrl+Enter save, as a desktop editor's do, while Save itself would.
        val canSave = NoteRules.canSave(target, state, editor.dirty)
        val pastedPicture = rememberClipboardPicture()
        var pasting by remember { mutableStateOf(false) }
        // Files dropped from the file manager attach as the button's picks do, one after another (a note's, not a text note's).
        val dropped: (List<suspend () -> PickedFile?>) -> Unit = { reads ->
            scope.launch { editor.attachAll(reads.map { read -> suspend { attachPicked(keeper, editor.postId ?: newId, read) } }) }
        }
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // A wide window shows the editor as a card of a writing column's width over the dimmed page, not a page across the screen.
        val card = maxWidth >= CARD_FROM_WIDTH && maxHeight >= CARD_FROM_HEIGHT
        val shape = if (card) MaterialTheme.shapes.extraLarge else RectangleShape
        Surface(
            // A card keeps clear of the system bars and the keyboard around it; a page across the screen, inside it.
            (if (card) Modifier.systemBarsPadding().imePadding().widthIn(max = CARD_WIDTH).heightIn(max = minOf(maxHeight - 96.dp, CARD_MAX_HEIGHT)).fillMaxWidth() else Modifier.fillMaxSize())
                .takesDroppedFiles(enabled = note != null && !state.busy, onDropped = dropped).onPreviewKeyEvent { event ->
                val down = event.type == KeyEventType.KeyDown
                val saves = down && NoteKeys.saves(shortcutKey(event) == Key.S, event.key == Key.Enter || event.key == Key.NumPadEnter, event.isCtrlPressed, event.isAltPressed || event.isShiftPressed)
                if (saves && canSave && !state.busy) save()
                // Ctrl+V with a picture on the clipboard (a screenshot) attaches it to a note; text still pastes into the field.
                if (down && note != null && pastedPicture != null && !pasting && event.isCtrlPressed && !event.isAltPressed && !event.isShiftPressed && shortcutKey(event) == Key.V) {
                    // One read at a time, however long Ctrl+V is held. Read first: only a picture starts attaching, which would keep
                    // the field from taking pasted text meanwhile; it waits for an attaching under way (a drop) rather than being lost.
                    pasting = true
                    scope.launch {
                        try {
                            val picture = pastedPicture() ?: return@launch
                            editor.state.first { !it.busy }
                            editor.attach { attachPicked(keeper, editor.postId ?: newId) { picture } }
                        } finally {
                            pasting = false
                        }
                    }
                }
                saves
            },
            shape = shape,
            color = MaterialTheme.colorScheme.background,
            // A card has an edge, as the rail's menu has, or on Lights out's black it is lost in the dimmed page.
            border = if (card) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        ) {
            Box(Modifier.fillMaxSize().then(if (card) Modifier else Modifier.systemBarsPadding().imePadding()), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                    EditorBar(title(target), state.busy, state.saving, NoteRules.canSave(target, state, editor.dirty), close, save)
                    HorizontalDivider()
                    if (state.recovered) Text(stringResource(Res.string.saved_draft_restored), modifier = Modifier.padding(8.dp))
                    if (state.restoring) LinearProgressIndicator(Modifier.fillMaxWidth().heightIn(max = 2.dp))
                    Row(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        NoteAvatar()
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            NoteIdentity()
                            Spacer(Modifier.size(12.dp))
                            note?.replyTo?.let { ReplyParent(it) }
                            // A bare field, so its words start under the name above them, at a post's own size.
                            val bodyStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
                            BasicTextField(
                                value = text,
                                onValueChange = { typed ->
                                    if (!editor.state.value.busy) {
                                        text = typed.take(LocalPosts.MAX_LENGTH)
                                        editor.setBody(text)
                                    }
                                },
                                readOnly = state.busy,
                                textStyle = bodyStyle,
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                minLines = 5,
                                modifier = Modifier.fillMaxWidth().focusRequester(focus).tabLeavesField(),
                                decorationBox = { field ->
                                    Box {
                                        if (text.isEmpty()) {
                                            Text(
                                                stringResource(if (note != null) Res.string.saved_note_hint else Res.string.saved_add_note),
                                                style = bodyStyle,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        field()
                                    }
                                },
                            )
                            if (state.media.isNotEmpty()) {
                                MediaStrip(editor.postId ?: newId, state.media, enabled = !state.busy) { editor.removeMedia(it) }
                            }
                            note?.quoted?.let { QuotedPreview(SavedContent.of(it)) }
                        }
                    }
                    if (state.saveFailed || state.draftFailed || state.attachFailed) {
                        Text(
                            stringResource(if (state.saveFailed || state.draftFailed) Res.string.saved_note_save_error else Res.string.saved_note_attach_error),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    HorizontalDivider()
                    EditorFoot(state.body, attach = note?.let { { scope.launch { editor.attach { attachPicked(keeper, editor.postId ?: newId, pick) } } } }, state.attaching, state.busy)
                }
            }
        }
        }
        if (confirming) {
            AlertDialog(
                onDismissRequest = { confirming = false },
                title = { Text(stringResource(Res.string.saved_discard_title)) },
                text = { Text(stringResource(Res.string.saved_discard_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirming = false
                        scope.launch { if (editor.discard()) onDismiss() }
                    }, colors = destructiveTextColors()) { Text(stringResource(Res.string.saved_discard)) }
                },
                dismissButton = { TextButton(onClick = { confirming = false }, modifier = Modifier.dialogDefault()) { Text(stringResource(Res.string.cancel)) } },
            )
        }
    }
}

/** The editor for [target], its draft written as typed in a scope of its own, so the last keystroke is kept. */
private fun editorFor(target: NoteTarget, newId: String, keeper: Keeper): NoteEditor {
    val drafts = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    return when (target) {
        is NoteTarget.Note -> NoteEditor(
            body = target.editing?.body.orEmpty(),
            media = target.editing?.media.orEmpty(),
            draftKey = NoteRules.draftKey(target),
            postId = newId,
            drafts = keeper.noteDrafts,
            scope = drafts,
        )
        is NoteTarget.Text -> NoteEditor(body = target.note.orEmpty(), draftKey = target.draftKey, drafts = keeper.noteDrafts, scope = drafts)
    }
}

/** The picked file, kept as one of note [postId]'s files; null when the reader backed out. */
private suspend fun attachPicked(keeper: Keeper, postId: String, pick: suspend () -> PickedFile?): NoteMedia? {
    val picked = pick() ?: return null
    if (picked.bytes.isEmpty()) return null
    val id = UUID.randomUUID().toString()
    keeper.localPosts.writeMedia(postId, id, picked.bytes)
    return NoteMedia(id, picked.name, picked.mime)
}

/** An abandoned edit removes the files it attached, never those the note already had. */
private fun dropNewFiles(keeper: Keeper, postId: String, kept: List<NoteMedia>) {
    keeper.keep {
        withContext(Dispatchers.IO) {
            val keep = kept.mapTo(HashSet()) { it.id }
            keeper.localPosts.mediaFile(postId, "probe").parentFile?.listFiles()?.filter { it.isFile && it.name !in keep }?.forEach { it.delete() }
        }
    }
}

@Composable
private fun title(target: NoteTarget): String = when (NoteRules.title(target)) {
    NoteTitle.NEW -> stringResource(Res.string.saved_new_note)
    NoteTitle.EDIT -> stringResource(Res.string.saved_edit_note)
    NoteTitle.REPLY -> stringResource(Res.string.saved_note_reply_title)
}

@Composable
private fun EditorBar(title: String, busy: Boolean, saving: Boolean, canSave: Boolean, onClose: () -> Unit, onSave: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTip(stringResource(Res.string.close)) { IconButton(onClick = onClose, enabled = !busy) { Icon(Icons.Filled.Close, stringResource(Res.string.close)) } }
        Spacer(Modifier.width(8.dp))
        TipText(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Button(onClick = onSave, enabled = !busy && canSave, modifier = Modifier.heightIn(min = 44.dp)) {
            if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text(stringResource(Res.string.saved_save), fontWeight = FontWeight.Bold)
        }
    }
}

/** The lock and "Save on this device", the attach button for a note, and, near its limit, how full the note is and how many characters are left. */
@Composable
private fun EditorFoot(body: String, attach: (() -> Unit)?, attaching: Boolean, busy: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (attach != null) {
            IconTip(stringResource(Res.string.saved_note_attach)) { IconButton(onClick = attach, enabled = !busy) {
                if (attaching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(SavedIcons.PermMedia, stringResource(Res.string.saved_note_attach))
            } }
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Filled.Lock, stringResource(Res.string.saved_notice_notes), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(Res.string.saved_note_on_device), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        NoteRules.charactersLeft(body)?.let { left ->
            Spacer(Modifier.width(12.dp))
            Text(
                left.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = if (left == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            CircularProgressIndicator(
                progress = { NoteRules.fullness(body) },
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = if (left == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

/** "Replying to your note" over the start of the note answered. */
@Composable
private fun ReplyParent(parent: LocalPost) {
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).padding(12.dp)) {
        Text(stringResource(Res.string.saved_note_replying_to), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(4.dp))
        Text(parent.body.ifEmpty { stringResource(Res.string.saved_note_attach) }, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
}

/** The files attached so far, each with a button that removes it. */
@Composable
private fun MediaStrip(postId: String, media: List<NoteMedia>, enabled: Boolean, onRemove: (String) -> Unit) {
    val keeper = LocalKeeper.current
    LazyRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(media, key = { it.id }) { item ->
            Box(Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                if (item.isImage) {
                    AsyncImage(model = keeper.localPosts.mediaFile(postId, item.id), contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(if (item.isVideo) SavedIcons.Videocam else SavedIcons.AttachMedia, contentDescription = item.name, modifier = Modifier.align(Alignment.Center))
                }
                FilledTonalIconButton(onClick = { onRemove(item.id) }, enabled = enabled, modifier = Modifier.align(Alignment.TopEnd).size(32.dp)) {
                    Icon(Icons.Filled.Close, stringResource(Res.string.saved_note_remove_media), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** The post a new note quotes, as it will show under the note. */
@Composable
private fun QuotedPreview(content: SavedContent) {
    val post = content.xPost ?: return
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(shape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).padding(12.dp)) {
        TipText(post.author.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("@" + post.author.screenName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(post.text, maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

/** From this window size on, the editor is a card ([CARD_WIDTH] wide at most) rather than the whole window. */
private val CARD_FROM_WIDTH = 840.dp
private val CARD_FROM_HEIGHT = 560.dp
private val CARD_WIDTH = 640.dp
private val CARD_MAX_HEIGHT = 720.dp

/** The note editor's keys: Ctrl+S and Ctrl+Enter save ([s], [enter]), as a desktop editor's do; with Alt or Shift too ([other]) they are not the editor's. */
internal object NoteKeys {
    fun saves(s: Boolean, enter: Boolean, ctrl: Boolean, other: Boolean): Boolean = ctrl && !other && (s || enter)
}
