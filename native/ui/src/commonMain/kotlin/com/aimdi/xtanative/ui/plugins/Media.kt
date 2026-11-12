package com.aimdi.xtanative.ui.plugins

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.aimdi.xtanative.ui.common.AltBadge
import com.aimdi.xtanative.ui.common.AltTextDialog
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.common.rememberPicture
import com.aimdi.xtanative.ui.common.turnTo
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.close
import com.aimdi.xtanative.ui.generated.resources.media_alt_title
import com.aimdi.xtanative.ui.generated.resources.posts_open_post
import com.aimdi.xtanative.ui.generated.resources.reader_download
import com.aimdi.xtanative.ui.downloads.PluginDownloads
import com.aimdi.xtanative.ui.generated.resources.reader_link_copied
import com.aimdi.xtanative.ui.generated.resources.reader_link_share
import com.aimdi.xtanative.store.settings.MediaPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.ShowMediaTile
import com.aimdi.xtanative.ui.common.mediaShown
import com.aimdi.xtanative.ui.generated.resources.reader_media
import com.aimdi.xtanative.ui.media.MediaIcons
import com.aimdi.xtanative.ui.media.ReaderIcons
import com.aimdi.xtanative.ui.media.SourceVideoPage
import com.aimdi.xtanative.ui.media.TileGif
import com.aimdi.xtanative.ui.media.TileVideo
import com.aimdi.xtanative.ui.media.VideoQuality
import com.aimdi.xtanative.ui.media.VideoRules
import com.aimdi.xtanative.ui.media.VideoSource
import com.aimdi.xtanative.ui.media.rememberVideoPlayers
import com.aimdi.xtanative.ui.post.PlayBadge
import com.aimdi.xtanative.ui.post.PostIcons
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private val Gap = 2.dp

/**
 * How a long press on a photo's tile saves it, as XTA's tiles do: where the
 * app draws a plugin's screens and its posts, through that plugin's
 * downloads; null where nothing saves it.
 */
val LocalMediaSave = staticCompositionLocalOf<((MediaItem) -> Unit)?> { null }

/**
 * Photos and videos laid out as the app lays out X's: one at its shape, two
 * side by side, three, or four in a square. Videos play in their tiles as X's
 * do, under Settings › Media (in a feed, the one seen most plays by itself
 * where Autoplay videos says; [playing], which chose one before, is kept
 * for the plugins built against it), GIFs loop, and while media loads manually a video
 * waits behind "Tap to show Media"; with [hidden], the pictures are blurred.
 * A described one carries "ALT", and a long press shows its description, or
 * saves a photo where the screen saves them ([LocalMediaSave]), as XTA's
 * tiles (plugin_post_media.dart).
 */
@Composable
fun MediaTiles(items: List<MediaItem>, onOpen: (Int) -> Unit, modifier: Modifier = Modifier, playing: Boolean = false, hidden: Boolean = false) {
    if (items.isEmpty()) return
    val save = LocalMediaSave.current
    var describing by remember { mutableStateOf<String?>(null) }
    val shape = RoundedCornerShape(16.dp)
    val frame = modifier.fillMaxWidth().clip(shape).border(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant, shape)
    val tile: @Composable (Int, Modifier) -> Unit = { index, tileModifier ->
        val item = items[index]
        val described = item.description.takeIf { !hidden }
        val hold: (() -> Unit)? = when (if (hidden) TileHold.NOTHING else tileHold(item, save != null)) {
            TileHold.DESCRIPTION -> { { describing = described } }
            TileHold.SAVE -> save?.let { { it(item) } }
            TileHold.NOTHING -> null
        }
        MediaTile(item, hidden, tileModifier, onClick = { onOpen(index) }, onHold = hold, onDescribe = described?.let { { describing = it } })
    }
    describing?.let { AltTextDialog(it) { describing = null } }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaTile(item: MediaItem, hidden: Boolean, modifier: Modifier, onClick: () -> Unit, onHold: (() -> Unit)?, onDescribe: (() -> Unit)?) {
    val moving = item.kind != MediaItem.Kind.PHOTO
    var revealed by rememberSaveable(item.tileUrl) { mutableStateOf(false) }
    // A video and its poster wait for a tap while media loads manually, as XTA's PluginVideoGate holds them back.
    if (!mediaShown(item.tileUrl.takeIf { moving }, revealed) { revealed = true }) {
        ShowMediaTile(stringResource(Res.string.reader_media), MediaIcons.ImageOutlined, onShow = { if (hidden) onClick() else revealed = true }, modifier = modifier)
        return
    }
    val poster = rememberPicture(item.tileUrl)
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant).combinedClickable(onClick = onClick, onLongClick = onHold)) {
        AsyncImage(
            model = poster,
            contentDescription = item.alt,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().let { if (hidden) it.blur(24.dp) else it },
        )
        when (item.kind) {
            MediaItem.Kind.PHOTO -> Unit
            MediaItem.Kind.VIDEO -> {
                PlayBadge(Modifier.align(Alignment.Center))
                item.durationMs?.let { TileLabel(duration(it), Modifier.align(Alignment.BottomStart)) }
            }
            MediaItem.Kind.GIF -> TileLabel("GIF", Modifier.align(Alignment.BottomStart))
        }
        if (moving && !hidden) TileClip(item, poster, Modifier.matchParentSize())
        // XTA's badge sits at the start; a video's own label holds that corner.
        onDescribe?.let { AltBadge(it, Modifier.align(if (item.kind == MediaItem.Kind.PHOTO) Alignment.BottomStart else Alignment.BottomEnd)) }
    }
}

