package com.aimdi.xtanative.ui.chrome

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt

/** A point or a distance on the card, in dp. */
data class CardVec(val x: Float, val y: Float) {
    operator fun plus(other: CardVec) = CardVec(x + other.x, y + other.y)
    operator fun minus(other: CardVec) = CardVec(x - other.x, y - other.y)
    operator fun times(k: Float) = CardVec(x * k, y * k)
    operator fun div(k: Float) = CardVec(x / k, y / k)
    val distance: Float get() = hypot(x, y)

    companion object {
        val ZERO = CardVec(0f, 0f)
    }
}

data class CardSize(val width: Float, val height: Float)

/**
 * Where the card is, in a frame where it leaves towards +x: [offset] from
 * rest, turned [rotation] degrees and shrunk around [origin], [lift] of the
 * way into a card. [mirrored] when it really leaves towards -x. [uncovered]
 * is how much of the page behind it shows, from 0 under the card to 1 clear.
 */
data class CardPose(
    val offset: CardVec,
    val origin: CardVec,
    val rotation: Float = 0f,
    val lift: Float = 0f,
    val mirrored: Boolean = false,
    val uncovered: Float = 0f,
)

/** The page behind a card: its size, its corners in dp and how dark it is. */
data class BehindLook(val scale: Float, val radius: Float, val dim: Float)

/**
 * The swipe-back card's numbers and pure parts, as XTA's swipe_back_card.dart
 * has them from the owner's concept (docs/specs/post-swipe-back-card.md):
 * lengths in dp, speeds in dp per millisecond, times in milliseconds.
 */
object SwipeBack {
    /** How long the page takes to lift into a card once grabbed. */
    const val LIFT_MS = 140f

    /** How much smaller a lifted card is, and how much more across a full width. */
    const val LIFT_SCALE = 0.06f
    const val TRAVEL_SCALE = 0.03f

    /** The corners of a lifted card. */
    const val RADIUS = 24f

    /** Degrees a card grabbed at the very top or bottom turns across a full width. */
    const val TILT = 7f

    /** Degrees a card swings per dp/ms of sideways speed, and at most. */
    const val SWAY = 3f
    const val SWAY_MAX = 4f

    /** Degrees a thrown card turns on its way out. */
    const val THROW_SPIN = 10f

    const val BEHIND_SCALE = 0.96f
    const val BEHIND_RADIUS = 16f
    const val BEHIND_DIM = 0.25f

    /** Letting go past this much of the width, or of the height, leaves. */
    const val LEAVE_X = 0.3f
    const val LEAVE_Y = 0.25f

    /** Letting go faster than this away from rest leaves. */
    const val FLING = 0.35f

    /** How far from the edge a drag may start when it may not start anywhere. */
    const val EDGE = 28f

    /** How far a finger goes before the drag picks its direction. */
    const val SLOP = 6f

    /** How many times more a drag must go across than up or down to be the card's. */
    const val SIDEWAYS = 1.15f

    /** The spring that drops a card back: stiff and critically damped. */
    const val STIFFNESS = 900f

    const val OPEN_MS = 260
    const val CLOSE_MS = 220

    private const val TRANSIT_LIFT = 0.6f
    private const val TRANSIT_LIFT_DISTANCE = 0.4f
    private const val CLOSE_TRAVEL = 1.1f
    private const val BEHIND_CLEAR_AT = 0.85f
    private const val RUBBER_BAND = 0.3f
    private const val FLICK_BACK = 0.2f
    private const val FLING_TRAVEL = 16f
    private const val THROW_ALONG_DRAG = 0.3f
    private const val THROW_MIN_SPEED = 1.6f
    private const val THROW_MARGIN_X = 140f
    private const val THROW_MARGIN_Y = 90f
    private const val THROW_MIN_TRAVEL = 40f
    private const val THROW_SHORTEST_MS = 140f
    private const val THROW_LONGEST_MS = 260f
    private const val SETTLE_LIFT_STRETCH = 1.6f
    private const val SWAY_FOLLOW = 0.25f
    private const val SWAY_DECAY = 0.8f
    private const val FRAME_MS = 1000f / 60f

    /** The concept's eases exactly: a throw has to start at the speed it was thrown at. */
    fun easeOutQuad(t: Float): Float = 1 - (1 - t).pow(2)

    fun easeOutCubic(t: Float): Float = 1 - (1 - t).pow(3)

    /** How much of a card at [lift] is left after [offset] of travel across [size]. */
    fun cardScale(offset: CardVec, lift: Float, size: CardSize): Float {
        val travel = (offset.distance / size.width).coerceIn(0f, 1f)
        return 1 - LIFT_SCALE * lift - TRAVEL_SCALE * travel * lift
    }

