package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.PixivImageQuality
import com.aimdi.xtanative.networks.pixiv.PixivImages
import com.aimdi.xtanative.store.downloads.OkHttpMediaHttp
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The reader of a work's pages: the work handed over, else read by its id. */
@Composable
internal fun PixivReaderScreen(route: PixivRoute.Reader) {
    val runtime = LocalPixiv.current
    val seed = remember(route) { runtime.shelf.get(route.shelf) as? PixivIllust }
    val fetch = rememberKept("pixiv-reader-${route.id}") { scope -> PixivFetch(scope) { seed ?: runtime.client.illustDetail(route.id) }.also { it.ensure() } }
    val state by fetch.state.collectAsState()
    val illust = state.value
    if (illust == null) {
        PluginScaffold(title = pluginString(Strings.title), onBack = runtime.host::back) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) { if (state.error != null) PixivProblem(state.error, fetch::load) else LoadingRow() }
        }
        return
    }
    Reader(illust, route.page, route.vertical)
}

/** A work's pages full screen, read down a column or across, with the page's actions, original quality and a way to any page. */
@Composable
private fun Reader(illust: PixivIllust, start: Int, verticalFirst: Boolean) {
    val runtime = LocalPixiv.current
    val sheets = LocalPixivSheets.current
    val settings = pixivSettings()
    var vertical by rememberSaveable { mutableStateOf(verticalFirst) }
    var hd by rememberSaveable { mutableStateOf(PixivQualitySlot.READER.of(settings) == PixivImageQuality.ORIGINAL) }
    val surface = remember(illust) { ReaderSurface(illust.viewerUrls.size, start) { vertical = !vertical } }
    PluginScaffold(
        title = illust.title.ifEmpty { pluginString(Strings.title) },
        onBack = runtime.host::back,
        actions = {
            IconButton(onClick = surface::changeDirection) {
                Icon(
                    if (vertical) Icons.AutoMirrored.Filled.KeyboardArrowRight else PixivIcons.ArrowDownward,
                    pluginString(if (vertical) Strings.read_horizontally else Strings.read_vertically),
                )
            }
            SavePageButton(illust, surface.currentPage) { sheets.savePages(illust, listOf(surface.currentPage)) }
            IconButton(onClick = { sheets.show(PixivSheet.PageActions(illust, surface.currentPage, surface)) }) {
                Icon(Icons.Filled.MoreVert, pluginString(Strings.more_actions))
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            val urls = remember(illust, hd) { illust.viewerUrls.indices.map { if (hd) illust.downloadUrlAt(it) else illust.viewerUrls[it] } }
            if (vertical) ColumnPages(illust, urls, surface) else SwipedPages(illust, urls, surface)
            val share = rememberShareImage(illust)
            val shown = urls.getOrNull(surface.page)
            ReaderBar(
                illust.viewerUrls.size,
                surface,
                hd,
                onHd = { hd = !hd },
                onShare = shown?.takeIf { it in surface.loaded }?.let { url -> { share(surface.page, url) } },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
    OverviewHost(surface, illust)
}

/**
 * Share image (pixiv_share_image.dart): the page's picture, as the image
 * cache holds it or else fetched again with Pixiv's Referer, handed to the
 * share sheet under Pixiv's own name for it; the desktop, which has none,
 * saves it instead.
 */
@Composable
private fun rememberShareImage(illust: PixivIllust): (page: Int, url: String) -> Unit {
    val runtime = LocalPixiv.current
    val context = LocalPlatformContext.current
    val language = currentLanguage()
    return remember(illust, runtime, context, language) {
        { page, url ->
            runtime.launch {
                val shown = PixivImages.url(url, runtime.settings[PixivPrefs.imageHost])
                val bytes = runCatching { pictureBytes(context, shown) }.getOrNull()
                if (bytes == null) {
                    runtime.tell(Strings.share_image_failed.get(language))
                } else {
                    val (name, type) = PixivDownloads.sharedName(illust, page, shown)
                    runtime.host.shareFile(bytes, name, type)
                }
            }
        }
    }
}

/** The picture at [url]: the copy Coil's disk cache keeps of what is on screen, else downloaded again with Pixiv's Referer. */
private suspend fun pictureBytes(context: PlatformContext, url: String): ByteArray = withContext(Dispatchers.IO) {
    cachedPicture(context, url) ?: OkHttpMediaHttp().get(url, PixivImages.HEADERS).use { response ->
        if (response.code !in 200..299) throw IOException("picture: HTTP ${response.code}")
        response.body.readBytes()
    }
}

private fun cachedPicture(context: PlatformContext, url: String): ByteArray? = runCatching {
    val cache = SingletonImageLoader.get(context).diskCache ?: return null
    cache.openSnapshot(url)?.use { snapshot -> cache.fileSystem.read(snapshot.data) { readByteArray() } }
}.getOrNull()

/** The reader's page and its way between pages, which the bar, the sheets and the overview move. */
internal class ReaderSurface(private val pages: Int, start: Int, private val flip: () -> Unit) : PixivOverviewSurface {
    var page by mutableIntStateOf(start.coerceIn(0, (pages - 1).coerceAtLeast(0)))

    /** A jump asked for, which the page list answers by scrolling there. */
    var jump by mutableStateOf<Int?>(null)

    /** The pictures shown so far, which Share image waits for, as XTA's reader does. */
    var loaded by mutableStateOf(emptySet<String>())
        private set

    fun shown(url: String) {
        if (url !in loaded) loaded = loaded + url
    }

    override var overviewRequest by mutableStateOf<Boolean?>(null)
        private set

    override val currentPage: Int get() = page

    override val offersVertical: Boolean get() = true

    override fun showPage(page: Int) {
        jump = page.coerceIn(0, (pages - 1).coerceAtLeast(0))
    }

    override fun changeDirection() = flip()

    override fun openOverview(selecting: Boolean) {
        overviewRequest = selecting
    }

    override fun closeOverview() {
        overviewRequest = null
    }
}

/** Every page down one column at the screen's width; the page most on screen is the page. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColumnPages(illust: PixivIllust, urls: List<String>, surface: ReaderSurface) {
    val sheets = LocalPixivSheets.current
    val haptics = rememberHaptics()
    val list = rememberLazyListState(initialFirstVisibleItemIndex = surface.page)
    val shown by remember(list) { derivedStateOf { list.layoutInfo.visibleItemsInfo.maxByOrNull { visibleHeight(it.offset, it.size, list.layoutInfo.viewportEndOffset) }?.index } }
    LaunchedEffect(shown) { shown?.let { surface.page = it } }
    LaunchedEffect(surface.jump) {
        surface.jump?.let {
            list.scrollToItem(it)
            surface.jump = null
        }
    }
    LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
        items(urls.size, key = { "page-$it" }) { index ->
            PixivPicture(
                urls[index],
                Modifier.fillMaxWidth().aspectRatio(illust.aspectRatio.toFloat().coerceIn(0.2f, 5f)).combinedClickable(onClick = {}, onLongClick = {
                    haptics.medium()
                    sheets.show(PixivSheet.PageActions(illust, index, surface))
                }),
                ContentScale.FillWidth,
                contentDescription = pluginString(Strings.page_of, index + 1, urls.size),
                onLoaded = { surface.shown(urls[index]) },
            )
        }
    }
}

private fun visibleHeight(offset: Int, size: Int, end: Int): Int = (minOf(offset + size, end) - maxOf(offset, 0)).coerceAtLeast(0)

/** One page at a time, swiped across; a page zooms with two fingers or a double tap, and a long press opens its actions. */
@Composable
private fun SwipedPages(illust: PixivIllust, urls: List<String>, surface: ReaderSurface) {
    val sheets = LocalPixivSheets.current
    val haptics = rememberHaptics()
    val pager = rememberPagerState(initialPage = surface.page) { urls.size }
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { surface.page = it } }
    LaunchedEffect(surface.jump) {
        surface.jump?.let {
            pager.scrollToPage(it)
            surface.jump = null
        }
    }
    HorizontalPager(pager, Modifier.fillMaxSize()) { index ->
        ZoomablePage(urls[index], onLoaded = { surface.shown(urls[index]) }, onLongPress = {
            haptics.medium()
            sheets.show(PixivSheet.PageActions(illust, index, surface))
        })
    }
}

@Composable
internal fun ZoomablePage(url: String, onLoaded: (() -> Unit)? = null, onLongPress: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onLongPress() },
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
    ) {
        PixivPicture(url, Modifier.fillMaxSize(), ContentScale.Fit, onLoaded = onLoaded)
    }
}

/** Share image (once the page on screen is in) and original quality for it and, for a work of many pages, a slider and the counter that opens every page. */
@Composable
private fun ReaderBar(pages: Int, surface: ReaderSurface, hd: Boolean, onHd: () -> Unit, onShare: (() -> Unit)?, modifier: Modifier) {
    var scrub by remember { mutableStateOf<Float?>(null) }
    val shown = scrub?.toInt() ?: surface.page
    val commands: @Composable () -> Unit = {
        IconButton(onClick = { onShare?.invoke() }, enabled = onShare != null) { Icon(Icons.Filled.Share, pluginString(Strings.share_image)) }
        IconToggleButton(checked = hd, onCheckedChange = { onHd() }) {
            Icon(PixivIcons.Hd, pluginString(Strings.original_quality), tint = if (hd) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val pager: @Composable RowScope.() -> Unit = {
        Slider(
            value = shown.toFloat(),
            onValueChange = { scrub = it },
            onValueChangeFinished = {
                scrub?.let { surface.showPage(it.toInt()) }
                scrub = null
            },
            valueRange = 0f..(pages - 1).toFloat(),
            steps = (pages - 2).coerceAtLeast(0),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = { surface.openOverview(false) }) {
            Icon(GroupIcons.GridView, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(pluginString(Strings.page_of, shown + 1, pages))
        }
    }
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background.copy(alpha = 0.92f)) {
        BoxWithConstraints(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 4.dp, end = 12.dp, bottom = 4.dp)) {
            // Below this width in text-sized units the page controls get a line of their own (pixiv_reader_bar.dart).
            val oneRow = maxWidth / LocalDensity.current.fontScale >= ONE_ROW_WIDTH
            when {
                pages < 2 -> Row(verticalAlignment = Alignment.CenterVertically) { commands() }
                oneRow -> Row(verticalAlignment = Alignment.CenterVertically) {
                    commands()
                    pager()
                }
                else -> Column {
                    Row(verticalAlignment = Alignment.CenterVertically, content = pager)
                    Row(verticalAlignment = Alignment.CenterVertically) { commands() }
                }
            }
        }
    }
}

private val ONE_ROW_WIDTH = 300.dp
