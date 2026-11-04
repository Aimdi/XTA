package com.aimdi.xtanative.ui.post

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.photoUrl
import com.aimdi.xtanative.ui.common.AltBadge
import com.aimdi.xtanative.ui.common.AltTextDialog
import com.aimdi.xtanative.ui.common.MediaBadge
import com.aimdi.xtanative.ui.common.ShowMediaTile
import com.aimdi.xtanative.ui.common.mediaShown
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.posts_filter_videos
import com.aimdi.xtanative.ui.generated.resources.reader_media
import com.aimdi.xtanative.ui.media.LocalMediaSaver
import com.aimdi.xtanative.ui.media.MediaFiles
import com.aimdi.xtanative.ui.media.MediaIcons
import com.aimdi.xtanative.ui.media.MediaSaver
import com.aimdi.xtanative.ui.media.TileGif
import com.aimdi.xtanative.ui.media.TileVideo
import com.aimdi.xtanative.ui.media.rememberPhotoSize
import org.jetbrains.compose.resources.stringResource

private val MediaShape = RoundedCornerShape(16.dp)

/**
 * A post's photos, videos and GIFs as XTA lays them out (_media.dart): one
 * at its own shape on black; several along a row at one height, each at
 * its own shape, the next one's edge in view, the row scrolling sideways
 * ([MediaStrip]). Photos come at the image-quality setting's size; a
 * described one carries "ALT"; a long press on a photo shows its
 * description, else saves it under [username]'s name from post [postId].
 * While media loads manually each waits behind "Tap to show Media". A video
 * plays in its tile with its controls, and a GIF loops there (`ui/media`'s
 * TileVideo and TileGif): with [playing], the feed plays the first of them
 * by itself; [post] names a saved video's file. The row starts at
 * [start] and runs [bleed] on past the end of its column, to the screen's
 * edge, as XTA's does.
 */
@Composable
fun MediaGrid(
    media: List<XMedia>,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier,
    playing: Boolean = false,
    post: XPost? = null,
    username: String = post?.author?.screenName.orEmpty(),
    postId: String = post?.id.orEmpty(),
    start: Int = 0,
    bleed: Dp = 0.dp,
) {
    if (media.isEmpty()) return
    val playIndex = if (playing) media.indexOfFirst { it.kind != XMedia.Kind.PHOTO } else -1
    val owner = MediaOwner(username, postId, post)
    if (media.size == 1) {
        val frame = modifier.fillMaxWidth().aspectRatio(MediaStrip.singleAspect(MediaStrip.aspectOf(media[0]))).clip(MediaShape)
        Box(frame.border(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant, MediaShape).background(Color.Black)) {
            ShownMedia(media[0], 0, 1, playIndex == 0, ContentScale.Fit, Color.Black, owner) { onOpen(0) }
        }
    } else {
        MediaRow(media, playIndex, owner, start, bleed, modifier, onOpen)
    }
}

/** Whose a post's media is, for a photo saved with a long press, and the [post] a saved video is named by. */
private class MediaOwner(val username: String, val postId: String, val post: XPost?)

/** Several along a row at one height, opened at [start], running [bleed] past the column's end. */
@Composable
private fun MediaRow(media: List<XMedia>, playIndex: Int, owner: MediaOwner, start: Int, bleed: Dp, modifier: Modifier, onOpen: (Int) -> Unit) {
    val row = rememberLazyListState(initialFirstVisibleItemIndex = MediaStrip.startOf(start, media.size))
    val backdrop = MaterialTheme.colorScheme.surfaceVariant
    BoxWithConstraints(modifier.fillMaxWidth().bleedEnd(bleed)) {
        val layout = remember(maxWidth, media) { MediaStrip.layout(maxWidth.value, media.map(MediaStrip::aspectOf)) }
        LazyRow(
            state = row,
            modifier = Modifier.fillMaxWidth().height(layout.height.dp),
            contentPadding = PaddingValues(end = bleed),
            horizontalArrangement = Arrangement.spacedBy(MediaStrip.GAP.dp),
        ) {
            itemsIndexed(media, key = { index, item -> "$index:" + item.url }) { index, item ->
                val frame = Modifier.width(layout.widths[index].dp).fillMaxHeight().clip(MediaShape)
                Box(frame.border(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant, MediaShape)) {
                    ShownMedia(item, index, media.size, playIndex == index, ContentScale.Crop, backdrop, owner) { onOpen(index) }
                }
            }
        }
    }
}

