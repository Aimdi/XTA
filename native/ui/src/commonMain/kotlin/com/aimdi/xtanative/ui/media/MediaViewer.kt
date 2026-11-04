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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.store.settings.MediaPrefs
import com.aimdi.xtanative.store.settings.MediaQuality
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.common.turnTo
import com.aimdi.xtanative.ui.downloads.MediaSharing
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.reader_back
import com.aimdi.xtanative.ui.generated.resources.reader_download
import com.aimdi.xtanative.ui.generated.resources.reader_link_copied
import com.aimdi.xtanative.ui.generated.resources.reader_link_share
import com.aimdi.xtanative.ui.generated.resources.reader_media
import com.aimdi.xtanative.ui.generated.resources.reader_media_next
import com.aimdi.xtanative.ui.generated.resources.reader_media_previous
import com.aimdi.xtanative.ui.generated.resources.reader_media_unknown
import com.aimdi.xtanative.ui.links.LocalLinks
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * What saves a picture or a video the viewer shows; the downloads provide it.
 * While none is provided, the viewer offers no download.
 */
val LocalMediaSaver = staticCompositionLocalOf<MediaSaver?> { null }

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
    val graph = LocalGraph.current
    val viewed = remember(postId) { ViewerMedia.of(graph.posts.post(postId), postId) }
    MediaPages(viewed, start, onClose)
}

/** One picture full screen as it is, a profile's banner or face (profile.dart's _openProfileMedia), saved under [username]'s name. */
@Composable
fun ImageViewer(url: String, username: String, onClose: () -> Unit) {
    MediaPages(remember(url, username) { ViewerMedia.image(url, username) }, 0, onClose)
}

/** The viewer's pages over [viewed], from [start]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaPages(viewed: ViewerMedia, start: Int, onClose: () -> Unit) {
    val graph = LocalGraph.current
    val items = viewed.items
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val size = MediaFiles.viewerSize(settings?.get(MediaPrefs.imageQuality) ?: MediaQuality.MEDIUM)
    val reduced = LocalReducedMotion.current
    val saver = LocalMediaSaver.current
    val links = LocalLinks.current
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copied = stringResource(Res.string.reader_link_copied)
    var backdrop by remember { mutableFloatStateOf(1f) }
    val playing = remember { mutableStateMapOf<Int, String>() }
    val focus = remember { FocusRequester() }
    val pager = rememberPagerState(initialPage = start.coerceIn(0, (items.size - 1).coerceAtLeast(0))) { items.size }

    fun turn(by: Int) {
        val target = pager.currentPage + by
        if (target !in items.indices) return
        scope.launch { pager.turnTo(target, reduced) }
    }

    // A photo as the picture itself where the platform shares files, else its address (_media.dart).
    fun share(save: MediaSave) {
        scope.launch {
            if (MediaSharing.share(graph.platformFiles, save) || links?.share(save.url) == true) return@launch
            clipboard.setText(AnnotatedString(save.url))
            snackbar.showSnackbar(copied)
        }
    }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = backdrop))
            // Before the focus target: key events reach the nodes above the focused one.
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
        Column(Modifier.fillMaxSize()) {
            val current = items.getOrNull(pager.currentPage)
            ViewerBar(pager, items.size, backdrop, onClose) {
                val save = current?.let { viewed.save(it, playing[pager.currentPage]) }
                if (saver != null && save != null) {
                    IconButton(onClick = { saver.save(save) }) { Icon(ReaderIcons.Download, stringResource(Res.string.reader_download), tint = Color.White) }
                }
                if (save != null) {
                    IconButton(onClick = { share(save) }) { Icon(Icons.Filled.Share, stringResource(Res.string.reader_link_share), tint = Color.White) }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (items.isEmpty()) {
                    Text(stringResource(Res.string.reader_media_unknown), color = Color.White, modifier = Modifier.align(Alignment.Center))
                } else {
                    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { it }) { page ->
                        val item = items[page]
                        when (item.kind) {
                            XMedia.Kind.PHOTO -> ZoomablePhoto(viewed.shownUrl(item, size), item.alt, motion = !reduced, onBackdrop = { backdrop = it }, onClose = onClose)
                            XMedia.Kind.VIDEO, XMedia.Kind.GIF -> {
                                val download: ((String) -> Unit)? = if (saver == null) null else { url -> viewed.save(item, url)?.let(saver::save) }
                                VideoPage(item, active = pager.currentPage == page, onPlaying = { url -> playing[page] = url }, onDownload = download)
                            }
                        }
                    }
                    if (viewerPageButtons && items.size > 1) PageButtons(pager, items.size, ::turn)
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
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
        IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.reader_back), tint = Color.White) }
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
            FilledIconButton(onClick = { turn(-1) }, colors = colors) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(Res.string.reader_media_previous)) }
        }
        Spacer(Modifier.weight(1f))
        if (pager.currentPage < count - 1) {
            FilledIconButton(onClick = { turn(1) }, colors = colors) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(Res.string.reader_media_next)) }
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
