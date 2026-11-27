package com.aimdi.xtanative.ui.media

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.core.x.model.PhotoSize
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.photoUrl
import com.aimdi.xtanative.data.LocalPostCache
import com.aimdi.xtanative.ui.common.IconTip
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.common.currentLocale
import com.aimdi.xtanative.ui.common.onSecondaryClick
import com.aimdi.xtanative.ui.common.rememberCopyText
import com.aimdi.xtanative.ui.common.turnTo
import com.aimdi.xtanative.ui.downloads.LocalPlatformFiles
import com.aimdi.xtanative.ui.downloads.MediaSharing
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.posts_open_post
import com.aimdi.xtanative.ui.generated.resources.reader_back
import com.aimdi.xtanative.ui.generated.resources.reader_close
import androidx.compose.ui.text.style.TextOverflow
import com.aimdi.xtanative.ui.settings.isDesktop
import com.aimdi.xtanative.ui.generated.resources.reader_download
import com.aimdi.xtanative.ui.generated.resources.reader_link_copied
import com.aimdi.xtanative.ui.generated.resources.reader_link_share
import com.aimdi.xtanative.ui.generated.resources.reader_media
import com.aimdi.xtanative.ui.generated.resources.reader_media_next
import com.aimdi.xtanative.ui.generated.resources.reader_media_previous
import com.aimdi.xtanative.ui.generated.resources.reader_media_unknown
import com.aimdi.xtanative.ui.generated.resources.reader_share_image
import com.aimdi.xtanative.ui.home.HoldsKeys
import com.aimdi.xtanative.ui.home.skippedByTab
import com.aimdi.xtanative.ui.links.LocalLinks
import com.aimdi.xtanative.ui.post.PostIcons
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * What saves a picture or a video the viewer shows; the downloads provide it.
 * While none is provided, the viewer offers no download.
 */
val LocalMediaSaver = staticCompositionLocalOf<MediaSaver?> { null }

/** Saves where the reader picks in the save dialog, whatever the download setting says: the desktop viewer's "Save as…". */
val LocalMediaSaveAs = staticCompositionLocalOf<MediaSaver?> { null }

/** Whether the viewer has buttons at its sides to turn pages: the desktop, where pages otherwise turn only with a touch. */
internal expect val viewerPageButtons: Boolean

/**
 * A post's photos, videos and GIFs full screen (_media.dart's TweetMediaView):
 * swiped between, a photo pinched, panned or double-tapped to zoom and
 * dragged up or down to close, a video under its controls, a GIF looping on
 * its own. The bar counts the pages and saves or shares the one shown; on
 * the desktop, ← and → turn pages and Esc closes.
 */
@Composable
fun MediaViewer(postId: String, start: Int, onClose: () -> Unit) {
    val posts = LocalPostCache.current
    val viewed = remember(postId) { ViewerMedia.of(posts.post(postId), postId) }
    MediaPages(viewed, start, onClose)
}

/** One picture full screen as it is, a profile's banner or face (profile.dart's _openProfileMedia), saved under [username]'s name. */
@Composable
fun ImageViewer(url: String, username: String, onClose: () -> Unit) {
    MediaPages(remember(url, username) { ViewerMedia.image(url, username) }, 0, onClose)
}

/**
 * A grid's, a card's or a poll's media full screen (media_grid_lightbox.dart):
 * the pages of the gallery [Galleries] keeps under [key], from [start], as
 * they grow; near the last one it asks the grid for more, and "Open post"
 * ([openPost]) opens the post of the page shown at that picture. A gallery
 * no longer kept, as after the app was restarted, leaves post
 * [fallbackPostId]'s own media at [fallbackIndex].
 */
