package com.aimdi.xtanative.networks.booru

import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Paces the requests to one booru host, as XTA does. At most [perSecond] start in any second,
 * the sliding window Boorusama puts in front of every engine. After a 429 or 5xx the host is left
 * alone for a while that doubles with each such answer in a row, from one second up to
 * [maxPause], or for as long as its `Retry-After` asks; requests waiting meanwhile go out once it
 * is over, in the order they asked, instead of piling on. [now] (epoch milliseconds) and [wait]
 * stand in for the clock in tests.
 */
class BooruRequestGate(
    private val perSecond: Int = 10,
    private val maxPause: Duration = 32.seconds,
    private val now: () -> Long = System::currentTimeMillis,
    private val wait: suspend (Duration) -> Unit = { delay(it) },
) {
    private val turns = Mutex()
    private val lock = Any()
    private val starts = ArrayDeque<Long>()
    private var pausedUntil: Long? = null
    private var failures = 0

    /** Returns when a request to the host may start. */
    suspend fun admit() {
        turns.withLock {
            while (true) {
                val pause = synchronized(lock) { pauseBeforeStart() } ?: break
                wait(pause)
            }
        }
    }

    /** Records how the host answered: a 429 or 5xx pauses it, any other answer ends the run of failures. */
    fun answered(status: Int, retryAfter: Duration? = null) {
        synchronized(lock) {
            if (status != 429 && status < 500) {
                failures = 0
            } else {
                failures++
                val doubled = FIRST_PAUSE * (1 shl min(failures - 1, 16))
                pausedUntil = now() + (retryAfter ?: minOf(doubled, maxPause)).inWholeMilliseconds
            }
        }
    }

    /** Null when a request may start now, which is then counted; else how long to wait first. */
    private fun pauseBeforeStart(): Duration? {
        val at = now()
        val paused = pausedUntil
        if (paused != null && at < paused) return (paused - at).milliseconds
        while (starts.isNotEmpty() && at - starts.first() >= WINDOW_MILLIS) starts.removeFirst()
        if (starts.size < perSecond) {
            starts.addLast(at)
            return null
        }
        return (WINDOW_MILLIS - (at - starts.first())).milliseconds
    }

    companion object {
        private const val WINDOW_MILLIS = 1_000L
        private val FIRST_PAUSE = 1.seconds

        /** The delay an HTTP `Retry-After` header asks for, in seconds. A date is not read: the doubling pause stands in. */
        fun retryAfter(header: String?): Duration? = header?.trim()?.toIntOrNull()?.takeIf { it >= 0 }?.seconds
    }
}
