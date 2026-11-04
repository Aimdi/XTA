package com.aimdi.xtanative.plugins.ehviewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.networks.ehviewer.EhGallery
import com.aimdi.xtanative.networks.ehviewer.EhGalleryLoader
import com.aimdi.xtanative.networks.ehviewer.EhGalleryState
import com.aimdi.xtanative.networks.ehviewer.EhQuery
import com.aimdi.xtanative.ui.common.XIcons
import com.aimdi.xtanative.ui.plugins.NoticeHost
import com.aimdi.xtanative.ui.plugins.PluginScaffold
import com.aimdi.xtanative.ui.plugins.WithSettings
import com.aimdi.xtanative.ui.plugins.currentLanguage
import com.aimdi.xtanative.ui.plugins.pluginString
import com.aimdi.xtanative.ui.plugins.rememberKept
import com.aimdi.xtanative.ui.plugins.rememberOpenOutside
import com.aimdi.xtanative.ui.saved.SavedIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** A gallery's page as one screen keeps it: its loader, and the scope its reads run in. */
internal class EhGalleryPage(private val app: EhApp, val loader: EhGalleryLoader, private val scope: CoroutineScope) {
    fun load() {
        scope.launch {
            app.ready()
            loader.load()
        }
    }

    /** Read again, as a pull asks; a failure over a page already shown is told at its foot (eh_gallery_screen.dart). */
    fun refresh(language: String) {
        scope.launch {
            loader.load()
            val now = loader.state.value
            if (now.error != null && now.detail != null) app.notices.tell(EhText.error(now.error).get(language))
        }
    }

    fun more() {
        scope.launch { loader.loadMorePreviews() }
    }

    fun nearEnd() {
        scope.launch { loader.nearEnd() }
    }
}

/**
 * A gallery's page (eh_gallery_screen.dart): its cover and titles, what the
 * site says of it, Read and Continue, its tags, the first comments and the
 * previews, more sheets of them as the reader scrolls; the bar favourites it
 * here, copies its link and opens it on the site.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EhGalleryScreen(app: EhApp, route: EhRoute.Gallery) = WithSettings(app.host.settings) { settings ->
    val listed = remember(route) { app.shelf.take<EhGallery>(route.shelf)?.takeIf { it.gid == route.gid } ?: EhGallery(route.gid, route.token, title = "") }
    val page = rememberKept("eh-gallery:${route.gid}") { scope -> EhGalleryPage(app, EhGalleryLoader(app.client, listed), scope).also { it.load() } }
    val state by page.loader.state.collectAsState()
    val shown = state.detail?.gallery ?: listed
    val language = currentLanguage()
    PluginScaffold(title = "", onBack = app.host::back, actions = { GalleryActions(app, shown) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val error = state.error
            if (state.detail == null && error != null && !state.loading) {
                EhProblem(error, page::load)
            } else {
                PullToRefreshBox(isRefreshing = state.loading && state.detail != null, onRefresh = { page.refresh(language) }, modifier = Modifier.fillMaxSize()) {
                    GalleryBody(app, page, state, shown, listed.thumbUrl, EhConfig.preferJapanese(settings))
                }
            }
            NoticeHost(app.notices)
        }
    }
}

/** Favourite here, copy the link, open on the site. */
@Composable
private fun GalleryActions(app: EhApp, gallery: EhGallery) {
    val favorites by app.favorites.state.collectAsState()
    val saved = favorites.orEmpty().any { it.gallery.gid == gallery.gid }
    val clipboard = LocalClipboardManager.current
    val openOutside = rememberOpenOutside()
    val copied = pluginString(Strings.link_copied)
    IconButton(onClick = { app.launch { app.favorites.toggle(gallery) } }) {
        Icon(if (saved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, pluginString(if (saved) Strings.unfavorite else Strings.favorite))
    }
    IconButton(onClick = {
        clipboard.setText(AnnotatedString(gallery.galleryUrl(app.client.host)))
        app.notices.tell(copied)
    }) { Icon(XIcons.Link, pluginString(Strings.copy_link)) }
    IconButton(onClick = { openOutside(gallery.galleryUrl(app.client.host)) }) { Icon(SavedIcons.Public, pluginString(Strings.open_on_site)) }
}

@Composable
private fun GalleryBody(app: EhApp, page: EhGalleryPage, state: EhGalleryState, gallery: EhGallery, listCover: String?, japanese: Boolean) {
    val grid = rememberLazyGridState()
    var allComments by rememberSaveable { mutableStateOf(false) }
    val history by app.history.state.collectAsState()
    val continuePage = gallery.continuePage(history.orEmpty().firstOrNull { it.gallery.gid == gallery.gid }?.lastPage)
    ReadsOnNearEnd(grid, state.previews.size, page::nearEnd)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(EhLayout.previewColumns(maxWidth.value - 2 * EDGE.value)),
            state = grid,
            contentPadding = PaddingValues(start = EDGE, end = EDGE, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            line("header") { GalleryHeader(app, gallery, listCover, japanese) { app.openSearch(EhQuery.uploader(it)) } }
            val detail = state.detail
            if (detail == null) {
                line("skeleton") { GallerySkeleton() }
            } else {
                line("facts") { GalleryFacts(detail) }
                line("read") { ReadActions(continuePage) { app.openReader(gallery, it, state.previews) } }
                line("tags") { GalleryTags(app, detail.gallery.tags, detail.weakTags) }
                line("comments") { GalleryComments(detail.comments, onShowAll = { allComments = true }) }
                previews(app, state, detail.gallery.pageCount) { app.openReader(gallery, it, state.previews) }
                line("footer") { PreviewFooter(state, page::more) }
            }
        }
    }
    val comments = state.detail?.comments
    if (allComments && comments != null) AllComments(comments, onDismiss = { allComments = false })
}

/** The previews' margin from the screen's edges; the page's other parts keep their own. */
private val EDGE = 12.dp

/** One row of the page's grid, the whole width of the screen, past the previews' margin. */
private fun LazyGridScope.line(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.pastMargin()) { content() } }
}

/** Laid out [EDGE] wider on each side than the grid's content allows. */
private fun Modifier.pastMargin(): Modifier = layout { measurable, constraints ->
    val edge = EDGE.roundToPx()
    val width = constraints.maxWidth + 2 * edge
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-edge, 0) }
}

private fun LazyGridScope.previews(app: EhApp, state: EhGalleryState, pageCount: Int?, onOpen: (Int) -> Unit) {
    if (state.previews.isEmpty()) return
    val total = pageCount ?: state.previews.last().page
    line("previews") { SectionTitle(pluginString(Strings.previews)) }
    items(state.previews, key = { "page-${it.page}" }) { preview -> PreviewTile(app, preview, total) { onOpen(preview.page) } }
}
