package com.aimdi.xtanative.ui.search

import java.time.LocalDate

/** The advanced search's Since and Until, as a date picker hands days over: midnights UTC, in milliseconds. */
object SearchDates {
    /** XTA's picker reaches back to X's first year, and forward to today. */
    val FIRST: LocalDate = LocalDate.of(2006, 1, 1)
    private const val DAY_MILLIS = 86_400_000L
    private const val WEEK_DAYS = 7L

    fun millisOf(day: LocalDate): Long = day.toEpochDay() * DAY_MILLIS

    fun dayOf(millis: Long): LocalDate = LocalDate.ofEpochDay(Math.floorDiv(millis, DAY_MILLIS))

    fun selectable(millis: Long, today: LocalDate): Boolean = dayOf(millis) in FIRST..today

    fun years(today: LocalDate): IntRange = FIRST.year..today.year

    /** The day the picker opens on: the one set, else today. */
    fun opening(current: LocalDate?, today: LocalDate): LocalDate = current ?: today

    /** The month the picker shows for [day]: its first, as the picker counts months. */
    fun monthOf(day: LocalDate): Long = millisOf(day.withDayOfMonth(1))

    /** A step the keyboard takes through the picker's days: ← → a day, ↑ ↓ a week, Page Up and Down a month (with Shift a year), Home and End the month's ends. */
    enum class Step { PREVIOUS_DAY, NEXT_DAY, PREVIOUS_WEEK, NEXT_WEEK, PREVIOUS_MONTH, NEXT_MONTH, PREVIOUS_YEAR, NEXT_YEAR, MONTH_START, MONTH_END }

    /** [day] moved by [step], kept between X's first day and [today], as the picker lets one pick. */
    fun moved(day: LocalDate, step: Step, today: LocalDate): LocalDate = when (step) {
        Step.PREVIOUS_DAY -> day.minusDays(1)
        Step.NEXT_DAY -> day.plusDays(1)
        Step.PREVIOUS_WEEK -> day.minusDays(WEEK_DAYS)
        Step.NEXT_WEEK -> day.plusDays(WEEK_DAYS)
        Step.PREVIOUS_MONTH -> day.minusMonths(1)
        Step.NEXT_MONTH -> day.plusMonths(1)
        Step.PREVIOUS_YEAR -> day.minusYears(1)
        Step.NEXT_YEAR -> day.plusYears(1)
        Step.MONTH_START -> day.withDayOfMonth(1)
        Step.MONTH_END -> day.withDayOfMonth(day.lengthOfMonth())
    }.coerceIn(FIRST, today)
}
