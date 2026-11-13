package com.aimdi.xtanative.ui.chrome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** XTA's swipe_back_card_test.dart, number for number. */
class SwipeBackMathTest {
    private val size = CardSize(390f, 844f)

    private fun near(expected: Float, actual: Float, tolerance: Float = 1e-4f, message: String? = null) =
        assertEquals(expected, actual, tolerance, message)

    @Test
    fun aCardShrinksBySixPercentLiftedAndThreeMoreAcrossAFullWidth() {
        near(1f, SwipeBack.cardScale(CardVec.ZERO, 0f, size))
        near(0.94f, SwipeBack.cardScale(CardVec.ZERO, 1f, size))
        near(0.91f, SwipeBack.cardScale(CardVec(390f, 0f), 1f, size))
        near(1 - 0.03f - 0.0075f, SwipeBack.cardScale(CardVec(195f, 0f), 0.5f, size))
    }

    @Test
    fun itsCornersRoundToTwentyFourAtFullLiftInWholeDp() {
        near(0f, SwipeBack.cardRadius(0f))
        near(24f, SwipeBack.cardRadius(1f))
        near(12f, SwipeBack.cardRadius(0.51f))
    }

    @Test
    fun itComesInFromTheFarEdgeAndGoesOutATenthPastIt() {
        near(390f, SwipeBack.slideOffset(0f, 390f, closing = false))
        near(429f, SwipeBack.slideOffset(0f, 390f, closing = true))
        near(0f, SwipeBack.slideOffset(1f, 390f, closing = true))
    }

    @Test
    fun slidingItIsLiftedWhileFarAndFlatWithinFortyPercentOfAWidth() {
        near(0.6f, SwipeBack.slideLift(390f, 390f))
        near(0.3f, SwipeBack.slideLift(78f, 390f))
        near(0f, SwipeBack.slideLift(0f, 390f))
    }

    @Test
    fun theLiftRisesIn140MsEasingOut() {
        near(0f, SwipeBack.liftAfterGrab(0f))
        near(0.875f, SwipeBack.liftAfterGrab(70f))
        near(1f, SwipeBack.liftAfterGrab(SwipeBack.LIFT_MS))
        near(1f, SwipeBack.liftAfterGrab(1000f))
    }

    @Test
    fun aCaughtCardLiftsOnFromWhereItIs() {
        listOf(0f, 0.2f, 0.5f, 0.9f).forEach { lift -> near(lift, SwipeBack.liftAfterGrab(SwipeBack.grabbedFor(lift)), 1e-3f) }
    }

    @Test
    fun droppingBackItFlattensByDistanceAndIsFlatWithin224Ms() {
        near(0.5f, SwipeBack.settlingLift(from = 1f, startDistance = 100f, distance = 50f, elapsedMs = 0f))
        near(0.5f, SwipeBack.settlingLift(from = 1f, startDistance = 100f, distance = 100f, elapsedMs = 112f))
        near(0f, SwipeBack.settlingLift(from = 1f, startDistance = 100f, distance = 100f, elapsedMs = 224f))
    }

    @Test
    fun itTurnsClockwiseGrabbedAboveTheMiddleAndTheOtherWayBelow() {
        near(1f, SwipeBack.tiltFactor(0f, 844f))
        near(-1f, SwipeBack.tiltFactor(844f, 844f))
        near(0.55f, SwipeBack.tiltFactor(422f, 844f))
        near(0.775f, SwipeBack.tiltFactor(211f, 844f))
    }

    @Test
    fun itReachesSevenDegreesAcrossAFullWidthPlusSwingAndSpin() {
        near(7f, SwipeBack.cardRotation(tilt = 1f, dx = 390f, width = 390f))
        near(-1.925f, SwipeBack.cardRotation(tilt = -0.55f, dx = 195f, width = 390f))
        near(12f, SwipeBack.cardRotation(tilt = 1f, dx = 0f, width = 390f, sway = 2f, spin = 10f))
    }

    @Test
    fun theSwingFollowsTheSpeedUpToFourDegreesAndOnlyWithTilt() {
        near(0f, SwipeBack.swayTarget(0f, 1f))
        near(3f, SwipeBack.swayTarget(1f, 1f))
        near(4f, SwipeBack.swayTarget(0.6f, 2f))
        near(-3f, SwipeBack.swayTarget(-1f, 1f))
    }

    @Test
    fun theSwingGoesAQuarterOfTheWayEach60HzFrame() {
        val frame = 16.667f
        near(1f, SwipeBack.swayToward(0f, 4f, frame), 1e-3f)
        near(4f, SwipeBack.swayToward(0f, 4f, 1000f), 1e-5f)
        near(3.2f, SwipeBack.swayDecay(4f, frame), 1e-3f)
    }

    @Test
    fun pastTheStartingEdgeADragGivesOnlyAThird() {
        near(-9f, SwipeBack.rubberBand(-30f))
        near(30f, SwipeBack.rubberBand(30f))
    }

    @Test
    fun lettingGoStandingStillLeavesPastThirtyPercentOfTheWidth() {
        assertTrue(SwipeBack.leavesOnRelease(CardVec(118f, 0f), CardVec.ZERO, size))
        assertFalse(SwipeBack.leavesOnRelease(CardVec(116f, 0f), CardVec.ZERO, size))
    }

