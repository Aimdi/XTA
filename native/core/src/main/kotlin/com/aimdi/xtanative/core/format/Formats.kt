package com.aimdi.xtanative.core.format

import java.time.Duration
import java.time.Instant

/** How long ago a post was written, in the unit X shows it. */
sealed interface Ago {
    data class Seconds(val n: Long) : Ago
    data class Minutes(val n: Long) : Ago
    data class Hours(val n: Long) : Ago

    /** A day or more ago: shown as a date, [sameYear] without the year. */
    data class OnDate(val at: Instant, val sameYear: Boolean) : Ago
}

object Formats {
    /** As X shows counts: 999, 1.2K, 12K, 1.2M, 12M; [decimal] is the locale's separator. */
    fun count(n: Long, decimal: Char = '.'): String = when {
        n < 1_000 -> n.toString()
        n < 10_000 -> tenths(n / 100, decimal) + "K"
        n < 1_000_000 -> "${n / 1_000}K"
        n < 10_000_000 -> tenths(n / 100_000, decimal) + "M"
        n < 1_000_000_000 -> "${n / 1_000_000}M"
        else -> tenths(n / 100_000_000, decimal) + "B"
    }

    /**
     * A count as the reader's language shortens it ("1,2 Mio.", "1.2万",
     * "12K"), cut down rather than rounded as X does: one decimal while the
     * shortened number has a single digit before it, none from two on (12K,
     * 1234万 not 1234.5万). [compact] is the platform's compact number format
     * for the language, writing a count with at most the given decimals and
     * rounding down; [decimal] is the language's separator. Where the platform
     * has none ([compact] null or failing), X's own [count].
     */
    fun compactCount(n: Long, decimal: Char, compact: ((Long, Int) -> String)?): String {
        val count = n.coerceAtLeast(0)
        val format = compact ?: return count(count, decimal)
        val tenths = runCatching { format(count, 1) }.getOrNull() ?: return count(count, decimal)
        val before = tenths.substringBefore(decimal, missingDelimiterValue = "")
        val digits = before.takeLastWhile { it.isDigit() }.length
        return if (digits >= 2) runCatching { format(count, 0) }.getOrDefault(tenths) else tenths
    }

    private val handle = Regex("^@?([A-Za-z0-9_]{1,15})$")

    /** The X handle [query] names as typed into search, "@nasa" or "nasa"; null when it cannot be one. */
    fun handleOf(query: String): String? = handle.find(query.trim())?.groupValues?.get(1)

    private fun tenths(tenths: Long, decimal: Char): String =
        if (tenths % 10 == 0L) "${tenths / 10}" else "${tenths / 10}$decimal${tenths % 10}"

    /** The bucket a feed row shows [then] in, seen at [now]; [yearOf] says which year an instant falls in, locally. */
    fun ago(then: Instant, now: Instant, yearOf: (Instant) -> Int): Ago {
        val elapsed = Duration.between(then, now).coerceAtLeast(Duration.ZERO)
        return when {
            elapsed.seconds < 60 -> Ago.Seconds(elapsed.seconds)
            elapsed.toMinutes() < 60 -> Ago.Minutes(elapsed.toMinutes())
            elapsed.toHours() < 24 -> Ago.Hours(elapsed.toHours())
            else -> Ago.OnDate(then, yearOf(then) == yearOf(now))
        }
    }
}
