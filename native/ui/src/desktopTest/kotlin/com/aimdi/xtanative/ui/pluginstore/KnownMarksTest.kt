package com.aimdi.xtanative.ui.pluginstore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class KnownMarksTest {
    @Test
    fun everyPluginOfXtasHasItsMarkBeforeItIsInstalled() {
        StoreRows.REGISTRY.forEach { id ->
            val mark = assertNotNull(KnownMarks.of(id), id)
            assertEquals(24f, mark.icon.viewportWidth, id)
            assertEquals(0xFF, (mark.color shr 24).toInt(), id)
        }
    }

    @Test
    fun aPluginXtaNeverHadKeepsItsInitial() {
        assertNull(KnownMarks.of("somethingnew"))
    }

    @Test
    fun marksAreDrawnInXtasBrandColours() {
        assertEquals(0xFFFE2C55, KnownMarks.of("tiktok")?.color)
        assertEquals(0xFFFF6719, KnownMarks.of("substack")?.color)
        assertEquals(0xFF00C805, KnownMarks.of("stocks")?.color)
    }
}
