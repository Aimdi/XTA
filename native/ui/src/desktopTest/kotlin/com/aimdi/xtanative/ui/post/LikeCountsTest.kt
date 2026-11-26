package com.aimdi.xtanative.ui.post

import com.aimdi.xtanative.core.format.Formats
import kotlin.test.Test
import kotlin.test.assertEquals

class LikeCountsTest {
    @Test
    fun aLikeOnThisDeviceOnlyCountsOneMoreThanX() {
        assertEquals(43, LikeCounts.shown(42, likedHere = true, likedOnX = false))
        assertEquals(1, LikeCounts.shown(0, likedHere = true, likedOnX = false), "a post nobody liked on X reads 1 once liked here")
    }

    @Test
    fun unlikedItDropsBackToXsCount() {
        assertEquals(42, LikeCounts.shown(42, likedHere = false, likedOnX = false))
        assertEquals(42, LikeCounts.shown(42, likedHere = false, likedOnX = true), "X's own like stays in its count")
    }

    @Test
    fun aPostXSaysTheReaderLikedIsNotCountedTwice() {
        assertEquals(42, LikeCounts.shown(42, likedHere = true, likedOnX = true))
    }

    @Test
    fun theOneMoreIsWrittenCompactlyAsXsCountIs() {
        assertEquals("1K", Formats.compactCount(LikeCounts.shown(999, likedHere = true, likedOnX = false), '.', null))
    }
}
