package com.aimdi.xtanative.ui.media

import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XVideoVariant
import com.aimdi.xtanative.store.settings.MediaPrefs
import com.aimdi.xtanative.store.settings.MediaQuality
import com.aimdi.xtanative.store.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoSettingsTest {
    private val video = XMedia(
        XMedia.Kind.VIDEO,
        "https://pbs.twimg.com/ext_tw_video_thumb/1/pu/img/poster.jpg",
        1280,
        720,
        variants = listOf(
            XVideoVariant("https://video.twimg.com/ext_tw_video/1/pu/pl/list.m3u8", null, "application/x-mpegURL"),
            XVideoVariant("https://video.twimg.com/ext_tw_video/1/pu/vid/avc1/480x270/low.mp4", 256_000, "video/mp4"),
            XVideoVariant("https://video.twimg.com/ext_tw_video/1/pu/vid/avc1/1280x720/high.mp4", 2_176_000, "video/mp4"),
        ),
    )
    private val gif = XMedia(
        XMedia.Kind.GIF,
        "https://pbs.twimg.com/tweet_video_thumb/abc.jpg",
        480,
        270,
        variants = listOf(XVideoVariant("https://video.twimg.com/tweet_video/abc.mp4", 0, "video/mp4")),
    )

    @Test
    fun aVideoPlaysAsTheMediaSettingsSay() {
        val settings = Settings().with(MediaPrefs.videoQuality, MediaQuality.LARGE).with(MediaPrefs.loop, true)
        val source = VideoSource.of(video, settings)!!
        assertTrue(source.url.endsWith("high.mp4"))
        assertEquals(listOf("720p", "270p"), source.qualities.map { it.label })
        assertTrue(source.loop)
        assertFalse(source.gif)
        assertEquals(VideoMute.muted(true), source.muted)
        val unlooped = VideoSource.of(video, Settings().with(MediaPrefs.videoQuality, MediaQuality.THUMB))!!
        assertTrue(unlooped.url.endsWith("low.mp4"))
        assertFalse(unlooped.loop)
    }

    @Test
    fun aNetworksFilePlaysAsTheMediaSettingsSayWithoutAQualityMenu() {
        val looped = VideoSource.of("https://v.redd.it/x/DASH_720.mp4", gif = false, Settings().with(MediaPrefs.loop, true))
        assertEquals("https://v.redd.it/x/DASH_720.mp4", looped.url)
        assertTrue(looped.loop && !looped.gif && looped.qualities.isEmpty())
        val gif = VideoSource.of("https://v.redd.it/x/DASH_480.mp4", gif = true, Settings().with(MediaPrefs.loop, false).with(MediaPrefs.mute, false))
        assertTrue(gif.loop && gif.muted && gif.gif)
        assertFalse(VideoSource.of("https://cdn/a.mp4", gif = false, null).loop)
    }

    @Test
    fun aGifLoopsSilentlyWhateverTheSettings() {
        val source = VideoSource.of(gif, Settings().with(MediaPrefs.loop, false).with(MediaPrefs.mute, false))!!
        assertTrue(source.loop && source.muted && source.gif)
        assertEquals("https://video.twimg.com/tweet_video/abc.mp4", source.url)
        assertNull(VideoSource.of(video.copy(variants = emptyList()), null))
        assertTrue(VideoSource.looping("https://cdn/a.mp4").let { it.loop && it.muted && it.gif })
    }

    @Test
    fun theAppsMuteIsTheSettingUntilTheReaderChooses() {
        VideoMute.set(false)
        assertFalse(VideoMute.muted(setting = true))
        assertEquals(false, VideoMute.choice.value)
        VideoMute.set(true)
        assertTrue(VideoMute.muted(setting = false))
    }

    @Test
    fun aPlayerIsMadeAsTheMediaSettingsSay() {
        assertEquals(VideoOptions(prefetchSeconds = 0, directDecoding = false, backgroundPlay = true, mixWithOthers = false), VideoOptions.of(null))
        val chosen = Settings()
            .with(MediaPrefs.prefetchSeconds, 15)
            .with(MediaPrefs.directHardwareDecoding, true)
            .with(MediaPrefs.backgroundPlayback, false)
            .with(MediaPrefs.backgroundPlaybackWithOtherApps, true)
        assertEquals(VideoOptions(prefetchSeconds = 15, directDecoding = true, backgroundPlay = false, mixWithOthers = true), VideoOptions.of(chosen))
    }

    @Test
    fun aVideoReadsTenSecondsAheadUnlessThePrefetchSaysOtherwise() {
        val unset = VideoBuffering.of(0)
        assertEquals(10_000, unset.minMs)
        assertEquals(10_000, unset.maxMs)
        assertEquals(2_500, unset.playbackMs)
        assertEquals(5_000, unset.rebufferMs)
        assertEquals(20 * 1024 * 1024, unset.targetBytes)
        assertEquals(10_000, unset.backMs)
        val minute = VideoBuffering.of(60)
        assertEquals(60_000, minute.maxMs)
        assertEquals(60_000, minute.minMs)
        // A second ahead starts as soon as that second is there: Media3 wants no more to start than it keeps.
        val second = VideoBuffering.of(1)
        assertEquals(listOf(1_000, 1_000, 1_000, 1_000), listOf(second.minMs, second.maxMs, second.playbackMs, second.rebufferMs))
    }

    @Test
    fun aTileIsSeenWhileHalfOfItIsOnScreen() {
        assertTrue(VideoRules.mostlySeen(100f, 200))
        assertFalse(VideoRules.mostlySeen(99f, 200))
        assertFalse(VideoRules.mostlySeen(0f, 0))
    }

    @Test
    fun onlyAVideoThatIsHeardPlaysOnWhenTheAppIsLeft() {
        assertFalse(VideoRules.pausesWhenLeft(backgroundPlay = true, heard = true))
        assertTrue(VideoRules.pausesWhenLeft(backgroundPlay = true, heard = false))
        assertTrue(VideoRules.pausesWhenLeft(backgroundPlay = false, heard = true))
    }

    @Test
    fun fullScreenTurnsToTheVideosShape() {
        assertTrue(VideoRules.portrait(9f / 16f))
        assertFalse(VideoRules.portrait(1f))
        assertFalse(VideoRules.portrait(16f / 9f))
        assertEquals(1777, VideoRules.pictureInPictureRatio(16f / 9f))
        assertEquals(2390, VideoRules.pictureInPictureRatio(4f))
        assertEquals(420, VideoRules.pictureInPictureRatio(0.2f))
        assertEquals(1777, VideoRules.pictureInPictureRatio(Float.NaN))
        assertEquals(1777, VideoRules.pictureInPictureRatio(0f))
    }
}
