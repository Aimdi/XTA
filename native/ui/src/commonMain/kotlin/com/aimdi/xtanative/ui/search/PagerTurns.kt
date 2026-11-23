package com.aimdi.xtanative.ui.search

/**
 * Whether a pager coming to rest picks its tab. A tab picked in the app
 * (a click, Ctrl+Tab) slides the pager to it; picked again before it
 * arrives, the slide is cut short and starts over, and for that moment the
 * pager rests on a page on the way. That rest is not the reader's choice:
 * taken as one, it picked the tab before the last, and quick turns stopped
 * a tab short. Only rests while no slide is under way (a swipe) pick.
 */
class PagerTurns {
    private var target: Int? = null

    /** The pager starts sliding to [page]. */
    fun started(page: Int) {
        target = page
    }

    /** The slide arrived, or a drag stopped it: the pager's rests count again. */
    fun ended() {
        target = null
    }

    /** Whether the pager resting on [page] picks that tab. */
    fun picks(page: Int): Boolean = target.let { it == null || it == page }
}
