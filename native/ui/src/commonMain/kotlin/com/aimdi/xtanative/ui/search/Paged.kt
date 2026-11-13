package com.aimdi.xtanative.ui.search

import com.aimdi.xtanative.data.Problem

/** When a paged list is at its end, by the rule of the XTA list it stands for. */
enum class PageEnd {
    /** XTA's lists of posts: a page with nothing in it ends the list too. */
    POSTS,

    /** XTA's media grids: media is sparse, so a page without any does not end the grid. */
    MEDIA,

    /** XTA's lists of people and communities: a page that brings no one new ends the list. */
    FRESH,
}

/**
 * One paged list as a screen shows it: its [items], the cursor of the page
 * after them ([next], null at the end), and how its reads stand.
 */
data class Paged<T>(
    val items: List<T> = emptyList(),
    val next: String? = null,
    /** The first page has answered. */
    val loaded: Boolean = false,
    val loading: Boolean = false,
    /** The first page failed: nothing to show, and why. */
    val problem: Problem? = null,
    val loadingMore: Boolean = false,
    /** The page after failed; what was shown stays. */
    val moreProblem: Problem? = null,
) {
    /** X answered, and there is nothing to show. */
    val empty: Boolean get() = loaded && items.isEmpty() && next == null

    /** Whether the page after may be asked for now. */
    val canLoadMore: Boolean get() = loaded && next != null && !loading && !loadingMore

    fun failed(problem: Problem): Paged<T> = copy(loading = false, problem = problem)

    fun askingMore(): Paged<T> = copy(loadingMore = true, moreProblem = null)

    fun moreFailed(problem: Problem): Paged<T> = copy(loadingMore = false, moreProblem = problem)

    /**
     * [page], read with the cursor [asked], after what is shown, without what
     * is shown already; [next] is followed unless [end]'s rule says the list
     * is over. A missing, empty or unchanged cursor always ends it: asking
     * for it again would bring the same page forever.
     */
    fun <K> appended(page: List<T>, next: String?, asked: String?, end: PageEnd, key: (T) -> K): Paged<T> {
        val known = items.mapTo(HashSet(), key)
        val fresh = page.filter { known.add(key(it)) }
        val over = next.isNullOrEmpty() || next == asked || when (end) {
            PageEnd.POSTS -> page.isEmpty()
            PageEnd.MEDIA -> false
            PageEnd.FRESH -> fresh.isEmpty()
        }
        return Paged(items = items + fresh, next = next.takeUnless { over }, loaded = true)
    }

    companion object {
        /** A first page under way. */
        fun <T> reading(): Paged<T> = Paged(loading = true)
    }
}
