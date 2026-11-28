package com.aimdi.xtanative.core.x

import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * A random gap of [gapMillis] between two requests of the same account, as a
 * person's clicks have: a burst of reads leaves X spaced out, account by
 * account, while other accounts go on at once. Times are the monotonic
 * clock's, not the wall clock's. The default gap is none.
 */
class XPacer(private val gapMillis: LongRange = 0L..0L, private val random: Random = Random.Default) {
    private val next = ConcurrentHashMap<String, Long>()

    /** Waits for [account]'s turn, and takes it. */
    suspend fun await(account: String) {
        if (gapMillis.last <= 0L) return
        val now = System.nanoTime() / 1_000_000
        var start = now
        next.compute(account) { _, due ->
            start = maxOf(now, due ?: now)
            start + gapMillis.first + random.nextLong(gapMillis.last - gapMillis.first + 1)
        }
        if (start > now) delay(start - now)
    }

    companion object {
        /** What the app spaces each account's requests by. */
        val PEOPLE_PACE = 100L..600L
    }
}
