package com.aimdi.xtanative.plugins.booru

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.booru.BooruNote
import com.aimdi.xtanative.networks.booru.BooruPost
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.MediaTiles
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.post.PostIcons

/**
 * The post's picture, opening full screen where it zooms, with its
 * translation notes over it; or its video, played by the app's player
 * (booru_post_media.dart). The desktop, which has no player of its own, opens
 * the video where the host shows it.
 */
@Composable
internal fun PostMedia(app: BooruApp, viewer: ViewerState, post: BooruPost, display: BooruDisplay) {
    val video = post.videoUrl
    if (video != null) {
        val item = MediaItem(post.thumbnailUrl, MediaItem.Kind.VIDEO, videoUrl = video, aspectRatio = post.aspectRatio.toFloat(), webUrl = post.hostPageUrl)
        MediaTiles(listOf(item), onOpen = { app.host.openMedia(listOf(item), 0) }, modifier = Modifier.padding(horizontal = 16.dp), playing = true)
        return
    }
    val full = pluginString(Strings.full_screen)
    Box(Modifier.fillMaxWidth().aspectRatio(post.aspectRatio.toFloat().coerceIn(0.4f, 2.2f))) {
        BooruImage(
            if (post.isVideo) post.thumbnailUrl else post.displayUrl,
            Modifier.fillMaxSize().clickable(onClickLabel = full, role = Role.Image) {
                app.host.openMedia(listOf(MediaItem(display.viewerUrl(post), aspectRatio = post.aspectRatio.toFloat())), 0)
            },
            contentScale = ContentScale.Fit,
            description = full,
        )
        if (!post.isVideo) NotesOverlay(app, viewer, post)
    }
}

/**
 * [post]'s translation notes over its picture (booru_notes.dart), which fills
 * the box the way [ContentScale.Fit] fits it: a chip says how many there are
 * and shows or hides them; a tapped note shows its text. Nothing when there are none.
 */
@Composable
private fun NotesOverlay(app: BooruApp, viewer: ViewerState, post: BooruPost) {
    val notes = produceState(emptyList<BooruNote>(), post.key) {
        value = attempt { viewer.lookups.remember("notes:${post.key}") { app.client.notes(post) } }.orEmpty()
    }.value
    if (notes.isEmpty() || post.width <= 0 || post.height <= 0) return
    var shown by rememberSaveable(post.key) { mutableStateOf(false) }
    var reading by remember(post.key) { mutableStateOf<BooruNote?>(null) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (shown) {
            val scale = minOf(maxWidth.value / post.width, maxHeight.value / post.height)
            val left = (maxWidth.value - post.width * scale) / 2
            val top = (maxHeight.value - post.height * scale) / 2
            notes.forEach { note -> NoteBox(note, left, top, scale) { reading = note } }
            reading?.let { note -> NoteText(note.text, Modifier.align(Alignment.BottomCenter)) { reading = null } }
        }
        FilterChip(
            selected = shown,
            onClick = { shown = !shown; reading = null },
            label = { Text(pluginString(Strings.notes, notes.size)) },
            leadingIcon = { Icon(PostIcons.Translate, contentDescription = null, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
        )
    }
}

/** A note's box, scaled and placed where the picture is drawn: [left], [top] and [scale] in dp. */
@Composable
private fun NoteBox(note: BooruNote, left: Float, top: Float, scale: Float, onClick: () -> Unit) {
    Box(
        Modifier
            .offset(Dp(left + note.x * scale), Dp(top + note.y * scale))
            .size(Dp(note.width * scale), Dp(note.height * scale))
            .background(Color.Yellow.copy(alpha = 0.18f))
            .border(1.dp, Color.Black.copy(alpha = 0.54f))
            .clickable(onClick = onClick),
    )
}

@Composable
private fun NoteText(text: String, modifier: Modifier, onClose: () -> Unit) {
    Surface(
        modifier.padding(12.dp).clickable(onClick = onClose),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}
