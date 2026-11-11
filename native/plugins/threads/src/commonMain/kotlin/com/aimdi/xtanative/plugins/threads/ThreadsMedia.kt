package com.aimdi.xtanative.plugins.threads

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aimdi.xtanative.networks.threads.ThreadsMedia
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.ui.plugins.MediaPager
import kotlin.math.roundToInt

private val MediaShape = RoundedCornerShape(16.dp)

/**
 * A post's pictures and videos (threads_media.dart): one at its own shape
 * across the width, several along one row at one height that scrolls
 * sideways, the next one's edge in view. A tap opens the app's viewer at
 * that one, with Open post where the post [opens] on a tap. Each tile loads
 * the size Meta offers that fills its pixels ([ThreadsMedia.tileUrl]); the
 * viewer shows, and a download saves, the largest.
 */
@Composable
internal fun ThreadsPostMedia(app: ThreadsApp, post: ThreadsPost, modifier: Modifier = Modifier, opens: Boolean = true) {
    val media = post.media
    if (media.isEmpty()) return
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val width = with(density) { maxWidth.toPx() }
        val layout = ThreadsStrip.layout(width, media.map { it.aspectRatio })
        if (media.size == 1) {
            val shape = ThreadsStrip.plugin(media.single().aspectRatio)
            MediaTile(media.single(), width.roundToInt(), (width / shape).roundToInt(), Modifier.fillMaxWidth().aspectRatio(shape)) { app.openMedia(post, 0, opens) }
        } else {
            val toDp = { px: Float -> with(density) { px.toDp() } }
            LazyRow(Modifier.fillMaxWidth().height(toDp(layout.height)), horizontalArrangement = Arrangement.spacedBy(ThreadsStrip.GAP.dp)) {
                itemsIndexed(media, key = { index, item -> "$index:${item.url}" }) { index, item ->
                    MediaTile(item, layout.widths[index].roundToInt(), layout.height.roundToInt(), Modifier.width(toDp(layout.widths[index])).height(toDp(layout.height))) {
                        app.openMedia(post, index, opens)
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaTile(media: ThreadsMedia, widthPx: Int, heightPx: Int, modifier: Modifier, onClick: () -> Unit) {
    val picture = remember(media, widthPx, heightPx) { media.tileUrl(widthPx, heightPx) }
    Box(
        modifier
            .clip(MediaShape)
            .border(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant, MediaShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = picture,
            contentDescription = media.alt,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.Medium,
            modifier = Modifier.fillMaxSize(),
        )
        if (media.isVideo) PlayBadge(Modifier.align(Alignment.Center))
    }
}

@Composable
internal fun PlayBadge(modifier: Modifier = Modifier) {
    Box(modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
    }
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

/** A little space under what is above: the gap between a post's parts. */
internal val PartGap = 10.dp

internal fun Modifier.partGap(): Modifier = padding(top = PartGap)
