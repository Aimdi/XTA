package com.aimdi.xtanative.ui.saved

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * A switch drawn as tapped before the device has written it (a bookmark,
 * a heart): [shown] is what the reader asked for until the kept value
 * [actual] agrees, or until [GIVE_UP_MS] pass without it agreeing, when
 * the kept value is shown again.
 */
internal data class Optimistic(val asked: Boolean? = null) {
    /** What to draw while the kept value is [actual]. */
    fun shown(actual: Boolean): Boolean = asked ?: actual

    /** Tapped while the kept value is [actual]: the other of what is shown. */
    fun tapped(actual: Boolean): Optimistic = Optimistic(!shown(actual))

    /** The kept value is now [actual]: the ask is answered once they agree. */
    fun kept(actual: Boolean): Optimistic = if (asked == actual) Optimistic() else this

    companion object {
        /** How long a tap's look is held for a write that does not come. */
        const val GIVE_UP_MS = 3_000L
    }
}

/** [Optimistic] over [actual] in a composable: the value to draw, and what a tap calls before it writes. */
internal class OptimisticSwitch(val shown: Boolean, val tap: () -> Unit)

/** An [Optimistic] switch over [actual], kept per [key] (the post). */
@Composable
internal fun rememberOptimistic(key: String, actual: Boolean): OptimisticSwitch {
    var state by remember(key) { mutableStateOf(Optimistic()) }
    LaunchedEffect(actual, state.asked) {
        state = state.kept(actual)
        if (state.asked != null) {
            delay(Optimistic.GIVE_UP_MS)
            state = Optimistic()
        }
    }
    return OptimisticSwitch(state.shown(actual)) { state = state.tapped(actual) }
}
