package com.aimdi.xtanative.networks.pixiv

import com.aimdi.xtanative.networks.Page

/**
 * How Pixiv's lists page: each page names the next with its `next_url`
 * ([Page.next]), pages may overlap, and the reader's filters and mutes can
 * empty a whole page while Pixiv still has more.
 */
object PixivPages {
    /** How many pages in a row the filters may empty before a list stops looking further for now. */
    const val EMPTY_PAGE_ADVANCE_LIMIT = 5

    /**
     * The page at [next] (the first when null) as [visible] leaves it, moving
     * on while the filters empty it and the list goes on. Without this a list
     * shows nothing yet never scrolls far enough to ask for more.
     */
    suspend fun <T> visiblePage(
        next: String?,
        load: suspend (next: String?) -> Page<T>,
        visible: (List<T>) -> List<T> = { it },
    ): Page<T> = advance(next, load, visible, EMPTY_PAGE_ADVANCE_LIMIT)

    private suspend fun <T> advance(next: String?, load: suspend (String?) -> Page<T>, visible: (List<T>) -> List<T>, tries: Int): Page<T> {
        val page = load(next)
        val shown = visible(page.items)
        if (shown.isNotEmpty() || page.next == null || tries <= 0) return Page(shown, page.next)
        return advance(page.next, load, visible, tries - 1)
    }

    /** [incoming] appended to [existing], without the items whose key is already there. */
    fun <T> merge(existing: List<T>, incoming: List<T>, keyOf: (T) -> Any): List<T> {
        if (incoming.isEmpty()) return existing
        val seen = existing.mapTo(HashSet(), keyOf)
        return existing + incoming.filter { seen.add(keyOf(it)) }
    }
}
