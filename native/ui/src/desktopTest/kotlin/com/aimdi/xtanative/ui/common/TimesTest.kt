package com.aimdi.xtanative.ui.common

import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** XTA's stale_feed_preview_test.dart sameCalendarDay: a kept feed's age line says the time for today, the date before. */
class TimesTest {
    private val zone = ZoneId.of("Europe/Berlin")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int) = LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant()

    @Test
    fun theDayIsComparedNotTheTimeElapsed() {
        assertTrue(sameCalendarDay(at(2026, 8, 4, 0, 5), at(2026, 8, 4, 23, 55), zone))
        assertFalse(sameCalendarDay(at(2026, 8, 3, 23, 55), at(2026, 8, 4, 0, 5), zone))
        assertFalse(sameCalendarDay(at(2025, 8, 4, 12, 0), at(2026, 8, 4, 12, 0), zone))
    }
}
