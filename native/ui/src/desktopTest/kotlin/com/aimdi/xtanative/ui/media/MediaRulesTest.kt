package com.aimdi.xtanative.ui.media

import com.aimdi.xtanative.core.x.model.PhotoSize
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XVideoVariant
import com.aimdi.xtanative.store.settings.MediaQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaRulesTest {
    private val video = XMedia(
        XMedia.Kind.VIDEO,
        "https://pbs.twimg.com/ext_tw_video_thumb/1/pu/img/poster.jpg",
        1280,
        720,
        variants = listOf(
            XVideoVariant("https://video.twimg.com/ext_tw_video/1/pu/pl/list.m3u8?tag=12", null, "application/x-mpegURL"),
            XVideoVariant("https://video.twimg.com/ext_tw_video/1/pu/vid/avc1/480x270/low.mp4?tag=12", 256_000, "video/mp4"),
            XVideoVariant("https://video.twimg.com/ext_tw_video/1/pu/vid/avc1/1280x720/high.mp4?tag=12", 2_176_000, "video/mp4"),
            XVideoVariant("https://video.twimg.com/ext_tw_video/1/pu/vid/avc1/640x360/mid.mp4?tag=12", 832_000, "video/mp4"),
        ),
    )

    @Test
    fun qualitiesAreTheMp4sHighestFirstNamedByTheirHeight() {
        assertEquals(listOf("720p", "360p", "270p"), VideoRules.qualities(video).map { it.label })
        assertEquals("2.2 Mbps", VideoRules.label("https://video.twimg.com/amplify/a.mp4", 2_176_000))
        assertEquals("—", VideoRules.label("https://video.twimg.com/a.mp4", null))
        assertTrue(VideoRules.streamUrl(video)!!.contains("high.mp4"))
        val live = video.copy(variants = video.variants.take(1))
        assertTrue(VideoRules.streamUrl(live)!!.endsWith("list.m3u8?tag=12"))
    }

    @Test
    fun theQualitySettingPicksAVariant() {
        val qualities = VideoRules.qualities(video)
        assertEquals(qualities[0].url, VideoRules.defaultUrl(qualities, null, MediaQuality.LARGE))
        assertEquals(qualities[1].url, VideoRules.defaultUrl(qualities, null, MediaQuality.MEDIUM))
        assertEquals(qualities[2].url, VideoRules.defaultUrl(qualities, null, MediaQuality.SMALL))
        assertEquals(qualities[2].url, VideoRules.defaultUrl(qualities, null, MediaQuality.THUMB))
        assertEquals("fallback", VideoRules.defaultUrl(emptyList(), "fallback", MediaQuality.LARGE))
    }

    @Test
    fun timesAndSeeksStayWithinTheVideo() {
        assertEquals("0:05", VideoRules.timeText(5_400))
        assertEquals("12:00", VideoRules.timeText(720_000))
        assertEquals("1:02:05", VideoRules.timeText(3_725_000))
        assertEquals("0:00", VideoRules.timeText(-1))
        assertEquals(0, VideoRules.seekTarget(3_000, -10_000, 60_000))
        assertEquals(60_000, VideoRules.seekTarget(55_000, 10_000, 60_000))
        assertEquals(25_000, VideoRules.seekTarget(15_000, 10_000, 60_000))
        assertEquals(0.5f, VideoRules.fraction(30_000, 60_000))
        assertEquals(0f, VideoRules.fraction(30_000, 0))
        assertEquals(listOf("2.0", "1.75", "1.5", "1.25", "1.0", "0.75", "0.5", "0.25"), VideoRules.SPEEDS.reversed().map(VideoRules::speedText))
    }

    @Test
    fun aDownloadSavesTheVariantWatchedOrTheBest() {
        val qualities = VideoRules.qualities(video)
        assertEquals(qualities[1].url, MediaFiles.downloadUrl(video, playingUrl = qualities[1].url))
        assertEquals(qualities[0].url, MediaFiles.downloadUrl(video, playingUrl = video.variants[0].url))
        assertEquals(qualities[0].url, MediaFiles.downloadUrl(video))
        val gif = video.copy(kind = XMedia.Kind.GIF, variants = listOf(XVideoVariant("https://video.twimg.com/tweet_video/abc.mp4", 0, "video/mp4")))
        assertEquals("https://video.twimg.com/tweet_video/abc.mp4", MediaFiles.downloadUrl(gif))
        assertNull(MediaFiles.downloadUrl(video.copy(variants = video.variants.take(1))))
        val photo = XMedia(XMedia.Kind.PHOTO, "https://pbs.twimg.com/media/Fabc.jpg", 100, 100)
        assertEquals("https://pbs.twimg.com/media/Fabc?format=jpg&name=orig", MediaFiles.downloadUrl(photo))
    }

    @Test
    fun aSavedFileIsNamedForItsAuthor() {
        assertEquals("maya-Fabc.jpg", MediaFiles.fileName("maya", "https://pbs.twimg.com/media/Fabc?format=jpg&name=orig"))
        assertEquals("maya-high.mp4", MediaFiles.fileName("maya", "https://video.twimg.com/ext_tw_video/1/pu/vid/avc1/1280x720/high.mp4?tag=12"))
        assertEquals("abc.mp4", MediaFiles.fileName("", "https://video.twimg.com/tweet_video/abc.mp4"))
        val save = MediaFiles.save(video, "42", "maya")!!
        assertEquals("maya-high.mp4", save.fileName)
        assertEquals("42", save.postId)
        assertEquals(XMedia.Kind.VIDEO, save.kind)
    }

    @Test
    fun theViewerShowsPhotosAtTheChosenSize() {
        assertEquals(PhotoSize.MEDIUM, MediaFiles.viewerSize(MediaQuality.MEDIUM))
        assertEquals(PhotoSize.LARGE, MediaFiles.viewerSize(MediaQuality.LARGE))
        assertEquals(PhotoSize.THUMB, MediaFiles.viewerSize(MediaQuality.THUMB))
    }

    @Test
    fun aDoubleTapZoomsAroundThePointTapped() {
        assertEquals(ViewerZoom.MAX, ViewerZoom.doubleTapScale(1f))
        assertEquals(ViewerZoom.MIN, ViewerZoom.doubleTapScale(2.5f))
        // Tapping 100 px right of the middle of a 1000 × 800 page: that point stays under the finger.
        val shift = ViewerZoom.shiftFor(Shift(100f, 0f), from = 1f, fromShift = Shift.ZERO, to = 4f, width = 1000f, height = 800f)
        assertEquals(Shift(-300f, 0f), shift)
        assertEquals(Shift.ZERO, ViewerZoom.shiftFor(Shift(100f, 0f), from = 4f, fromShift = shift, to = 1f, width = 1000f, height = 800f))
        // A shift stops where the photo still covers the page.
        assertEquals(Shift(-1500f, 900f), ViewerZoom.bounds(Shift(-2000f, 900f), 4f, 1000f, 800f))
        assertEquals(Shift.ZERO, ViewerZoom.bounds(Shift(-2000f, 900f), 1f, 1000f, 800f))
        assertEquals(Shift(-1500f, -1200f), ViewerZoom.shiftFor(Shift(500f, 400f), 1f, Shift.ZERO, 4f, 1000f, 800f))
        assertEquals(0.9f, ViewerZoom.pinched(1f, 0.5f))
        assertEquals(4f, ViewerZoom.pinched(3f, 2f))
        assertEquals(1f, ViewerZoom.settled(0.9f))
        assertFalse(ViewerZoom.zoomed(1f))
        assertTrue(ViewerZoom.zoomed(1.5f))
    }

    @Test
    fun aDragUpOrDownClosesTheViewerWhenItGoesFarOrFast() {
        assertTrue(SlideToClose.startsSlide(dx = 2f, dy = 20f, slop = 8f))
        assertFalse(SlideToClose.startsSlide(dx = 18f, dy = 20f, slop = 8f))
        assertTrue(SlideToClose.startsPage(dx = 20f, dy = 4f, slop = 8f))
        assertTrue(SlideToClose.closes(drag = 200f, height = 1000f, velocity = 0f))
        assertFalse(SlideToClose.closes(drag = 100f, height = 1000f, velocity = 0f))
        assertTrue(SlideToClose.closes(drag = 10f, height = 1000f, velocity = -2_500f))
        assertEquals(1f, SlideToClose.backdrop(0f, 1000f))
        assertEquals(0.5f, SlideToClose.backdrop(300f, 1000f))
        assertEquals(0.2f, SlideToClose.backdrop(5000f, 1000f))
    }
}