@Composable
fun GalleryViewer(key: String, start: Int, fallbackPostId: String?, fallbackIndex: Int, openPost: (postId: String, mediaIndex: Int) -> Unit, onClose: () -> Unit) {
    val gallery = remember(key) { Galleries.get(key) }
    if (gallery == null) {
        MediaViewer(fallbackPostId.orEmpty(), fallbackIndex, onClose)
        return
    }
    val pages by gallery.updates.collectAsState(gallery.pages())
    val viewed = remember(pages, gallery) { ViewerMedia(pages, gallery.asIs) }
    val open: ((ViewerPage) -> Unit)? = if (gallery.opensPosts) ({ page -> openPost(page.postId, page.index) }) else null
    MediaPages(viewed, start, onClose, onOpenPost = open, onNearEnd = gallery.loadMore)
}

/** The viewer's pages over [viewed], from [start]; [onOpenPost] puts "Open post" in its bar, and [onNearEnd] hears it near the last page. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaPages(viewed: ViewerMedia, start: Int, onClose: () -> Unit, onOpenPost: ((ViewerPage) -> Unit)? = null, onNearEnd: (() -> Unit)? = null) {
    val files = LocalPlatformFiles.current
    val posts = LocalPostCache.current
    val items = viewed.items
    val size = rememberPhotoSize()
    val reduced = LocalReducedMotion.current
    val saver = LocalMediaSaver.current
    val links = LocalLinks.current
    val copyText = rememberCopyText()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copied = stringResource(Res.string.reader_link_copied)
    var backdrop by remember { mutableFloatStateOf(1f) }
    val playing = remember { mutableStateMapOf<Int, String>() }
    val focus = remember { FocusRequester() }
    val pager = rememberPagerState(initialPage = start.coerceIn(0, (items.size - 1).coerceAtLeast(0))) { items.size }
    /** What Save, Share and the desktop's menu act on: the page shown, as the variant a video plays. */
    fun shownSave(): MediaSave? = viewed.pages.getOrNull(pager.currentPage)?.let { viewed.save(it, playing[pager.currentPage]) }

    val fileMenu = rememberViewerFileMenu(snackbar, refocus = { runCatching { focus.requestFocus() } }, shown = ::shownSave)

    fun turn(by: Int) {
        val target = pager.currentPage + by
        if (target !in items.indices) return
        scope.launch { pager.turnTo(target, reduced) }
    }

    // A photo as the picture itself where the platform shares files, else its address (_media.dart).
    fun share(save: MediaSave) {
        scope.launch {
            if (MediaSharing.share(files, save) || links?.share(save.url) == true) return@launch
            copyText(save.url)
            snackbar.showSnackbar(copied)
        }
    }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // Closed, it leaves the keys to what it covered, which would otherwise have none.
    HoldsKeys(focus)
    if (onNearEnd != null) {
        LaunchedEffect(pager.currentPage, items.size) { if (MediaGallery.nearEnd(pager.currentPage, items.size)) onNearEnd() }
    }
    // The zoom and pan keys, for the photo shown, and which pages' photos are zoomed in.
    val zoomKeys = remember { MutableSharedFlow<ViewerKey>(extraBufferCapacity = 8) }
    val zoomedPages = remember { mutableStateMapOf<Int, Boolean>() }
    // How far each page's photo is zoomed, in percent; only the bar's readout reads it, so a pinch redraws only that.
    val zoomPercents = remember { mutableStateMapOf<Int, Int>() }
    // A video page's keys: Space, k, j, l, m, f, < > and 0 to 9, for the page shown.
    val videoKeys = remember { MutableSharedFlow<VideoKey>(extraBufferCapacity = 8) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = backdrop))
            // Before the focus target: key events reach the nodes above the focused one.
            .onKeyEvent { event ->
                val shownKind = items.getOrNull(pager.currentPage)?.kind
                val onVideo = shownKind == XMedia.Kind.VIDEO || shownKind == XMedia.Kind.GIF
                val key = when (val pageKey = pageKeyOf(event, onVideo, zoomed = zoomedPages[pager.currentPage] == true)) {
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
                    ViewerKey.COPY, ViewerKey.SAVE_AS, ViewerKey.MENU -> fileMenu?.key(key) ?: false
                    // The photo shown zooms and moves about; a video's page leaves the keys alone.
                    ViewerKey.ZOOM_IN, ViewerKey.ZOOM_OUT, ViewerKey.ZOOM_RESET, ViewerKey.PAN_LEFT, ViewerKey.PAN_RIGHT, ViewerKey.PAN_UP, ViewerKey.PAN_DOWN ->
                        (items.getOrNull(pager.currentPage)?.kind == XMedia.Kind.PHOTO).also { if (it) zoomKeys.tryEmit(key) }
                    ViewerKey.NONE -> false
                }
            }
            // It holds the keys, yet Tab goes round the bar's buttons without stopping on it, where nothing would show.
            .skippedByTab()
            .focusRequester(focus)
            .focusable(),
    ) {
        Column(Modifier.fillMaxSize()) {
            val current = viewed.pages.getOrNull(pager.currentPage)
            val zoom = { zoomPercents[pager.currentPage]?.takeIf { zoomedPages[pager.currentPage] == true } }
            ViewerBar(pager, items.size, backdrop, onClose, zoom) {
                if (onOpenPost != null && current != null) {
                    IconTip(stringResource(Res.string.posts_open_post)) { IconButton(onClick = { onOpenPost(current) }) { Icon(PostIcons.OpenInNew, stringResource(Res.string.posts_open_post), tint = Color.White) } }
                }
                val save = current?.let { viewed.save(it, playing[pager.currentPage]) }
                if (save != null && save.kind == XMedia.Kind.PHOTO) {
                    items.getOrNull(pager.currentPage)?.let { PictureDragHandle(save, viewed.shownUrl(it, size)) }
                }
                if (saver != null && save != null) {
                    IconTip(stringResource(Res.string.reader_download)) { IconButton(onClick = { saver.save(save) }) { Icon(ReaderIcons.Download, stringResource(Res.string.reader_download), tint = Color.White) } }
                }
                if (save != null) {
                    val label = if (MediaSharing.sharesPicture(files, save)) Res.string.reader_share_image else Res.string.reader_link_share
                    IconTip(stringResource(label)) { IconButton(onClick = { share(save) }) { Icon(Icons.Filled.Share, stringResource(label), tint = Color.White) } }
                }
            }
            // A right-click on the page opens the desktop's menu for it, as a browser's on a picture.
            val pageMenu = if (fileMenu != null) Modifier.onSecondaryClick(fileMenu::openForShown) else Modifier
            // A zoomed photo is clipped, so it stays under the bar, whose buttons stay in reach; unzoomed, one dragged away to close slides out under the bar.
            val clip = if (zoomedPages[pager.currentPage] == true) Modifier.clipToBounds() else Modifier
            Box(Modifier.weight(1f).fillMaxWidth().then(clip).then(pageMenu)) {
                if (items.isEmpty()) {
                    Text(stringResource(Res.string.reader_media_unknown), color = Color.White, modifier = Modifier.align(Alignment.Center))
                } else {
                    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { it }) { page ->
                        val item = items[page]
                        when (item.kind) {
                            XMedia.Kind.PHOTO -> ZoomablePhoto(
                                viewed.shownUrl(item, size),
                                item.alt,
                                motion = !reduced,
                                onBackdrop = { backdrop = it },
                                onClose = onClose,
                                zoomKeys = zoomKeys.takeIf { pager.currentPage == page },
                                onZoomed = { zoomedPages[page] = it },
                                onPercent = { zoomPercents[page] = it },
                            )
                            XMedia.Kind.VIDEO, XMedia.Kind.GIF -> {
                                val download: ((String) -> Unit)? = if (saver == null) null else { url -> viewed.save(viewed.pages[page], url)?.let(saver::save) }
                                val credit = remember(page, viewed) { viewed.pages.getOrNull(page)?.let { VideoCredit.of(posts.post(it.postId), photoUrl(item.url, PhotoSize.LARGE)) } }
                                CompositionLocalProvider(LocalVideoCredit provides credit, LocalViewerVideoKeys provides videoKeys) {
                                    VideoPage(item, active = pager.currentPage == page, onPlaying = { url -> playing[page] = url }, onDownload = download)
                                }
                            }
                        }
                    }
                    if (viewerPageButtons && items.size > 1) PageButtons(pager, items.size, ::turn)
                    // On the desktop the picture's description (its alt text) reads under it while it is not zoomed in.
                    val alt = items.getOrNull(pager.currentPage)?.alt?.takeIf { it.isNotBlank() }
                    if (isDesktop && alt != null && zoomedPages[pager.currentPage] != true) AltCaption(alt, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
    fileMenu?.Menu()
}

/** A picture's description over its foot, white on a dark band, a few lines at most. */
@Composable
private fun AltCaption(alt: String, modifier: Modifier) {
    Text(
        alt,
        color = Color.White,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = ALT_LINES,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

/** The most lines of a picture's description the viewer shows. */
private const val ALT_LINES = 4

/** The black bar over the pages: back, "2 / 4" rolling with the page, on the desktop how far the photo is zoomed while it is ([zoom]), and [actions] for the page shown. */
@Composable
private fun ViewerBar(pager: PagerState, count: Int, backdrop: Float, onClose: () -> Unit, zoom: () -> Int?, actions: @Composable () -> Unit) {
    val media = stringResource(Res.string.reader_media)
    Row(
        Modifier.fillMaxWidth().background(Color.Black.copy(alpha = backdrop)).statusBarsPadding().heightIn(min = 56.dp).padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A phone's viewer goes back from its start; a desktop's closes with ✕ at its end, as a window does.
        if (isDesktop) Spacer(Modifier.width(12.dp)) else IconTip(stringResource(Res.string.reader_back)) { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.reader_back), tint = Color.White) } }
        if (count > 1) {
            val page = pager.currentPage + 1
            Row(Modifier.padding(start = 8.dp).semantics { contentDescription = "$media $page/$count" }) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        val forward = targetState > initialState
                        (slideInVertically { h -> if (forward) h else -h } + fadeIn()) togetherWith (slideOutVertically { h -> if (forward) -h else h } + fadeOut())
                    },
                ) { shown -> Text("$shown", color = Color.White) }
                Text(" / $count", color = Color.White)
            }
        }
        if (isDesktop) ZoomReadout(zoom)
        Spacer(Modifier.weight(1f))
        actions()
        if (isDesktop) IconTip(stringResource(Res.string.reader_close)) { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(Res.string.reader_close), tint = Color.White) } }
    }
}

