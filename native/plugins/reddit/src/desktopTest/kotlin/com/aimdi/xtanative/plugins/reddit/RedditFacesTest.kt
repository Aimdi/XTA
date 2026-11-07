package com.aimdi.xtanative.plugins.reddit

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The pixel faces as XTA's reddit_avatar_test holds them. */
class RedditFacesTest {
    @Test
    fun theSameNameAlwaysGetsTheSameOneWhateverItsCase() {
        assertEquals(RedditFaces.of("spez"), RedditFaces.of("spez"))
        assertEquals(RedditFaces.of("SomeOne"), RedditFaces.of("someone"))
    }

    @Test
    fun differentNamesGenerallyLookDifferent() {
        val looks = (0 until 60).map { RedditFaces.of("reader$it") }.map { Triple(RedditFaces.faces.indexOf(it.rows), it.background, it.accent) }.toSet()
        assertTrue(looks.size > 50, "${looks.size}")
    }

    @Test
    fun everyFaceAndBackgroundGetsUsedAcrossEnoughNames() {
        val faces = (0 until 400).map { RedditFaces.of("user$it") }
        assertEquals(RedditFaces.faces.size, faces.map { it.rows }.toSet().size, "a face nobody gets is dead weight")
        assertEquals(RedditFaces.backgrounds.size, faces.map { it.background }.toSet().size)
    }

    @Test
    fun anEmptyNameStillResolves() {
        assertTrue(RedditFaces.of("").rows in RedditFaces.faces)
    }

    @Test
    fun theFacesAreAllAnEightByEightGridOfTheThreeKnownCellsAndDrawSomething() {
        RedditFaces.faces.forEach { face ->
            assertEquals(8, face.size, face.joinToString("|"))
            face.forEach { row ->
                assertEquals(8, row.length, row)
                assertTrue(row.all { it in ".#o" }, row)
            }
            assertTrue(face.joinToString("").replace(".", "").isNotEmpty())
        }
    }

    @Test
    fun theInkIsDarkOnALightBackgroundLightOnADarkOneAndAlwaysContrasts() {
        val rows = RedditFaces.faces.first()
        assertTrue(RedditContrast.luminance(RedditFace(rows, 0xFFE9E9E9.toInt(), 0).ink) < 0.2)
        assertTrue(RedditContrast.luminance(RedditFace(rows, 0xFF37474F.toInt(), 0).ink) > 0.8)
        RedditFaces.backgrounds.forEach { background ->
            val face = RedditFace(rows, background.toInt(), 0)
            assertTrue(abs(RedditContrast.luminance(face.ink) - RedditContrast.luminance(face.background)) > 0.25, background.toString(16))
        }
    }
}
