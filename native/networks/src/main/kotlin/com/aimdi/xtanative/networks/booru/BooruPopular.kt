package com.aimdi.xtanative.networks.booru

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** How long a popular list spans. [wire] is how the hosts name it. */
enum class BooruPopularScale {
    DAY,
    WEEK,
    MONTH,
    ;

    val wire: String get() = name.lowercase()
}

/** Which popular list to read: the day, week or month of [date]. */
data class BooruPopularQuery(val scale: BooruPopularScale, val date: LocalDate) {
    fun withScale(next: BooruPopularScale): BooruPopularQuery = copy(scale = next)

    /** One day, week or month earlier ([direction] -1) or later (1), never past [today]. A month keeps its day where it can (Mar 31 − 1 is Feb 28). */
    fun step(direction: Int, today: LocalDate = hostToday()): BooruPopularQuery {
        val next = when (scale) {
            BooruPopularScale.DAY -> date.plusDays(direction.toLong())
            BooruPopularScale.WEEK -> date.plusWeeks(direction.toLong())
            BooruPopularScale.MONTH -> date.plusMonths(direction.toLong())
        }
        return copy(date = if (next.isAfter(today)) today else next)
    }

    fun isLatest(today: LocalDate = hostToday()): Boolean = !date.isBefore(today)

    companion object {
        fun today(scale: BooruPopularScale = BooruPopularScale.DAY): BooruPopularQuery = BooruPopularQuery(scale, hostToday())

        /**
         * The newest day a host can have a popular list for: its own date, taken as UTC's, but never
         * past the reader's. East of UTC the reader's morning is a day the host has not reached;
         * west of it, the host's new day has only just begun in the reader's evening.
         */
        fun hostToday(now: Instant = Instant.now(), offset: ZoneOffset = ZoneId.systemDefault().rules.getOffset(now)): LocalDate {
            val hostDay = now.atOffset(ZoneOffset.UTC).toLocalDate()
            val readerDay = now.atOffset(offset).toLocalDate()
            return if (hostDay.isBefore(readerDay)) hostDay else readerDay
        }
    }
}
