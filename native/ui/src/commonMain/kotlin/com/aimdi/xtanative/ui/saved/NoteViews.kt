package com.aimdi.xtanative.ui.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.aimdi.xtanative.store.saved.LocalPost
import com.aimdi.xtanative.store.saved.NoteMedia
import com.aimdi.xtanative.store.saved.SavedContent
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.common.agoText
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.close
import com.aimdi.xtanative.ui.generated.resources.saved_delete
import com.aimdi.xtanative.ui.generated.resources.saved_edit_note
import com.aimdi.xtanative.ui.generated.resources.saved_note_author
import com.aimdi.xtanative.ui.generated.resources.saved_note_handle
import com.aimdi.xtanative.ui.generated.resources.saved_note_reply
import com.aimdi.xtanative.ui.generated.resources.saved_quoted_unavailable
import com.aimdi.xtanative.ui.generated.resources.saved_show_more
import com.aimdi.xtanative.ui.offline.OfflineVideo
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.post.QuotedPost
import java.io.File
import java.time.Instant
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** The round mark notes are written under: the reader, on this device. */
@Composable
fun NoteAvatar() {
    Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
    }
}

/** "Notes @local": who a note is by, which is no one on any network. */
@Composable
fun NoteIdentity() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(Res.string.saved_note_author), style = MaterialTheme.typography.titleMedium)
        Text("@" + stringResource(Res.string.saved_note_handle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A note as Notes and its thread list it (local_post_tile.dart): its time, a
 * menu to edit, answer or delete it, its text (cut at seven lines when
 * [compact]), its files, the post it quotes, and a footer that answers it,
 * opens its thread and edits it.
 */
@Composable
fun NoteTile(
    note: LocalPost,
    actions: PostActions,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReply: (() -> Unit)? = null,
    onOpen: (() -> Unit)? = null,
    replies: Int = 0,
    compact: Boolean = false,
) {
    val graph = LocalGraph.current
    var menu by remember { mutableStateOf(false) }
    val quoted = remember(note.quoted) { note.quoted?.let { SavedContent.of(it).xPost } }
    if (quoted != null) LaunchedEffect(quoted.id) { graph.posts.remember(quoted) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 4.dp)) {
            NoteAvatar()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        NoteIdentity()
                        Text(agoText(Instant.ofEpochMilli(note.updatedAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(SavedIcons.MoreHoriz, contentDescription = null) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(Res.string.saved_edit_note)) }, onClick = { menu = false; onEdit() })
                            if (onReply != null) DropdownMenuItem(text = { Text(stringResource(Res.string.saved_note_reply)) }, onClick = { menu = false; onReply() })
                            DropdownMenuItem(text = { Text(stringResource(Res.string.saved_delete)) }, onClick = { menu = false; onDelete() })
                        }
                    }
                }
                if (note.body.isNotEmpty()) {
                    Text(
                        note.body,
                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 1.4f),
                        maxLines = if (compact) 7 else Int.MAX_VALUE,
                        overflow = if (compact) TextOverflow.Ellipsis else TextOverflow.Clip,
                        modifier = Modifier.fillMaxWidth().let { if (onOpen != null) it.clickable(onClick = onOpen) else it }.padding(top = 4.dp, bottom = 8.dp),
                    )
                }
                if (note.media.isNotEmpty()) NoteMediaBlock(note.id, note.media, Modifier.padding(bottom = 8.dp))
                when {
                    quoted != null && compact -> CompactQuote(quoted.text, onOpen)
                    quoted != null -> QuotedPost(quoted, actions, Modifier.padding(bottom = 8.dp))
                    note.quotedId != null -> Text(
                        stringResource(Res.string.saved_quoted_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                NoteFooter(replies, onReply, if (compact) onOpen else null, onEdit)
            }
        }
        Hairline()
    }
}

@Composable
private fun CompactQuote(text: String, onOpen: (() -> Unit)?) {
    val shape = RoundedCornerShape(16.dp)
    Text(
        text.ifEmpty { stringResource(Res.string.saved_show_more) },
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .let { if (onOpen != null) it.clickable(onClick = onOpen) else it }
            .padding(12.dp),
    )
}

/** Answer (or the number of answers, which opens them), open the thread, edit. */
@Composable
private fun NoteFooter(replies: Int, onReply: (() -> Unit)?, onOpen: (() -> Unit)?, onEdit: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        if (onReply != null) {
            TextButton(onClick = { if (NoteRules.replyOpensThread(replies)) (onOpen ?: onReply)() else onReply() }) {
                Icon(XIcons.Reply, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (replies > 0) replies.toString() else stringResource(Res.string.saved_note_reply))
            }
        } else {
            Spacer(Modifier.width(1.dp))
        }
        Row {
            if (onOpen != null) {
                IconButton(onClick = onOpen) { Icon(SavedIcons.OpenInFull, stringResource(Res.string.saved_show_more), modifier = Modifier.size(18.dp)) }
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, stringResource(Res.string.saved_edit_note), modifier = Modifier.size(20.dp)) }
        }
    }
}

/** A note's pictures in a grid of up to four (one alone drawn tall), then its other files, each opening on a tap. */
@Composable
fun NoteMediaBlock(postId: String, media: List<NoteMedia>, modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val layout = remember(media) { NoteRules.layout(media) }
    var viewing by remember { mutableStateOf<NoteMedia?>(null) }
    Column(modifier.fillMaxWidth()) {
        if (layout.single) {
            NoteImage(graph.localPosts.mediaFile(postId, layout.images.first().id), Modifier.fillMaxWidth().height(220.dp)) { viewing = layout.images.first() }
        } else if (layout.images.isNotEmpty()) {
            layout.images.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { image -> NoteImage(graph.localPosts.mediaFile(postId, image.id), Modifier.weight(1f).height(140.dp)) { viewing = image } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        layout.files.forEach { file ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable {
                    if (file.isVideo) {
                        viewing = file
                    } else {
                        scope.launch {
                            if (!graph.platformFiles.openFile(graph.localPosts.mediaFile(postId, file.id), file.mime)) KeepNotices.show(KeepNotice.OpenFailed)
                        }
                    }
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (file.isVideo) SavedIcons.Videocam else SavedIcons.AttachMedia, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    viewing?.let { item -> LocalFileViewer(graph.localPosts.mediaFile(postId, item.id), item.isVideo) { viewing = null } }
}

@Composable
private fun NoteImage(file: File, modifier: Modifier, onClick: () -> Unit) {
    AsyncImage(
        model = file,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).clickable(onClick = onClick),
    )
}

/** A file of the device's own, full screen: a picture to zoom, or a video. */
@Composable
fun LocalFileViewer(file: File, video: Boolean, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (video) OfflineVideo(file, active = true, modifier = Modifier.fillMaxSize()) else ZoomableFile(file)
            IconButton(onClick = onClose, modifier = Modifier.statusBarsPadding().padding(8.dp).align(Alignment.TopStart)) {
                Icon(Icons.Filled.Close, stringResource(Res.string.close), tint = Color.White)
            }
        }
    }
}

/** A picture from a file, fitted to the screen, that a double tap or a pinch zooms. */
@Composable
fun ZoomableFile(file: File, modifier: Modifier = Modifier) {
    ZoomableImage(model = file, modifier = modifier.fillMaxSize())
}

/** A square thumbnail of a picture, for grids. */
@Composable
fun SquareImage(model: Any?, modifier: Modifier = Modifier, description: String? = null) {
    AsyncImage(
        model = model,
        contentDescription = description,
        contentScale = ContentScale.Crop,
        modifier = modifier.aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant),
    )
}
