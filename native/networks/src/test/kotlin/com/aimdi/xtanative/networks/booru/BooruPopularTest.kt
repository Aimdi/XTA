package com.aimdi.xtanative.networks.booru

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Popular tab's dates, as XTA's booru_popular.dart steps them. */
class BooruPopularTest {
    private val today = LocalDate.of(2026, 10, 9)

    @Test
    fun daysWeeksAndMonthsStepAndNeverPassToday() {
        val query = BooruPopularQuery(BooruPopularScale.DAY, LocalDate.of(2026, 10, 8))
        assertEquals(LocalDate.of(2026, 10, 7), query.step(-1, today).date)
        assertEquals(today, query.step(1, today).date)
        assertEquals(today, query.step(1, today).step(1, today).date)
        assertEquals(LocalDate.of(2026, 10, 1), query.withScale(BooruPopularScale.WEEK).step(-1, today).date)
        val march = BooruPopularQuery(BooruPopularScale.MONTH, LocalDate.of(2026, 3, 31))
        assertEquals(LocalDate.of(2026, 2, 28), march.step(-1, today).date, "a month keeps its day where it can")
        assertTrue(query.step(1, today).isLatest(today))
        assertFalse(query.isLatest(today))
    }

    @Test
    fun todayIsTheHostsDayNeverOneItHasNotReached() {
        fun todayAt(utc: String, hours: Int) = BooruPopularQuery.hostToday(Instant.parse(utc), ZoneOffset.ofHours(hours))
        assertEquals(LocalDate.of(2026, 10, 12), todayAt("2026-10-12T23:30:00Z", 9), "at 08:30 in Tokyo the host is still on the 12th")
        assertEquals(LocalDate.of(2026, 10, 12), todayAt("2026-10-13T02:00:00Z", -5), "at 21:00 in New York the 13th has only just begun for the host")
        assertEquals(LocalDate.of(2026, 10, 13), todayAt("2026-10-13T12:00:00Z", 0))
        assertEquals(BooruPopularQuery.hostToday(), BooruPopularQuery.today().date)
    }
}
