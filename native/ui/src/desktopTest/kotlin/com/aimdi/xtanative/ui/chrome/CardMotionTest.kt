package com.aimdi.xtanative.ui.chrome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** How an opened post moves as a card, as XTA's card_page_route_test.dart has it. */
class CardMotionTest {
    private val size = CardSize(390f, 844f)

    /** A finger down at [from], moved [by] in [steps] over [ms], each step ticked. */
    private fun CardMotion.drag(from: CardVec, by: CardVec, start: Double = 0.0, ms: Double = 160.0, steps: Int = 8, tilt: Boolean = true, flat: Boolean = false): Double {
        hand.grab(from, size, mirrored = false, tilt = tilt, flat = flat, now = start, pointer = true)
        var now = start
        for (step in 1..steps) {
            now = start + ms * step / steps
            hand.move(from + by * (step.toFloat() / steps), now)
            tick(now)
        }
        return now
    }

    /** Ticks at 60 Hz from [from] to [until]. */
    private fun CardMotion.run(from: Double, until: Double): Double {
        var now = from
        while (now < until && !gone) {
            now += 16.0
            tick(now)
        }
        return now
    }

    @Test
    fun aPostSlidesInFromTheRightOverThePageItWasOpenedFrom() {
        val motion = CardMotion()
        motion.arrive(animate = true)
        motion.tick(0.0)
        val first = motion.pose(size, mirrored = false)
        assertEquals(390f, first.offset.x, 0.5f)
        assertEquals(1f, first.uncovered)
        motion.tick(130.0)
        assertTrue(motion.pose(size, mirrored = false).offset.x in 1f..389f, "on its way at half the slide")
        motion.tick(300.0)
        assertEquals(CardVec.ZERO, motion.pose(size, mirrored = false).offset)
        assertFalse(motion.moving)
    }

    @Test
    fun theBackButtonSlidesItOutPastTheRightEdgeThenItGoes() {
        val motion = CardMotion().also { it.rest() }
        motion.back(0.0, animate = true)
        motion.tick(0.0)
        motion.tick(110.0)
        val half = motion.pose(size, mirrored = false).offset.x
        assertEquals(429f * 0.25f, half, 1f)
        assertFalse(motion.gone)
        motion.tick(240.0)
        assertTrue(motion.gone)
    }

    @Test
    fun withReducedMotionThePostAppearsAndLeavesAtOnce() {
        val motion = CardMotion()
        motion.arrive(animate = false)
        assertEquals(CardVec.ZERO, motion.pose(size, mirrored = false).offset)
        assertFalse(motion.moving)
        motion.back(0.0, animate = false)
        assertTrue(motion.gone)
    }

    @Test
    fun theCardFollowsTheFingerThePageBehindShowingUnderIt() {
        val motion = CardMotion().also { it.rest() }
        motion.drag(CardVec(100f, 300f), CardVec(80f, 0f))
        val pose = assertNotNull(motion.hand.pose)
        assertEquals(80f, pose.offset.x, 0.01f)
        assertTrue(pose.uncovered > 0f)
        assertTrue(pose.lift > 0.9f, "lifted within 140 ms")
    }

    @Test
    fun letGoPastThirtyPercentOfTheWidthItFliesOffAndThePostGoes() {
        val motion = CardMotion().also { it.rest() }
        val at = motion.drag(CardVec(100f, 300f), CardVec(150f, 0f), ms = 600.0, steps = 30)
        val ended = motion.hand.release(at + 50)
        motion.released(ended)
        assertEquals(CardRelease.THROWN, ended)
        assertTrue(motion.leaving)
        motion.run(at + 50, at + 400)
        assertTrue(motion.gone)
    }

    @Test
    fun letGoShortOfTheLineItDropsBackAndThePostStays() {
        val motion = CardMotion().also { it.rest() }
        val at = motion.drag(CardVec(100f, 300f), CardVec(60f, 0f), ms = 600.0, steps = 30)
        val ended = motion.hand.release(at + 50)
        motion.released(ended)
        assertEquals(CardRelease.SETTLING, ended)
        motion.run(at + 50, at + 1000)
        assertFalse(motion.gone)
        assertNull(motion.hand.pose, "at rest again")
        assertTrue(motion.mayTake(catching = false))
    }

    @Test
    fun aQuickThrowLeavesFromAShortDrag() {
        val motion = CardMotion().also { it.rest() }
        val at = motion.drag(CardVec(100f, 300f), CardVec(40f, 0f), ms = 60.0, steps = 6)
        assertEquals(CardRelease.THROWN, motion.hand.release(at))
    }

    @Test
    fun aFlickBackTowardsRestKeepsThePostEvenPastTheLine() {
        val motion = CardMotion().also { it.rest() }
        val start = CardVec(100f, 300f)
        motion.hand.grab(start, size, mirrored = false, tilt = true, flat = false, now = 0.0, pointer = true)
        motion.hand.move(start + CardVec(200f, 0f), 300.0)
        motion.hand.move(start + CardVec(185f, 0f), 330.0)
        motion.hand.move(start + CardVec(170f, 0f), 360.0)
        assertEquals(CardRelease.SETTLING, motion.hand.release(360.0))
    }

    @Test
    fun grabbedAboveTheMiddleItTurnsClockwiseBelowTheOtherWay() {
        val above = CardMotion().also { it.rest() }
        above.drag(CardVec(100f, 100f), CardVec(100f, 0f))
        assertTrue(assertNotNull(above.hand.pose).rotation > 0f)
        val below = CardMotion().also { it.rest() }
        below.drag(CardVec(100f, 700f), CardVec(100f, 0f))
        assertTrue(assertNotNull(below.hand.pose).rotation < 0f)
    }