    /** A card's corners at [lift], in whole dp. */
    fun cardRadius(lift: Float): Float = (RADIUS * lift).roundToInt().toFloat()

    /**
     * How far a card sliding in or out is from rest at [curved] of its slide:
     * it comes in from the far edge and goes out a little past it, so its
     * shadow leaves too.
     */
    fun slideOffset(curved: Float, width: Float, closing: Boolean): Float = (1 - curved) * width * (if (closing) CLOSE_TRAVEL else 1f)

    /** The lift of a card sliding in or out: partly lifted while far, flat once within 40 % of a width of rest. */
    fun slideLift(distance: Float, width: Float): Float = TRANSIT_LIFT * (abs(distance) / (TRANSIT_LIFT_DISTANCE * width)).coerceIn(0f, 1f)

    /** Where a card sliding in or out is drawn, [curved] of its way in. */
    fun slidePose(curved: Float, size: CardSize, closing: Boolean, mirrored: Boolean): CardPose {
        val offset = CardVec(slideOffset(curved, size.width, closing), 0f)
        return CardPose(
            offset = offset,
            origin = CardVec(size.width / 2, size.height / 2),
            lift = slideLift(offset.x, size.width),
            mirrored = mirrored,
            uncovered = behindUncovered(offset, size),
        )
    }

    /** The lift [heldMs] after a grab: up in [LIFT_MS], easing out. */
    fun liftAfterGrab(heldMs: Float): Float = easeOutCubic((heldMs / LIFT_MS).coerceIn(0f, 1f))

    /** How long after a grab a card has [lift]: a card caught on its way back lifts on from where it is. */
    fun grabbedFor(lift: Float): Float = LIFT_MS * (1 - (1 - lift.coerceIn(0f, 0.999f)).pow(1f / 3f))

    /** The lift of a card dropping back: it flattens as it nears rest, and is flat within 1.6 times the lift time whatever happens. */
    fun settlingLift(from: Float, startDistance: Float, distance: Float, elapsedMs: Float): Float {
        val byDistance = (distance / startDistance).coerceIn(0f, 1f)
        val byTime = (1 - elapsedMs / (LIFT_MS * SETTLE_LIFT_STRETCH)).coerceIn(0f, 1f)
        return from * min(byDistance, byTime)
    }

    /** How a card grabbed at [grabY] turns as it travels: clockwise above the middle and the other way below it, more the further from it. */
    fun tiltFactor(grabY: Float, height: Float): Float {
        val half = height / 2
        val fromMiddle = (half - grabY) / half
        return (if (fromMiddle < 0) -1f else 1f) * (0.55f + 0.45f * min(1f, abs(fromMiddle)))
    }

    /** A card's rotation in degrees: its tilt by how far across it has gone, its swing, and a throw's spin. */
    fun cardRotation(tilt: Float, dx: Float, width: Float, sway: Float = 0f, spin: Float = 0f): Float = tilt * (dx / width) * TILT + sway + spin

    /** Where the swing heads at a sideways speed of [vx]. */
    fun swayTarget(tilt: Float, vx: Float): Float = if (tilt == 0f) 0f else sign(tilt) * (vx * SWAY).coerceIn(-SWAY_MAX, SWAY_MAX)

    /** [sway] after [elapsedMs] on its way to [target]: a quarter of the way in each 60 Hz frame, so it swings back when the finger stops. */
    fun swayToward(sway: Float, target: Float, elapsedMs: Float): Float = target + (sway - target) * (1 - SWAY_FOLLOW).pow(elapsedMs / FRAME_MS)

    /** What is left of a swing [elapsedMs] after a card was let go. */
    fun swayDecay(sway: Float, elapsedMs: Float): Float = sway * SWAY_DECAY.pow(elapsedMs / FRAME_MS)

    /** A drag past the starting edge gives only a third. */
    fun rubberBand(dx: Float): Float = if (dx < 0) dx * RUBBER_BAND else dx

    /**
     * Whether letting go at [offset] with [velocity] leaves: past the ellipse
     * through 30 % of the width and 25 % of the height, or thrown away from
     * rest; never when flicked back towards it.
     */
    fun leavesOnRelease(offset: CardVec, velocity: CardVec, size: CardSize): Boolean {
        val speed = velocity.distance
        val away = velocity.x * offset.x + velocity.y * offset.y
        if (away < 0 && speed > FLICK_BACK) return false
        val across = offset.x / (LEAVE_X * size.width)
        val down = offset.y / (LEAVE_Y * size.height)
        val far = across * across + down * down > 1
        return far || (speed > FLING && away > 0 && offset.distance > FLING_TRAVEL)
    }