/** "200 %" while the photo shown is zoomed in, as the reader's language writes it; nothing at its fitted size. */
@Composable
private fun ZoomReadout(zoom: () -> Int?) {
    val percent = zoom() ?: return
    val locale = currentLocale()
    Text(remember(percent, locale) { ViewerZoom.percentText(percent, locale) }, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(start = 16.dp))
}

/** ‹ and › at the sides while there is a page that way, white on black over any picture. */
@Composable
private fun PageButtons(pager: PagerState, count: Int, turn: (Int) -> Unit) {
    val colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.54f), contentColor = Color.White)
    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (pager.currentPage > 0) {
            IconTip(stringResource(Res.string.reader_media_previous)) { FilledIconButton(onClick = { turn(-1) }, colors = colors) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(Res.string.reader_media_previous)) } }
        }
        Spacer(Modifier.weight(1f))
        if (pager.currentPage < count - 1) {
            IconTip(stringResource(Res.string.reader_media_next)) { FilledIconButton(onClick = { turn(1) }, colors = colors) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(Res.string.reader_media_next)) } }
        }
    }
}

/**
 * A video or GIF in the viewer, playing while it is the page shown: the
 * platform's player under [VideoControls] for a video, looping silently for
 * a GIF. [onPlaying] hears the variant it plays, which a download keeps;
 * [onDownload] saves that variant, where the downloads offer saving.
 */
@Composable
internal expect fun VideoPage(media: XMedia, active: Boolean, onPlaying: (String) -> Unit, onDownload: ((playingUrl: String) -> Unit)?)
