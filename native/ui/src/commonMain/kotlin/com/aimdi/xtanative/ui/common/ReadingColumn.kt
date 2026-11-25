package com.aimdi.xtanative.ui.common

/**
 * The widest a list of posts grows (reading_column.dart): wider than this
 * the posts stay in a centred column, so a post and its pictures do not
 * span a whole desktop window. A phone, or a narrow deck column, keeps its
 * width.
 */
object ReadingColumn {
    /** The column's width, in dp. */
    const val MAX_WIDTH = 720f

    /** The room left on either side of the column in a list [width] dp wide. */
    fun inset(width: Float): Float = inset(width, MAX_WIDTH)

    /** The room left on either side of a centred column [column] dp wide in a space [width] dp wide: none once the column fills it. */
    fun inset(width: Float, column: Float): Float = if (width.isFinite()) ((width - column) / 2).coerceAtLeast(0f) else 0f
}
