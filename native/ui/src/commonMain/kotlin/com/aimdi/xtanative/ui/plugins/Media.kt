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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import coil3.compose.AsyncImagePainter
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.close
import com.aimdi.xtanative.ui.generated.resources.reader_download
import com.aimdi.xtanative.ui.downloads.PluginDownloads
import com.aimdi.xtanative.ui.media.LocalInlinePlayer
import com.aimdi.xtanative.ui.media.ReaderIcons
import com.aimdi.xtanative.ui.media.VideoQuality
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
            model = rememberMediaModel(item.tileUrl),
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
 * A video may play another of the files its network offers: [qualities]
 * holds, item by item, a video's files highest first, named as the quality
 * menu lists them ("720p"); one with more than one has the menu and starts
 * on [MediaItem.videoUrl]. With [onDownload], the bar saves the one shown
 * where it has a file of its own (a photo, a video's file; never a stream).
 * With nothing to show, it closes.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaPager(
    items: List<MediaItem>,
    start: Int,
    onClose: () -> Unit,
    qualities: List<List<VideoQuality>> = emptyList(),
    onDownload: ((MediaItem) -> Unit)? = null,
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (items.isEmpty()) {
            LaunchedEffect(Unit) { onClose() }
            return@Box
        }
        val pager = rememberPagerState(initialPage = start.coerceIn(0, items.lastIndex)) { items.size }
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { it }) { page ->
            val item = items[page]
            when (item.kind) {
                MediaItem.Kind.PHOTO -> ZoomableImage(rememberMediaModel(item.imageUrl), item.alt, Modifier.fillMaxSize())
                MediaItem.Kind.VIDEO, MediaItem.Kind.GIF -> QualityVideoPage(item, qualities.getOrNull(page).orEmpty(), active = pager.currentPage == page)
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.statusBarsPadding().padding(8.dp).align(Alignment.TopStart)) {
            Icon(Icons.Filled.Close, stringResource(Res.string.close), tint = Color.White)
        }
        Row(Modifier.statusBarsPadding().padding(8.dp).align(Alignment.TopEnd), verticalAlignment = Alignment.CenterVertically) {
            if (items.size > 1) Text("${pager.currentPage + 1} / ${items.size}", color = Color.White, modifier = Modifier.padding(horizontal = 12.dp))
            val shown = items.getOrNull(pager.currentPage)
            if (onDownload != null && shown != null && PluginDownloads.url(shown) != null) {
                IconButton(onClick = { onDownload(shown) }) { Icon(ReaderIcons.Download, stringResource(Res.string.reader_download), tint = Color.White) }
            }
        }
    }
}

/** A video page playing the quality chosen in its menu, the item's own file until one is. */
@Composable
private fun QualityVideoPage(item: MediaItem, qualities: List<VideoQuality>, active: Boolean) {
    var chosen by rememberSaveable(item.videoUrl) { mutableStateOf(item.videoUrl) }
    val url = playedUrl(item, qualities, chosen)
    Box(Modifier.fillMaxSize()) {
        key(url) { ItemVideoPage(item.copy(videoUrl = url), active) }
        if (qualities.size > 1) {
            QualityMenu(qualities, url, { chosen = it }, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 52.dp, end = 8.dp))
        }
    }
}

@Composable
private fun QualityMenu(qualities: List<VideoQuality>, playing: String?, onChoose: (String) -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        TextButton(onClick = { open = true }) {
            Text(qualityOf(playing, qualities)?.label ?: qualities.first().label, color = Color.White)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = Color.White)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            qualities.forEach { quality ->
                DropdownMenuItem(
                    text = { Text(quality.label) },
                    trailingIcon = if (quality.url == playing) { { Icon(Icons.Filled.Check, contentDescription = null) } } else null,
                    onClick = {
                        open = false
                        onChoose(quality.url)
                    },
                )
            }
        }
    }
}

/**
 * A picture fitted to its box that pinches and double-taps to zoom, as the
 * viewer's photos do: one finger pans only once zoomed, so a pager around it
 * still swipes. [model] is what Coil's AsyncImage loads (an address, or a
 * request with headers, [rememberImageRequest]); [onTap] and [onLongPress]
 * hear where the picture was touched, and [onState] how its load goes.
 */
@Composable
fun ZoomableImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onTap: ((Offset) -> Unit)? = null,
    onLongPress: ((Offset) -> Unit)? = null,
    onState: ((AsyncImagePainter.State) -> Unit)? = null,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        onState = onState,
        modifier = modifier
            .pointerInput(onTap, onLongPress) {
                detectTapGestures(
                    onTap = onTap,
                    onLongPress = onLongPress,
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
