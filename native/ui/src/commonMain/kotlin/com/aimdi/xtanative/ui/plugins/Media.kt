package com.aimdi.xtanative.ui.plugins

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.aimdi.xtanative.store.settings.MediaPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.common.AltBadge
import com.aimdi.xtanative.ui.common.AltTextDialog
import com.aimdi.xtanative.ui.common.IconTip
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.common.LocalSettingsStore
import com.aimdi.xtanative.ui.common.ShowMediaTile
import com.aimdi.xtanative.ui.common.mediaShown
import com.aimdi.xtanative.ui.common.onSecondaryClick
import com.aimdi.xtanative.ui.common.onSecondaryClickAt
import com.aimdi.xtanative.ui.common.rememberPicture
import com.aimdi.xtanative.ui.common.turnTo
import com.aimdi.xtanative.ui.downloads.PluginDownloads
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.close
import com.aimdi.xtanative.ui.generated.resources.media_alt_title
import com.aimdi.xtanative.ui.generated.resources.posts_open_post
import com.aimdi.xtanative.ui.generated.resources.reader_download
import com.aimdi.xtanative.ui.generated.resources.reader_link_copied
import com.aimdi.xtanative.ui.generated.resources.reader_link_share
import com.aimdi.xtanative.ui.generated.resources.reader_media
import com.aimdi.xtanative.ui.home.HoldsKeys
import com.aimdi.xtanative.ui.home.skippedByTab
import com.aimdi.xtanative.ui.media.GifCorner
import com.aimdi.xtanative.ui.media.LocalViewerVideoKeys
import com.aimdi.xtanative.ui.media.MOTION_FAST_MS
import com.aimdi.xtanative.ui.media.MediaIcons
import com.aimdi.xtanative.ui.media.PageKey
import com.aimdi.xtanative.ui.media.ReaderIcons
import com.aimdi.xtanative.ui.media.Shift
import com.aimdi.xtanative.ui.media.SourceVideoPage
import com.aimdi.xtanative.ui.media.TileCorner
import com.aimdi.xtanative.ui.media.TileGif
import com.aimdi.xtanative.ui.media.TileVideo
import com.aimdi.xtanative.ui.media.VideoKey
import com.aimdi.xtanative.ui.media.VideoQuality
import com.aimdi.xtanative.ui.media.VideoRules
import com.aimdi.xtanative.ui.media.VideoSource
import com.aimdi.xtanative.ui.media.ViewerKey
import com.aimdi.xtanative.ui.media.ViewerZoom
import com.aimdi.xtanative.ui.media.pageKeyOf
import com.aimdi.xtanative.ui.media.rememberVideoPlayers
import com.aimdi.xtanative.ui.media.rememberViewerFileMenu
import com.aimdi.xtanative.ui.post.PostIcons
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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
            // A blurred video only says how long it is; one shown has the tile's own corners (TileVideo).
            MediaItem.Kind.VIDEO -> if (hidden) TileCorner(VideoRules.cornerTimeMs(null, item.durationMs), play = true, Modifier.align(Alignment.BottomStart))
            MediaItem.Kind.GIF -> GifCorner(Modifier.align(Alignment.BottomStart))
        }
        if (moving && !hidden) TileClip(item, poster, Modifier.matchParentSize())
        // XTA's badge sits at the start; a video's own label holds that corner, and its mute switch the other.
        onDescribe?.let { AltBadge(it, Modifier.align(if (item.kind == MediaItem.Kind.PHOTO) Alignment.BottomStart else Alignment.TopStart)) }
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
    val settingsStore = LocalSettingsStore.current
    if (item.kind == MediaItem.Kind.GIF) {
        val source = remember(url) { VideoSource.of(url, true, settingsStore.state.value) }
        return TileGif(source, poster, modifier)
    }
    val save = LocalMediaSave.current
    TileVideo({ VideoSource.of(url, false, it) }, item.alt, item.aspectRatio, item.durationMs, poster, modifier) {
        save?.takeIf { PluginDownloads.url(item) != null }?.let { saves -> { _ -> saves(item) } }
    }
}

