package com.aimdi.xtanative.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * One clock for every "5m" on screen: it ticks at each whole minute while
 * at least one time is drawn ([minuteMeasured]), and stops when the last is gone.
 * A card reads its state, so a tick redraws only the times that move on, and
 * no card runs a timer of its own.
 */
internal object MinuteClock {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tick = mutableLongStateOf(0L)
    private var readers = 0
    private var ticking: Job? = null

    /** The minutes ticked so far; reading it in composition redraws the reader at the next whole minute. */
    val ticks: Long get() = tick.longValue

    @Synchronized
    fun join() {
        readers += 1
        if (readers == 1) ticking = scope.launch {
            while (isActive) {
                delay(MinuteTicks.untilNextMinute(System.currentTimeMillis()))
                tick.longValue += 1
            }
        }
    }

    @Synchronized
    fun leave() {
        readers = (readers - 1).coerceAtLeast(0)
        if (readers == 0) {
            ticking?.cancel()
            ticking = null
        }
    }

    /** Whether the clock is running, for the tests. */
    @Synchronized
    fun running(): Boolean = ticking?.isActive == true
}

/** When the next tick of [MinuteClock] is due. */
object MinuteTicks {
    private const val MINUTE = 60_000L

    /** Milliseconds from [nowMs] to the next whole minute, never less than a second, so a tick never comes twice in a row. */
    fun untilNextMinute(nowMs: Long): Long = (MINUTE - Math.floorMod(nowMs, MINUTE)).coerceAtLeast(1_000L)
}

/** A place on screen that reads [MinuteClock] while it is composed. */
private class MinuteLease : RememberObserver {
    override fun onRemembered() = MinuteClock.join()
    override fun onForgotten() = MinuteClock.leave()
    override fun onAbandoned() = Unit
}

/**
 * [measure] of the wall clock's time in milliseconds, measured again at
 * every whole minute while the caller is composed: a relative time ("5m")
 * moves on as a feed stays open. A tick redraws the caller only when what it
 * measures changes, so "3h" and a date are left alone, and the cards on
 * screen are not all redrawn on the same frame. [keys] are what [measure]
 * reads besides the time.
 */
@Composable
internal fun <T> minuteMeasured(vararg keys: Any?, measure: (nowMs: Long) -> T): T {
    remember { MinuteLease() }
    remember(*keys) { derivedStateOf { MinuteClock.ticks; measure(System.currentTimeMillis()) } }.value
    return measure(System.currentTimeMillis())
}