/**
 * A video playing in its tile as the app's X videos do (XTA's
 * PluginPostVideo in TweetVideo): with XTA's controls, Autoplay, Mute, Loop
 * and the player's settings; a GIF loops silently. Where nothing plays in
 * place it draws nothing, and the tile's tap opens the viewer.
 */
@Composable
private fun TileClip(item: MediaItem, poster: Any, modifier: Modifier) {
    val url = item.inlineVideoUrl ?: item.videoUrl ?: return
    val graph = LocalGraph.current
    if (item.kind == MediaItem.Kind.GIF) {
        val source = remember(url) { VideoSource.of(url, true, graph.settings.state.value) }
        return TileGif(source, poster, modifier)
    }
    val save = LocalMediaSave.current
    TileVideo({ VideoSource.of(url, false, it) }, item.alt, item.aspectRatio, poster, modifier) {
        save?.takeIf { PluginDownloads.url(item) != null }?.let { saves -> { saves(item) } }
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
 * between them (or the arrow keys), pinch or double-tap a photo to zoom;
 * videos play, GIFs loop. A video may play another of the files its network
 * offers: [qualities] holds, item by item, a video's files highest first,
 * named as the quality menu lists them ("720p"); one with more than one has
 * the menu and starts on [MediaItem.videoUrl]. The bar, as XTA's
 * (plugin_post_media.dart), has the counter, [onOpenPost]'s Open post where
 * it is given, the description of one that has it, with [onDownload] the
 * download of the one shown where it has a file of its own (a photo, a
 * video's file; never a stream), and Share link ([MediaItem.sharedUrl]).
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
    onOpenPost: (() -> Unit)? = null,
) {
    val focus = remember { FocusRequester() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val reduced = LocalReducedMotion.current
    val pager = rememberPagerState(initialPage = start.coerceIn(0, (items.size - 1).coerceAtLeast(0))) { items.size }
    val turn = { by: Int -> (pager.currentPage + by).takeIf { it in items.indices }?.let { page -> scope.launch { pager.turnTo(page, reduced) } } }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> true.also { turn(-1) }
                    Key.DirectionRight -> true.also { turn(1) }
                    Key.Escape -> true.also { onClose() }
                    else -> false
                }
            }
            .focusRequester(focus)
            .focusable(),
    ) {
        if (items.isEmpty()) {
            LaunchedEffect(Unit) { onClose() }
            return@Box
        }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { it }) { page ->
            val item = items[page]
            when (item.kind) {
                MediaItem.Kind.PHOTO -> ZoomableImage(rememberPicture(item.imageUrl), item.alt, Modifier.fillMaxSize())
                MediaItem.Kind.VIDEO, MediaItem.Kind.GIF -> QualityVideoPage(item, qualities.getOrNull(page).orEmpty(), active = pager.currentPage == page)
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.statusBarsPadding().padding(8.dp).align(Alignment.TopStart)) {
            Icon(Icons.Filled.Close, stringResource(Res.string.close), tint = Color.White)
        }
        PagerBar(items, pager, onDownload, onOpenPost, snackbar, Modifier.statusBarsPadding().padding(8.dp).align(Alignment.TopEnd))
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

/** [MediaPager] as plugin API 2 calls it, without Open post. */
@Deprecated("The viewer of plugin API 2", level = DeprecationLevel.HIDDEN)
@Composable
fun MediaPager(
    items: List<MediaItem>,
    start: Int,
    onClose: () -> Unit,
    qualities: List<List<VideoQuality>> = emptyList(),
    onDownload: ((MediaItem) -> Unit)? = null,
) = MediaPager(items, start, onClose, qualities, onDownload, onOpenPost = null)

/** The viewer's bar over the page shown: the counter, Open post, the description, Download and Share link. */
@Composable
private fun PagerBar(items: List<MediaItem>, pager: PagerState, onDownload: ((MediaItem) -> Unit)?, onOpenPost: (() -> Unit)?, snackbar: SnackbarHostState, modifier: Modifier) {
    val shown = items.getOrNull(pager.currentPage) ?: return
    val share = rememberShareText()
    val scope = rememberCoroutineScope()
    val copied = stringResource(Res.string.reader_link_copied)
    var describing by remember { mutableStateOf<String?>(null) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (items.size > 1) Text("${pager.currentPage + 1} / ${items.size}", color = Color.White, modifier = Modifier.padding(horizontal = 12.dp))
        if (onOpenPost != null) {
            IconButton(onClick = onOpenPost) { Icon(PostIcons.OpenInNew, stringResource(Res.string.posts_open_post), tint = Color.White) }
        }
        shown.description?.let { description ->
            IconButton(onClick = { describing = description }) { Icon(ReaderIcons.ClosedCaption, stringResource(Res.string.media_alt_title), tint = Color.White) }
        }
        if (onDownload != null && PluginDownloads.url(shown) != null) {
            IconButton(onClick = { onDownload(shown) }) { Icon(ReaderIcons.Download, stringResource(Res.string.reader_download), tint = Color.White) }
        }
        IconButton(onClick = { if (!share(shown.sharedUrl)) scope.launch { snackbar.showSnackbar(copied) } }) {
            Icon(Icons.Filled.Share, stringResource(Res.string.reader_link_share), tint = Color.White)
        }
    }
    describing?.let { AltTextDialog(it) { describing = null } }
}

/**
 * A video page in the app's player as its X videos play (XTA's TweetVideo):
 * at the quality Settings › Media picks among [qualities], the others in the
 * controls' menu, with Mute, Loop and the player's settings. Where the
 * platform has no player, the item's own file or the quality chosen in a
 * menu of its own, played outside the app ([ItemVideoOutside]).
 */
@Composable
private fun QualityVideoPage(item: MediaItem, qualities: List<VideoQuality>, active: Boolean) {
    val graph = LocalGraph.current
    val players = rememberVideoPlayers()
    if (players != null) {
        val source = remember(item, qualities) { VideoSource.of(item.videoUrl, qualities, item.kind == MediaItem.Kind.GIF, graph.settings.state.value) }
        val poster = rememberPicture(item.imageUrl)
        return SourceVideoPage(source, item.aspectRatio, active, players, onPlaying = {}, onDownload = null) {
            AsyncImage(model = poster, contentDescription = item.alt, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
    }
    var chosen by rememberSaveable(item.videoUrl) {
        mutableStateOf(VideoRules.defaultUrl(qualities, item.videoUrl, (graph.settings.state.value ?: Settings())[MediaPrefs.videoQuality]))
    }
    val url = playedUrl(item, qualities, chosen)
    Box(Modifier.fillMaxSize()) {
        key(url) { ItemVideoOutside(item.copy(videoUrl = url)) }
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

/** A video or GIF in the viewer where the platform has no player of its own: its poster, and on the desktop a click that plays it outside the app. */
@Composable
internal expect fun ItemVideoOutside(item: MediaItem)
