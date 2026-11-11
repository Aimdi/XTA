package com.aimdi.xtanative.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals

/** The size a picture is decoded to for its box, before the desktop shrinks it smoothly. */
class PictureScalingTest {
    @Test
    fun aCroppedTileIsCoveredAndAFittedPictureStaysInsideItsBox() {
        assertEquals(400 to 500, PictureScaling.decodedSize(1440, 1800, 400, 300, fill = true))
        assertEquals(240 to 300, PictureScaling.decodedSize(1440, 1800, 400, 300, fill = false))
    }

    @Test
    fun aPictureIsNeverEnlarged() {
        assertEquals(320 to 400, PictureScaling.decodedSize(320, 400, 1080, 1350, fill = true))
    }

    @Test
    fun aSideWithoutABoundFollowsTheOther() {
        assertEquals(720 to 900, PictureScaling.decodedSize(1440, 1800, 720, 0, fill = true))
        assertEquals(1440 to 1800, PictureScaling.decodedSize(1440, 1800, 0, 0, fill = true), "no box: as it is")
    }

    @Test
    fun noSideIsLongerThanTheLimit() {
        assertEquals(2048 to 4096, PictureScaling.decodedSize(6000, 12000, 0, 0, fill = false))
    }

    @Test
    fun aPictureWithoutASizeIsLeftAlone() {
        assertEquals(0 to 0, PictureScaling.decodedSize(0, 0, 400, 300, fill = true))
    }
}
