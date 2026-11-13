package com.aimdi.xtanative.ui.chrome

import kotlin.math.abs
import kotlin.math.max

/**
 * What a hold keeps from the grab: the card's size, whether it leaves to the
 * left, the point it turns around, how it tilts, and whether it moves flat.
 */
data class CardGrip(val size: CardSize, val mirrored: Boolean, val origin: CardVec, val tilt: Float, val flat: Boolean)

/** How a release ended. */
enum class CardRelease {
    /** Dropping back on the spring. */
    SETTLING,

    /** Back in place at once, flat. */
    RESTED,

    /** Flying off: the screen goes once the throw has run ([CardHand.gone]). */
    THROWN,

    /** Gone at once, flat: the screen goes now. */
    LEFT,
}

/**
 * Holds the card while its slide does not: a finger or Android's back
 * gesture, the spring that drops it back, or a throw (XTA's _CardHand).
 * Lengths are in dp and times in milliseconds of one clock, the caller's.
 */
class CardHand {
    private sealed interface Hold {
        val grip: CardGrip
    }

    /** A finger that went down at [start], or Android's back gesture, holding the card that was at [base] and had been lifting for [liftedFor]. */
    private class Held(override val grip: CardGrip, val base: CardVec, val start: CardVec, val liftedFor: Float, val system: Boolean, val since: Double) : Hold {
        var travel: CardVec = CardVec.ZERO

        val offset: CardVec
            get() {
                val at = base + travel
                return CardVec(SwipeBack.rubberBand(at.x), at.y)
            }
    }

    private class Settling(override val grip: CardGrip, val spring: CardSpring, val lift: Float, val distance: Float, val sway: Float, val since: Double) : Hold

    private class Thrown(override val grip: CardGrip, val flight: CardThrow, val lift: Float, val since: Double) : Hold

    private var hold: Hold? = null
    private var lastTick: Double? = null
    private var sway = 0f
    private val tracker = CardVelocity()

    /** Where the card is drawn while the hand has it; null while it rests. */
    var pose: CardPose? = null
        private set

    val busy: Boolean get() = hold != null

    /** Whether a pointer going down now catches the card, as one does while it drops back. */
    val catchable: Boolean get() = hold is Settling

    /** Whether the card is flying off. */
    val throwing: Boolean get() = hold is Thrown

    /** A thrown card is off screen: its screen goes. */
    var gone: Boolean = false
        private set

    /**
     * A finger that went down at [start] at [downAt] ([pointer]), or Android's
     * back gesture touching there, takes the card at [now]. A card caught on
     * its way back keeps how it turns and lifts on from where it is.
     */
    fun grab(start: CardVec, size: CardSize, mirrored: Boolean, tilt: Boolean, flat: Boolean, now: Double, pointer: Boolean, downAt: Double = now) {
        val settling = hold as? Settling
        val caught = settling?.let { pose }
        val fresh = caught == null || caught.offset.distance < 0.5f
        val grip = if (settling == null || fresh) gripOf(start, size, mirrored, tilt, flat) else settling.grip
        hold = Held(
            grip,
            base = caught?.offset ?: CardVec.ZERO,
            start = start,
            liftedFor = caught?.let { SwipeBack.grabbedFor(it.lift) } ?: 0f,
            system = !pointer,
            since = now,
        )
        gone = false
        lastTick = null
        tracker.clear()
        if (pointer) tracker.add(start, downAt)
        pose = posed(grip, caught?.offset ?: CardVec.ZERO, lift = caught?.lift ?: 0f)
    }

    /** The finger is at [position]. */
    fun move(position: CardVec, now: Double) {
        val held = hold as? Held ?: return
        if (held.system) return
        val travel = position - held.start
        held.travel = if (held.grip.mirrored) CardVec(-travel.x, travel.y) else travel
        tracker.add(position, now)
    }

    /** Android's back gesture is [progress] of the way across. */
    fun follow(progress: Float) {
        val held = hold as? Held ?: return
        if (!held.system) return
        held.travel = CardVec(progress * held.grip.size.width, 0f)
    }

    /** Let go: the card leaves or drops back by where it is and how fast it was going. Null when nothing held it. */
    fun release(now: Double): CardRelease? = letGo(velocity(now), now)

    /** The pointer was cancelled: as letting go, standing still. */
    fun cancel(now: Double): CardRelease? = letGo(CardVec.ZERO, now)

