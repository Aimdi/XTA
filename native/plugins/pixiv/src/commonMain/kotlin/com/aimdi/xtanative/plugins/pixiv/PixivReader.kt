package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.PixivImageQuality
import com.aimdi.xtanative.networks.pixiv.PixivImages
import com.aimdi.xtanative.store.downloads.OkHttpMediaHttp
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.store.settings.PrefValue
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.common.IconTip
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.onContextClick
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    val surface = remember(illust) { ReaderSurface(illust.viewerUrls.size, start, illust.aspectRatio.toFloat()) { vertical = !vertical } }
    PluginScaffold(
        title = illust.title.ifEmpty { pluginString(Strings.title) },
        onBack = runtime.host::back,
        actions = {
            IconTip(pluginString(if (vertical) Strings.read_horizontally else Strings.read_vertically)) { IconButton(onClick = surface::changeDirection) {
                Icon(
                    if (vertical) Icons.AutoMirrored.Filled.KeyboardArrowRight else PixivIcons.ArrowDownward,
                    pluginString(if (vertical) Strings.read_horizontally else Strings.read_vertically),
                )
            } }
            if (!vertical) RightToLeftButton()
            SavePageButton(illust, surface.currentPage) { sheets.savePages(illust, listOf(surface.currentPage)) }
            IconTip(pluginString(Strings.more_actions)) { IconButton(onClick = { sheets.show(PixivSheet.PageActions(illust, surface.currentPage, surface)) }) {
                Icon(Icons.Filled.MoreVert, pluginString(Strings.more_actions))
            } }
        },
    ) { padding ->
        val settled = sheets.open == null && surface.overviewRequest == null
        val rightToLeft = readsRightToLeft(settings)
        val keys = Modifier.readerKeys(settled, rightToLeft) { key ->
            when (key) {
                ReaderKey.DOWNLOAD -> true.also { sheets.savePages(illust, listOf(surface.currentPage)) }
                ReaderKey.HD -> true.also { hd = !hd }
                else -> surface.key(key)
            }
        }
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).then(keys)) {
            val urls = remember(illust, hd) { illust.viewerUrls.indices.map { if (hd) illust.downloadUrlAt(it) else illust.viewerUrls[it] } }
            var barHeight by remember { mutableIntStateOf(0) }
            val underBar = with(LocalDensity.current) { barHeight.toDp() }
            if (vertical) ColumnPages(illust, urls, surface, underBar) else SwipedPages(illust, urls, surface, pagesReversed(settings))
            val share = rememberShareImage(illust)
            val shown = urls.getOrNull(surface.page)
            ReaderBar(
                illust.viewerUrls.size,
                surface,
                hd,
                onHd = { hd = !hd },
                onShare = shown?.takeIf { it in surface.loaded }?.let { url -> { share(surface.page, url) } },
                modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged { barHeight = it.height },
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
internal class ReaderSurface(private val pages: Int, start: Int, private val firstRatio: Float = 1f, private val flip: () -> Unit) : PixivOverviewSurface {
    var page by mutableIntStateOf(start.coerceIn(0, (pages - 1).coerceAtLeast(0)))

    /** Each page's width over height as its picture came in; Pixiv tells only the first page's. */
    var ratios by mutableStateOf(emptyMap<Int, Float>())
        private set

    /** The shape [page] takes in the column: its own once its picture is in, the first page's until then. */
    fun ratioOf(page: Int): Float = (ratios[page] ?: firstRatio).takeIf { it.isFinite() && it > 0f }?.coerceIn(MIN_RATIO, MAX_RATIO) ?: 1f

    fun measured(page: Int, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val ratio = width.toFloat() / height
        if (ratios[page] != ratio) ratios = ratios + (page to ratio)
    }

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

    /** What the way of reading shown (the column, the swiped pages) does with the keys it has of its own: moving, zooming. */
    var modeKeys: ((ReaderKey) -> Boolean)? = null

    /** A reader key: a page turned (none past either end), else the way of reading's own; whether the key was the reader's. */
    fun key(key: ReaderKey): Boolean = when (key) {
        in ReaderKeys.TURNS -> true.also { ReaderKeys.target(key, page, pages)?.let(::showPage) }
        else -> modeKeys?.invoke(key) ?: false
    }

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

/**
 * Every page down one column at the screen's width, up to [COLUMN_MAX_WIDTH]
 * and centred in a wide window, each in its own shape once its picture is in,
 * the last one clear of the bar; the page most on screen is the page. The
 * column zooms as one.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColumnPages(illust: PixivIllust, urls: List<String>, surface: ReaderSurface, underBar: Dp) {
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
    val zoom = remember { ColumnZoom() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()
    val line = with(LocalDensity.current) { KEY_LINE.toPx() }
    DisposableEffect(surface, list) {
        surface.modeKeys = { key ->
            val by = when (key) {
                ReaderKey.LINE_UP -> -line
                ReaderKey.LINE_DOWN -> line
                ReaderKey.SCREEN_UP -> -size.height * SCREEN_STEP
                ReaderKey.SCREEN_DOWN -> size.height * SCREEN_STEP
                else -> null
            }
            when {
                by != null -> true.also { scope.launch { list.animateScrollBy(by) } }
                else -> zoomStep(key)?.let { zoom.step(it, size); true } ?: false
            }
        }
        onDispose { surface.modeKeys = null }
    }
    LazyColumn(
        state = list,
        contentPadding = PaddingValues(bottom = underBar),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().onSizeChanged { size = it }.zoomsColumn(zoom),
    ) {
        items(urls.size, key = { "page-$it" }) { index ->
            val actions = {
                haptics.medium()
                sheets.show(PixivSheet.PageActions(illust, index, surface))
            }
            PixivPicture(
                urls[index],
                Modifier.widthIn(max = COLUMN_MAX_WIDTH).fillMaxWidth().aspectRatio(surface.ratioOf(index)).onContextClick(actions).combinedClickable(
                    onClick = {},
                    onDoubleClick = { zoom.toggle(size) },
                    onLongClick = actions,
                ),
                ContentScale.FillWidth,
                contentDescription = pluginString(Strings.page_of, index + 1, urls.size),
                onLoaded = { surface.shown(urls[index]) },
                loading = true,
                onSize = { width, height -> surface.measured(index, width, height) },
            )
        }
    }
}

/** The tallest and widest page shapes the column draws whole: a long webtoon strip, a panorama. */
private const val MIN_RATIO = 0.02f
private const val MAX_RATIO = 50f

/** How far ↑ and ↓ move the column, and Space a screen of it. */
private val KEY_LINE = 64.dp
private const val SCREEN_STEP = 0.9f

/** The zoom step a key asks for: in, out, or 0 back to the page's own size; null for any other key. */
internal fun zoomStep(key: ReaderKey): Int? = when (key) {
    ReaderKey.ZOOM_IN -> 1
    ReaderKey.ZOOM_OUT -> -1
    ReaderKey.ZOOM_RESET -> 0
    else -> null
}

/** The widest a page grows in the column: a wide window shows it centred, not blown up to the window's width. */
private val COLUMN_MAX_WIDTH = 960.dp

private fun visibleHeight(offset: Int, size: Int, end: Int): Int = (minOf(offset + size, end) - maxOf(offset, 0)).coerceAtLeast(0)

/** One page at a time, swiped across, the next one asked for already; a page zooms with two fingers or a double tap, and a long press opens its actions. */
@Composable
private fun SwipedPages(illust: PixivIllust, urls: List<String>, surface: ReaderSurface, reversed: Boolean) {
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
    val zooms = remember(urls.size) { List(urls.size) { PageZoom() } }
    DisposableEffect(surface, pager, zooms) {
        surface.modeKeys = { key -> zoomStep(key)?.let { step -> zooms.getOrNull(pager.currentPage)?.step(step); true } ?: false }
        onDispose { surface.modeKeys = null }
    }
    HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1, reverseLayout = reversed) { index ->
        ZoomablePage(urls[index], onLoaded = { surface.shown(urls[index]) }, zoom = zooms[index], onLongPress = {
            haptics.medium()
            sheets.show(PixivSheet.PageActions(illust, index, surface))
        })
    }
}

@Composable
internal fun ZoomablePage(url: String, onLoaded: (() -> Unit)? = null, zoom: PageZoom = remember { PageZoom() }, onLongPress: () -> Unit) {
    var scale by zoom::scale
    var offset by zoom::offset
    Box(
        Modifier.fillMaxSize()
            .onContextClick(onLongPress)
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
                            scale = (scale * event.calculateZoom()).coerceIn(1f, PageZoom.MAX_PAGE_SCALE)
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
        PixivPicture(url, Modifier.fillMaxSize(), ContentScale.Fit, onLoaded = onLoaded, loading = true)
    }
}

/**
 * Whether swiped pages read right to left, as manga does. It is a key of the
 * plugin's own under its `plugin.pixiv.` prefix, which the app keeps and backs
 * up with the rest of its settings without declaring it.
 */
internal object PixivRightToLeft {
    const val KEY = "plugin.pixiv.reader_right_to_left"

    fun of(settings: Settings): Boolean = (settings.values[KEY] as? PrefValue.Bool)?.value == true

    fun with(settings: Settings, on: Boolean): Settings = settings.with(KEY, PrefValue.Bool(on))

    /** Whether a pager is laid out reversed for [on]: a right-to-left layout already reads that way. */
    fun reversed(on: Boolean, layoutRightToLeft: Boolean): Boolean = on && !layoutRightToLeft
}

/** Whether ← goes on: the pages read right to left, or the whole layout does. */
@Composable
internal fun readsRightToLeft(settings: Settings): Boolean = PixivRightToLeft.of(settings) || LocalLayoutDirection.current == LayoutDirection.Rtl

/** Whether a pager of a work's pages lays them out reversed ([PixivRightToLeft.reversed]). */
@Composable
internal fun pagesReversed(settings: Settings): Boolean = PixivRightToLeft.reversed(PixivRightToLeft.of(settings), LocalLayoutDirection.current == LayoutDirection.Rtl)

/** The swiped reader's way across, right to left as manga reads or left to right, kept for every work; the work page's own pages stay as they are, inside the pager of works. */
@Composable
private fun RightToLeftButton() {
    val runtime = LocalPixiv.current
    val on = PixivRightToLeft.of(pixivSettings())
    val label = pluginString(Strings.reader_right_to_left)
    IconTip(label) {
        IconToggleButton(checked = on, onCheckedChange = { checked -> runtime.settings.update { PixivRightToLeft.with(it, checked) } }) {
            Icon(PixivIcons.RightToLeft, label, tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Share image (once the page on screen is in) and original quality for it and, for a work of many pages, a slider and the counter that opens every page. */
@Composable
private fun ReaderBar(pages: Int, surface: ReaderSurface, hd: Boolean, onHd: () -> Unit, onShare: (() -> Unit)?, modifier: Modifier) {
    var scrub by remember { mutableStateOf<Float?>(null) }
    val shown = scrub?.toInt() ?: surface.page
    val commands: @Composable () -> Unit = {
        IconTip(pluginString(Strings.share_image)) { IconButton(onClick = { onShare?.invoke() }, enabled = onShare != null) { Icon(Icons.Filled.Share, pluginString(Strings.share_image)) } }
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
        TextButton(onClick = { surface.openOverview(false) }) {
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
