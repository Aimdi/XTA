package com.aimdi.xtanative.core.feed

import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * XTA's Home reading controls (plugin_home_reading_controls.dart): the
 * secondary row on a Home timeline hides after a deliberate scroll down and
 * comes back after a deliberate scroll up. A pin, a short feed, the top, and
 * a protected moment (a screen reader, the keyboard, a dialog, focus) keep
 * it on screen. Presentation only: it owns no reader and no list.
 *
 * A scroll toward the end is a positive [observe] delta, as XTA's is.
 */
class ReadingControls {
    data class State(val source: String = "", val visible: Boolean = true, val pinned: Boolean = false)

    var state: State = State()
        private set

    /** Told after [state] changes, so a screen can show it. */
    var onChange: ((State) -> Unit)? = null

    private var closed = false
    private var travel = 0.0
    private var direction = 0
    private val writes = Mutex()

    /** The timeline now shown, and whether the pin was saved. A change reveals the row. */
    fun bind(source: String, pinned: Boolean) {
        if (closed) return
        if (source != state.source || pinned != state.pinned) {
            travel = 0.0
            direction = 0
            update(State(source = source, pinned = pinned))
        }
    }

    /** Back on screen, and the scroll toward hiding or revealing starts over. */
    fun reveal() {
        travel = 0.0
        direction = 0
        if (!closed && !state.visible) update(state.copy(visible = true))
    }

    /**
     * One report from the timeline's list. [extentBefore] is how far the list
     * is from its top, [scrollExtent] how far it can scroll, [controlsHeight]
     * the row's height, [userDelta] the finger's movement (a jump or a fling
     * passes 0), and [protected] a moment the row must not leave.
     */
    fun observe(
        source: String,
        extentBefore: Double,
        scrollExtent: Double,
        controlsHeight: Double,
        userDelta: Double,
        protected: Boolean = false,
    ) {
        if (closed || source != state.source) return
        // Account for the space already reclaimed; otherwise short feeds can
        // repeatedly hide and reveal as their viewport grows and shrinks.
        val expanded = scrollExtent + if (state.visible) 0.0 else controlsHeight
        if (protected || state.pinned || extentBefore <= TOP || expanded <= controlsHeight + SHORT_SLACK) {
            reveal()
            return
        }
        if (!userDelta.isFinite() || userDelta == 0.0) return
        val nextDirection = if (userDelta > 0) 1 else -1
        if (nextDirection != direction) travel = 0.0
        direction = nextDirection
        travel += abs(userDelta)
        val next = if (nextDirection < 0) travel >= SHOW_AFTER || state.visible else travel < HIDE_AFTER && state.visible
        if (next == state.visible) return
        update(state.copy(visible = next))
    }

    /**
     * Pins the row open, or lets it hide again. [persist] keeps the choice;
     * a refusal or a failure leaves the previous one, as XTA's does.
     */
    suspend fun setPinned(pinned: Boolean, persist: suspend (Boolean) -> Boolean = { true }) {
        if (closed) return
        writes.withLock {
            if (closed) return@withLock
            val kept = try {
                persist(pinned)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            if (!kept || closed) return@withLock
            travel = 0.0
            direction = 0
            update(State(source = state.source, pinned = pinned))
        }
    }

    /** Ignores anything further, as a disposed store does. */
    fun close() {
        closed = true
    }

    private fun update(next: State) {
        if (next == state) return
        state = next
        onChange?.invoke(next)
    }

    companion object {
        /** Down this far, the row hides. Up this far, it comes back. Pixels, as XTA's. */
        const val HIDE_AFTER = 64.0
        const val SHOW_AFTER = 24.0

        /** At or above this, the list counts as at its top and the row stays. */
        const val TOP = 0.5

        /** A feed no taller than the row plus this stays visible. */
        const val SHORT_SLACK = 96.0

        /** The row is at least this tall, as XTA's viewport assumes before it has been measured. */
        const val MIN_HEIGHT = 48.0
    }
}

/**
 * How far a list is from its top, and how far it can scroll, from what a lazy
 * list or grid has laid out. A list that has not been laid out answers null.
 * One whose ends are both on screen answers its real extent; one that runs
 * past what is laid out answers [LONG], so it is not treated as short.
 */
object ReadingScroll {
    /** Far enough that a feed is not short and not at its top. */
    const val LONG = 100_000.0

    fun of(viewportStart: Int, viewportEnd: Int, firstIndex: Int, firstOffset: Int, total: Int, lastIndex: Int, lastEnd: Int): Pair<Double, Double>? {
        if (viewportEnd - viewportStart <= 0) return null
        val before = if (firstIndex <= 0) firstOffset.toDouble().coerceAtLeast(0.0) else LONG
        if (total <= 0) return before to 0.0
        if (firstIndex > 0 || lastIndex < total - 1) return before to LONG
        val remaining = (lastEnd - viewportEnd).coerceAtLeast(0)
        return before to (firstOffset.coerceAtLeast(0) + remaining).toDouble()
    }
}
