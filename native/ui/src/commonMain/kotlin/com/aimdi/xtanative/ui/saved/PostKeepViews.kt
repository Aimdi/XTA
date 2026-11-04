package com.aimdi.xtanative.ui.saved

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.store.saved.XtaTweetJson
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.saved_like
import com.aimdi.xtanative.ui.generated.resources.saved_save_here
import com.aimdi.xtanative.ui.generated.resources.saved_unlike
import com.aimdi.xtanative.ui.generated.resources.saved_unsave
import com.aimdi.xtanative.ui.plugins.NetworkCount
import org.jetbrains.compose.resources.stringResource

/** The bookmark: a tap saves the post on this device where plain taps file, or removes it; a long press opens the save sheet. */
@Composable
fun PostKeep.SaveButton(post: XPost, modifier: Modifier = Modifier, tint: Color = Color.Unspecified) = DeviceSaveButton(post, modifier, tint)

/** The heart, liked on this device only, drawn as a post's counts are, with X's like [count] beside it when above 0. */
@Composable
fun PostKeep.LikeButton(post: XPost, modifier: Modifier = Modifier, tint: Color = Color.Unspecified, count: Long = 0) =
    DeviceLikeButton(post, modifier, tint, count)

/** The save sheet for [post], as a long press on the bookmark opens it. */
@Composable
fun PostKeep.SaveSheet(post: XPost, onDismiss: () -> Unit) {
    val item = remember(post.shown.id) { Keepable.of(post) }
    SaveSheet(item, onDismiss)
}

/** The note on [post] in Saved, as its ⋯ sheet's "Add a note to this saved post" edits it: writing one saves the post first. */
@Composable
fun PostKeep.NoteSheet(post: XPost, onDismiss: () -> Unit) {
    val item = remember(post.shown.id) { Keepable.of(post) }
    SavedNoteEditor(item, onDismiss)
}

/** "Write a note" about [post] (XTA's quote menu): a note of Notes quoting it, which says so once kept. */
@Composable
fun PostKeep.NoteAbout(post: XPost, onDismiss: () -> Unit) {
    val shown = post.shown
    val quoted = remember(shown.id) { XtaTweetJson.encode(shown) }
    NoteEditorDialog(NoteTarget.Note(quotedId = shown.id, quoted = quoted), onDismiss, onSaved = { KeepNotices.show(KeepNotice.NoteSaved) })
}

/** What the media viewer's and the media menu's Download call: the media at an index of a post, saved where the setting says. */
@Composable
fun PostKeep.rememberDownload(): (post: XPost, index: Int) -> Unit {
    val graph = LocalGraph.current
    return remember(graph) { { post, index -> download(graph, post, index) } }
}

/** Whether [postId] is saved on this device, as that changes. */
@Composable
fun PostKeep.isSaved(postId: String): Boolean = savedHere(postId)

/** Whether [postId] is liked on this device, as that changes. */
@Composable
fun PostKeep.isLiked(postId: String): Boolean = likedHere(postId)

/**
 * The note on [item] in Saved, in the note editor (XTA's editPluginPostNote):
 * written, it saves the post first, where a plain tap on the bookmark files.
 */
@Composable
internal fun SavedNoteEditor(item: Keepable, onDismiss: () -> Unit) {
    val graph = LocalGraph.current
    val library by graph.saved.state.collectAsStateWithLifecycle()
    LaunchedEffect(graph) { graph.saved.library() }
    val loaded = library ?: return
    val note = remember(item.id) { loaded.post(item.id)?.note }
    NoteEditorDialog(NoteTarget.Text(note, NoteRules.savedNoteKey(item.id)), onDismiss, saveText = { text -> Keeping.writeNote(graph, item, text) })
}

@Composable
internal fun savedHere(postId: String): Boolean {
    val graph = LocalGraph.current
    LaunchedEffect(graph) { graph.saved.library() }
    val library by graph.saved.state.collectAsStateWithLifecycle()
    return library?.isSaved(postId) == true
}

@Composable
internal fun likedHere(postId: String): Boolean {
    val graph = LocalGraph.current
    val liked by remember(graph) { LikedIds.of(graph).ids }.collectAsStateWithLifecycle()
    return postId in liked
}

@Composable
internal fun DeviceSaveButton(post: XPost, modifier: Modifier, tint: Color) {
    val item = remember(post) { Keepable.of(post) }
    KeepableSaveButton(item, modifier, tint)
}

/** The bookmark of any network's kept post: a tap saves [item] where plain taps file or removes it, a long press opens the save sheet. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun KeepableSaveButton(item: Keepable, modifier: Modifier, tint: Color) {
    val graph = LocalGraph.current
    val saved = savedHere(item.id)
    var sheetFor by remember { mutableStateOf<Keepable?>(null) }
    val label = stringResource(if (saved) Res.string.saved_unsave else Res.string.saved_save_here)
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = label,
                onClick = { Keeping.toggleSave(graph, item) },
                onLongClick = { sheetFor = item },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (saved) SavedIcons.Bookmark else SavedIcons.BookmarkBorder,
            contentDescription = label,
            tint = if (saved) MaterialTheme.colorScheme.primary else tint.takeOrElse { MaterialTheme.colorScheme.onSurfaceVariant },
            modifier = Modifier.size(19.dp),
        )
    }
    sheetFor?.let { item -> SaveSheet(item) { sheetFor = null } }
}

@Composable
internal fun DeviceLikeButton(post: XPost, modifier: Modifier, tint: Color, count: Long) {
    val graph = LocalGraph.current
    val liked = likedHere(post.shown.id)
    val label = stringResource(if (liked) Res.string.saved_unlike else Res.string.saved_like)
    val color = if (liked) MaterialTheme.colorScheme.primary else tint.takeOrElse { MaterialTheme.colorScheme.onSurfaceVariant }
    Box(modifier) {
        NetworkCount(if (liked) Icons.Filled.Favorite else XIcons.Like, count, label, color) { Keeping.toggleLike(graph, Keepable.of(post)) }
    }
}
