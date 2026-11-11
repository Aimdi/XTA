package com.aimdi.xtanative.ui.plugins

import kotlin.test.Test
import kotlin.test.assertEquals

class BrandMarkIconTest {
    @Test
    fun anEdgeToEdgeGlyphSpansTheLiveAreaOfAnIcon() {
        val fit = markFit(left = 0f, top = 0f, side = 24f)
        assertEquals(2f, fit.x + 0f * fit.scale, 0.001f)
        assertEquals(22f, fit.x + 24f * fit.scale, 0.001f)
        assertEquals(2f, fit.y, 0.001f)
    }

    @Test
    fun aGlyphDrawnInASmallerFrameIsFittedFromItsOwnCorner() {
        val fit = markFit(left = 2.4f, top = 2.4f, side = 19.2f)
        assertEquals(2f, fit.x + 2.4f * fit.scale, 0.001f)
        assertEquals(22f, fit.x + 21.6f * fit.scale, 0.001f)
        assertEquals(22f, fit.y + 21.6f * fit.scale, 0.001f)
    }

    @Test
    fun theMarkIsATwentyFourDpIcon() {
        val icon = brandMarkIcon("Mark", "M0,0H24V24H0Z", evenOdd = true)
        assertEquals(24f, icon.viewportWidth)
        assertEquals(24f, icon.defaultWidth.value)
    }
}
