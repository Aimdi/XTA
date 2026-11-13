package com.aimdi.xtanative.plugins.ehviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import com.aimdi.xtanative.networks.ehviewer.EhPageResolver
import com.aimdi.xtanative.store.settings.EhViewerPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.plugins.NoticeHost
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.WithSettings
import com.aimdi.xtanative.ui.plugins.imageRequest
import com.aimdi.xtanative.ui.plugins.rememberKept
import kotlinx.coroutines.launch

/** What the reader is asking: the reading mode, a page's actions, or a page to jump to. */
private sealed interface ReaderAsk {
    data object Mode : ReaderAsk

    data class Page(val page: Int) : ReaderAsk

    data object Jump : ReaderAsk
}

/**
 * The page reader (eh_reader_screen.dart) on black: left to right, right to
 * left or one long column, pages found as the site links them and read ahead
 * three after and one before. The outer thirds turn pages (mirrored right to
 * left), the middle shows or hides the bars; a long press offers the page's
 * actions. Each page read goes into the history.
 */
@Composable
internal fun EhReaderScreen(app: EhApp, route: EhRoute.Reader) = WithSettings(app.host.settings) { settings ->
    val handed = remember(route) { app.shelf.take<EhReading>(route.shelf)?.takeIf { it.gallery.gid == route.gid } }
    val session = rememberKept("eh-reader:${route.gid}:${route.page}") { scope -> EhReaderSession(app, scope, route, handed) }
    val load by session.reader.state.collectAsState()
    val open = load.value
    val error = load.problem
    when {
        open != null -> Reader(app, open, settings)
        error != null && !load.loading -> PluginScaffold(title = "", onBack = app.host::back) { padding ->
            EhProblem(error, { session.reader.load() }, Modifier.padding(padding))
        }
        else -> Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun Reader(app: EhApp, open: EhOpenReader, settings: Settings) {
    val state by open.model.state.collectAsState()
    var ask by remember { mutableStateOf<ReaderAsk?>(null) }
    var width by remember { mutableFloatStateOf(1f) }
    ReaderWindow(keepOn = EhConfig.keepScreenOn(settings), barsHidden = !state.chromeVisible)
    ReadsAhead(app, open, state.page)
    val onTap: (Offset) -> Unit = { offset -> open.model.tap(EhReaderRules.tap(offset.x, width, open.model.state.value.mode)) }
    val onHold: (Int) -> Unit = { page -> ask = ReaderAsk.Page(page) }
    Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }) {
        if (state.mode.paged) PagedPages(app, open, state, onTap, onHold) else ColumnPages(app, open, state, onTap, onHold)
        ReaderChrome(
            title = open.gallery.titleFor(EhConfig.preferJapanese(settings)),
            state = state,
            model = open.model,
            onBack = app.host::back,
            onCounter = { ask = ReaderAsk.Jump },
            onMode = { ask = ReaderAsk.Mode },
        )
        NoticeHost(app.notices)
    }
    when (val asked = ask) {
        ReaderAsk.Mode -> ModeSheet(state.mode, onDismiss = { ask = null }) { mode ->
            ask = null
            open.model.setMode(mode)
            app.update { it.with(EhViewerPrefs.readingMode, mode) }
        }
        is ReaderAsk.Page -> PageActions(app, open, asked.page, onDismiss = { ask = null })
        ReaderAsk.Jump -> JumpDialog(app, state.page, open.model.total, onJump = { open.model.goTo(it) }, onDismiss = { ask = null })
        null -> Unit
    }
}

/** The pages around [page] fetched, and their pictures asked for, so turning to them shows them at once. */
@Composable
private fun ReadsAhead(app: EhApp, open: EhOpenReader, page: Int) {
    val context = LocalPlatformContext.current
    LaunchedEffect(page) {
        EhPageResolver.preloadPages(page, open.resolver.total).forEach { next ->
            launch {
                val image = open.resolver.resolve(next).await() ?: return@launch
                val url = image.imageUrls(app.client.signedIn).first()
                SingletonImageLoader.get(context).enqueue(imageRequest(context, url, app.client.imageHeaders(url)))
            }
        }
    }
}

/**
 * One page at a time, swiped sideways: laid out left to right whatever the
 * app's language, so the reading mode alone says which way the pages turn.
 */
@Composable
private fun PagedPages(app: EhApp, open: EhOpenReader, state: EhReaderState, onTap: (Offset) -> Unit, onHold: (Int) -> Unit) {
    val reduced = LocalReducedMotion.current
    val ambient = LocalLayoutDirection.current
    key(state.mode) {
        val pager = rememberPagerState(initialPage = state.page - 1) { open.model.total }
        LaunchedEffect(state.jump) {
            val target = state.page - 1
            if (pager.currentPage != target) if (state.smooth && !reduced) pager.animateScrollToPage(target) else pager.scrollToPage(target)
        }
        LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { open.model.observe(it + 1) } }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            HorizontalPager(state = pager, reverseLayout = state.mode.reversed, beyondViewportPageCount = 1, key = { it }, modifier = Modifier.fillMaxSize()) { index ->
                CompositionLocalProvider(LocalLayoutDirection provides ambient) {
                    ReaderPage(app, open, index + 1, vertical = false, onTap = onTap, onHold = onHold)
                }
            }
        }
    }
}

/** One long column of pages, as a webtoon is read. */
@Composable
private fun ColumnPages(app: EhApp, open: EhOpenReader, state: EhReaderState, onTap: (Offset) -> Unit, onHold: (Int) -> Unit) {
    val list = rememberLazyListState(initialFirstVisibleItemIndex = state.page - 1)
    val total = open.model.total
    LaunchedEffect(state.jump) { if (list.firstVisibleItemIndex != state.page - 1) list.scrollToItem(state.page - 1) }
    LaunchedEffect(list) { snapshotFlow { pageInView(list, total) }.collect { open.model.observe(it) } }
    LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
        items(total, key = { it }) { index -> ReaderPage(app, open, index + 1, vertical = true, onTap = onTap, onHold = onHold) }
    }
}

/** The page the column is on ([EhReaderRules.visiblePage]). */
private fun pageInView(list: LazyListState, total: Int): Int {
    val info = list.layoutInfo
    val last = info.visibleItemsInfo.lastOrNull()
    val lastWhole = last != null && last.index == total - 1 && last.offset + last.size <= info.viewportEndOffset
    return EhReaderRules.visiblePage(list.firstVisibleItemIndex + 1, lastWhole, total)
}
