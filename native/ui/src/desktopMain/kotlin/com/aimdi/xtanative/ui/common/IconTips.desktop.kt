package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.theme.AppRipples
import com.aimdi.xtanative.ui.theme.KeyboardFocusLook
import com.aimdi.xtanative.ui.theme.RingedRipples

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
        modifier = Modifier.keyboardFocusRing(),
    ) { RingedFocus(content) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal actual fun RingedFocus(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalRippleConfiguration provides RingedRipples, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal actual fun UnringedFocus(content: @Composable () -> Unit) {
    CompositionLocalProvider(AppRipples, content = content)
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
 * first stop on a button showed nothing. A focus a click gave shows no ring
 * ([KeyboardFocusLook]), as the ripple shows it no more than faintly.
 */
@Composable
internal actual fun Modifier.keyboardFocusRing(): Modifier = ring(RING_INSET)

@Composable
internal actual fun Modifier.keyboardFocusRing(outset: Dp): Modifier = ring(-outset)

/** The keyboard's ring [inset] inside the bounds (outside where negative), its ends round. */
@Composable
private fun Modifier.ring(inset: Dp): Modifier {
    var focused by remember { mutableStateOf(false) }
    val color = MaterialTheme.colorScheme.primary
    return onFocusChanged { focused = it.hasFocus && KeyboardFocusLook.keyboard }.drawWithContent {
        drawContent()
        if (!focused) return@drawWithContent
        val by = inset.toPx()
        val ring = Size(size.width - 2 * by, size.height - 2 * by)
        if (ring.minDimension <= 0f) return@drawWithContent
        val radius = ring.minDimension / 2
        drawRoundRect(color, Offset(by, by), ring, CornerRadius(radius, radius), style = Stroke(RING_WIDTH.toPx()))
    }
}

/**
 * Read without being followed: a mode turning to the keyboard must not
 * take away the focus a selection already holds (Ctrl+C, a key, turns it),
 * only keep Tab from stopping there.
 */
internal actual fun Modifier.pointerOnlyFocus(): Modifier = focusProperties {
    canFocus = !Snapshot.withoutReadObservation { KeyboardFocusLook.keyboard }
}
