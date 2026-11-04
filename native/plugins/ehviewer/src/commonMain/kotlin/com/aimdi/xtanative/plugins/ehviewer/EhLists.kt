package com.aimdi.xtanative.plugins.ehviewer

import com.aimdi.xtanative.networks.Page
import com.aimdi.xtanative.networks.ehviewer.EhFeed
import com.aimdi.xtanative.networks.ehviewer.EhGallery
import com.aimdi.xtanative.networks.ehviewer.EhToplistPeriod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The home's tabs, in XTA's order (eh_screen.dart). */
internal enum class EhTab { POPULAR, FRONT, TOPLIST, WATCHED, HISTORY, FAVORITES }

/**
 * A gallery list read in [scope], page by page, as XTA's EhFeedStore reads
 * it: [ensure] reads the first page while there is none, [refresh] reads it
 * again, [more] the next one.
 */
internal class EhList(private val scope: CoroutineScope, read: suspend (next: String?) -> Page<EhGallery>) {
    val feed = EhFeed(read)
    private val tried = MutableStateFlow(false)

    /** A first page was asked for and answered, or failed. */
    val attempted: StateFlow<Boolean> = tried.asStateFlow()

    fun ensure() {
        val now = feed.state.value
        if (now.galleries.isEmpty() && !now.loading) refresh()
    }

    fun refresh() {
        scope.launch {
            feed.refresh()
            tried.value = true
        }
    }

    fun more() {
        scope.launch { feed.loadMore() }
    }
}

/** How many columns the grids have (plugin_gallery_layout.dart, eh_gallery_previews.dart). */
internal object EhLayout {
    /** Galleries [width] dp wide: readable at large text, more on wide views, never past four. */
    fun galleryColumns(width: Float, fontScale: Float): Int {
        val least = if (14 * fontScale > 20) 240f else 152f
        return (width / least).toInt().coerceIn(1, 4)
    }

    /** Previews [width] dp wide: three or four on a phone, more on a tablet, none wider than 120 dp. */
    fun previewColumns(width: Float): Int = maxOf(3, kotlin.math.ceil(width / 120f).toInt())
}

/** The home's lists: Popular, the front page, the toplist over its [period], and Watched. */
internal class EhLists(app: EhApp, scope: CoroutineScope) {
    private val chosenPeriod = MutableStateFlow(EhToplistPeriod.YESTERDAY)
    val period: StateFlow<EhToplistPeriod> = chosenPeriod.asStateFlow()

    val popular = EhList(scope) { next -> app.ready(); app.client.popular(next) }
    val front = EhList(scope) { next -> app.ready(); app.client.frontPage(next) }
    val toplist = EhList(scope) { next -> app.ready(); app.client.toplist(chosenPeriod.value, next) }
    val watched = EhList(scope) { next -> app.ready(); app.client.watched(next) }

    /** The list a tab shows; History and Favorites are the device's own. */
    fun of(tab: EhTab): EhList? = when (tab) {
        EhTab.POPULAR -> popular
        EhTab.FRONT -> front
        EhTab.TOPLIST -> toplist
        EhTab.WATCHED -> watched
        EhTab.HISTORY, EhTab.FAVORITES -> null
    }

    /** Another window for the toplist, read at once. */
    fun choose(next: EhToplistPeriod) {
        if (next == chosenPeriod.value) return
        chosenPeriod.value = next
        toplist.refresh()
    }
}