    /** Android cancelled its back gesture: the card drops back. */
    fun drop(now: Double): CardRelease? {
        val held = hold as? Held ?: return null
        return settle(held, CardVec.ZERO, now)
    }

    /** Android committed its back gesture: the card is thrown. */
    fun leave(now: Double): CardRelease? {
        val held = hold as? Held ?: return null
        return throwAway(held.grip, held.offset, CardVec.ZERO, now)
    }

    /** Something else asks the screen to go while the hand holds the card: it is thrown from where it is. */
    fun leaving(now: Double): CardRelease? {
        val current = hold ?: return null
        if (current is Thrown) return CardRelease.THROWN
        return throwAway(current.grip, pose?.offset ?: CardVec.ZERO, CardVec.ZERO, now)
    }

    /** The card at [now]: its pose, null once it rests again. */
    fun tick(now: Double): CardPose? {
        val step = lastTick?.let { (now - it).toFloat() } ?: 0f
        lastTick = now
        when (val current = hold) {
            is Held -> holdTick(current, now, step)
            is Settling -> settleTick(current, now)
            is Thrown -> throwTick(current, now)
            null -> Unit
        }
        return pose
    }

    private fun letGo(velocity: CardVec, now: Double): CardRelease? {
        val held = hold as? Held ?: return null
        return if (SwipeBack.leavesOnRelease(held.offset, velocity, held.grip.size)) {
            throwAway(held.grip, held.offset, velocity, now)
        } else {
            settle(held, velocity, now)
        }
    }

    /** The finger's velocity in dp/ms, leaving towards +x. */
    private fun velocity(now: Double): CardVec {
        val velocity = tracker.velocity(now)
        val mirrored = hold?.grip?.mirrored ?: false
        return if (mirrored) CardVec(-velocity.x, velocity.y) else velocity
    }

    private fun settle(held: Held, velocity: CardVec, now: Double): CardRelease {
        if (held.grip.flat) {
            clear()
            return CardRelease.RESTED
        }
        val offset = held.offset
        hold = Settling(held.grip, CardSpring(offset, velocity * 1000f), pose?.lift ?: 0f, max(offset.distance, 1f), sway, now)
        return CardRelease.SETTLING
    }

    private fun throwAway(grip: CardGrip, offset: CardVec, velocity: CardVec, now: Double): CardRelease {
        if (grip.flat) {
            clear()
            gone = true
            return CardRelease.LEFT
        }
        hold = Thrown(grip, SwipeBack.cardThrow(offset, velocity, grip.size, grip.tilt), pose?.lift ?: 0f, now)
        return CardRelease.THROWN
    }

    private fun clear() {
        hold = null
        pose = null
        sway = 0f
        lastTick = null
        tracker.clear()
    }

    private fun holdTick(held: Held, now: Double, step: Float) {
        val grip = held.grip
        sway = if (grip.tilt == 0f) 0f else SwipeBack.swayToward(sway, SwipeBack.swayTarget(grip.tilt, velocity(now).x), step)
        val lift = if (grip.flat) 0f else SwipeBack.liftAfterGrab((now - held.since).toFloat() + held.liftedFor)
        pose = posed(grip, held.offset, lift)
    }

    private fun settleTick(settling: Settling, now: Double) {
        val elapsed = (now - settling.since).toFloat()
        val seconds = elapsed / 1000f
        if (settling.spring.landedAt(seconds)) return clear()
        val offset = settling.spring.offsetAt(seconds)
        sway = SwipeBack.swayDecay(settling.sway, elapsed)
        pose = posed(
            settling.grip,
            offset,
            lift = SwipeBack.settlingLift(from = settling.lift, startDistance = settling.distance, distance = offset.distance, elapsedMs = elapsed),
        )
    }

    private fun throwTick(thrown: Thrown, now: Double) {
        val t = ((now - thrown.since) / thrown.flight.durationMs).toFloat()
        pose = posed(thrown.grip, thrown.flight.offsetAt(t), lift = thrown.lift, spin = thrown.flight.spinAt(t), uncovered = thrown.flight.progress(t))
        if (t >= 1f) {
            hold = null
            gone = true
        }
    }

    private fun posed(grip: CardGrip, offset: CardVec, lift: Float, spin: Float = 0f, uncovered: Float = 0f): CardPose = CardPose(
        offset = offset,
        origin = grip.origin,
        rotation = SwipeBack.cardRotation(tilt = grip.tilt, dx = offset.x, width = grip.size.width, sway = sway, spin = spin),
        lift = lift,
        mirrored = grip.mirrored,
        uncovered = max(SwipeBack.behindUncovered(offset, grip.size), uncovered),
    )

