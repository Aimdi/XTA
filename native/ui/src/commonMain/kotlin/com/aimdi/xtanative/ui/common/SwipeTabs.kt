package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * True where a parent turns pages of its own with sideways swipes (Home's
 * timelines): tabs drawn inside leave the swipes to it ([swipeTabs]), as
 * XTA's readers embedded in Home leave them to Home.
 */
val LocalParentTurnsPages = compositionLocalOf { false }

/**
 * Sideways swipes over this turn [count] tabs, as XTA's tabs turn
 * (reader_swipe_navigation.dart): released far or fast enough across
 * ([TabSwipes]), a swipe calls [onTurn] with the tab before or after
 * [selected], with a tick. Touch and pens only (a mouse clicks the tabs),
 * never from the screen's sides (the system's back gesture), never a swipe
 * something inside takes first (a row of chips, a slider, a carousel), and
 * never where a parent turns its own pages ([LocalParentTurnsPages]).
 */
fun Modifier.swipeTabs(selected: Int, count: Int, onTurn: (Int) -> Unit): Modifier = composed {
    if (count < 2 || LocalParentTurnsPages.current) return@composed Modifier
    val current by rememberUpdatedState(selected)
    val turn by rememberUpdatedState(onTurn)
    val haptics = LocalHapticFeedback.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val gestures = WindowInsets.systemGestures
    pointerInput(count, rtl) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.type == PointerType.Mouse || nearSide(down.position.x, size.width, gestures, this)) return@awaitEachGesture
            val swipe = followSwipe(down) ?: return@awaitEachGesture
            val next = TabSwipes.swiped(current, count, swipe.x / density, swipe.y / density, swipe.velocity / density, rtl)
            if (next != current) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                turn(next)
            }
        }
    }
}

/** [swipeTabs] for tabs named by [tabs], [selected] among them. */
fun <T> Modifier.swipeTabs(tabs: List<T>, selected: T, onTurn: (T) -> Unit): Modifier =
    swipeTabs(tabs.indexOf(selected), if (selected in tabs) tabs.size else 0) { index -> tabs.getOrNull(index)?.let(onTurn) }

/** Where a swipe ended: how far it went across and down, and how fast across, in pixels. */
private class Swipe(val x: Float, val y: Float, val velocity: Float)

/**
 * The swipe [down] starts, once it goes across past the touch slop with
 * nothing inside taking it, followed to its release; null when it is not
 * one, is taken, turns back, or a second finger joins it.
 */
private suspend fun AwaitPointerEventScope.followSwipe(down: PointerInputChange): Swipe? {
    var travel = Offset.Zero
    val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
        change.consume()
        travel = change.position - down.position
    } ?: return null
    val tracker = VelocityTracker().apply { addPointerInputChange(start) }
    var first = TabSwipes.firstWay(0f, travel.x)
    var broken = false
    val released = horizontalDrag(start.id) { change ->
        tracker.addPointerInputChange(change)
        travel = change.position - down.position
        first = TabSwipes.firstWay(first, travel.x)
        if (TabSwipes.reversed(first, travel.x) || currentEvent.changes.count { it.pressed } > 1) broken = true
        change.consume()
    }
    return if (released && !broken) Swipe(travel.x, travel.y, tracker.calculateVelocity().x) else null
}

/** Whether [x] lies within the side strips a system back gesture starts in, at least [TabSwipes.EDGE] wide. */
private fun nearSide(x: Float, width: Int, gestures: WindowInsets, density: Density): Boolean {
    val edge = with(density) { TabSwipes.EDGE.dp.toPx() }
    val left = maxOf(edge, gestures.getLeft(density, LayoutDirection.Ltr).toFloat())
    val right = maxOf(edge, gestures.getRight(density, LayoutDirection.Ltr).toFloat())
    return x < left || x > width - right
}
