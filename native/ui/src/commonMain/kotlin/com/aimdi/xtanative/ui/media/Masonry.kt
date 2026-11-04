package com.aimdi.xtanative.ui.media

import kotlin.math.abs

/** A tile of a [Masonry]: its column, and where it starts and how tall it is, in pitches. */
data class MasonryCell(val column: Int, val top: Float, val height: Float) {
    val bottom: Float get() = top + height
}

/** A stripe of a [Masonry], from [top] to [bottom] in pitches, and the tiles ([cells]' indexes) it crosses. */
data class MasonrySlice(val top: Float, val bottom: Float, val cells: List<Int>)

/**
 * A masonry (media_grid.dart's MasonryGridView): tiles in their order, each
 * put in the shortest column, at its own shape. Lengths are in pitches, a
 * column's width with the gap after it, so one plan holds at any width.
 *
 * A list draws it in [slices]: stripes, each holding every tile it crosses,
 * so a tile on a cut is drawn in both halves. Cuts fall where the fewest
 * tiles cross, about [Masonry.SPAN] pitches apart, and only where no tile
 * still to come can reach, above the end of every column: the stripes cut
 * stay as they are when more tiles come, and the last one holds the rest.
 * With one column each tile is a stripe of its own.
 */
class Masonry private constructor(val columns: Int, val cells: List<MasonryCell>, val slices: List<MasonrySlice>) {
    /** Where the longest column ends. */
    val height: Float get() = cells.maxOfOrNull { it.bottom } ?: 0f

    companion object {
        /** About how tall a stripe is, in pitches. */
        const val SPAN = 2.5f

        /** Tiles of [aspects] (width over height) in [columns] columns. */
        fun of(aspects: List<Float>, columns: Int, span: Float = SPAN): Masonry {
            val count = columns.coerceAtLeast(1)
            val ends = FloatArray(count)
            val cells = aspects.map { aspect ->
                val column = ends.indices.minBy { ends[it] }
                MasonryCell(column, ends[column], 1f / (aspect.takeIf { it.isFinite() && it > 0f } ?: 1f)).also { ends[column] += it.height }
            }
            val slices = if (count == 1) cells.mapIndexed { i, cell -> MasonrySlice(cell.top, cell.bottom, listOf(i)) } else sliced(cells, ends.min(), span)
            return Masonry(count, cells, slices)
        }

        private fun sliced(cells: List<MasonryCell>, complete: Float, span: Float): List<MasonrySlice> {
            val end = cells.maxOfOrNull { it.bottom } ?: return emptyList()
            val ends = cells.map { it.bottom }.filter { it <= complete }.distinct().sorted()
            val cuts = generateSequence(nextCut(cells, ends, 0f, span, complete)) { nextCut(cells, ends, it, span, complete) }.toList()
            val edges = listOf(0f) + cuts + listOfNotNull(end.takeIf { it > (cuts.lastOrNull() ?: 0f) })
            return edges.zipWithNext { top, bottom -> MasonrySlice(top, bottom, cells.indices.filter { cells[it].top < bottom && cells[it].bottom > top }) }
        }

        /**
         * The cut after [cut]: among the tiles' [ends] about [span] on, the one
         * that splits the fewest tiles, else the first end past them; none
         * while tiles still to come could change the choice.
         */
        private fun nextCut(cells: List<MasonryCell>, ends: List<Float>, cut: Float, span: Float, complete: Float): Float? {
            if (cut + span * 3 / 2 > complete) return null
            val near = ends.filter { it > cut + span / 2 && it <= cut + span * 3 / 2 }
            if (near.isEmpty()) return ends.firstOrNull { it > cut + span * 3 / 2 }
            return near.minWith(compareBy<Float> { y -> crossings(cells, y) }.thenBy { y -> abs(y - cut - span) })
        }

        /** How many tiles a cut at [y] would split. Tiles start in order down the columns, so the first one starting past [y] ends the count. */
        private fun crossings(cells: List<MasonryCell>, y: Float): Int = cells.asSequence().takeWhile { it.top < y }.count { it.bottom > y }
    }
}
