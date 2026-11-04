package com.aimdi.xtanative.ui.saved

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.core.x.model.PhotoSize
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.photoUrl
import com.aimdi.xtanative.store.offline.OfflineSaved
import com.aimdi.xtanative.store.saved.LocalPost
import com.aimdi.xtanative.store.saved.SavedContent
import com.aimdi.xtanative.store.saved.SavedEmptyKind
import com.aimdi.xtanative.store.saved.SavedPost
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.offline_title
import com.aimdi.xtanative.ui.generated.resources.saved_add_note
import com.aimdi.xtanative.ui.generated.resources.saved_are_you_sure
import com.aimdi.xtanative.ui.generated.resources.saved_delete
import com.aimdi.xtanative.ui.generated.resources.saved_edit_note
import com.aimdi.xtanative.ui.generated.resources.saved_empty_folder
import com.aimdi.xtanative.ui.generated.resources.saved_empty_likes
import com.aimdi.xtanative.ui.generated.resources.saved_empty_notes
import com.aimdi.xtanative.ui.generated.resources.saved_empty_saved
import com.aimdi.xtanative.ui.generated.resources.saved_empty_search
import com.aimdi.xtanative.ui.generated.resources.saved_likes_ungrouped
import com.aimdi.xtanative.ui.generated.resources.saved_no_media
import com.aimdi.xtanative.ui.generated.resources.saved_note_button
import com.aimdi.xtanative.ui.offline.OfflineSavedAction
import com.aimdi.xtanative.ui.post.PostActions
import com.aimdi.xtanative.ui.post.PostCard
import org.jetbrains.compose.resources.stringResource

/** The open tab: its saved posts, likes or notes, the media grid, or why it is empty. */
@Composable
internal fun SavedBodyView(
    state: SavedUi,
    actions: PostActions,
    contentPadding: PaddingValues,
    onWriteNote: () -> Unit,
    onNote: (NoteTarget.Note) -> Unit,
    onDeleteNote: (LocalPost) -> Unit,
    onOpenThread: (String) -> Unit,
    vm: SavedViewModel,
) {
    when (val body = state.body) {
        is SavedBody.Empty -> EmptyPane(body.kind, onWriteNote)
        is SavedBody.Grid -> MediaGrid(body.tiles, actions, contentPadding, onRemove = vm::removeFromGrid)
        is SavedBody.Posts -> LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
            items(body.posts, key = { it.id }, contentType = { "saved" }) { post ->
                SavedRow(post, actions, state.view.selecting, post.id in state.view.selected, vm)
            }
        }
        is SavedBody.Likes -> LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
            items(body.likes, key = { it.id }, contentType = { "like" }) { like ->
                KeptPost(like.about, actions)
                Hairline()
            }
        }
        is SavedBody.LikesByGroup -> LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
            items(body.rows, key = { it.key }) { row ->
                when (row) {
                    is LikesRow.Heading -> Text(
                        row.name ?: stringResource(Res.string.saved_likes_ungrouped),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                    is LikesRow.Like -> {
                        KeptPost(row.like.about, actions)
                        Hairline()
                    }
                }
            }
        }
        is SavedBody.Notes -> LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
            items(body.notes, key = { it.note.id }, contentType = { "note" }) { row ->
                NoteTile(
                    note = row.note,
                    actions = actions,
                    compact = true,
                    replies = row.replies,
                    onEdit = { onNote(NoteTarget.Note(editing = row.note)) },
                    onDelete = { onDeleteNote(row.note) },
                    onReply = { onNote(NoteTarget.Note(replyTo = row.note)) },
                    onOpen = { onOpenThread(row.threadRoot) },
                )
            }
        }
    }
}

