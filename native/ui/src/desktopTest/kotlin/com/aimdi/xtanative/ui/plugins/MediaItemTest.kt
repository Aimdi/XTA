package com.aimdi.xtanative.ui.plugins

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MediaItemTest {
    @Test
    fun aDescriptionIsShownTrimmedAndOnlyWhenThereIsOne() {
        assertEquals("A cat on a mat", MediaItem("https://x.test/a.jpg", alt = "  A cat on a mat\n").description)
        assertNull(MediaItem("https://x.test/a.jpg", alt = "   ").description)
        assertNull(MediaItem("https://x.test/a.jpg").description)
    }

    @Test
    fun theViewerSharesThePageNamedElseThePicture() {
        assertEquals("https://booru.test/posts/1", MediaItem("https://x.test/a.jpg", shareUrl = "https://booru.test/posts/1").sharedUrl)
        assertEquals("https://x.test/a.jpg", MediaItem("https://x.test/a.jpg", shareUrl = " ").sharedUrl)
        assertEquals("https://x.test/a.jpg", MediaItem("https://x.test/a.jpg").sharedUrl)
    }

    @Test
    fun aLongPressShowsTheDescriptionElseSavesAPhoto() {
        val described = MediaItem("https://x.test/a.jpg", alt = "A cat")
        val photo = MediaItem("https://x.test/b.jpg")
        val video = MediaItem("https://x.test/p.jpg", MediaItem.Kind.VIDEO, videoUrl = "https://x.test/v.mp4")
        assertEquals(TileHold.DESCRIPTION, tileHold(described, saves = true))
        assertEquals(TileHold.DESCRIPTION, tileHold(described, saves = false))
        assertEquals(TileHold.SAVE, tileHold(photo, saves = true))
        assertEquals(TileHold.NOTHING, tileHold(photo, saves = false), "where the screen saves nothing")
        assertEquals(TileHold.NOTHING, tileHold(video, saves = true), "a video saves from its player")
    }
}