    @Test
    fun lettingGoLeavesPastTheEllipseThroughThirtyPercentAcrossAndTwentyFiveDown() {
        assertTrue(SwipeBack.leavesOnRelease(CardVec(0f, 212f), CardVec.ZERO, size))
        assertTrue(SwipeBack.leavesOnRelease(CardVec(78f, 169f), CardVec.ZERO, size))
        assertFalse(SwipeBack.leavesOnRelease(CardVec(58f, 127f), CardVec.ZERO, size))
    }

    @Test
    fun aThrowAwayFromRestLeavesAfterSixteenDp() {
        assertTrue(SwipeBack.leavesOnRelease(CardVec(20f, 0f), CardVec(0.4f, 0f), size))
        assertFalse(SwipeBack.leavesOnRelease(CardVec(10f, 0f), CardVec(0.4f, 0f), size))
        assertFalse(SwipeBack.leavesOnRelease(CardVec(20f, 0f), CardVec(0.3f, 0f), size))
    }

    @Test
    fun aFlickBackTowardsRestNeverLeavesASlowDriftBackStillDoes() {
        assertFalse(SwipeBack.leavesOnRelease(CardVec(200f, 0f), CardVec(-0.3f, 0f), size))
        assertTrue(SwipeBack.leavesOnRelease(CardVec(200f, 0f), CardVec(-0.1f, 0f), size))
    }

    @Test
    fun thePageBehindShowsAsTheCardLeavesAndNotWhenItIsDraggedPastItsEdge() {
        near(0f, SwipeBack.behindUncovered(CardVec.ZERO, size))
        near(0f, SwipeBack.behindUncovered(CardVec(-50f, 0f), size))
        near(0.5f, SwipeBack.behindUncovered(CardVec(165.75f, 0f), size))
        near(1f, SwipeBack.behindUncovered(CardVec(331.5f, 0f), size))
        near(1f, SwipeBack.behindUncovered(CardVec(0f, -400f), size))
    }

    @Test
    fun thePageBehindIs96PercentRoundedAndDimmedUnderTheCardWholeWhenClear() {
        assertEquals(BehindLook(0.96f, 16f, 0.25f), SwipeBack.behindLook(0f))
        assertEquals(BehindLook(1f, 0f, 0f), SwipeBack.behindLook(1f))
        val half = SwipeBack.behindLook(0.5f)
        near(0.98f, half.scale)
        near(8f, half.radius)
        near(0.125f, half.dim)
    }

    @Test
    fun aThrowGoesAlongTheThrowUntilTheCardIsOffScreenIn140To260Ms() {
        val flight = SwipeBack.cardThrow(offset = CardVec(150f, 0f), velocity = CardVec(2f, 0f), size = size, tilt = 0f)
        assertEquals(CardVec(1f, 0f), flight.direction)
        near(380f, flight.travel)
        near(260f, flight.durationMs)
        near(0f, flight.spin)
        near(530f, flight.offsetAt(1f).x)
        near(150 + 380 * 0.75f, flight.offsetAt(0.5f).x)
    }

    @Test
    fun letGoSlowlyItLeavesAlongTheDrag() {
        val flight = SwipeBack.cardThrow(offset = CardVec(150f, 80f), velocity = CardVec(0.1f, 0f), size = size, tilt = 1f)
        near(150f / 170f, flight.direction.x)
        near(80f / 170f, flight.direction.y)
    }

    @Test
    fun aFastShortThrowStillTakes140Ms() {
        val flight = SwipeBack.cardThrow(offset = CardVec(500f, 0f), velocity = CardVec(3f, 0f), size = size, tilt = 0f)
        near(40f, flight.travel)
        near(140f, flight.durationMs)
    }

    @Test
    fun aThrowSpinsOnTheWayItTurns() {
        near(10f, SwipeBack.cardThrow(CardVec(150f, 0f), CardVec(2f, 0f), size, tilt = 0.7f).spin)
        near(-10f, SwipeBack.cardThrow(CardVec(150f, 0f), CardVec(2f, 0f), size, tilt = -0.7f).spin)
        near(-10f, SwipeBack.cardThrow(CardVec(-40f, 0f), CardVec(-1f, 0f), size, tilt = 0.7f).spin)
    }

    @Test
    fun theSpringDropsTheCardBackWithoutOvershootingWhenLetGoStill() {
        val spring = CardSpring(CardVec(100f, -30f), CardVec.ZERO)
        var last = 100f
        for (ms in 0..600 step 10) {
            val at = spring.offsetAt(ms / 1000f)
            assertTrue(at.x in 0f..(last + 1e-4f), "$ms ms: ${at.x}")
            assertTrue(at.y <= 1e-4f, "$ms ms: ${at.y}")
            last = at.x
        }
        assertTrue(spring.landedAt(0.6f))
        assertFalse(spring.landedAt(0.05f))
    }

    @Test
    fun theSpringCarriesTheSpeedItWasLetGoAt() {
        val spring = CardSpring(CardVec(40f, 0f), CardVec(600f, 0f))
        assertTrue(spring.offsetAt(0.02f).x > 40f)
        assertEquals(CardVec(600f, 0f), spring.velocityAt(0f))
    }
}
