package com.aimdi.xtanative.ui.home

import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class SwitcherPopoverTest {
    @Test
    fun itStandsOnTheButtonsStartEdge() {
        assertEquals(340f, SwitcherPopoverPlace.x(340f, 388f, 360f, 1600f, 8f, rtl = false))
    }

    @Test
    fun rightToLeftItEndsOnTheButtonsRightEdge() {
        assertEquals(1140f, SwitcherPopoverPlace.x(1452f, 1500f, 360f, 1600f, 8f, rtl = true))
    }

    @Test
    fun itStaysInsideTheWindow() {
        assertEquals(1232f, SwitcherPopoverPlace.x(1500f, 1548f, 360f, 1600f, 8f, rtl = false))
        assertEquals(8f, SwitcherPopoverPlace.x(0f, 48f, 360f, 1600f, 8f, rtl = true))
        // A window narrower than the popover: at the margin.
        assertEquals(8f, SwitcherPopoverPlace.x(100f, 148f, 360f, 300f, 8f, rtl = false))
    }

    @Test
    fun itDropsJustUnderTheButton() {
        assertEquals(52f, SwitcherPopoverPlace.y(48f, 4f, 8f))
        // A bar scrolled away leaves the button above the window: the popover keeps to its top.
        assertEquals(8f, SwitcherPopoverPlace.y(-40f, 4f, 8f))
    }

    @Test
    fun itIsNoTallerThanTheRoomUnderTheButton() {
        assertEquals(840f, SwitcherPopoverPlace.maxHeight(52f, 900f, 8f, 160f))
        assertEquals(160f, SwitcherPopoverPlace.maxHeight(700f, 720f, 8f, 160f))
    }

    @Test
    fun arrowsMoveAsAMenusDo() {
        assertEquals(FocusDirection.Next, SwitcherPopoverPlace.move(Key.DirectionDown, modified = false))
        assertEquals(FocusDirection.Previous, SwitcherPopoverPlace.move(Key.DirectionUp, modified = false))
        assertEquals(FocusDirection.Left, SwitcherPopoverPlace.move(Key.DirectionLeft, modified = false))
        assertEquals(FocusDirection.Right, SwitcherPopoverPlace.move(Key.DirectionRight, modified = false))
        assertNull(SwitcherPopoverPlace.move(Key.DirectionDown, modified = true))
        assertNull(SwitcherPopoverPlace.move(Key.Enter, modified = false))
    }

    @Test
    fun homeAndEndGoToEitherEnd() {
        assertEquals(true, SwitcherPopoverPlace.end(Key.MoveHome, modified = false))
        assertEquals(true, SwitcherPopoverPlace.end(Key.PageUp, modified = false))
        assertEquals(false, SwitcherPopoverPlace.end(Key.MoveEnd, modified = false))
        assertEquals(false, SwitcherPopoverPlace.end(Key.PageDown, modified = false))
        assertNull(SwitcherPopoverPlace.end(Key.MoveEnd, modified = true))
        assertNull(SwitcherPopoverPlace.end(Key.Tab, modified = false))
    }

    @Test
    fun theButtonThatOpenedItIsTakenOnceSoAHoldOfHomeLaterRisesAsTheSheet() {
        val anchor = SwitcherAnchor(CoroutineScope(EmptyCoroutineContext))
        val picker = anchor.button("following")
        assertSame(picker, anchor.button("following"), "the same button however often it is made afresh")
        assertNull(anchor.take(), "nothing clicked: the sheet")
        anchor.claim(picker)
        assertSame(picker, anchor.take())
        assertNull(anchor.take(), "taken already: a later hold of Home opens no drop-down under it")
    }
}