    /** How much of the page behind shows with the card at [offset]: none while it covers it, all once it is 85 % of a width away. */
    fun behindUncovered(offset: CardVec, size: CardSize): Float =
        (CardVec(max(offset.x, 0f), offset.y).distance / (size.width * BEHIND_CLEAR_AT)).coerceIn(0f, 1f)

    /** The page behind a card that leaves [uncovered] of it. */
    fun behindLook(uncovered: Float): BehindLook = BehindLook(
        scale = BEHIND_SCALE + (1 - BEHIND_SCALE) * uncovered,
        radius = (BEHIND_RADIUS * (1 - uncovered)).roundToInt().toFloat(),
        dim = BEHIND_DIM * (1 - uncovered),
    )

    /**
     * Throws a card let go at [offset] with [velocity] across [size]: along the
     * throw, or along the drag when let go slowly, as far as it takes to be off
     * screen, in 140–260 ms, spinning on with tilt on.
     */
    fun cardThrow(offset: CardVec, velocity: CardVec, size: CardSize, tilt: Float): CardThrow {
        val speed = velocity.distance
        val direction = when {
            speed > THROW_ALONG_DRAG -> velocity / speed
            offset.distance > 0 -> offset / offset.distance
            else -> CardVec(1f, 0f)
        }
        val travel = max(THROW_MIN_TRAVEL, offScreen(offset, direction, size))
        val ms = (2 * travel / max(speed, THROW_MIN_SPEED)).coerceIn(THROW_SHORTEST_MS, THROW_LONGEST_MS)
        val spin = if (tilt == 0f) 0f else (if (direction.x < 0) -1f else 1f) * sign(tilt) * THROW_SPIN
        return CardThrow(offset, direction, travel, ms, spin)
    }

    /** How far a card at [offset] goes along [direction] before it is off screen. */
    private fun offScreen(offset: CardVec, direction: CardVec, size: CardSize): Float = listOfNotNull(
        if (direction.x > 0.01f) (size.width + THROW_MARGIN_X - offset.x) / direction.x else null,
        if (direction.x < -0.01f) (size.width + THROW_MARGIN_X + offset.x) / -direction.x else null,
        if (direction.y > 0.01f) (size.height + THROW_MARGIN_Y - offset.y) / direction.y else null,
        if (direction.y < -0.01f) (size.height + THROW_MARGIN_Y + offset.y) / -direction.y else null,
    ).minOrNull() ?: THROW_MIN_TRAVEL
}

/** A card flying off from [from] along [direction] until it is off screen, in [durationMs], turning [spin] degrees more by the end. */
data class CardThrow(val from: CardVec, val direction: CardVec, val travel: Float, val durationMs: Float, val spin: Float) {
    /** It starts at the throw's speed and eases out. */
    fun progress(t: Float): Float = SwipeBack.easeOutQuad(t.coerceIn(0f, 1f))

    fun offsetAt(t: Float): CardVec = from + direction * (travel * progress(t))

    fun spinAt(t: Float): Float = spin * progress(t)
}

/**
 * A card dropping back to rest from [from] at [velocity] (dp per second), on
 * the critically damped spring of mass 1 and [SwipeBack.STIFFNESS], as
 * Flutter's SpringSimulation runs it: it never overshoots.
 */
class CardSpring(from: CardVec, velocity: CardVec) {
    private val x = Axis(from.x, velocity.x)
    private val y = Axis(from.y, velocity.y)

    fun offsetAt(seconds: Float): CardVec = CardVec(x.position(seconds), y.position(seconds))

    fun velocityAt(seconds: Float): CardVec = CardVec(x.speed(seconds), y.speed(seconds))

    /** Within half a dp of rest and all but still. */
    fun landedAt(seconds: Float): Boolean {
        val offset = offsetAt(seconds)
        val velocity = velocityAt(seconds)
        return abs(offset.x) < REST_DISTANCE && abs(offset.y) < REST_DISTANCE && abs(velocity.x) < REST_SPEED && abs(velocity.y) < REST_SPEED
    }

    /** x(t) = (x0 + (v0 + ωx0)t)e^(−ωt), the critical solution towards 0. */
    private class Axis(private val x0: Float, private val v0: Float) {
        private val b = v0 + OMEGA * x0

        fun position(t: Float): Float = (x0 + b * t) * exp(-OMEGA * t)

        fun speed(t: Float): Float = (v0 - OMEGA * b * t) * exp(-OMEGA * t)
    }

    private companion object {
        val OMEGA = sqrt(SwipeBack.STIFFNESS)
        const val REST_DISTANCE = 0.4f
        const val REST_SPEED = 8f
    }
}
