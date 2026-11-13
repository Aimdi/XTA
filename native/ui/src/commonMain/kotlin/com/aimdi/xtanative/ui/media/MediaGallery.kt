package com.aimdi.xtanative.ui.media

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * What a media grid, a card or a poll hands the viewer it opens
 * (media_grid_lightbox.dart): the pages as they are now and as they grow,
 * and [loadMore], the grid's next page, which the viewer asks for as the
 * reader nears its last page. [opensPosts] puts "Open post" in the viewer's
 * bar, for pages from several posts; [asIs] shows pictures at their own
 * addresses rather than at X's sizes.
 */
class MediaGallery(
    private val opened: List<ViewerPage>,
    val updates: Flow<List<ViewerPage>>,
    val loadMore: () -> Unit = {},
    val asIs: Boolean = false,
    val opensPosts: Boolean = false,
) {
    /** The pages as the viewer opens on them. */
    fun pages(): List<ViewerPage> = opened

    /** The page a tap on [item] opens the viewer at; null for a broadcast with nothing to play here, whose post opens. */
    fun startOf(item: MediaGridItem): Int? {
        val page = item.page ?: return null
        return pages().indexOfFirst { it.postId == page.postId && it.index == page.index }.takeIf { it >= 0 }
    }

    companion object {
        /** How many pages from the last one the viewer asks for more. */
        const val FETCH_AHEAD = 3

        /** A grid's [items] as a tap saw them and as they grow ([updates]): every one the viewer can show, with "Open post". */
        fun ofGrid(items: List<MediaGridItem>, updates: Flow<List<MediaGridItem>>, loadMore: () -> Unit = {}) =
            MediaGallery(pagesOf(items), updates.map(::pagesOf), loadMore, opensPosts = true)

        /** [pages] that do not change: a post's card or poll. */
        fun of(pages: List<ViewerPage>, asIs: Boolean = false) = MediaGallery(pages, flowOf(pages), asIs = asIs)

        fun pagesOf(items: List<MediaGridItem>): List<ViewerPage> = items.mapNotNull { it.page }

        /** Whether the viewer, at [page] of [count], is near enough the end to ask for more. */
        fun nearEnd(page: Int, count: Int): Boolean = count > 0 && page >= count - FETCH_AHEAD
    }
}

/**
 * The galleries viewers were opened on, the last few kept: a route names one
 * by its key, as it cannot carry it. Keys carry the run of the app they were
 * made in, so a route kept across a restart names none of the new ones.
 */
object Galleries {
    private const val KEPT = 8
    private val kept = object : LinkedHashMap<String, MediaGallery>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MediaGallery>?): Boolean = size > KEPT
    }
    private val run = java.util.UUID.randomUUID().toString().substring(0, 8)
    private var last = 0

    @Synchronized
    fun put(gallery: MediaGallery): String = "$run-${++last}".also { kept[it] = gallery }

    /** What [key] names; nothing once it was let go, as after the app was restarted. */
    @Synchronized
    fun get(key: String): MediaGallery? = kept[key]
}
