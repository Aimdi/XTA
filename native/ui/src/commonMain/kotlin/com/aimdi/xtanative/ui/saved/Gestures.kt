package com.aimdi.xtanative.ui.saved

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * A long press anywhere on what this modifies, heard before what is inside
 * it: a tap still reaches the post within, but once the press is long the
 * rest of the gesture is the row's, so the post does not open as it ends.
 * A finger that moves is scrolling, and is left alone. The gesture keeps
 * running through the recomposition [onLongPress] causes.
 */
fun Modifier.longPressFirst(onLongPress: () -> Unit): Modifier = composed {
    val current by rememberUpdatedState(onLongPress)
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                var change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                while (change != null && change.pressed && (change.position - down.position).getDistance() <= viewConfiguration.touchSlop) {
                    change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                }
                true
            }
            if (ended == null) {
                current()
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            }
        }
    }
}
