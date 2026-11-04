package com.aimdi.xtanative.ui.media

import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XVideoVariant
import com.aimdi.xtanative.store.settings.MediaPrefs
import com.aimdi.xtanative.store.settings.MediaQuality
import com.aimdi.xtanative.store.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ViewerVideoTest {
    private val video = XMedia(
        XMedia.Kind.VIDEO,
        "https://pbs.twimg.com/ext_tw_video_thumb/1/pu/img/poster.jpg",
        1280,
        720,
        variants = listOf(
            XVideoVariant("https://video.twimg.com/ext_tw_video/1/pu/vid/avc1/480x270/low.mp4", 256_000, "video/mp4"),
            XVideoVariant("https://video.twimg.com/ext_tw_video/1/pu/vid/avc1/1280x720/high.mp4", 2_176_000, "video/mp4"),
            XVideoVariant("https://video.twimg.com/ext_tw_video/1/pu/vid/avc1/640x360/mid.mp4", 832_000, "video/mp4"),
        ),
    )

    @Test
    fun theQualitySettingPicksTheVariantAndTheRestStayToPickFrom() {
        val large = ViewerVideo.of(video, Settings().with(MediaPrefs.videoQuality, MediaQuality.LARGE))!!
        assertTrue(large.url.endsWith("high.mp4"))
        assertEquals(listOf("720p", "360p", "270p"), large.qualities.map { it.label })
        assertTrue(ViewerVideo.of(video, Settings().with(MediaPrefs.videoQuality, MediaQuality.THUMB))!!.url.endsWith("low.mp4"))
        assertTrue(ViewerVideo.of(video, null)!!.url.endsWith("mid.mp4"), "medium until the settings are read")
    }

    @Test
    fun theLoopAndPrefetchSettingsCount() {
        val looped = ViewerVideo.of(video, Settings().with(MediaPrefs.loop, true).with(MediaPrefs.prefetchSeconds, 15))!!
        assertTrue(looped.loop)
        assertEquals(15, looped.prefetchSeconds)
        val plain = ViewerVideo.of(video, Settings())!!
        assertEquals(false, plain.loop)
        assertEquals(0, plain.prefetchSeconds)
    }

    @Test
    fun aGifLoopsSilentWhateverTheSettingsSay() {
        val gif = ViewerVideo.of(video.copy(kind = XMedia.Kind.GIF), Settings().with(MediaPrefs.loop, false).with(MediaPrefs.mute, false))!!
        assertTrue(gif.gif && gif.loop && gif.muted)
        val file = ViewerVideo.of("https://v.redd.it/x/DASH_720.mp4", emptyList(), gif = true, settings = null)
        assertTrue(file.loop && file.muted && file.qualities.isEmpty())
    }

    @Test
    fun withNothingToPlayThereIsNoVideo() {
        assertNull(ViewerVideo.of(video.copy(variants = emptyList()), Settings()))
    }
}