/**
 * [items] full screen from [start], as the app's viewer shows X's: swipe
 * between them (or the arrow keys), pinch, double-tap or wheel a photo to
 * zoom (or + - and 0, the arrows then moving about it); videos play, GIFs
 * loop, and a video shown has the viewer's player keys (Space or k, j, l,
 * m, f, < >, 0 to 9). A video may play another of the files its network
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
    // The desktop's right-click menu, Ctrl+C and Ctrl+S, as the app's own viewer has them.
    val fileMenu = rememberViewerFileMenu(snackbar, refocus = { runCatching { focus.requestFocus() } }) { items.getOrNull(pager.currentPage)?.let(PluginDownloads::viewerSave) }
    // The zoom and pan keys for the photo shown, which pages' photos are zoomed in, and the video shown's keys, as the app's viewer has them.
    val zoomKeys = remember { MutableSharedFlow<ViewerKey>(extraBufferCapacity = 8) }
    val zoomedPages = remember { mutableStateMapOf<Int, Boolean>() }
    val videoKeys = remember { MutableSharedFlow<VideoKey>(extraBufferCapacity = 8) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { event ->
                val shown = items.getOrNull(pager.currentPage)?.kind
                val key = when (val pageKey = pageKeyOf(event, onVideo = shown == MediaItem.Kind.VIDEO || shown == MediaItem.Kind.GIF, zoomed = zoomedPages[pager.currentPage] == true)) {
                    is PageKey.Video -> return@onKeyEvent true.also { videoKeys.tryEmit(pageKey.key) }
                    is PageKey.Viewer -> pageKey.key
                    PageKey.None -> return@onKeyEvent false
                }
                when (key) {
                    ViewerKey.PREVIOUS -> true.also { turn(-1) }
                    ViewerKey.NEXT -> true.also { turn(1) }
                    ViewerKey.FIRST -> true.also { turn(-pager.currentPage) }
                    ViewerKey.LAST -> true.also { turn(items.size - 1 - pager.currentPage) }
                    ViewerKey.CLOSE -> true.also { onClose() }
                    ViewerKey.COPY, ViewerKey.SAVE_AS -> fileMenu?.key(key) ?: false
                    // The photo shown zooms and moves about, as the app's viewer's; a video's page leaves the keys alone.
                    ViewerKey.ZOOM_IN, ViewerKey.ZOOM_OUT, ViewerKey.ZOOM_RESET, ViewerKey.PAN_LEFT, ViewerKey.PAN_RIGHT, ViewerKey.PAN_UP, ViewerKey.PAN_DOWN ->
                        (shown == MediaItem.Kind.PHOTO).also { if (it) zoomKeys.tryEmit(key) }
                    ViewerKey.NONE -> false
                }
            }
            // It holds the keys, yet Tab goes round the viewer's buttons without stopping on it, where nothing would show.
            .skippedByTab()
            .focusRequester(focus)
            .focusable(),
    ) {
        if (items.isEmpty()) {
            LaunchedEffect(Unit) { onClose() }
            return@Box
        }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        // Closed, it leaves the keys to what it covered, which would otherwise have none.
        HoldsKeys(focus)
        val pageMenu = if (fileMenu != null) Modifier.onSecondaryClick(fileMenu::openForShown) else Modifier
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().then(pageMenu), key = { it }) { page ->
            val item = items[page]
            when (item.kind) {
                MediaItem.Kind.PHOTO -> ZoomingImage(
                    rememberPicture(item.imageUrl),
                    item.alt,
                    Modifier.fillMaxSize(),
                    zoomKeys = zoomKeys.takeIf { pager.currentPage == page },
                    onZoomed = { zoomedPages[page] = it },
                )
                MediaItem.Kind.VIDEO, MediaItem.Kind.GIF -> CompositionLocalProvider(LocalViewerVideoKeys provides videoKeys) {
                    QualityVideoPage(item, qualities.getOrNull(page).orEmpty(), active = pager.currentPage == page)
                }
            }
        }
        IconTip(stringResource(Res.string.close)) { IconButton(onClick = onClose, modifier = Modifier.statusBarsPadding().padding(8.dp).align(Alignment.TopStart)) {
            Icon(Icons.Filled.Close, stringResource(Res.string.close), tint = Color.White)
        } }
        PagerBar(items, pager, onDownload, onOpenPost, snackbar, Modifier.statusBarsPadding().padding(8.dp).align(Alignment.TopEnd))
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
    fileMenu?.Menu()
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
            IconTip(stringResource(Res.string.posts_open_post)) { IconButton(onClick = onOpenPost) { Icon(PostIcons.OpenInNew, stringResource(Res.string.posts_open_post), tint = Color.White) } }
        }
        shown.description?.let { description ->
            IconTip(stringResource(Res.string.media_alt_title)) { IconButton(onClick = { describing = description }) { Icon(ReaderIcons.ClosedCaption, stringResource(Res.string.media_alt_title), tint = Color.White) } }
        }
        if (onDownload != null && PluginDownloads.url(shown) != null) {
            IconTip(stringResource(Res.string.reader_download)) { IconButton(onClick = { onDownload(shown) }) { Icon(ReaderIcons.Download, stringResource(Res.string.reader_download), tint = Color.White) } }
        }
        IconTip(stringResource(Res.string.reader_link_share)) { IconButton(onClick = { if (!share(shown.sharedUrl)) scope.launch { snackbar.showSnackbar(copied) } }) {
            Icon(Icons.Filled.Share, stringResource(Res.string.reader_link_share), tint = Color.White)
        } }
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
    val settingsStore = LocalSettingsStore.current
    val players = rememberVideoPlayers()
    if (players != null) {
        val source = remember(item, qualities) { VideoSource.of(item.videoUrl, qualities, item.kind == MediaItem.Kind.GIF, settingsStore.state.value) }
        val poster = rememberPicture(item.imageUrl)
        return SourceVideoPage(source, item.aspectRatio, active, players, onPlaying = {}, onDownload = null) {
            AsyncImage(model = poster, contentDescription = item.alt, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
    }
    var chosen by rememberSaveable(item.videoUrl) {
        mutableStateOf(VideoRules.defaultUrl(qualities, item.videoUrl, (settingsStore.state.value ?: Settings())[MediaPrefs.videoQuality]))
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
 * A picture fitted to its box that zooms as the viewer's photos do: pinched
 * or wheeled between one and four times (the wheel about the pointer), and a
 * double tap toggles between the two around the point tapped; one finger (or
 * the mouse) pans only once zoomed, so a pager around it still swipes, and a
 * zoomed picture stays inside its box. [model] is what Coil's AsyncImage
 * loads (an address, or a request with headers, [rememberImageRequest]);
 * [onTap] and [onLongPress] hear where the picture was touched, and [onState]
 * how its load goes.
 */
