package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** A row that scrolls sideways, as a desktop mouse moves it: its up-and-down wheel turns it. */
internal object SidewaysWheel {
    /**
     * How far the row moves for a wheel turn of [x] and [y] notches, [notch]
     * pixels each; null leaves the turn to what is around it: a sideways turn
     * (the row takes that itself) or one towards an end the row is already at.
     */
    fun delta(x: Float, y: Float, notch: Float, canBack: Boolean, canForward: Boolean): Float? {
        if (y == 0f || abs(x) >= abs(y)) return null
        val move = y * notch
        return move.takeIf { (it > 0f && canForward) || (it < 0f && canBack) }
    }
}

/** The up-and-down wheel over a row scrolled by [state] moves it sideways ([SidewaysWheel]). */
@Composable
fun Modifier.wheelScrollsSideways(state: ScrollableState): Modifier {
    val notch = with(LocalDensity.current) { WHEEL_NOTCH.toPx() }
    return pointerInput(state, notch) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Scroll) continue
                val change = event.changes.firstOrNull() ?: continue
                val move = SidewaysWheel.delta(change.scrollDelta.x, change.scrollDelta.y, notch, state.canScrollBackward, state.canScrollForward) ?: continue
                state.dispatchRawDelta(move)
                change.consume()
            }
        }
    }
}

/** Fades the row scrolled by [state] out at an end with more beyond it, so a cut chip reads as "more this way". */
fun Modifier.edgeFade(state: ScrollableState, width: Dp = EDGE_FADE): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        val fade = width.toPx().coerceAtMost(size.width / 2)
        val rtl = layoutDirection == LayoutDirection.Rtl
        val left = if (rtl) state.canScrollForward else state.canScrollBackward
        val right = if (rtl) state.canScrollBackward else state.canScrollForward
        if (left) drawRect(Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.Black, startX = 0f, endX = fade), size = Size(fade, size.height), blendMode = BlendMode.DstIn)
        if (right) {
            val from = size.width - fade
            drawRect(Brush.horizontalGradient(0f to Color.Black, 1f to Color.Transparent, startX = from, endX = size.width), topLeft = Offset(from, 0f), size = Size(fade, size.height), blendMode = BlendMode.DstIn)
        }
    }

/** One wheel notch's travel, as a desktop list scrolls by. */
private val WHEEL_NOTCH = 64.dp

private val EDGE_FADE = 32.dp
