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
internal class ProfileTabScroll {
    /** Each tab's place, counted from the tab row, so a row that comes or goes above it (a problem's) moves none. */
    private val spots = HashMap<ProfileTab, ScrollSpot>()

    /**
     * Turned from [from] to [to] while the list is at [now], its tab row at
     * [tabRow] ([itemsAbove]'s count): where it goes, or null to stay.
     */
    fun turned(from: ProfileTab, to: ProfileTab, now: ScrollSpot, tabRow: Int): ScrollSpot? {
        if (from == to) return null
        if (now.index < tabRow) {
            spots.remove(from)
            return null
        }
        spots[from] = ScrollSpot(now.index - tabRow, now.offset)
        val back = spots[to] ?: return ScrollSpot(tabRow, 0)
        return ScrollSpot(back.index + tabRow, back.offset)
    }

    companion object {
        /**
         * The items of the profile's list above its tab row, in order: the
         * header, then the failure, the first load's row or the problem's,
         * when one shows. The list is drawn from these, and their count is
         * the tab row's place.
         */
        fun itemsAbove(failed: Boolean, firstLoad: Boolean, problem: Boolean): List<ProfileTop> =
            listOf(ProfileTop.HEADER) + listOfNotNull(
                when {
                    failed -> ProfileTop.FAILURE
                    firstLoad -> ProfileTop.LOADING
                    problem -> ProfileTop.PROBLEM
                    else -> null
                },
            )
    }
}

/** An item of the profile's list above its tab row ([ProfileTabScroll.itemsAbove]), by its key. */
internal enum class ProfileTop(val key: String) { HEADER("header"), FAILURE("failure"), LOADING("loading"), PROBLEM("problem") }
