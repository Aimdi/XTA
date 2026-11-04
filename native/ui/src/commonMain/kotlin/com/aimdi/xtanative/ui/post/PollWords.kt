package com.aimdi.xtanative.ui.post

import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** How a poll's end is told: when it ends, in minutes, hours or days, or when it ended, up to years ago. */
enum class PollPhrase { ENDS_MINUTES, ENDS_HOURS, ENDS_DAYS, ENDED_MINUTES, ENDED_HOURS, ENDED_DAYS, ENDED_MONTHS, ENDED_YEARS }

/** A poll's end as told: [phrase] with its count [n]. */
data class PollEnd(val phrase: PollPhrase, val n: Int)

/**
 * The line under a poll's bars, as XTA writes it (poll_results.dart): the
 * votes in the reader's locale, then when the poll ends or ended, in the
 * steps of XTA's timeago (a minute up to a minute and a half, minutes to
 * 45, about an hour to an hour and a half, hours to a day, a day to two,
 * days to a month, then months and years), and each choice's share.
 */
object PollWords {
    /** When a poll ending at [endsAt] ends, or ended, seen at [now]; null when X did not say. */
    fun end(endsAt: Instant?, now: Instant): PollEnd? {
        if (endsAt == null) return null
        val ended = endsAt.isBefore(now)
        val seconds = abs(Duration.between(now, endsAt).toMillis()) / 1000.0
        val (unit, n) = step(seconds)
        if (ended) return PollEnd(unit.ended, n)
        // A poll runs for days at most: what is left of one never reads in months.
        return unit.ends?.let { PollEnd(it, n) } ?: PollEnd(PollPhrase.ENDS_DAYS, (seconds / DAY).roundToInt())
    }

    /** [total] votes, grouped as [locale] writes numbers: 1,234. */
    fun votes(total: Long, locale: Locale): String = NumberFormat.getIntegerInstance(locale).format(total)

    /** A choice's [share] (0..1) as [locale] writes a percentage, to one decimal at most: 62.5%, 100%. */
    fun percent(share: Double, locale: Locale): String =
        NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 1 }.format(share)

    private enum class Step(val ends: PollPhrase?, val ended: PollPhrase) {
        MINUTES(PollPhrase.ENDS_MINUTES, PollPhrase.ENDED_MINUTES),
        HOURS(PollPhrase.ENDS_HOURS, PollPhrase.ENDED_HOURS),
        DAYS(PollPhrase.ENDS_DAYS, PollPhrase.ENDED_DAYS),
        MONTHS(null, PollPhrase.ENDED_MONTHS),
        YEARS(null, PollPhrase.ENDED_YEARS),
    }

    private fun step(seconds: Double): Pair<Step, Int> {
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        return when {
            seconds < 90 -> Step.MINUTES to 1
            minutes < 45 -> Step.MINUTES to minutes.roundToInt()
            minutes < 90 -> Step.HOURS to 1
            hours < 24 -> Step.HOURS to hours.roundToInt()
            hours < 48 -> Step.DAYS to 1
            days < 30 -> Step.DAYS to days.roundToInt()
            days < 60 -> Step.MONTHS to 1
            days < 365 -> Step.MONTHS to (days / 30).roundToInt()
            days < 730 -> Step.YEARS to 1
            else -> Step.YEARS to (days / 365).roundToInt()
        }
    }

    private const val DAY = 86_400.0
}
