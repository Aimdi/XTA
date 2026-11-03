package com.aimdi.xtanative.store.backup

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class XtaTimeTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val instant = Instant.parse("2026-01-02T03:04:05Z")

    @Test
    fun bothFormsAreWrittenAsXtaWritesThem() {
        assertEquals("2026-01-02 03:04:05", XtaTime.sqlite(instant, ZoneOffset.UTC))
        assertEquals("2026-01-02 04:04:05", XtaTime.sqlite(instant, berlin))
        assertEquals("2026-01-02T03:04:05.000Z", XtaTime.iso(instant))
    }

    @Test
    fun aTimeWithoutAZoneIsTheDevicesAsDartReadsIt() {
        assertEquals(instant, XtaTime.parse("2026-01-02 04:04:05", berlin))
        assertEquals(instant, XtaTime.parse("2026-01-02T04:04:05.000", berlin))
        assertEquals(instant, XtaTime.parse("2026-01-02T03:04:05.000Z", berlin))
        assertEquals(instant, XtaTime.parse("2026-01-02T04:04:05+01:00", berlin))
        assertEquals(Instant.parse("2026-01-02T03:04:05.123456Z"), XtaTime.parse("2026-01-02T03:04:05.123456Z"), "Dart writes microseconds")
    }

    @Test
    fun whatIsNoTimeReadsAsNone() {
        assertNull(XtaTime.parse(null))
        assertNull(XtaTime.parse(""))
        assertNull(XtaTime.parse("yesterday"))
    }
}
