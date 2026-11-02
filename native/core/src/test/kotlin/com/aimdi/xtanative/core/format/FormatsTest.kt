package com.aimdi.xtanative.core.format

import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatsTest {
    @Test
    fun countsReadAsXShowsThem() {
        assertEquals(listOf("0", "999", "1K", "1.2K", "9.9K", "12K", "999K", "1M", "1.2M", "12M", "1.5B"),
            listOf(0L, 999, 1_000, 1_250, 9_999, 12_345, 999_999, 1_000_000, 1_290_000, 12_345_678, 1_500_000_000).map { Formats.count(it) })
        assertEquals("1,2K", Formats.count(1_250, decimal = ','))
    }

    @Test
    fun aRowSaysHowLongAgoInOneUnit() {
        val now = Instant.parse("2026-11-02T12:00:00Z")
        val year = { at: Instant -> at.atZone(ZoneOffset.UTC).year }
        assertEquals(Ago.Seconds(5), Formats.ago(now.minusSeconds(5), now, year))
        assertEquals(Ago.Seconds(0), Formats.ago(now.plusSeconds(30), now, year), "a clock ahead of ours is now")
        assertEquals(Ago.Minutes(59), Formats.ago(now.minusSeconds(59 * 60 + 59), now, year))
        assertEquals(Ago.Hours(23), Formats.ago(now.minusSeconds(23 * 3600 + 1), now, year))
        val lastMonth = Instant.parse("2026-10-02T12:00:00Z")
        assertEquals(Ago.OnDate(lastMonth, sameYear = true), Formats.ago(lastMonth, now, year))
        val lastYear = Instant.parse("2025-12-31T12:00:00Z")
        assertEquals(Ago.OnDate(lastYear, sameYear = false), Formats.ago(lastYear, now, year))
    }
}
