package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * A drag from the start edge opens Home's drawer, as Flutter's Scaffold opens
 * XTA's (a strip 20dp wide past the screen's own inset): the gesture is taken
 * before the page sees it once it goes sideways towards the page's middle, so
 * a drag anywhere else still turns the timelines, and a tap at the edge still
 * reaches what is there.
 */
@Composable
internal fun Modifier.drawerEdge(drawer: DrawerState): Modifier {
    val scope = rememberCoroutineScope()
    val direction = LocalLayoutDirection.current
    val density = LocalDensity.current
    val slop = LocalViewConfiguration.current.touchSlop
    val inset = WindowInsets.safeDrawing.asPaddingValues(density).calculateStartPadding(direction)
    val edge = with(density) { (inset + EDGE).toPx() }
    val rtl = direction == LayoutDirection.Rtl
    return pointerInput(drawer, edge, rtl, slop) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val fromStart = if (rtl) size.width - down.position.x else down.position.x
            if (!drawer.isClosed || fromStart > edge) return@awaitEachGesture
            var claimed = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if (claimed) {
                    change.consume()
                    continue
                }
                val across = (change.position.x - down.position.x).let { if (rtl) -it else it }
                val along = abs(change.position.y - down.position.y)
                if (along > slop && along > abs(across)) break
                if (across < -slop) break
                if (across > slop) {
                    claimed = true
                    change.consume()
                    scope.launch { drawer.open() }
                }
            }
        }
    }
}

/** How far in from the edge a drag opens the drawer: Flutter's kEdgeDragWidth. */
private val EDGE = 20.dp
