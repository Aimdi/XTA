package com.aimdi.xtanative.ui.chrome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** When a drag is the card's, as XTA's SwipeBackDragRecognizer and its route decide it. */
class CardDragsTest {
    private val size = CardSize(390f, 844f)
    private val slop = 8f

    private fun decide(vararg moves: CardVec, direction: Float = 1f): CardDrag {
        var aimed = false
        var last = CardDrag.WAIT
        for (move in moves) {
            val (drag, nowAimed) = CardDrags.consider(move, aimed, direction, slop)
            aimed = nowAimed
            last = drag
            if (drag != CardDrag.WAIT) return drag
        }
        return last
    }

    @Test
    fun aDragToTheRightIsTheCardsOncePastTheSlop() {
        assertEquals(CardDrag.WAIT, decide(CardVec(3f, 0f)))
        assertEquals(CardDrag.WAIT, decide(CardVec(7f, 0f)), "past 6 dp it heads back, but a sideways drag inside may take it first")
        assertEquals(CardDrag.TAKE, decide(CardVec(7f, 0f), CardVec(12f, 1f)))
    }

    @Test
    fun aDragToTheLeftOrUpAndDownIsLeftToThePage() {
        assertEquals(CardDrag.LEAVE, decide(CardVec(-10f, 0f)))
        assertEquals(CardDrag.LEAVE, decide(CardVec(2f, 10f)))
        assertEquals(CardDrag.LEAVE, decide(CardVec(8f, 7f)), "it must go 1.15 times more across than down")
    }

    @Test
    fun onceAimedADriftUpOrDownDoesNotGiveItUp() {
        assertEquals(CardDrag.TAKE, decide(CardVec(7f, 1f), CardVec(10f, 9f)))
    }

    @Test
    fun inARightToLeftLanguageItIsDraggedToTheLeft() {
        assertEquals(CardDrag.TAKE, decide(CardVec(-7f, 0f), CardVec(-12f, 0f), direction = -1f))
        assertEquals(CardDrag.LEAVE, decide(CardVec(10f, 0f), direction = -1f))
    }

    @Test
    fun fromAnywhereADragStartsAnywhereOffOnlyAtTheEdge() {
        assertTrue(CardDrags.mayStart(CardVec(200f, 400f), size, rtl = false, anywhere = true, catching = false))
        assertFalse(CardDrags.mayStart(CardVec(200f, 400f), size, rtl = false, anywhere = false, catching = false))
        assertTrue(CardDrags.mayStart(CardVec(20f, 400f), size, rtl = false, anywhere = false, catching = false))
        assertTrue(CardDrags.mayStart(CardVec(370f, 400f), size, rtl = true, anywhere = false, catching = false), "the right edge in a right-to-left language")
        assertFalse(CardDrags.mayStart(CardVec(20f, 400f), size, rtl = true, anywhere = false, catching = false))
    }

    @Test
    fun aCardDroppingBackIsCaughtAnywhere() {
        assertTrue(CardDrags.mayStart(CardVec(200f, 400f), size, rtl = false, anywhere = false, catching = true))
    }

    @Test
    fun androidsGestureEdgesAreAndroids() {
        val edges = GestureEdges(left = 16f, top = 24f, right = 16f, bottom = 32f)
        assertFalse(CardDrags.mayStart(CardVec(10f, 400f), size, rtl = false, anywhere = true, catching = false, gestures = edges))
        assertFalse(CardDrags.mayStart(CardVec(380f, 400f), size, rtl = false, anywhere = true, catching = false, gestures = edges))
        assertFalse(CardDrags.mayStart(CardVec(200f, 830f), size, rtl = false, anywhere = true, catching = false, gestures = edges))
        assertTrue(CardDrags.mayStart(CardVec(20f, 400f), size, rtl = false, anywhere = false, catching = false, gestures = edges))
    }

    @Test
    fun theFingersSpeedIsTheLineThroughItsLast100Ms() {
        val velocity = CardVelocity()
        velocity.add(CardVec(0f, 0f), 0.0)
        velocity.add(CardVec(500f, 0f), 50.0)
        (0..5).forEach { velocity.add(CardVec(100f + it * 10f, it * 5f), 200.0 + it * 10) }
        val now = velocity.velocity(250.0)
        assertEquals(1f, now.x, 1e-4f)
        assertEquals(0.5f, now.y, 1e-4f)
    }

    @Test
    fun aFingerThatStoppedHasNoSpeed() {
        val velocity = CardVelocity()
        velocity.add(CardVec(0f, 0f), 0.0)
        velocity.add(CardVec(30f, 0f), 30.0)
        assertTrue(velocity.velocity(30.0).x > 0f)
        assertEquals(CardVec.ZERO, velocity.velocity(80.0))
    }
}
