package com.aimdi.xtanative.ui.home

/**
 * How Home uses a wide window (a desktop window, a tablet on its side): the
 * bottom bar and the account menu stand as one column down its start edge,
 * and Home's timelines are read side by side, as many as fit, instead of one
 * at a time in a column with empty room on both sides.
 */
object HomeWide {
    /** From this width (dp) the navigation is a column beside the pages. */
    const val SIDEBAR_FROM = 1000f

    /** The navigation column's width, in dp. */
    const val SIDEBAR_WIDTH = 320

    /** The column and its dividing line, in dp: how far the screens beside it stand from the window's start edge. */
    const val FRAME_INSET = SIDEBAR_WIDTH + 1

    /** The narrowest a timeline is read at beside another, in dp. */
    const val COLUMN_MIN = 420f

    /** The widest a timeline grows beside another: the reading column's width. */
    const val COLUMN_MAX = 720f

    /** More timelines than this side by side read too many feeds at once. */
    const val MAX_COLUMNS = 3

    fun sidebar(widthDp: Float): Boolean = widthDp >= SIDEBAR_FROM

    /** How many of [timelines] a page [widthDp] wide shows side by side: one where two do not fit. */
    fun columns(widthDp: Float, timelines: Int): Int =
        (widthDp / COLUMN_MIN).toInt().coerceAtMost(MAX_COLUMNS).coerceAtMost(timelines).coerceAtLeast(1)

    /** Each column's width (dp) when [count] share [widthDp], their dividers a dp each. */
    fun columnWidth(widthDp: Float, count: Int): Float =
        ((widthDp - (count - 1)) / count.coerceAtLeast(1)).coerceAtMost(COLUMN_MAX)

    /**
     * The timelines read beside the one shown, [index] of [tabs]: the next
     * ones in the picker's order, going round to the first after the last,
     * so every timeline can be the first column and the others follow it.
     */
    fun <T> beside(tabs: List<T>, index: Int, count: Int): List<T> {
        if (index !in tabs.indices || count <= 1) return emptyList()
        return (1 until tabs.size).map { tabs[(index + it) % tabs.size] }.take(count - 1)
    }
}
