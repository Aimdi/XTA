package com.aimdi.xtanative.networks.rss

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RssDatesTest {
    @Test
    fun readsRfc822AndIso8601() {
        assertEquals(Instant.parse("2026-08-04T23:03:08Z"), RssDates.parse("Tue, 04 Aug 2026 23:03:08 GMT"))
        assertEquals(Instant.parse("2026-08-04T12:00:00Z"), RssDates.parse("2026-08-04T12:00:00Z"))
        assertNull(RssDates.parse("not a date"))
        assertNull(RssDates.parse(null))
        assertNull(RssDates.parse("   "))
    }

    @Test
    fun anRfc822ZoneIsAppliedWhichXtaIgnored() {
        assertEquals(Instant.parse("2026-08-04T21:03:08Z"), RssDates.parse("Tue, 04 Aug 2026 23:03:08 +0200"))
        assertEquals(Instant.parse("2026-08-05T04:03:08Z"), RssDates.parse("Tue, 04 Aug 2026 23:03:08 EST"))
        assertEquals(Instant.parse("2026-08-05T06:03:08Z"), RssDates.parse("Tue, 04 Aug 2026 23:03:08 PDT"))
        assertEquals(Instant.parse("2026-08-04T23:03:08Z"), RssDates.parse("Tue, 04 Aug 2026 23:03:08 CEST"), "a zone RFC 822 does not name is UTC")
    }

    @Test
    fun rfc822WithoutDayNameOrSecondsAndInLowerCase() {
        assertEquals(Instant.parse("2026-08-04T23:03:00Z"), RssDates.parse("4 aug 2026 23:03 gmt"))
        assertEquals(Instant.parse("2026-08-04T23:03:08Z"), RssDates.parse("Published: Tue, 04 Aug 2026 23:03:08 GMT (approx.)"))
    }

    @Test
    fun iso8601AsDartReadsIt() {
        assertEquals(Instant.parse("2026-10-29T13:00:00Z"), RssDates.parse("2026-10-29T08:00:00.000-05:00"))
        assertEquals(Instant.parse("2026-10-29T08:00:00.123456Z"), RssDates.parse("2026-10-29 08:00:00.1234567Z"))
        assertEquals(Instant.parse("2026-10-29T06:30:00Z"), RssDates.parse("20261029T120000+0530"))
        assertEquals(Instant.parse("2026-10-29T00:00:00Z"), RssDates.parse("2026-10-29"))
    }

    @Test
    fun anIsoTimeWithoutAZoneIsUtcWhereXtaTookThePhonesOwnZone() {
        assertEquals(Instant.parse("2026-10-29T08:00:00Z"), RssDates.parse("2026-10-29T08:00:00"))
    }

    @Test
    fun aDateWithSomethingAfterItKeepsTheDay() {
        assertEquals(Instant.parse("2026-08-04T00:00:00Z"), RssDates.parse("2026/08/04 at noon"))
    }

    @Test
    fun outOfRangePartsRollOverAsDartRollsThem() {
        assertEquals(Instant.parse("2026-03-02T00:00:00Z"), RssDates.parse("2026-02-30"))
        assertEquals(Instant.parse("2026-08-05T00:00:00Z"), RssDates.parse("2026-08-04T24:00:00Z"))
    }
}
