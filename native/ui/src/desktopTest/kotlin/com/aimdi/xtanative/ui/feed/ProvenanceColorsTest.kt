package com.aimdi.xtanative.ui.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ProvenanceColorsTest {
    private val black = 0xFF000000.toInt()
    private val white = -1

    @Test
    fun aVividBrandIsItsOwnColourOnEitherSurface() {
        val reddit = 0xFFFF4500.toInt()
        assertEquals(reddit, ProvenanceColors.accent(reddit, black))
        assertEquals(reddit, ProvenanceColors.accent(reddit, white))
    }

    @Test
    fun aNearlyBlackBrandIsLightenedOnADarkSurface() {
        val threads = 0xFF101010.toInt()
        val shown = ProvenanceColors.accent(threads, black)
        assertNotEquals(threads, shown, "Threads' mark would be lost on true black")
        assertTrue(ProvenanceColors.contrast(shown, black) >= 2.0)
        assertEquals(threads, ProvenanceColors.accent(threads, white), "on a light surface it is seen as it is")
    }

    @Test
    fun aPaleBrandIsDarkenedUntilItStandsOutTwoToOne() {
        val pale = 0xFFFFE0E0.toInt()
        val shown = ProvenanceColors.accent(pale, white)
        assertNotEquals(pale, shown)
        assertTrue(ProvenanceColors.contrast(shown, white) >= 2.0)
        assertTrue(ProvenanceColors.luminance(shown) < ProvenanceColors.luminance(pale), "taken toward black on a light surface")
    }

    @Test
    fun theContrastIsWcags() {
        assertEquals(21.0, ProvenanceColors.contrast(black, white), 0.01)
        assertEquals(1.0, ProvenanceColors.contrast(white, white), 0.0001)
    }
}
