package com.aimdi.xtanative.ui.common

import coil3.ComponentRegistry
import kotlin.math.roundToInt

/**
 * What a platform adds to the app's image loader. The desktop adds a decoder
 * that shrinks a picture smoothly: Coil's own there draws it into its tile
 * with Skia's default sampling, the nearest pixel, which leaves every picture
 * larger than its tile jagged. Android's decoder samples well by itself.
 */
internal expect fun ComponentRegistry.Builder.addPlatformPictureDecoders()

/** The size a picture is decoded to. */
internal object PictureScaling {
    /** No decoded side is longer than this, as Coil's own limit. */
    const val LONGEST_SIDE = 4096

    /**
     * A picture of more pixels than this is not decoded at all: Skia decodes
     * the whole picture before shrinking it, four bytes a pixel, so a crafted
     * 30000 by 30000 one would take gigabytes. X's largest are 4096 square.
     */
    const val MOST_PIXELS = 64_000_000L

    /** Whether a [width] by [height] picture, as its header gives it, is decoded. */
    fun decodable(width: Int, height: Int): Boolean = width > 0 && height > 0 && width.toLong() * height <= MOST_PIXELS

    /**
     * Whether shrinking a [width] by [height] picture to [decodedWidth] by
     * [decodedHeight] goes through mipmaps: only below half its size, where
     * plain linear filtering would skip pixels. Above it they would cost a
     * third more memory for nothing.
     */
    fun mipmapped(width: Int, height: Int, decodedWidth: Int, decodedHeight: Int): Boolean =
        maxOf(decodedWidth.toDouble() / width, decodedHeight.toDouble() / height) < 0.5

    /**
     * The size a [width] by [height] picture is decoded to for a box
     * [boxWidth] by [boxHeight] (0 for a side that is not bounded): shrunk
     * until it just covers the box ([fill], a cropped tile) or just fits in
     * it, never enlarged, and never longer than [LONGEST_SIDE] on a side.
     */
    fun decodedSize(width: Int, height: Int, boxWidth: Int, boxHeight: Int, fill: Boolean): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return width to height
        val across = if (boxWidth > 0) boxWidth.toDouble() / width else null
        val down = if (boxHeight > 0) boxHeight.toDouble() / height else null
        val wanted = when {
            across != null && down != null -> if (fill) maxOf(across, down) else minOf(across, down)
            else -> across ?: down ?: 1.0
        }
        val limit = LONGEST_SIDE.toDouble() / maxOf(width, height)
        val factor = minOf(wanted, limit, 1.0)
        return (width * factor).roundToInt().coerceAtLeast(1) to (height * factor).roundToInt().coerceAtLeast(1)
    }
}