/**
 * One of a post's media where it is shown (_media.dart's _TweetMediaItem):
 * "Tap to show Media" while it waits, else the tile with its ALT badge,
 * read as "Media 2/4" when there are several.
 */
@Composable
private fun ShownMedia(media: XMedia, index: Int, count: Int, playing: Boolean, scale: ContentScale, backdrop: Color, owner: MediaOwner, onOpen: () -> Unit) {
    var revealed by rememberSaveable(media.url) { mutableStateOf(false) }
    var describing by remember { mutableStateOf(false) }
    val url = photoUrl(media.url, rememberPhotoSize())
    val word = stringResource(Res.string.reader_media)
    val shown = mediaShown(url, revealed) { revealed = true }
    val saver = LocalMediaSaver.current
    Box(Modifier.fillMaxSize().semantics { contentDescription = if (count > 1) "$word ${index + 1}/$count" else word }) {
        if (!shown) {
            ShowMediaTile(word, MediaIcons.ImageOutlined, onShow = { revealed = true }, modifier = Modifier.fillMaxSize())
            return@Box
        }
        // A long press on a photo shows what it is of, or saves it when it says nothing.
        val onLongPress: (() -> Unit)? = if (media.kind != XMedia.Kind.PHOTO) null else ({ describeOrSave(media, owner, saver) { describing = true } })
        MediaTile(media, playing, url, scale, owner.post, Modifier.fillMaxSize().background(backdrop), onLongPress, onOpen)
        media.alt?.let { AltBadge(it, Modifier.align(Alignment.BottomStart)) }
    }
    if (describing) media.alt?.let { AltTextDialog(it) { describing = false } }
}

/** A described photo's description ([describe]), else the photo saved through [saver] under its owner's name (_media.dart's long press). */
private fun describeOrSave(media: XMedia, owner: MediaOwner, saver: MediaSaver?, describe: () -> Unit) {
    if (media.alt != null) {
        describe()
    } else {
        saver?.let { MediaFiles.save(media, owner.postId, owner.username)?.let(it::save) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaTile(media: XMedia, playing: Boolean, url: String, scale: ContentScale, post: XPost?, modifier: Modifier, onLongClick: (() -> Unit)?, onClick: () -> Unit) {
    Box(modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        AsyncImage(
            model = url,
            contentDescription = media.alt,
            contentScale = scale,
            modifier = Modifier.fillMaxSize(),
        )
        when (media.kind) {
            XMedia.Kind.PHOTO -> Unit
            XMedia.Kind.VIDEO -> {
                PlayBadge(Modifier.align(Alignment.Center))
                // Clear of the ALT badge, which takes the start.
                media.durationMs?.let { Label(duration(it), Modifier.align(if (media.alt != null) Alignment.BottomEnd else Alignment.BottomStart)) }
                TileVideo(media, url, post, playing, Modifier.matchParentSize())
            }
            XMedia.Kind.GIF -> {
                TileGif(media, url, playing, Modifier.matchParentSize())
                MediaBadge(Modifier.align(Alignment.BottomEnd).padding(8.dp), icon = MediaIcons.GifBoxOutlined, description = stringResource(Res.string.posts_filter_videos))
            }
        }
    }
}

/** Lets what this holds run [bleed] on past the end of the room it is given, as a row running off the screen's edge does. */
private fun Modifier.bleedEnd(bleed: Dp): Modifier = if (bleed <= 0.dp) this else layout { measurable, constraints ->
    val extra = bleed.roundToPx()
    val wider = if (constraints.hasBoundedWidth) constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra) else constraints
    val placeable = measurable.measure(wider)
    layout((placeable.width - extra).coerceAtLeast(0), placeable.height) { placeable.placeRelative(0, 0) }
}

/** The round play sign over a video's poster. */
@Composable
internal fun PlayBadge(modifier: Modifier) {
    Box(
        modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun Label(text: String, modifier: Modifier) {
    Text(
        text,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

private fun duration(ms: Long): String {
    val seconds = ms / 1000
    return if (seconds >= 3600) {
        "%d:%02d:%02d".format(seconds / 3600, seconds % 3600 / 60, seconds % 60)
    } else {
        "%d:%02d".format(seconds / 60, seconds % 60)
    }
}
