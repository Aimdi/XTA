package com.aimdi.xtanative.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A list's item dragged by the mouse or a finger ([DragSlots], [ReorderDrags]). */
class DragSlotsTest {
    private val sizes = listOf(40, 60, 100, 50)

    @Test
    fun anItemLandsPastEachNeighbourItCrossesMoreThanHalfOf() {
        assertEquals(0, DragSlots.drop(0, 29f, sizes), "not yet half of the next")
        assertEquals(1, DragSlots.drop(0, 31f, sizes))
        assertEquals(2, DragSlots.drop(0, 111f, sizes), "past half of the third too")
        assertEquals(3, DragSlots.drop(0, 900f, sizes), "no further than the end")
        assertEquals(0, DragSlots.drop(3, -900f, sizes), "nor the start")
        assertEquals(3, DragSlots.drop(3, -49f, sizes))
        assertEquals(2, DragSlots.drop(3, -51f, sizes))
        assertEquals(1, DragSlots.drop(3, -151f, sizes))
        assertEquals(2, DragSlots.drop(2, 0f, sizes), "not moved")
        assertEquals(7, DragSlots.drop(7, 50f, sizes), "an item not there")
    }

    @Test
    fun theOthersMakeWayByTheDraggedItemsSize() {
        // The first (40) dragged to the third place: the second and third move up by 40.
        assertEquals(listOf(0f, -40f, -40f, 0f), sizes.indices.map { DragSlots.shift(it, 0, 2, sizes) })
        // The last (50) dragged to the second place: the second and third move down by 50.
        assertEquals(listOf(0f, 50f, 50f, 0f), sizes.indices.map { DragSlots.shift(it, 3, 1, sizes) })
        assertEquals(listOf(0f, 0f, 0f, 0f), sizes.indices.map { DragSlots.shift(it, 1, 1, sizes) }, "dropped where it was")
        assertEquals(0f, DragSlots.shift(2, null, null, sizes), "no drag")
    }

    @Test
    fun theViewScrollsNearAnEdgeTheWayTheItemIsDragged() {
        // A view from 0 to 600 with 50 px bands, 20 px a frame at most.
        fun step(lead: Float, trail: Float, moving: Float) = DragSlots.edgeStep(lead, trail, 0f, 600f, 50f, 20f, moving)
        assertEquals(0f, step(200f, 260f, 30f), "in the middle")
        assertEquals(10f, step(500f, 575f, 30f), "half into the far band: half speed")
        assertEquals(20f, step(560f, 700f, 30f), "past the edge: full speed")
        assertEquals(-20f, step(-10f, 40f, -30f), "towards the start")
        assertEquals(-4f, step(40f, 100f, -30f))
        assertEquals(0f, step(560f, 620f, -30f), "picked up at the far edge but dragged back: no scroll")
        assertEquals(0f, step(-10f, 40f, 0f), "not dragged yet")
        assertEquals(0f, step(-10f, 700f, 30f), "longer than the view")
        assertEquals(0f, DragSlots.edgeStep(560f, 580f, 0f, 90f, 50f, 20f, 30f), "a view too short for two bands")
    }

    @Test
    fun escCancelsTheDragGoingOnOnce() {
        var cancelled = 0
        val stop = { cancelled++; Unit }
        assertFalse(ReorderDrags.escape(), "nothing dragged: Esc goes on to the page")
        ReorderDrags.began(stop)
        assertTrue(ReorderDrags.escape())
        assertEquals(1, cancelled)
        assertFalse(ReorderDrags.escape(), "spent")
        ReorderDrags.began(stop)
        ReorderDrags.ended(stop)
        assertFalse(ReorderDrags.escape(), "let go before Esc")
        val other = { Unit }
        ReorderDrags.began(stop)
        ReorderDrags.ended(other)
        assertTrue(ReorderDrags.escape(), "another drag's end leaves this one")
        assertEquals(2, cancelled)
    }
}
