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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.photoUrl
import com.aimdi.xtanative.core.x.model.PhotoSize
import com.aimdi.xtanative.data.LocalPostCache
import com.aimdi.xtanative.ui.common.IconTip
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.common.rememberCopyText
import com.aimdi.xtanative.ui.common.turnTo
import com.aimdi.xtanative.ui.downloads.LocalPlatformFiles
import com.aimdi.xtanative.ui.downloads.MediaSharing
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.posts_open_post
import com.aimdi.xtanative.ui.generated.resources.reader_back
import com.aimdi.xtanative.ui.generated.resources.reader_download
import com.aimdi.xtanative.ui.generated.resources.reader_link_copied
import com.aimdi.xtanative.ui.generated.resources.reader_link_share
import com.aimdi.xtanative.ui.generated.resources.reader_media
import com.aimdi.xtanative.ui.generated.resources.reader_media_next
import com.aimdi.xtanative.ui.generated.resources.reader_media_previous
import com.aimdi.xtanative.ui.generated.resources.reader_media_unknown
import com.aimdi.xtanative.ui.generated.resources.reader_share_image
import com.aimdi.xtanative.ui.home.HoldsKeys
import com.aimdi.xtanative.ui.links.LocalLinks
import com.aimdi.xtanative.ui.post.PostIcons
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import com.aimdi.xtanative.ui.common.onSecondaryClick
import com.aimdi.xtanative.ui.generated.resources.media_copy_failed
import com.aimdi.xtanative.ui.generated.resources.media_copy_image
import com.aimdi.xtanative.ui.generated.resources.media_save_as
import com.aimdi.xtanative.ui.generated.resources.posts_copied
import com.aimdi.xtanative.ui.generated.resources.reader_link_copy
import com.aimdi.xtanative.ui.post.PostMenuSurface
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
    val fileActions = rememberViewerFileActions()
    var menu by remember { mutableStateOf<MediaSave?>(null) }
    val imageCopied = stringResource(Res.string.posts_copied)
    val imageNotCopied = stringResource(Res.string.media_copy_failed)

    /** What Save, Share and the desktop's menu act on: the page shown, as the variant a video plays. */
    fun shownSave(): MediaSave? = viewed.pages.getOrNull(pager.currentPage)?.let { viewed.save(it, playing[pager.currentPage]) }

    fun copyImage(save: MediaSave?) {
        val actions = fileActions ?: return
        if (save?.kind != XMedia.Kind.PHOTO) return
        scope.launch { snackbar.showSnackbar(if (actions.copyImage(save.url)) imageCopied else imageNotCopied) }
    }

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
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = backdrop))
            // Before the focus target: key events reach the nodes above the focused one.
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (viewerKeyOf(event)) {
                    ViewerKey.PREVIOUS -> true.also { turn(-1) }
                    ViewerKey.NEXT -> true.also { turn(1) }
                    ViewerKey.CLOSE -> true.also { onClose() }
                    ViewerKey.COPY -> (fileActions != null && shownSave()?.kind == XMedia.Kind.PHOTO).also { if (it) copyImage(shownSave()) }
                    ViewerKey.SAVE_AS -> fileActions?.let { actions -> shownSave()?.let { save -> true.also { actions.saveAs(save) } } } ?: false
                    ViewerKey.NONE -> false
                }
            }
            .focusRequester(focus)
            .focusable(),
    ) {
        Column(Modifier.fillMaxSize()) {
            val current = viewed.pages.getOrNull(pager.currentPage)
            ViewerBar(pager, items.size, backdrop, onClose) {
                if (onOpenPost != null && current != null) {
                    IconTip(stringResource(Res.string.posts_open_post)) { IconButton(onClick = { onOpenPost(current) }) { Icon(PostIcons.OpenInNew, stringResource(Res.string.posts_open_post), tint = Color.White) } }
                }
                val save = current?.let { viewed.save(it, playing[pager.currentPage]) }
                if (saver != null && save != null) {
                    IconTip(stringResource(Res.string.reader_download)) { IconButton(onClick = { saver.save(save) }) { Icon(ReaderIcons.Download, stringResource(Res.string.reader_download), tint = Color.White) } }
                }
                if (save != null) {
                    val label = if (MediaSharing.sharesPicture(files, save)) Res.string.reader_share_image else Res.string.reader_link_share
                    IconTip(stringResource(label)) { IconButton(onClick = { share(save) }) { Icon(Icons.Filled.Share, stringResource(label), tint = Color.White) } }
                }
            }
            // A right-click on the page opens the desktop's menu for it, as a browser's on a picture.
            val pageMenu = if (fileActions != null) Modifier.onSecondaryClick { menu = shownSave() } else Modifier
            Box(Modifier.weight(1f).fillMaxWidth().then(pageMenu)) {
                if (items.isEmpty()) {
                    Text(stringResource(Res.string.reader_media_unknown), color = Color.White, modifier = Modifier.align(Alignment.Center))
                } else {
                    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { it }) { page ->
                        val item = items[page]
                        when (item.kind) {
                            XMedia.Kind.PHOTO -> ZoomablePhoto(viewed.shownUrl(item, size), item.alt, motion = !reduced, onBackdrop = { backdrop = it }, onClose = onClose)
                            XMedia.Kind.VIDEO, XMedia.Kind.GIF -> {
                                val download: ((String) -> Unit)? = if (saver == null) null else { url -> viewed.save(viewed.pages[page], url)?.let(saver::save) }
                                val credit = remember(page, viewed) { viewed.pages.getOrNull(page)?.let { VideoCredit.of(posts.post(it.postId), photoUrl(item.url, PhotoSize.LARGE)) } }
                                CompositionLocalProvider(LocalVideoCredit provides credit) {
                                    VideoPage(item, active = pager.currentPage == page, onPlaying = { url -> playing[page] = url }, onDownload = download)
                                }
                            }
                        }
                    }
                    if (viewerPageButtons && items.size > 1) PageButtons(pager, items.size, ::turn)
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
    val shownMenu = menu
    if (fileActions != null && shownMenu != null) {
        val closeMenu = {
            menu = null
            runCatching { focus.requestFocus() }
            Unit
        }
        PostMenuSurface(onDismiss = closeMenu) {
            if (shownMenu.kind == XMedia.Kind.PHOTO) {
                DropdownMenuItem(text = { Text(stringResource(Res.string.media_copy_image)) }, onClick = {
                    closeMenu()
                    copyImage(shownMenu)
                })
            }
            DropdownMenuItem(text = { Text(stringResource(Res.string.media_save_as)) }, onClick = {
                closeMenu()
                fileActions.saveAs(shownMenu)
            })
            DropdownMenuItem(text = { Text(stringResource(Res.string.reader_link_copy)) }, onClick = {
                closeMenu()
                copyText(shownMenu.url)
                scope.launch { snackbar.showSnackbar(copied) }
            })
        }
    }
}

/** The black bar over the pages: back, "2 / 4" rolling with the page, and [actions] for the page shown. */
@Composable
private fun ViewerBar(pager: PagerState, count: Int, backdrop: Float, onClose: () -> Unit, actions: @Composable () -> Unit) {
    val media = stringResource(Res.string.reader_media)
    Row(
        Modifier.fillMaxWidth().background(Color.Black.copy(alpha = backdrop)).statusBarsPadding().heightIn(min = 56.dp).padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTip(stringResource(Res.string.reader_back)) { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.reader_back), tint = Color.White) } }
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
        Spacer(Modifier.weight(1f))
        actions()
    }
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
