package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.store.settings.MediaPrefs
import com.aimdi.xtanative.ui.common.AltBadge
import com.aimdi.xtanative.ui.common.AltTextDialog
import com.aimdi.xtanative.ui.plugins.LocalMediaSave
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.MediaPager
import com.aimdi.xtanative.ui.plugins.MediaTiles
import com.aimdi.xtanative.ui.plugins.TileHold
import com.aimdi.xtanative.ui.plugins.tileHold
import kotlin.math.roundToInt

private val MediaShape = RoundedCornerShape(16.dp)

/**
 * A post's pictures and videos (threads_media.dart): one at its own shape
 * across the width, several along one row at one height that scrolls
 * sideways, the next one's edge in view, with a dot for each under it. A tap opens the app's viewer at
 * that one, with Open post where the post [opens] on a tap; in a
 * conversation ([LocalThreadsGallery]) among all its pictures. Each tile loads
 * the size Meta offers that fills its pixels ([ThreadsMediaItems.tile]); the
 * viewer shows, and a download saves, the largest.
 */
@Composable
internal fun ThreadsPostMedia(app: ThreadsApp, post: ThreadsPost, modifier: Modifier = Modifier, opens: Boolean = true) {
    val media = post.media
    if (media.isEmpty()) return
    val settings by app.prefs.collectAsState()
    val quality = settings[MediaPrefs.videoQuality]
    val gallery = LocalThreadsGallery.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val width = with(density) { maxWidth.toPx() }
        val layout = ThreadsStrip.layout(width, media.map { it.aspectRatio })
        val tile: @Composable (Int, Float, Float, Modifier) -> Unit = { index, wide, tall, sized ->
            val item = remember(post, index, quality, wide, tall) { ThreadsMediaItems.tile(post, media[index], quality, wide.roundToInt(), tall.roundToInt()) }
            MediaTile(item, sized) { app.openMedia(post, index, opens, gallery) }
        }
        if (media.size == 1) {
            val shape = ThreadsStrip.plugin(media.single().aspectRatio)
            tile(0, width, width / shape, Modifier.fillMaxWidth().aspectRatio(shape))
        } else {
            val toDp = { px: Float -> with(density) { px.toDp() } }
            val row = rememberLazyListState()
            Column(Modifier.fillMaxWidth()) {
                LazyRow(Modifier.fillMaxWidth().height(toDp(layout.height)), state = row, horizontalArrangement = Arrangement.spacedBy(ThreadsStrip.GAP.dp)) {
                    itemsIndexed(media, key = { index, item -> "$index:${item.url}" }) { index, _ ->
                        tile(index, layout.widths[index], layout.height, Modifier.width(toDp(layout.widths[index])).height(toDp(layout.height)))
                    }
                }
                StripDots(row, layout.widths)
            }
        }
    }
}

/** Which of a row's tiles is in view, as a dot for each: more than XTA draws, so a reader sees how many there are and where they are. */
@Composable
private fun StripDots(row: LazyListState, widths: List<Float>) {
    val shown by remember(row, widths) {
        derivedStateOf {
            val first = row.firstVisibleItemIndex
            ThreadsStrip.shown(first, row.firstVisibleItemScrollOffset, widths.getOrElse(first) { 0f }, !row.canScrollForward, widths.size)
        }
    }
    val on = MaterialTheme.colorScheme.onSurfaceVariant
    val off = MaterialTheme.colorScheme.outlineVariant
    Row(Modifier.fillMaxWidth().padding(top = 8.dp).clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
        widths.indices.forEach { index -> Box(Modifier.size(6.dp).clip(CircleShape).background(if (index == shown) on else off)) }
    }
}

/**
 * One tile, as XTA's (plugin_post_media.dart): a video plays in place as
 * the app's X videos do, under Settings › Media; a described picture
 * carries "ALT", and a long press shows its description, or saves a
 * picture that has none where the screen saves them.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaTile(item: MediaItem, modifier: Modifier, onClick: () -> Unit) {
    if (item.kind != MediaItem.Kind.PHOTO) return MediaTiles(listOf(item), onOpen = { onClick() }, modifier = modifier, playing = true)
    val save = LocalMediaSave.current
    var describing by remember { mutableStateOf(false) }
    val description = item.description
    val hold: (() -> Unit)? = when (tileHold(item, save != null)) {
        TileHold.DESCRIPTION -> { { describing = true } }
        TileHold.SAVE -> save?.let { { it(item) } }
        TileHold.NOTHING -> null
    }
    Box(
        modifier
            .clip(MediaShape)
            .border(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant, MediaShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = hold),
    ) {
        AsyncImage(
            model = item.tileUrl,
            contentDescription = item.alt,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.Medium,
            modifier = Modifier.fillMaxSize(),
        )
        if (description != null) AltBadge({ describing = true }, Modifier.align(Alignment.BottomStart))
    }
    if (describing && description != null) AltTextDialog(description) { describing = false }
}

/** The viewer the post's media opened in: the app's, with each video's qualities in its menu and Download saving the one shown through the app's downloads. */
@Composable
internal fun ThreadsMediaScreen(app: ThreadsApp, route: ThreadsRoute.Media) {
    val viewed = app.shelf.viewed(route.media)
    val post = viewed?.post
    MediaPager(
        viewed?.items.orEmpty(),
        route.start,
        onClose = app.host::back,
        qualities = viewed?.qualities.orEmpty(),
        onDownload = { app.host.download(it) },
        onOpenPost = post?.let { { app.openPost(it) } },
    )
}

/** The posts of the conversation on screen, in order, whose pictures a viewer opened there swipes through; null elsewhere. */
internal val LocalThreadsGallery = staticCompositionLocalOf<List<ThreadsPost>?> { null }

/** A little space under what is above: the gap between a post's parts. */
internal val PartGap = 10.dp

internal fun Modifier.partGap(): Modifier = padding(top = PartGap)
