package com.aimdi.xtanative.ui.chrome

/**
 * An opened post as XTA's XtaCardPageRoute moves it: it slides in over the
 * page it was opened from, goes back as a card the reader drags away,
 * throws or lets drop back ([hand]), and slides out on Back. Times are in
 * milliseconds of the caller's clock; [gone] says when its screen is to be
 * taken away, with nothing more to show.
 */
class CardMotion {
    val hand = CardHand()

    /** The slide, unchanged in time: 0 off screen, 1 in place. */
    private var linear = 0f

    /** +1 sliding in, -1 sliding out, 0 still. */
    private var direction = 0

    /** Which way the slide set off, which picks its curve and how far it goes out, as a Flutter curve keeps it. */
    private var setOffClosing = false
    private var lastTick: Double? = null

    /** Leaving: it slides or flies out, and takes nothing more. */
    var leaving = false
        private set

    /** Its screen is to go now. */
    var gone = false
        private set

    /** Whether frames are needed: it slides, or the hand has it. */
    val moving: Boolean get() = direction != 0 || hand.busy

    /** Opened: it slides in ([animate]), or is in place at once. */
    fun arrive(animate: Boolean) {
        linear = if (animate) 0f else 1f
        slide(if (animate) 1 else 0)
    }

    /** It is in place already, as when its screen comes back. */
    fun rest() {
        linear = 1f
        slide(0)
    }

    /**
     * Back was pressed: a held or falling card is thrown from where it is, one
     * in place slides out ([animate]) or goes at once, and one already leaving
     * goes on as it does.
     */
    fun back(now: Double, animate: Boolean) {
        if (leaving) return
        leaving = true
        when {
            hand.busy -> released(hand.leaving(now))
            animate -> slide(-1)
            else -> gone = true
        }
    }

    /** Whether a hand may take the card now: in place, or dropping back when [catching]. */
    fun mayTake(catching: Boolean): Boolean = !leaving && ((catching && hand.catchable) || (direction == 0 && linear >= 1f && !hand.busy))

    /** A release, Android's commit or a back press that threw the card or let it go flat: it leaves. */
    fun released(result: CardRelease?) {
        when (result) {
            CardRelease.THROWN -> leaving = true
            CardRelease.LEFT -> {
                leaving = true
                gone = true
            }
            CardRelease.SETTLING, CardRelease.RESTED, null -> Unit
        }
    }

    /** Moves everything on to [now]. */
    fun tick(now: Double) {
        hand.tick(now)
        if (hand.gone) gone = true
        val step = lastTick?.let { (now - it).toFloat() } ?: 0f
        lastTick = now
        if (direction == 0) return
        val length = if (direction > 0) SwipeBack.OPEN_MS else SwipeBack.CLOSE_MS
        linear = (linear + direction * step / length).coerceIn(0f, 1f)
        if (direction > 0 && linear >= 1f) slide(0)
        if (direction < 0 && linear <= 0f) {
            slide(0)
            gone = true
        }
    }

    /** Where the card is drawn on [size]: where the hand holds it, else on the slide. */
    fun pose(size: CardSize, mirrored: Boolean): CardPose {
        hand.pose?.let { return it }
        val curved = if (setOffClosing) SwipeBack.easeOutQuad(linear) else SwipeBack.easeOutCubic(linear)
        return SwipeBack.slidePose(curved, size, setOffClosing, mirrored)
    }

    private fun slide(to: Int) {
        if (direction == 0 && to != 0) {
            setOffClosing = to < 0
            lastTick = null
        }
        direction = to
    }
}
