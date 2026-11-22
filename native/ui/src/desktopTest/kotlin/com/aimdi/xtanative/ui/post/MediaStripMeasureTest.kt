package com.aimdi.xtanative.ui.post

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** A row of media sized where it is measured: the same sizes the row was composed with before. */
class MediaStripMeasureTest {
    private val unbounded = Int.MAX_VALUE
    private val aspects = listOf(0.75f, 16f / 9f, 1f)

    @Test
    fun theRowIsLaidOutForTheWidthItIsGivenInDp() {
        val strip = StripMeasure(aspects)
        assertEquals(MediaStrip.layout(360f, aspects), strip.at(720, 2f))
        assertEquals(MediaStrip.layout(360f, aspects), strip.layout, "its cards read what the row was measured at")
        assertEquals(MediaStrip.layout(1080 / 2.625f, aspects), strip.at(1080, 2.625f))
        assertEquals(MediaStrip.layout(Float.POSITIVE_INFINITY, aspects), strip.at(unbounded, 2f), "no bound is an endless width, as it was")
    }

    @Test
    fun theSameWidthIsNotLaidOutAgain() {
        val strip = StripMeasure(aspects)
        assertSame(strip.at(720, 2f), strip.at(720, 2f))
        assertEquals(MediaStrip.layout(240f, aspects), strip.at(720, 3f), "the same pixels are another width at another density")
    }

    @Test
    fun cardsAreMeasuredAgainOnlyWhereTheWidthChangedAndTheHeightInPixelsDidNot() {
        val strip = StripMeasure(aspects)
        val start = strip.followStale()
        strip.at(720, 2f)
        strip.heightIs(446)
        assertEquals(start, strip.followStale(), "a first measure measures its cards anyway")
        strip.at(720, 2f)
        strip.heightIs(446)
        assertEquals(start, strip.followStale(), "nothing changed")
        strip.at(900, 2f)
        strip.heightIs(558)
        assertEquals(start, strip.followStale(), "a new height measures the cards by itself")
        strip.at(901, 2f)
        strip.heightIs(558)
        assertEquals(start + 1, strip.followStale(), "a pixel wider at the same height: the cards would have kept their widths")
        strip.at(901, 2f)
        strip.heightIs(558)
        assertEquals(start + 1, strip.followStale())
    }

    @Test
    fun aSizeIsFixedWithinItsBoundsAsWidthAndHeightModifiersFixIt() {
        assertEquals(100..100, fixedSize(100, 0, unbounded))
        assertEquals(60..60, fixedSize(100, 0, 60), "no more than is allowed")
        assertEquals(120..120, fixedSize(100, 120, 200), "no less than is asked")
        assertEquals(0..0, fixedSize(-5, 0, 200))
        assertEquals(0..unbounded, fixedSize(unbounded, 0, unbounded), "an endless size fixes nothing")
        assertEquals(0..300, fixedSize(unbounded, 0, 300))
    }
}