    @Test
    fun withTiltOffItDoesNotTurn() {
        val motion = CardMotion().also { it.rest() }
        motion.drag(CardVec(100f, 100f), CardVec(100f, 0f), tilt = false)
        assertEquals(0f, assertNotNull(motion.hand.pose).rotation)
    }

    @Test
    fun draggedLeftOfRestItGivesOnlyAThird() {
        val motion = CardMotion().also { it.rest() }
        motion.drag(CardVec(200f, 300f), CardVec(-60f, 0f))
        assertEquals(-18f, assertNotNull(motion.hand.pose).offset.x, 0.01f)
    }

    @Test
    fun caughtWhileItDropsBackItIsHeldAgainWhereItIs() {
        val motion = CardMotion().also { it.rest() }
        val at = motion.drag(CardVec(100f, 300f), CardVec(80f, 0f), ms = 600.0, steps = 30)
        motion.released(motion.hand.release(at + 50))
        motion.tick(at + 66)
        val falling = assertNotNull(motion.hand.pose).offset.x
        assertTrue(motion.mayTake(catching = true) && motion.hand.catchable)
        motion.hand.grab(CardVec(150f, 300f), size, mirrored = false, tilt = true, flat = false, now = at + 70, pointer = true)
        motion.tick(at + 86)
        assertEquals(falling, assertNotNull(motion.hand.pose).offset.x, 0.01f)
    }

    @Test
    fun aBackPressWhileItDropsBackThrowsItFromWhereItIs() {
        val motion = CardMotion().also { it.rest() }
        val at = motion.drag(CardVec(100f, 300f), CardVec(80f, 0f), ms = 600.0, steps = 30)
        motion.released(motion.hand.release(at + 50))
        motion.tick(at + 66)
        val from = assertNotNull(motion.hand.pose).offset.x
        motion.back(at + 70, animate = true)
        assertTrue(motion.hand.throwing)
        motion.tick(at + 86)
        assertTrue(assertNotNull(motion.hand.pose).offset.x > from, "flies on from there, not from the slide's start")
        motion.run(at + 86, at + 600)
        assertTrue(motion.gone)
    }

    @Test
    fun withReducedMotionADragStillMovesThePostFlatAndShortOfTheLineItIsBackAtOnce() {
        val motion = CardMotion().also { it.rest() }
        val at = motion.drag(CardVec(100f, 300f), CardVec(60f, 0f), ms = 600.0, steps = 30, flat = true)
        val pose = assertNotNull(motion.hand.pose)
        assertEquals(60f, pose.offset.x, 0.01f)
        assertEquals(0f, pose.lift)
        assertEquals(0f, pose.rotation)
        assertEquals(CardRelease.RESTED, motion.hand.release(at + 50))
        assertNull(motion.hand.pose)
    }

    @Test
    fun withReducedMotionLetGoPastTheLineItLeavesAtOnce() {
        val motion = CardMotion().also { it.rest() }
        val at = motion.drag(CardVec(100f, 300f), CardVec(200f, 0f), ms = 600.0, steps = 30, flat = true)
        motion.released(motion.hand.release(at + 50))
        assertTrue(motion.gone)
    }

    @Test
    fun androidsBackGestureMovesTheCardAndThrowsItOnCommit() {
        val motion = CardMotion().also { it.rest() }
        motion.hand.grab(CardVec(0f, 400f), size, mirrored = false, tilt = true, flat = false, now = 0.0, pointer = false)
        motion.hand.follow(0.25f)
        motion.tick(16.0)
        assertEquals(97.5f, assertNotNull(motion.hand.pose).offset.x, 0.01f)
        motion.released(motion.hand.leave(20.0))
        motion.run(20.0, 400.0)
        assertTrue(motion.gone)
    }

    @Test
    fun androidsBackGestureCancelledDropsTheCardBack() {
        val motion = CardMotion().also { it.rest() }
        motion.hand.grab(CardVec(0f, 400f), size, mirrored = false, tilt = true, flat = false, now = 0.0, pointer = false)
        motion.hand.follow(0.25f)
        motion.tick(16.0)
        assertEquals(CardRelease.SETTLING, motion.hand.drop(20.0))
        motion.run(20.0, 1000.0)
        assertFalse(motion.gone)
        assertNull(motion.hand.pose)
    }

    @Test
    fun fromTheRightEdgeTheGestureTakesTheCardLeft() {
        val motion = CardMotion().also { it.rest() }
        motion.hand.grab(CardVec(390f, 400f), size, mirrored = true, tilt = true, flat = false, now = 0.0, pointer = false)
        motion.hand.follow(0.25f)
        motion.tick(16.0)
        val pose = assertNotNull(motion.hand.pose)
        assertTrue(pose.mirrored, "drawn mirrored: it leaves to the left")
        assertEquals(97.5f, pose.offset.x, 0.01f)
    }

    @Test
    fun nothingTakesTheCardWhileItSlidesInOrOnceItLeaves() {
        val motion = CardMotion()
        motion.arrive(animate = true)
        assertFalse(motion.mayTake(catching = true))
        motion.tick(0.0)
        motion.tick(300.0)
        assertTrue(motion.mayTake(catching = false))
        motion.back(300.0, animate = true)
        assertFalse(motion.mayTake(catching = true))
    }
}
