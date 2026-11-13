package com.aimdi.xtanative.ui.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MpvOptionsTest {
    private fun List<Pair<String, String>>.of(key: String): String? = lastOrNull { it.first == key }?.second

    @Test
    fun aVideoIsDrawnByTheAppAndReadsAheadAsXtasDo() {
        val video = MpvOptions.video(prefetchSeconds = 0, loop = false, muted = true)
        assertEquals("libmpv", video.of("vo"))
        assertEquals("no", video.of("video-rotate"), "mpv's renderer aborts on a turned picture; the app turns it")
        assertEquals("10", video.of("cache-secs"))
        assertEquals("10", video.of("demuxer-readahead-secs"))
        assertEquals("${16 * 1024 * 1024}", video.of("demuxer-max-bytes"))
        assertEquals("${4 * 1024 * 1024}", video.of("demuxer-max-back-bytes"))
        assertEquals("no", video.of("loop-file"))
        assertEquals("yes", video.of("mute"))
        assertNull(video.of("vid"))
    }

    @Test
    fun theReadersPrefetchAndLoopCount() {
        val video = MpvOptions.video(prefetchSeconds = 30, loop = true, muted = false)
        assertEquals("30", video.of("cache-secs"))
        assertEquals("inf", video.of("loop-file"))
        assertEquals("no", video.of("mute"))
    }

    @Test
    fun aPodcastIsSoundAlone() {
        assertEquals("no", MpvOptions.AUDIO.of("vid"))
        assertEquals("no", MpvOptions.AUDIO.of("audio-display"))
        assertNull(MpvOptions.AUDIO.of("vo"))
    }

    @Test
    fun everyPlayerStartsPausedKeepsItsLastFrameAndNeedsNoSoundCard() {
        for (options in listOf(MpvOptions.AUDIO, MpvOptions.video(0, loop = false, muted = false))) {
            assertEquals("yes", options.of("pause"))
            assertEquals("yes", options.of("keep-open"))
            assertEquals("yes", options.of("audio-fallback-to-null"))
            assertEquals("no", options.of("ytdl"))
            assertEquals("no", options.of("config"))
            assertEquals("no", options.of("sub-visibility"))
            assertEquals(options.size, options.map { it.first }.toSet().size, "each option once")
        }
        assertTrue(MpvOptions.REQUIRED.containsAll(listOf("vo", "vid", "video-rotate")))
    }

    @Test
    fun aStartIsWholeSecondsWithoutADecimalPoint() {
        assertEquals("none", MpvOptions.start(0))
        assertEquals("none", MpvOptions.start(999))
        assertEquals("12", MpvOptions.start(12_750))
        assertEquals("3600", MpvOptions.start(3_600_000))
    }

    @Test
    fun aFrameIsDrawnAsLargeAsShownNeverLargerThanTheVideo() {
        assertEquals(FrameSize(640, 360), VideoFrameSizes.of(1280, 720, 0, 640, 640, crop = false))
        assertEquals(FrameSize(1138, 640), VideoFrameSizes.of(1280, 720, 0, 640, 640, crop = true))
        assertEquals(FrameSize(1280, 720), VideoFrameSizes.of(1280, 720, 0, 2560, 1440, crop = false), "never past the video's own size")
        assertEquals(FrameSize(405, 720), VideoFrameSizes.of(1080, 1920, 0, 1280, 720, crop = false), "a portrait video fitted in a wide window")
    }

    @Test
    fun aTurnedVideoIsDrawnUprightAtTheSizeItIsShown() {
        // A phone's portrait video, kept wide and turned a quarter: drawn wide, shown tall.
        assertEquals(FrameSize(640, 360), VideoFrameSizes.of(1280, 720, 90, 360, 640, crop = false))
        assertEquals(FrameSize(1280, 720), VideoFrameSizes.of(1280, 720, 270, 720, 1280, crop = false))
        assertEquals(FrameSize(640, 360), VideoFrameSizes.of(1280, 720, 180, 640, 360, crop = false), "half a turn keeps the shape")
        assertEquals(0.5f, VideoFrameSizes.shownScale(640, 360, 90, 180f, 400f, crop = false))
        assertEquals(1.0f, VideoFrameSizes.shownScale(640, 360, 270, 180f, 640f, crop = true))
        assertEquals(0.28125f, VideoFrameSizes.shownScale(640, 360, 0, 180f, 640f, crop = false))
    }

    @Test
    fun withoutASurfaceAFrameIsSmallAndWithoutASizeThereIsNone() {
        assertEquals(FrameSize(64, 36), VideoFrameSizes.of(1280, 720, 0, 0, 0, crop = false))
        assertNull(VideoFrameSizes.of(0, 720, 0, 640, 360, crop = false))
        assertEquals(FrameSize(10, 2), VideoFrameSizes.of(4000, 1, 0, 10, 10, crop = false), "two pixels at least either way")
    }

    @Test
    fun aRowIsAlignedForMpvsRenderer() {
        assertEquals(1280 * 4, FrameSize(1280, 720).stride)
        assertEquals(1664, FrameSize(405, 720).stride)
        assertEquals(64, FrameSize(2, 2).stride)
    }
}