@Composable
fun ZoomableImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onTap: ((Offset) -> Unit)? = null,
    onLongPress: ((Offset) -> Unit)? = null,
    onState: ((AsyncImagePainter.State) -> Unit)? = null,
) = ZoomingImage(model, contentDescription, modifier, onTap, onLongPress, onState)

/**
 * [ZoomableImage], and in [MediaPager] the zoom and pan keys too: [zoomKeys]
 * ([ViewerKey.ZOOM_IN] and the rest) zoom about the middle and move a zoomed
 * picture about, and [onZoomed] hears whether it is zoomed in, so the arrows
 * pan rather than turn the page.
 */
@Composable
internal fun ZoomingImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onTap: ((Offset) -> Unit)? = null,
    onLongPress: ((Offset) -> Unit)? = null,
    onState: ((AsyncImagePainter.State) -> Unit)? = null,
    zoomKeys: Flow<ViewerKey>? = null,
    onZoomed: (Boolean) -> Unit = {},
) {
    var scale by remember { mutableFloatStateOf(ViewerZoom.MIN) }
    var shift by remember { mutableStateOf(Shift.ZERO) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    val motion = !LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val animation = remember { arrayOfNulls<Job>(1) }

    fun animateTo(toScale: Float, toShift: Shift) {
        animation[0]?.cancel()
        val fromScale = scale
        val fromShift = shift
        animation[0] = scope.launch {
            animate(0f, 1f, animationSpec = tween(if (motion) MOTION_FAST_MS else 0)) { t, _ ->
                scale = fromScale + (toScale - fromScale) * t
                shift = fromShift + (toShift - fromShift) * t
            }
        }
    }

    // The keys' zoom goes about the middle, eased as a double tap's; the arrows move a zoomed picture about.
    LaunchedEffect(zoomKeys) {
        zoomKeys?.collect { key ->
            val width = area.width.toFloat()
            val height = area.height.toFloat()
            val to = ViewerZoom.keyed(scale, key)
            when {
                to != scale -> animateTo(to, if (ViewerZoom.zoomed(to)) ViewerZoom.shiftFor(Shift.ZERO, scale, shift, to, width, height) else Shift.ZERO)
                ViewerZoom.zoomed(scale) -> animateTo(scale, ViewerZoom.panned(shift, scale, key, width, height))
            }
        }
    }
    val zoomedNow by rememberUpdatedState(onZoomed)
    LaunchedEffect(Unit) { snapshotFlow { ViewerZoom.zoomed(scale) }.collect { zoomedNow(it) } }

    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        onState = onState,
        modifier = modifier
            .onSizeChanged { area = it }
            .clipToBounds()
            // A mouse gives no long press: its right-click does the same.
            .then(onLongPress?.let { Modifier.onSecondaryClickAt(action = it) } ?: Modifier)
            // The mouse wheel zooms about the pointer, as a desktop's photo viewers do.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type != PointerEventType.Scroll) continue
                        val change = event.changes.firstOrNull() ?: continue
                        val to = ViewerZoom.wheeled(scale, change.scrollDelta.y)
                        if (to != scale) {
                            animation[0]?.cancel()
                            val focus = Shift(change.position.x - size.width / 2f, change.position.y - size.height / 2f)
                            shift = ViewerZoom.shiftFor(focus, scale, shift, to, size.width.toFloat(), size.height.toFloat())
                            scale = to
                        }
                        change.consume()
                    }
                }
            }
            .pointerInput(onTap, onLongPress) {
                detectTapGestures(
                    onTap = onTap,
                    onLongPress = onLongPress,
                    onDoubleTap = { tap ->
                        val focus = Shift(tap.x - size.width / 2f, tap.y - size.height / 2f)
                        val to = ViewerZoom.doubleTapScale(if (ViewerZoom.zoomed(scale)) scale else ViewerZoom.MIN)
                        animateTo(to, ViewerZoom.shiftFor(focus, scale, shift, to, size.width.toFloat(), size.height.toFloat()))
                    },
                )
            }
            .pointerInput(Unit) {
                // Two fingers zoom about their middle; one finger pans only once zoomed, so the pager still swipes.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } > 1 || ViewerZoom.zoomed(scale)) {
                            animation[0]?.cancel()
                            val width = size.width.toFloat()
                            val height = size.height.toFloat()
                            val next = ViewerZoom.pinched(scale, event.calculateZoom()).coerceAtLeast(ViewerZoom.MIN)
                            val centroid = event.calculateCentroid(useCurrent = true)
                            val pan = event.calculatePan()
                            val focus = if (centroid == Offset.Unspecified) Shift.ZERO else Shift(centroid.x - width / 2f, centroid.y - height / 2f)
                            shift = ViewerZoom.bounds(focus - (focus - shift) * (next / scale) + Shift(pan.x, pan.y), next, width, height)
                            scale = next
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = shift.x
                translationY = shift.y
            },
    )
}

/** A video or GIF in the viewer where the platform has no player of its own: its poster, and on the desktop a click that plays it outside the app. */
@Composable
internal expect fun ItemVideoOutside(item: MediaItem)
