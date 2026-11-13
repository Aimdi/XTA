package com.aimdi.xtanative.ui.search

import java.time.LocalDate

/** The advanced search's Since and Until, as a date picker hands days over: midnights UTC, in milliseconds. */
object SearchDates {
    /** XTA's picker reaches back to X's first year, and forward to today. */
    val FIRST: LocalDate = LocalDate.of(2006, 1, 1)
    private const val DAY_MILLIS = 86_400_000L

    fun millisOf(day: LocalDate): Long = day.toEpochDay() * DAY_MILLIS

    fun dayOf(millis: Long): LocalDate = LocalDate.ofEpochDay(Math.floorDiv(millis, DAY_MILLIS))

    fun selectable(millis: Long, today: LocalDate): Boolean = dayOf(millis) in FIRST..today

    fun years(today: LocalDate): IntRange = FIRST.year..today.year

    /** The day the picker opens on: the one set, else today. */
    fun opening(current: LocalDate?, today: LocalDate): LocalDate = current ?: today
}
