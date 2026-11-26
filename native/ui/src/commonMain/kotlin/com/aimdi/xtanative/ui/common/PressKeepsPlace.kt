package com.aimdi.xtanative.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.relocation.BringIntoViewModifierNode
import androidx.compose.ui.relocation.bringIntoView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize

/**
 * Keeps the list still when a mouse press lands here. On the desktop a
 * click focuses what it lands on, and anything focused asks its list to
 * scroll it fully into view: a post whose top had scrolled under the bar
 * jumped down to the list's top at the press, and so came back there from
 * the post it opened. Requests made just after a mouse press are dropped;
 * those of the keys (Tab, the reading keys) still scroll as before. A touch
 * focuses nothing, so on a phone this changes nothing.
 */
fun Modifier.pressKeepsPlace(): Modifier = this then PressKeepsPlaceElement

/** When the scroll a mouse press's focus asks for may come. */
internal object PressKeepsPlace {
    /** Half a second: the focus asks within a frame or two; a key pressed this soon after a click is not expected. */
    const val PRESS_WINDOW_NANOS: Long = 500_000_000L

    /** Whether a scroll asked for at [now] follows a mouse press at [pressedAt] (both in nanoseconds; null: none yet) closely enough to be that press's focus. */
    fun followsPress(pressedAt: Long?, now: Long): Boolean = pressedAt != null && now - pressedAt in 0..PRESS_WINDOW_NANOS
}

private object PressKeepsPlaceElement : ModifierNodeElement<PressKeepsPlaceNode>() {
    override fun create() = PressKeepsPlaceNode()
    override fun update(node: PressKeepsPlaceNode) = Unit
    override fun hashCode() = "pressKeepsPlace".hashCode()
    override fun equals(other: Any?) = other === this
}

private class PressKeepsPlaceNode : Modifier.Node(), BringIntoViewModifierNode, PointerInputModifierNode {
    private var pressedAt: Long? = null

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        val mouse = pointerEvent.changes.any { it.type == PointerType.Mouse }
        if (pass == PointerEventPass.Initial && pointerEvent.type == PointerEventType.Press && mouse) pressedAt = System.nanoTime()
    }

    override fun onCancelPointerInput() = Unit

    override suspend fun bringIntoView(childCoordinates: LayoutCoordinates, boundsProvider: () -> Rect?) {
        if (PressKeepsPlace.followsPress(pressedAt, System.nanoTime())) return
        val own = requireLayoutCoordinates()
        bringIntoView {
            if (!childCoordinates.isAttached || !own.isAttached) return@bringIntoView null
            val rect = boundsProvider() ?: Rect(Offset.Zero, childCoordinates.size.toSize())
            rect.translate(own.localBoundingBoxOf(childCoordinates, clipBounds = false).topLeft)
        }
    }
}
