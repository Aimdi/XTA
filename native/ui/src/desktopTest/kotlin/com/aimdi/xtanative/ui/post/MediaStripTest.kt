package com.aimdi.xtanative.ui.post

import com.aimdi.xtanative.core.x.model.XMedia
import kotlin.test.Test
import kotlin.test.assertEquals

/** XTA's media_strip_test.dart: how a post's media is laid out along its row. */
class MediaStripTest {
    private fun photo(width: Int?, height: Int?) = XMedia(XMedia.Kind.PHOTO, "https://pbs.twimg.com/media/a.jpg", width, height)

    @Test
    fun onePictureKeepsTheWidthAndItsOwnShapeHoweverTall() {
        assertEquals(MediaStrip.Layout(300f, listOf(400f)), MediaStrip.layout(400f, listOf(4f / 3f)))
        assertEquals(MediaStrip.Layout(1200f, listOf(400f)), MediaStrip.layout(400f, listOf(1f / 3f)), "a tall photo is not put in bars")
        assertEquals(1f, MediaStrip.singleAspect(0f), "a shape X did not give is square")
        assertEquals(1f, MediaStrip.singleAspect(Float.NaN))
        assertEquals(MediaStrip.Layout(0f, emptyList()), MediaStrip.layout(400f, emptyList()))
    }

    @Test
    fun severalShareOneHeightAndAreAsWideAsTheirShapesThere() {
        val row = MediaStrip.layout(400f, listOf(1f, 0.5f, 1.25f))
        assertEquals(400f * 0.62f, row.height)
        assertEquals(row.height, row.widths[0], "a square is as wide as it is tall")
        assertEquals(row.height * MediaStrip.MIN_ASPECT, row.widths[1], "a taller one is cropped to 4:5")
        assertEquals(row.height * 1.25f, row.widths[2], 0.01f)
    }

    @Test
    fun noCardTakesTheWholeRowSoTheNextOnesEdgeShows() {
        val row = MediaStrip.layout(400f, listOf(3f, 3f))
        assertEquals(listOf(400f * 0.86f, 400f * 0.86f), row.widths, "a panorama, cropped to 1.91:1, would still be wider than that")
    }

    @Test
    fun aShapeIsTheMediasOwnElseSixteenByNine() {
        assertEquals(2f, MediaStrip.aspectOf(photo(200, 100)))
        assertEquals(16f / 9f, MediaStrip.aspectOf(photo(null, 100)))
        assertEquals(16f / 9f, MediaStrip.aspectOf(photo(200, 0)))
        assertEquals(1f, MediaStrip.clamp(-1f))
        assertEquals(MediaStrip.MAX_ASPECT, MediaStrip.clamp(9f))
    }

    @Test
    fun aRowOpenedAtAPictureStartsThereWithinTheRow() {
        assertEquals(2, MediaStrip.startOf(2, 4))
        assertEquals(3, MediaStrip.startOf(9, 4))
        assertEquals(0, MediaStrip.startOf(-1, 4))
        assertEquals(0, MediaStrip.startOf(3, 0))
    }
}
