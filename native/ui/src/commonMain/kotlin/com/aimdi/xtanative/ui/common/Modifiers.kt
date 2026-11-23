package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput

/** A tap target for a line of text, without the ripple a row would show. */
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
}

/**
 * A long press anywhere on this, over its tappable parts too, as a Flutter
 * GestureDetector around a card takes one: a tap still goes to what is under
 * it, and a drag or a scroll to what moves; a press held past the long
 * press timeout instead runs [onLongPress] with where it was pressed, and
 * what is under it does not take it for a tap. A press [accepts] turns down
 * where it starts is left alone. It buzzes while Haptic feedback is on.
 */
fun Modifier.longPressOver(accepts: (Offset) -> Boolean = { true }, onLongPress: (Offset) -> Unit): Modifier = composed {
    val haptics = rememberSwitchedHaptics()
    val takes by rememberUpdatedState(accepts)
    val press by rememberUpdatedState(onLongPress)
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!takes(down.position)) return@awaitEachGesture
            // Released, moved away or taken by a scroll before the timeout: nothing of this press is ours.
            val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation(PointerEventPass.Initial) ?: down }
            if (ended != null) return@awaitEachGesture
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            press(down.position)
            consumeUntilUp()
        }
    }
}

/** The rest of a press taken, so that nothing under it acts on it. */
private suspend fun AwaitPointerEventScope.consumeUntilUp() {
    do {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        event.changes.forEach { it.consume() }
    } while (event.changes.any { it.pressed })
}

/**
 * [action] at a press of the mouse's secondary (right) button, as a desktop
 * opens a thing's menu; nothing on a touch screen, which has no such button.
 * The innermost thing with a menu takes the press (a photo's in a post, not
 * the post's as well). The primary button's tap is left to the modifiers
 * after it.
 */
fun Modifier.onSecondaryClick(action: () -> Unit): Modifier = composed {
    val latest by rememberUpdatedState(action)
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                // Main: the innermost first. What a press runs through is not consumed, so taps, selection and
                // the like below see it as before; the press is only marked as one a menu took.
                val event = awaitPointerEvent(PointerEventPass.Main)
                val press = event.changes.firstOrNull() ?: continue
                if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed && SecondaryPresses.take(press.uptimeMillis)) latest()
            }
        }
    }
}

/** The right-click a menu has taken, by its time: the things around it with a menu of their own leave it be. */
internal object SecondaryPresses {
    private var taken = -1L

    /** Whether the press at [uptime] is still free, taking it if so. */
    fun take(uptime: Long): Boolean = (uptime != taken).also { taken = uptime }
}
