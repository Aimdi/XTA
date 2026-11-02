package com.aimdi.xtanative.networks.stocks

import java.time.Duration

/**
 * How much history a chart shows, and how finely, in the host's spelling.
 * The interval is tied to the range: a year of five-minute bars is a line
 * nobody can read, and a day of daily closes is one point.
 */
enum class TickerRange(val range: String, val interval: String) {
    DAY("1d", "5m"),
    WEEK("5d", "30m"),
    MONTH("1mo", "1d"),
    SIX_MONTHS("6mo", "1d"),
    YEAR("1y", "1d"),
    FIVE_YEARS("5y", "1wk"),
    ;

    /** The span and step the range asks for, as the coin exchanges are asked. */
    val window: ChartWindow get() = checkNotNull(ChartWindow.parse(range, interval))

    /** Only the day's chart carries the pre-market and after-hours bars. */
    val includesExtendedHours: Boolean get() = this == DAY
}

/** The bars a chart wants: how far back ([span]) and how finely ([step]). */
data class ChartWindow(val span: Duration, val step: Duration) {
    companion object {
        private val spelling = Regex("^(\\d+)(m|h|d|wk|mo|y)$")

        /** The window a range and interval as the host spells them ask for (`5d`, `30m`); null for any other spelling. */
        fun parse(range: String, interval: String): ChartWindow? {
            val span = durationOf(range) ?: return null
            val step = durationOf(interval) ?: return null
            return ChartWindow(span, step)
        }

        private fun durationOf(text: String): Duration? {
            val match = spelling.find(text) ?: return null
            val count = match.groupValues[1].toLongOrNull() ?: return null
            return when (match.groupValues[2]) {
                "m" -> Duration.ofMinutes(count)
                "h" -> Duration.ofHours(count)
                "d" -> Duration.ofDays(count)
                "wk" -> Duration.ofDays(7 * count)
                "mo" -> Duration.ofDays(30 * count)
                else -> Duration.ofDays(365 * count)
            }
        }
    }

    /** The finest of [steps] no finer than this window's that covers its span in at most [maxBars] bars; null when none does. */
    fun coarsestFit(steps: List<Duration>, maxBars: Int): Duration? =
        steps.firstOrNull { it >= step && span.seconds.toDouble() / it.seconds <= maxBars }
}
