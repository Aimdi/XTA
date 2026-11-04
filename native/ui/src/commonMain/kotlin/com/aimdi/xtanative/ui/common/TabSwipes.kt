package com.aimdi.xtanative.ui.common

import kotlin.math.abs
import kotlin.math.sign

/**
 * When a sideways swipe turns a page of tabs, as XTA's tabs and Home's bar
 * turn (reader_swipe_navigation.dart): it must go [DISTANCE] across, or be
 * flicked at [FLICK] after [MINIMUM], be released the way it went, and go
 * more across than up or down ([ACROSS] times as far); the tabs do not wrap.
 * Distances are in dp, speeds in dp/s.
 */
object TabSwipes {
    const val DISTANCE = 72f
    const val MINIMUM = 24f
    const val FLICK = 650f
    const val ACROSS = 1.3f

    /** A swipe starting this close to the screen's side is left to the system's back gesture. */
    const val EDGE = 24f

    /**
     * The tab a swipe across ends on (pageAfterNavigationSwipe): [distance]
     * travelled (left negative) and [velocity] at release; in a right-to-left
     * layout, left leads back.
     */
    fun turned(current: Int, count: Int, distance: Float, velocity: Float, rtl: Boolean = false): Int {
        if (count <= 1 || current !in 0 until count) return current
        if (!distance.isFinite() || !velocity.isFinite() || abs(distance) < MINIMUM) return current
        if (velocity != 0f && velocity.sign != distance.sign) return current
        if (abs(velocity) < FLICK && abs(distance) < DISTANCE) return current
        val physical = if (distance < 0) 1 else -1
        val next = current + if (rtl) -physical else physical
        return if (next in 0 until count) next else current
    }

    /** The tab a swipe that went [dx] across and [dy] down ends on: [current] unless it went across ([turned]). */
    fun swiped(current: Int, count: Int, dx: Float, dy: Float, velocity: Float, rtl: Boolean = false): Int =
        if (abs(dx) > abs(dy) * ACROSS) turned(current, count, dx, velocity, rtl) else current

    /**
     * Whether a swipe that first went [first] across (its sign; 0 before it
     * went [MINIMUM]) and is now [dx] from where it started has turned back,
     * which leaves the tabs as they are.
     */
    fun reversed(first: Float, dx: Float): Boolean = first != 0f && dx * first < 0

    /** The way a swipe at [dx] from its start first went, once it went [MINIMUM]: its sign, else [first] as it was. */
    fun firstWay(first: Float, dx: Float): Float = if (first == 0f && abs(dx) >= MINIMUM) dx.sign else first
}
