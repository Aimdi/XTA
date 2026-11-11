package com.aimdi.xtanative.ui.plugins

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PluginFacesTest {
    @Test
    fun aMissingPictureIsTheNamesFirstLetterOnTheColourTheIdPicks() {
        val face = PluginFallbackFace.of("  émile zola", "emile")
        assertEquals("É", face.letter)
        assertEquals(face.colour, PluginFallbackFace.of("Renamed", "emile").colour, "a rename keeps the colour")
        assertNotEquals(PluginFallbackFace.of("Sam", "sam_a").colour, PluginFallbackFace.of("Sam", "sam_b").colour, "two of one name differ")
    }

    @Test
    fun aNameWithoutLettersFallsBackToTheIdThenToAHash() {
        assertEquals("Z", PluginFallbackFace.of("🙂", "zuck").letter)
        assertEquals("#", PluginFallbackFace.of("", "._").letter)
        assertEquals(Color(0xFF1D9BF0), PluginFallbackFace.of("", "").colour, "XTA's first colour for an empty id")
    }
}
