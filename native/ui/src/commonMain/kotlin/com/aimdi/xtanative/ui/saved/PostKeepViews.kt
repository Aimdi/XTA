package com.aimdi.xtanative.ui.saved

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import org.jetbrains.compose.resources.stringResource

/** The bookmark: a tap saves the post on this device where plain taps file, or removes it; a long press opens the save sheet. */
@Composable
fun PostKeep.SaveButton(post: XPost, modifier: Modifier = Modifier, tint: Color = Color.Unspecified) = DeviceSaveButton(post, modifier, tint)

/** The heart, liked on this device only, with [count] beside it when given. */
@Composable
fun PostKeep.LikeButton(post: XPost, modifier: Modifier = Modifier, tint: Color = Color.Unspecified, count: String? = null) =
    DeviceLikeButton(post, modifier, tint, count)

/** The save sheet for [post], as a long press on the bookmark opens it. */
@Composable
fun PostKeep.SaveSheet(post: XPost, onDismiss: () -> Unit) {
    val item = remember(post.shown.id) { Keepable.of(post) }
    SaveSheet(item, onDismiss)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DeviceSaveButton(post: XPost, modifier: Modifier, tint: Color) {
    val graph = LocalGraph.current
    val saved = savedHere(post.shown.id)
    var sheetFor by remember { mutableStateOf<Keepable?>(null) }
    val label = stringResource(if (saved) Res.string.saved_unsave else Res.string.saved_save_here)
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = label,
                onClick = { Keeping.toggleSave(graph, Keepable.of(post)) },
                onLongClick = { sheetFor = Keepable.of(post) },
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DeviceLikeButton(post: XPost, modifier: Modifier, tint: Color, count: String?) {
    val graph = LocalGraph.current
    val liked = likedHere(post.shown.id)
    val label = stringResource(if (liked) Res.string.saved_unlike else Res.string.saved_like)
    val color = if (liked) MaterialTheme.colorScheme.primary else tint.takeOrElse { MaterialTheme.colorScheme.onSurfaceVariant }
    Row(
        modifier
            .heightIn(min = 40.dp)
            .widthIn(min = 40.dp)
            .clip(CircleShape)
            .combinedClickable(role = Role.Button, onClickLabel = label, onClick = { Keeping.toggleLike(graph, Keepable.of(post)) })
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(if (liked) Icons.Filled.Favorite else XIcons.Like, contentDescription = label, tint = color, modifier = Modifier.size(18.dp))
        if (count != null) Text(count, style = MaterialTheme.typography.labelMedium, color = color, modifier = Modifier.padding(start = 4.dp))
    }
}
