package com.aimdi.xtanative.ui.common

import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * The pace of the tools that read X item by item for a long while (the
 * broken-subscriptions scan, finding deleted saved posts, importing a
 * following): a random two to three seconds between two reads, as a person
 * clicking through. Each tool stops at X's first rate limit or pause.
 */
object BulkPace {
    val GAP_MILLIS = 2_000L..3_000L

    fun gap(random: Random = Random.Default): Long = random.nextLong(GAP_MILLIS.first, GAP_MILLIS.last + 1)

    suspend fun pause() = delay(gap())
}
