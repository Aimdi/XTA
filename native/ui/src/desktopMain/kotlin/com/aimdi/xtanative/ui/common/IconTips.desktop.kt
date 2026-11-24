package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/** How long the pointer rests before the label shows, as the desktop's own tooltips wait. */
internal const val TIP_DELAY_MS = 600

@OptIn(ExperimentalFoundationApi::class)
@Composable
actual fun IconTip(label: String, content: @Composable () -> Unit) {
    if (label.isBlank()) return content()
    TooltipArea(
        tooltip = { TipBubble(label) },
        delayMillis = TIP_DELAY_MS,
        tooltipPlacement = TipPlacement,
        modifier = Modifier.focusRing(),
    ) { content() }
}

/** Where a tip shows: just under the pointer. */
@OptIn(ExperimentalFoundationApi::class)
internal val TipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 16.dp))

/** The widest a tip grows before its words wrap: a long title should not cross the whole window. */
private val TIP_MAX_WIDTH = 480.dp

/** The small dark label a tip shows ([IconTip], [TextTip]). */
@Composable
internal fun TipBubble(label: String) {
    Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, shadowElevation = 2.dp) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier.widthIn(max = TIP_MAX_WIDTH).padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** How far in from the button's touch area the ring is drawn: an icon button's own 40 dp circle in its 48 dp area. */
private val RING_INSET = 4.dp

private val RING_WIDTH = 2.dp

/**
 * A ring round what the keyboard's focus is on. The button's own ripple
 * shows the focus too, but only from its second time: Compose makes the
 * ripple when the focus first arrives, after the focus was told, so Tab's
 * first stop on a button showed nothing.
 */
@Composable
private fun Modifier.focusRing(): Modifier {
    var focused by remember { mutableStateOf(false) }
    val color = MaterialTheme.colorScheme.primary
    return onFocusChanged { focused = it.hasFocus }.drawWithContent {
        drawContent()
        if (!focused) return@drawWithContent
        val inset = RING_INSET.toPx()
        val ring = Size(size.width - 2 * inset, size.height - 2 * inset)
        if (ring.minDimension <= 0f) return@drawWithContent
        val radius = ring.minDimension / 2
        drawRoundRect(color, Offset(inset, inset), ring, CornerRadius(radius, radius), style = Stroke(RING_WIDTH.toPx()))
    }
}
