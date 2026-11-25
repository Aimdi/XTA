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

    @Test
    fun aLongVideosPlaceOutlivesTheAppAndAClipsDoesNot() {
        val before = VideoPlaceMemory()
        before.keep("talk.mp4", 600_000, 3_600_000, completed = false)
        before.keep("clip.mp4", 5_000, 20_000, completed = false)
        val after = VideoPlaceMemory()
        after.restore(before.lasting())
        assertEquals(600_000, after.startOf("talk.mp4"))
        assertEquals(0, after.startOf("clip.mp4"))
    }

    @Test
    fun whatThisRunLeftWinsOverTheFileAndAFinishedVideoLeavesIt() {
        val places = VideoPlaceMemory()
        places.keep("talk.mp4", 900_000, 3_600_000, completed = false)
        places.restore("""{"talk.mp4":600000,"film.mp4":1200000}""")
        assertEquals(900_000, places.startOf("talk.mp4"))
        assertEquals(1_200_000, places.startOf("film.mp4"))
        places.keep("film.mp4", 0, 7_200_000, completed = true)
        assertEquals("""{"talk.mp4":900000}""", places.lasting())
    }

    @Test
    fun aBrokenFileIsReadAsNothing() {
        val places = VideoPlaceMemory()
        places.restore("not json")
        places.restore("""{"a.mp4":"soon","b.mp4":12,"c.mp4":45000}""")
        assertEquals(0, places.startOf("a.mp4"))
        assertEquals(0, places.startOf("b.mp4"))
        assertEquals(45_000, places.startOf("c.mp4"))
    }
}

class SeekTimeTest {
    @Test
    fun theTimeRidesOverTheFingerAndStaysWithinTheBar() {
        assertEquals(450, SeekTime.left(0.5f, barWidth = 1000, labelWidth = 100))
        assertEquals(0, SeekTime.left(0f, barWidth = 1000, labelWidth = 100))
        assertEquals(900, SeekTime.left(1f, barWidth = 1000, labelWidth = 100))
        assertEquals(0, SeekTime.left(0.5f, barWidth = 0, labelWidth = 100), "before the bar is measured")
    }
}

class SharpPhotoTest {
    @Test
    fun aZoomedPhotoIsReadLargerUnlessDataIsSaved() {
        assertEquals("https://pbs.twimg.com/media/a?format=jpg&name=large", SharpPhoto.url("https://pbs.twimg.com/media/a?format=jpg&name=large", savesData = false))
        assertEquals(null, SharpPhoto.url("https://pbs.twimg.com/media/a?format=jpg&name=large", savesData = true))
    }
}