/**
 * A saved post with its note under it, lined up with the post's text, and the
 * button that keeps its media offline. While selecting, a tap toggles it;
 * otherwise a long press starts selecting it.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SavedRow(post: SavedPost, actions: PostActions, selecting: Boolean, selected: Boolean, vm: SavedViewModel) {
    var editing by remember { mutableStateOf(false) }
    var offline by remember { mutableStateOf(false) }
    val content = post.about
    Box(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
            // Attached while selecting too, so the press that starts a selection ends without opening the post.
            .longPressFirst { if (!selecting) vm.beginSelection(post.id) },
    ) {
        Column(Modifier.fillMaxWidth()) {
            KeptPost(content, actions)
            if (!selecting) NoteLine(post.note, OfflineSaved.hasMedia(content), onEdit = { editing = true }, onOffline = { offline = true })
            Hairline()
        }
        if (selecting) {
            // Over the post, so a tap selects rather than opens it.
            Box(Modifier.matchParentSize().clickable { vm.toggle(post.id) })
            Checkbox(checked = selected, onCheckedChange = { vm.toggle(post.id) }, modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp))
        }
    }
    if (editing) {
        NoteEditorDialog(
            target = NoteTarget.Text(post.note, NoteRules.savedNoteKey(post.id)),
            onDismiss = { editing = false },
            saveText = { note -> vm.setNote(post.id, note) },
        )
    }
    if (offline) {
        ModalBottomSheet(onDismissRequest = { offline = false }) {
            OfflineSavedAction(post.id, content, Modifier.padding(bottom = 16.dp))
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

/**
 * A kept post as the device has it: an X post as a feed row (opening the
 * post, its author and its media), another network's as a card that opens
 * where it lives, and one too large to keep as a line saying so.
 */
@Composable
fun KeptPost(content: SavedContent, actions: PostActions) {
    val graph = LocalGraph.current
    val post = content.xPost
    when {
        post != null -> {
            LaunchedEffect(post.id) { graph.posts.remember(post) }
            PostCard(post, actions)
        }
        content.kind == SavedContent.Kind.NONE -> TooLarge()
        else -> KeptCard.of(content)?.let { KeptCardView(it) } ?: TooLarge()
    }
}

/** The grid Media only draws: a tap opens the media, a long press offers to remove its post. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaGrid(tiles: List<SavedMediaTile>, actions: PostActions, contentPadding: PaddingValues, onRemove: (String) -> Unit) {
    val graph = LocalGraph.current
    var removing by remember { mutableStateOf<String?>(null) }
    if (tiles.isEmpty()) {
        Message(stringResource(Res.string.saved_no_media))
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(tiles, key = { it.savedId + "/" + it.index }) { tile ->
                val open = {
                    // The viewer draws the post's media from the posts on screen.
                    graph.posts.remember(tile.post)
                    actions.openMedia(tile.postId, tile.index)
                }
                Box(Modifier.combinedClickable(onClick = open, onLongClick = { removing = tile.savedId })) {
                    SquareImage(photoUrl(tile.media.url, PhotoSize.SMALL), Modifier.fillMaxWidth(), tile.media.alt)
                    if (tile.media.kind != XMedia.Kind.PHOTO) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(32.dp))
                    }
                }
            }
        }
    }
    removing?.let { id ->
        ConfirmDialog(
            title = stringResource(Res.string.saved_are_you_sure),
            text = null,
            confirm = stringResource(Res.string.saved_delete),
            onDismiss = { removing = null },
        ) {
            onRemove(id)
            removing = null
        }
    }
}

/** An empty tab, with an icon for what it would hold, and a button to write one in Notes. */
@Composable
private fun EmptyPane(kind: SavedEmptyKind, onWriteNote: () -> Unit) {
    val (icon, message) = when (kind) {
        SavedEmptyKind.SEARCH -> Icons.Filled.Search to stringResource(Res.string.saved_empty_search)
        SavedEmptyKind.LIKES -> Icons.Filled.FavoriteBorder to stringResource(Res.string.saved_empty_likes)
        SavedEmptyKind.SAVED -> SavedIcons.BookmarkBorder to stringResource(Res.string.saved_empty_saved)
        SavedEmptyKind.FOLDER -> SavedIcons.FolderOpen to stringResource(Res.string.saved_empty_folder)
        SavedEmptyKind.NOTES -> SavedIcons.EditNote to stringResource(Res.string.saved_empty_notes)
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (kind == SavedEmptyKind.NOTES) {
            Button(onClick = onWriteNote) {
                Icon(SavedIcons.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.saved_note_button))
            }
        }
    }
}

@Composable
internal fun Message(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 64.dp),
    )
}
