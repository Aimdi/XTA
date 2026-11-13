package com.aimdi.xtanative.ui.plugins

import com.aimdi.xtanative.ui.media.VideoQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MediaQualitiesTest {
    private val qualities = listOf(VideoQuality("https://cdn/1080.mp4", "1080p"), VideoQuality("https://cdn/720.mp4", "720p"))
    private val item = MediaItem(imageUrl = "https://cdn/poster.jpg", kind = MediaItem.Kind.VIDEO, videoUrl = "https://cdn/1080.mp4")

    @Test
    fun aVideoPlaysTheQualityChosenAmongItsOwn() {
        assertEquals("https://cdn/720.mp4", playedUrl(item, qualities, "https://cdn/720.mp4"))
        assertEquals("https://cdn/1080.mp4", playedUrl(item, qualities, "https://elsewhere/x.mp4"), "a file it does not offer is not played")
        assertEquals("https://cdn/1080.mp4", playedUrl(item, emptyList(), null))
    }

    @Test
    fun theMenuNamesTheQualityPlaying() {
        assertEquals("720p", qualityOf("https://cdn/720.mp4", qualities)?.label)
        assertNull(qualityOf(null, qualities))
    }
}
