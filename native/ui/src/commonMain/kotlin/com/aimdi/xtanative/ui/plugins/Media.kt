package com.aimdi.xtanative.ui.plugins

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.close
import com.aimdi.xtanative.ui.media.LocalInlinePlayer
import com.aimdi.xtanative.ui.post.PlayBadge
import org.jetbrains.compose.resources.stringResource

private val Gap = 2.dp

/**
 * Photos and videos laid out as the app lays out X's: one at its shape, two
 * side by side, three, or four in a square. With [playing], the first video
 * or GIF plays in its tile, muted; with [hidden], the pictures are blurred.
 */
@Composable
fun MediaTiles(items: List<MediaItem>, onOpen: (Int) -> Unit, modifier: Modifier = Modifier, playing: Boolean = false, hidden: Boolean = false) {
    if (items.isEmpty()) return
    val playIndex = if (playing && !hidden) items.indexOfFirst { it.kind != MediaItem.Kind.PHOTO } else -1
    val shape = RoundedCornerShape(16.dp)
    val frame = modifier.fillMaxWidth().clip(shape).border(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant, shape)
    val tile: @Composable (Int, Modifier) -> Unit = { index, tileModifier ->
        MediaTile(items[index], playIndex == index, hidden, tileModifier) { onOpen(index) }
    }
    when (items.size) {
        1 -> tile(0, frame.aspectRatio((items[0].aspectRatio ?: (16f / 9f)).coerceIn(0.8f, 2f)))
        2 -> Row(frame.aspectRatio(16f / 9f), horizontalArrangement = Arrangement.spacedBy(Gap)) {
            tile(0, Modifier.weight(1f).fillMaxHeight())
            tile(1, Modifier.weight(1f).fillMaxHeight())
        }
        3 -> Row(frame.aspectRatio(16f / 9f), horizontalArrangement = Arrangement.spacedBy(Gap)) {
            tile(0, Modifier.weight(1f).fillMaxHeight())
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Gap)) {
                tile(1, Modifier.weight(1f).fillMaxWidth())
                tile(2, Modifier.weight(1f).fillMaxWidth())
            }
        }
        else -> Column(frame.aspectRatio(16f / 9f), verticalArrangement = Arrangement.spacedBy(Gap)) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Gap)) {
                tile(0, Modifier.weight(1f).fillMaxHeight())
                tile(1, Modifier.weight(1f).fillMaxHeight())
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Gap)) {
                tile(2, Modifier.weight(1f).fillMaxHeight())
                tile(3, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun MediaTile(item: MediaItem, playing: Boolean, hidden: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick)) {
        AsyncImage(
            model = item.tileUrl,
            contentDescription = item.alt,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().let { if (hidden) it.blur(24.dp) else it },
        )
        val player = LocalInlinePlayer.current
        val playable = item.inlineVideoUrl ?: item.videoUrl
        if (playing && player != null && playable != null) player.Video(playable, Modifier.fillMaxSize())
        when (item.kind) {
            MediaItem.Kind.PHOTO -> Unit
            MediaItem.Kind.VIDEO -> {
                PlayBadge(Modifier.align(Alignment.Center))
                item.durationMs?.let { TileLabel(duration(it), Modifier.align(Alignment.BottomStart)) }
            }
            MediaItem.Kind.GIF -> TileLabel("GIF", Modifier.align(Alignment.BottomStart))
        }
    }
}

@Composable
private fun TileLabel(text: String, modifier: Modifier) {
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
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds % 3600 / 60, seconds % 60) else "%d:%02d".format(seconds / 60, seconds % 60)
}

/**
 * [items] full screen from [start], as the app's viewer shows X's: swipe
 * between them, pinch or double-tap a photo to zoom; videos play, GIFs loop.
 * With nothing to show, it closes.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaPager(items: List<MediaItem>, start: Int, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (items.isEmpty()) {
            LaunchedEffect(Unit) { onClose() }
            return@Box
        }
        val pager = rememberPagerState(initialPage = start.coerceIn(0, items.lastIndex)) { items.size }
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { it }) { page ->
            val item = items[page]
            when (item.kind) {
                MediaItem.Kind.PHOTO -> ZoomableImage(item.imageUrl, item.alt)
                MediaItem.Kind.VIDEO, MediaItem.Kind.GIF -> ItemVideoPage(item, active = pager.currentPage == page)
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.statusBarsPadding().padding(8.dp).align(Alignment.TopStart)) {
            Icon(Icons.Filled.Close, stringResource(Res.string.close), tint = Color.White)
        }
        if (items.size > 1) {
            Text(
                "${pager.currentPage + 1} / ${items.size}",
                color = Color.White,
                modifier = Modifier.statusBarsPadding().padding(20.dp).align(Alignment.TopEnd),
            )
        }
    }
}

@Composable
private fun ZoomableImage(url: String, alt: String?) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    AsyncImage(
        model = url,
        contentDescription = alt,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            offset = (Offset(size.width / 2f, size.height / 2f) - tap) * 1.5f
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                // Two fingers zoom; one finger pans only once zoomed, so the pager still swipes.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1 || scale > 1f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                            offset = if (scale == 1f) Offset.Zero else offset + event.calculatePan()
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}

/** A video or GIF in the viewer, playing while it is the page shown; each platform plays it its own way. */
@Composable
internal expect fun ItemVideoPage(item: MediaItem, active: Boolean)
