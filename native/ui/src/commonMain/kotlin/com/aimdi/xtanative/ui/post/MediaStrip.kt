package com.aimdi.xtanative.ui.post

import com.aimdi.xtanative.core.x.model.XMedia
import kotlin.math.min

/**
 * How a post's media is laid out (media_strip.dart): one at its own shape
 * across the width; several along a row at one height, each as wide as its
 * shape makes it there, the next one's edge in view, the row scrolling
 * sideways. Tall photos are narrow and three fit, wide ones barely two: that
 * falls out of the shapes, not a rule about the count.
 */
object MediaStrip {
    /** The range a card of a row is shown between: 4:5 at the tallest, 1.91:1 at the widest; beyond, it is cropped to the nearest. */
    const val MIN_ASPECT = 0.8f
    const val MAX_ASPECT = 1.91f

    /** How tall a row of several is, against the width it has to fill. */
    const val HEIGHT_FACTOR = 0.62f

    /** The most of the row one card may take, so an edge of the next one always shows. */
    const val CARD_MAX_WIDTH_FACTOR = 0.86f

    /** The gap between cards, in dp. */
    const val GAP = 8f

    /** The height of the row and the width of every card in it, in the unit of the width given. */
    data class Layout(val height: Float, val widths: List<Float>)

    /**
     * The shape of [media], width over height: a video's or a GIF's own, a
     * photo's, else 16:9 as XTA falls back to when X sent no size.
     */
    fun aspectOf(media: XMedia): Float = media.aspectRatio?.takeIf { it.isFinite() && it > 0f } ?: (16f / 9f)

    /** A picture alone keeps its own shape, however tall: nothing beside it has to agree with it. */
    fun singleAspect(aspect: Float): Float = if (!aspect.isFinite() || aspect <= 0f) 1f else aspect

    /** [aspect] kept within the row's range. */
    fun clamp(aspect: Float): Float = if (!aspect.isFinite() || aspect <= 0f) 1f else aspect.coerceIn(MIN_ASPECT, MAX_ASPECT)

    /**
     * [aspects] laid out along a row [width] wide: one keeps the width and its
     * own shape; several share a height and each is as wide as its shape at
     * that height, never more than [CARD_MAX_WIDTH_FACTOR] of the row.
     */
    fun layout(width: Float, aspects: List<Float>): Layout = when (aspects.size) {
        0 -> Layout(0f, emptyList())
        1 -> Layout(width / singleAspect(aspects.single()), listOf(width))
        else -> {
            val height = width * HEIGHT_FACTOR
            val widest = width * CARD_MAX_WIDTH_FACTOR
            Layout(height, aspects.map { min(height * clamp(it), widest) })
        }
    }

    /** The card a row opened at [start] shows first: the one tapped in a grid, kept within the row. */
    fun startOf(start: Int, count: Int): Int = if (count <= 0) 0 else start.coerceIn(0, count - 1)
}
