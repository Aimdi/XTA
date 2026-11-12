package com.aimdi.xtanative.ui.media

import kotlin.test.Test
import kotlin.test.assertEquals

class VideoPlacesTest {
    @Test
    fun aVideoLeftMidwayStartsWhereItWas() {
        val places = VideoPlaceMemory()
        places.keep("a.mp4", 12_000, 60_000, completed = false)
        assertEquals(12_000, places.startOf("a.mp4"))
        assertEquals(0, places.startOf("b.mp4"))
    }

    @Test
    fun aVideoHardlyBegunOrFinishedStartsOver() {
        val places = VideoPlaceMemory()
        places.keep("a.mp4", 12_000, 60_000, completed = false)
        places.keep("a.mp4", 500, 60_000, completed = false)
        assertEquals(0, places.startOf("a.mp4"))
        places.keep("b.mp4", 59_500, 60_000, completed = false)
        assertEquals(0, places.startOf("b.mp4"), "at its last second")
        places.keep("c.mp4", 30_000, 60_000, completed = true)
        assertEquals(0, places.startOf("c.mp4"))
        places.keep("d.mp4", 30_000, 0, completed = false)
        assertEquals(30_000, places.startOf("d.mp4"), "a stream of no known length")
    }

    @Test
    fun onlyTheLatestVideosAreRemembered() {
        val places = VideoPlaceMemory(limit = 2)
        places.keep("a.mp4", 5_000, 60_000, completed = false)
        places.keep("b.mp4", 5_000, 60_000, completed = false)
        places.startOf("a.mp4")
        places.keep("c.mp4", 5_000, 60_000, completed = false)
        assertEquals(5_000, places.startOf("a.mp4"), "read last, so kept")
        assertEquals(0, places.startOf("b.mp4"))
        assertEquals(5_000, places.startOf("c.mp4"))
    }
}
