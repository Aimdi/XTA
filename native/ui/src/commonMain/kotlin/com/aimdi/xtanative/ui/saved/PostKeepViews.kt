package com.aimdi.xtanative.ui.saved

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.store.saved.XtaTweetJson
import com.aimdi.xtanative.store.settings.PostsPrefs
import com.aimdi.xtanative.ui.common.HEART_BURST_MILLIS
import com.aimdi.xtanative.ui.common.HeartBurst
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.common.onSecondaryClick
import com.aimdi.xtanative.ui.common.rememberSwitchedHaptics
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.saved_like
import com.aimdi.xtanative.ui.generated.resources.saved_save_here
import com.aimdi.xtanative.ui.generated.resources.saved_unlike
import com.aimdi.xtanative.ui.generated.resources.saved_unsave
import com.aimdi.xtanative.ui.plugins.CountButton
import com.aimdi.xtanative.ui.post.sharedPostPrefs
import kotlinx.coroutines.launch
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
    val keeper = LocalKeeper.current
    return remember(keeper) { { post, index -> download(keeper, post, index) } }
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
    val keeper = LocalKeeper.current
    val library by keeper.saved.index.collectAsStateWithLifecycle()
    LaunchedEffect(keeper) { keeper.saved.index() }
    val loaded = library ?: return
    val note = remember(item.id) { loaded.post(item.id)?.note }
    NoteEditorDialog(NoteTarget.Text(note, NoteRules.savedNoteKey(item.id)), onDismiss, saveText = { text -> Keeping.writeNote(keeper, item, text) })
}

/**
 * Whether [postId] is saved here, as that changes: read from the frame's
 * [KeptNow], where a change redraws only the bookmarks it changes, else
 * followed here.
 */
@Composable
internal fun savedHere(postId: String): Boolean {
    val keeper = LocalKeeper.current
    val kept = keptNow(keeper)
    if (kept != null) {
        SideEffect(kept::wantSaved)
        return remember(kept, postId) { derivedStateOf { kept.isSaved(postId) } }.value
    }
    LaunchedEffect(keeper) { keeper.saved.index() }
    val library by keeper.saved.index.collectAsStateWithLifecycle()
    return library?.isSaved(postId) == true
}

/** Whether [postId] is liked here, as that changes: read as [savedHere] reads. */
@Composable
internal fun likedHere(postId: String): Boolean {
    val keeper = LocalKeeper.current
    val kept = keptNow(keeper)
    if (kept != null) {
        SideEffect(kept::wantLiked)
        return remember(kept, postId) { derivedStateOf { kept.isLiked(postId) } }.value
    }
    val liked by remember(keeper) { LikedIds.of(keeper).ids }.collectAsStateWithLifecycle()
    return postId in liked
}

@Composable
internal fun DeviceSaveButton(post: XPost, modifier: Modifier, tint: Color) {
    // The post is written out as it is kept only when the bookmark is pressed, not for every card scrolled past.
    KeepableSaveButton(post.shown.id, modifier, tint) { Keepable.of(post) }
}

/** The bookmark of any network's kept post: a tap saves [item] where plain taps file or removes it, a long press opens the save sheet. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun KeepableSaveButton(item: Keepable, modifier: Modifier, tint: Color) = KeepableSaveButton(item.id, modifier, tint) { item }

/** [KeepableSaveButton] of the post [id], made [item] only once it is pressed; a tap ticks, while Haptic feedback is on. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun KeepableSaveButton(id: String, modifier: Modifier, tint: Color, item: () -> Keepable) {
    val keeper = LocalKeeper.current
    val saved = savedHere(id)
    var sheetFor by remember { mutableStateOf<Keepable?>(null) }
    val label = stringResource(if (saved) Res.string.saved_unsave else Res.string.saved_save_here)
    val haptics = rememberSwitchedHaptics()
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            // A right-click picks the folder, as a long press does.
            .onSecondaryClick { sheetFor = item() }
            .combinedClickable(
                role = Role.Button,
                onClickLabel = label,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    Keeping.toggleSave(keeper, item())
                },
                onLongClick = { sheetFor = item() },
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

/**
 * The device's like under an X post (XTA's LikeButton): liking plays X's
 * burst ([HeartBurst]) in the colour of a device like, unless motion is
 * reduced or calm or zen mode keeps actions quiet; X's count beside it rolls.
 */
@Composable
internal fun DeviceLikeButton(post: XPost, modifier: Modifier, tint: Color, count: Long) {
    val keeper = LocalKeeper.current
    val liked = likedHere(post.shown.id)
    val label = stringResource(if (liked) Res.string.saved_unlike else Res.string.saved_like)
    val rest = tint.takeOrElse { MaterialTheme.colorScheme.onSurfaceVariant }
    val active = MaterialTheme.colorScheme.primary
    val burst = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val still = LocalReducedMotion.current || quietActions()
    Box(modifier) {
        CountButton(
            glyph = {
                HeartBurst(
                    burst.value,
                    active,
                    17.dp,
                    filledAtRest = liked,
                    outline = { Icon(XIcons.Like, contentDescription = null, tint = rest, modifier = Modifier.size(17.dp)) },
                    filled = { Icon(Icons.Filled.Favorite, contentDescription = null, tint = active, modifier = Modifier.size(17.dp)) },
                )
            },
            n = count,
            label = label,
            tint = if (liked) active else rest,
            onLongClick = null,
        ) {
            scope.launch {
                burst.snapTo(0f)
                if (!liked && !still) burst.animateTo(1f, tween(HEART_BURST_MILLIS, easing = LinearEasing))
            }
            Keeping.toggleLike(keeper, Keepable.of(post))
        }
    }
}

/** Calm or zen mode is on, which keep actions quiet (XTA's quietActions). */
@Composable
private fun quietActions(): Boolean {
    val store = LocalKeeper.current.settings
    // The post settings already read for every card say it, where they are this store's.
    sharedPostPrefs(store)?.let { return it.hideCounts }
    val settings by store.state.collectAsStateWithLifecycle()
    return settings?.let { it[PostsPrefs.calmMode] || it[PostsPrefs.zenMode] } ?: false
}
