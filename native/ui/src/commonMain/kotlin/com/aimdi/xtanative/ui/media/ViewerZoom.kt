package com.aimdi.xtanative.ui.media

import kotlin.math.abs
import kotlin.math.max

/** A point or a shift on screen, in pixels. */
data class Shift(val x: Float, val y: Float) {
    operator fun plus(other: Shift) = Shift(x + other.x, y + other.y)

    operator fun minus(other: Shift) = Shift(x - other.x, y - other.y)

    operator fun times(factor: Float) = Shift(x * factor, y * factor)

    companion object {
        val ZERO = Shift(0f, 0f)
    }
}

/**
 * How a photo in the viewer zooms and pans (_photo.dart's gesture config,
 * double_tap_zoom.dart): between [MIN] and [MAX] times its fitted size, a
 * pinch allowed a little below [MIN] before it springs back, and a double
 * tap toggling between the two ends around the point tapped. The photo is
 * scaled about the middle of the page, then shifted.
 */
object ViewerZoom {
    const val MIN = 1f
    const val MAX = 4f

    /** How far below [MIN] a pinch may go while the fingers are down. */
    const val PINCH_FLOOR = 0.9f

    /** The other end of the zoom from [scale]: fully in from the fitted size, else back to it. */
    fun doubleTapScale(scale: Float): Float = if (scale == MIN) MAX else MIN

    /**
     * The shift that keeps the photo's point under [focus] where it is while
     * the scale goes from [from] to [to], [focus] measured from the middle of
     * a page [width] by [height]; within [bounds].
     */
    fun shiftFor(focus: Shift, from: Float, fromShift: Shift, to: Float, width: Float, height: Float): Shift {
        if (to <= MIN) return Shift.ZERO
        val ratio = to / from
        return bounds(focus - (focus - fromShift) * ratio, to, width, height)
    }

    /** [shift] kept where the scaled photo still covers the page: no edge drawn in from outside it. */
    fun bounds(shift: Shift, scale: Float, width: Float, height: Float): Shift {
        if (scale <= MIN) return Shift.ZERO
        val maxX = max(0f, (width * scale - width) / 2f)
        val maxY = max(0f, (height * scale - height) / 2f)
        return Shift(shift.x.coerceIn(-maxX, maxX), shift.y.coerceIn(-maxY, maxY))
    }

    /** A pinch's new scale, within [PINCH_FLOOR] and [MAX]. */
    fun pinched(scale: Float, zoom: Float): Float = (scale * zoom).coerceIn(PINCH_FLOOR, MAX)

    /** Where a scale settles once the fingers lift: back up to [MIN] from below it. */
    fun settled(scale: Float): Float = scale.coerceIn(MIN, MAX)

    /** Whether the photo is zoomed in, so one finger pans it rather than turning the page or closing the viewer. */
    fun zoomed(scale: Float): Boolean = scale > MIN + 0.01f
}

/**
 * Dragging the viewer up or down to close it (_photo.dart's slide-out page):
 * the page follows the finger, the black behind it fades, and letting go far
 * enough, or quickly enough, closes the viewer.
 */
object SlideToClose {
    /** The share of the page's height a drag must pass to close it. */
    const val DISTANCE = 0.15f

    /** A flick faster than this, in pixels a second, closes it whatever the distance. */
    const val VELOCITY = 1_800f

    /** How a drag starts: mostly up or down, by at least [slop]; a sideways one turns the page instead. */
    fun startsSlide(dx: Float, dy: Float, slop: Float): Boolean = abs(dy) > slop && abs(dy) > abs(dx) * 1.5f

    fun startsPage(dx: Float, dy: Float, slop: Float): Boolean = abs(dx) > slop && abs(dx) >= abs(dy)

    fun closes(drag: Float, height: Float, velocity: Float): Boolean =
        (height > 0 && abs(drag) > height * DISTANCE) || abs(velocity) > VELOCITY

    /** How dark the backdrop stays while the page is [drag] off its place. */
    fun backdrop(drag: Float, height: Float): Float = if (height <= 0) 1f else (1f - abs(drag) / (height * 0.6f)).coerceIn(0.2f, 1f)
}
