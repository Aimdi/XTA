package com.aimdi.xtanative.ui.home

import kotlin.math.abs

/**
 * Turning Home's timeline by dragging it sideways (reader_swipe_navigation.dart,
 * home-gestures.md): the timeline stays still while a small preview names
 * the next one, and a release far or fast enough turns to it
 * ([HomeBar.swiped]). A drag that turns back on itself is called off.
 */
object TimelineSwipe {
    /** How near the screen's edges a drag may start, in dp: closer belongs to the system's back gesture. */
    const val EDGE = 24f

    /** A drag counts as sideways once it went this much further across than down, as XTA's release asks. */
    const val SIDEWAYS = 1.3f

    /** The timeline a drag of [distance] dp (leftward negative) points to from [current] of [count]; null before it moved and past either end. */
    fun target(current: Int, count: Int, distance: Float, rtl: Boolean = false): Int? {
        if (abs(distance) < 1f || current !in 0 until count) return null
        val physical = if (distance < 0) 1 else -1
        return (current + if (rtl) -physical else physical).takeIf { it in 0 until count }
    }

    /** How far the drag is toward turning, from 0 to 1: at 1 a release turns. */
    fun progress(distance: Float): Float = (abs(distance) / HomeBar.SWIPE_DISTANCE).coerceIn(0f, 1f)

    /** Whether a drag that went [first] (its first clear direction, as a sign) is now [distance] dp the other way: called off. */
    fun reversed(first: Float, distance: Float): Boolean = first != 0f && distance * first < 0

    /** The drag's first clear direction once it went [HomeBar.SWIPE_MINIMUM] dp: its sign, or 0 before. */
    fun direction(distance: Float): Float = if (abs(distance) >= HomeBar.SWIPE_MINIMUM) Math.signum(distance) else 0f

    /** Whether a drag starting [x] dp from the start of a [width] dp wide timeline may turn it: not at the edges. */
    fun startsInside(x: Float, width: Float): Boolean = x > EDGE && x < width - EDGE
}
