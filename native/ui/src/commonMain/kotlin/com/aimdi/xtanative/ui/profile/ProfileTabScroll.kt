package com.aimdi.xtanative.ui.profile

/** Where a list was scrolled: its first item in view, and how far that one is scrolled. */
internal data class ScrollSpot(val index: Int, val offset: Int)

/**
 * Each profile tab's own place in the one list they share: turning to a tab
 * leaves the last one where it was, and the tab turned to comes back where
 * the reader left it. While the header is in view nothing moves; once the
 * tab row sticks at the top, it stays there, the tab opening at its own
 * place below the row, or at its start.
 */
internal class ProfileTabScroll(private val tabRowIndex: Int = TAB_ROW_INDEX) {
    private val spots = HashMap<ProfileTab, ScrollSpot>()

    /** Turned from [from] to [to] while the list is at [now]: where it goes, or null to stay. */
    fun turned(from: ProfileTab, to: ProfileTab, now: ScrollSpot): ScrollSpot? {
        if (from == to) return null
        spots[from] = now
        if (now.index < tabRowIndex) return null
        return spots[to]?.takeIf { it.index >= tabRowIndex } ?: ScrollSpot(tabRowIndex, 0)
    }

    companion object {
        /** The tab row's place in the list, under the header. */
        const val TAB_ROW_INDEX = 1
    }
}
