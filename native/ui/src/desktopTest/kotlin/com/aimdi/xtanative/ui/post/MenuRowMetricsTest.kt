package com.aimdi.xtanative.ui.post

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MenuRowMetricsTest {
    @Test
    fun aPointerMenuIsDenserThanAPhoneSheet() {
        val sheet = MenuRowMetrics.Sheet
        val pointer = MenuRowMetrics.Pointer
        assertTrue(pointer.minHeight < sheet.minHeight)
        assertTrue(pointer.padding < sheet.padding)
        assertTrue(pointer.gap < sheet.gap)
        // Still a comfortable target for a pointer.
        assertTrue(pointer.minHeight >= 32.dp)
    }

    @Test
    fun aPhoneSheetKeepsItsRows() {
        assertEquals(52.dp, MenuRowMetrics.Sheet.minHeight)
        assertEquals(24.dp, MenuRowMetrics.Sheet.padding)
    }
}