    private companion object {
        fun gripOf(start: CardVec, size: CardSize, mirrored: Boolean, tilt: Boolean, flat: Boolean): CardGrip {
            val origin = if (mirrored) CardVec(size.width - start.x, start.y) else start
            return CardGrip(size, mirrored, origin, if (tilt && !flat) SwipeBack.tiltFactor(origin.y, size.height) else 0f, flat)
        }
    }
}

/**
 * A finger's speed in dp/ms: the straight line that best fits where it was
 * over the last 100 ms, nothing once it has not moved for 40 ms, as
 * Flutter's tracker reads it.
 */
class CardVelocity {
    private val samples = ArrayDeque<Pair<CardVec, Double>>()

    fun add(at: CardVec, time: Double) {
        samples.addLast(at to time)
        while (samples.size > MAX_SAMPLES) samples.removeFirst()
    }

    fun clear() = samples.clear()

    fun velocity(now: Double): CardVec {
        val last = samples.lastOrNull() ?: return CardVec.ZERO
        if (now - last.second > STOPPED_MS) return CardVec.ZERO
        val recent = samples.filter { last.second - it.second <= HORIZON_MS }
        if (recent.size < 2) return CardVec.ZERO
        val meanT = recent.sumOf { it.second } / recent.size
        val spread = recent.sumOf { (it.second - meanT) * (it.second - meanT) }
        if (spread <= 0.0) return CardVec.ZERO
        fun slope(of: (CardVec) -> Float): Float {
            val mean = recent.sumOf { of(it.first).toDouble() } / recent.size
            return (recent.sumOf { (it.second - meanT) * (of(it.first) - mean) } / spread).toFloat()
        }
        return CardVec(slope { it.x }, slope { it.y })
    }

    private companion object {
        const val MAX_SAMPLES = 20
        const val HORIZON_MS = 100.0
        const val STOPPED_MS = 40.0
    }
}

/** What a pointer that went down on the card says as it moves (XTA's SwipeBackDragRecognizer). */
enum class CardDrag {
    /** Not far enough yet to tell. */
    WAIT,

    /** It heads elsewhere: the page has it. */
    LEAVE,

    /** It heads back: the card has it. */
    TAKE,
}

/** How far in from each side of the window the system keeps swipes for itself, in dp. */
data class GestureEdges(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    companion object {
        val NONE = GestureEdges(0f, 0f, 0f, 0f)
    }
}

/** When a drag is the card's, as XTA's recognizer decides it. */
object CardDrags {
    /**
     * Whether a drag may start at [at] on a card of [size]: anywhere when the
     * reader swipes back from anywhere or the card is [catching], else only
     * within [SwipeBack.EDGE] of the edge it leaves from ([rtl]: the right);
     * never within Android's own gesture edges ([gestures]: left, top, right,
     * bottom), whose swipes are Android's back gesture.
     */
    fun mayStart(at: CardVec, size: CardSize, rtl: Boolean, anywhere: Boolean, catching: Boolean, gestures: GestureEdges = GestureEdges.NONE): Boolean {
        val inEdge = at.x < gestures.left || at.x > size.width - gestures.right || at.y < gestures.top || at.y > size.height - gestures.bottom
        if (inEdge) return false
        if (catching || anywhere) return true
        return (if (rtl) size.width - at.x else at.x) <= SwipeBack.EDGE
    }

    /**
     * A pointer [travel] from where it went down, back being [direction]
     * (1 to the right, -1 to the left), [aimed] once it went far enough to
     * pick a way: it must go more across than up or down once past
     * [SwipeBack.SLOP], and is the card's once past [slop] across, as far as
     * a sideways drag inside the card would go first.
     */
    fun consider(travel: CardVec, aimed: Boolean, direction: Float, slop: Float): Pair<CardDrag, Boolean> {
        val across = travel.x * direction
        if (!aimed) {
            if (travel.distance < SwipeBack.SLOP) return CardDrag.WAIT to false
            if (across <= abs(travel.y) * SwipeBack.SIDEWAYS) return CardDrag.LEAVE to true
        }
        return (if (across <= slop) CardDrag.WAIT else CardDrag.TAKE) to true
    }
}
