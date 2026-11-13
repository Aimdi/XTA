package com.aimdi.xtanative.core.repairs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The rules of XTA's test/plugin_repairs_test.dart: what a repair may be, and what a published file holds. */
class RepairsTest {
    @Test
    fun idsAndNumbersAreDigitsOnly() {
        assertTrue(RepairKind.DIGITS.fits("6232751443445612"))
        assertFalse(RepairKind.DIGITS.fits("12a"))
        assertFalse(RepairKind.DIGITS.fits(""))
    }

    @Test
    fun tokensHoldNoDotsOrSlashes() {
        assertTrue(RepairKind.TOKEN.fits("BarcelonaProfileThreadsTabQuery"))
        assertFalse(RepairKind.TOKEN.fits("a/b"))
        assertFalse(RepairKind.TOKEN.fits("evil.example"))
    }

    @Test
    fun versionsAreNumbersJoinedByDots() {
        assertTrue(RepairKind.VERSION.fits("5.0.234"))
        assertTrue(RepairKind.VERSION.fits("5"))
        assertFalse(RepairKind.VERSION.fits("5.0."))
        assertFalse(RepairKind.VERSION.fits("v5"))
    }

    @Test
    fun headerTextIsOneLineOfPrintableAsciiWithNoEdgeSpaces() {
        assertTrue(RepairKind.HEADER.fits("Mozilla/5.0 (Android) XTA"))
        assertFalse(RepairKind.HEADER.fits("XTA\r\nCookie: stolen=1"))
        assertFalse(RepairKind.HEADER.fits(" XTA"))
        assertFalse(RepairKind.HEADER.fits("XTA ✓"))
        assertFalse(RepairKind.HEADER.fits("x".repeat(401)))
    }

    @Test
    fun aSlotTakesAPublishedValueOfItsKindAndOtherwiseKeepsTheShippedOne() {
        val slot = RepairSlot("tiktok.aid", "1988", RepairKind.DIGITS)

        assertEquals("2000", slot.valueIn(mapOf("tiktok.aid" to "2000")))
        assertEquals("1988", slot.valueIn(mapOf("tiktok.aid" to "20a0")))
        assertEquals("1988", slot.valueIn(emptyMap()))
    }

    @Test
    fun onlyKnownValuesThatFitAreInForce() {
        val slots = listOf(
            RepairSlot("threads.guest_doc_id", "6232751443445612", RepairKind.DIGITS),
            RepairSlot("pixiv.app_version", "5.0.234", RepairKind.VERSION),
        )
        val published = mapOf(
            "threads.guest_doc_id" to "1111111111111111",
            "pixiv.app_version" to "not a version",
            "threads.host" to "https://evil.example",
        )

        assertEquals(mapOf("threads.guest_doc_id" to "1111111111111111"), Repairs.inForce(published, slots))
    }

    @Test
    fun aFileThatDoesNotReadAsRepairsHoldsNone() {
        assertEquals(emptyMap(), Repairs.parse("oops"))
        assertEquals(emptyMap(), Repairs.parse("""{"repairs": []}"""))
        assertEquals(
            mapOf("pixiv.app_version" to "6.0.0"),
            Repairs.parse("""{"repairs": {"tiktok.aid": 2000, "pixiv.app_version": "6.0.0"}}"""),
        )
    }
}
