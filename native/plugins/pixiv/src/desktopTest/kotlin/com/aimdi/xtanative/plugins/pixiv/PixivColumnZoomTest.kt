package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PixivColumnZoomTest {
    private val screen = IntSize(400, 800)

    @Test
    fun theColumnZoomsBetweenItsOwnSizeAndFourTimesAndStaysOverTheScreen() {
        val zoom = ColumnZoom()
        zoom.transform(0.5f, Offset(50f, 50f), screen)
        assertEquals(1f, zoom.scale)
        assertEquals(Offset.Zero, zoom.offset, "at its own size it cannot be moved")
        zoom.transform(10f, Offset(1000f, -2000f), screen)
        assertEquals(ColumnZoom.MAX_SCALE, zoom.scale)
        assertEquals(Offset(600f, -1200f), zoom.offset, "moved no further than its magnified edges")
    }

    @Test
    fun aDoubleTapZoomsInAndTheNextOneBackOut() {
        val zoom = ColumnZoom()
        zoom.toggle(screen)
        assertTrue(zoom.zoomed)
        assertEquals(ColumnZoom.DOUBLE_TAP_SCALE, zoom.scale)
        zoom.transform(1f, Offset(100f, 0f), screen)
        zoom.toggle(screen)
        assertFalse(zoom.zoomed)
        assertEquals(Offset.Zero, zoom.offset)
    }
}
