package com.aimdi.xtanative.ui.plugins

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostCoversTest {
    @Test
    fun mediaUnderANetworksOwnCoverIsNotDrawnUntilItIsOpened() {
        assertFalse(PostCovers.drawsMedia(warned = true, revealed = false, ownCover = true))
        assertTrue(PostCovers.drawsMedia(warned = true, revealed = true, ownCover = true))
    }

    @Test
    fun mediaUnderTheSharedLineWaitsBlurredAndUnwarnedMediaIsAlwaysDrawn() {
        assertTrue(PostCovers.drawsMedia(warned = true, revealed = false, ownCover = false))
        assertTrue(PostCovers.drawsMedia(warned = false, revealed = false, ownCover = true))
    }
}
