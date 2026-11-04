package com.aimdi.xtanative.ui.media

import kotlin.test.Test
import kotlin.test.assertEquals

class MpvVideoRulesTest {
    @Test
    fun aFailedChangeOfQualityGoesBackToTheVariantBefore() {
        assertEquals(VideoFailure.Back("https://v/720.mp4", 12_000), VideoFailure.of("https://v/720.mp4" to 12_000L, gif = false, retries = 0))
        assertEquals(VideoFailure.Back("https://v/a.mp4", 0), VideoFailure.of("https://v/a.mp4" to 0L, gif = true, retries = 3), "even for a GIF")
    }

    @Test
    fun aGifOpensAgainAFewTimesThenFails() {
        assertEquals(VideoFailure.Again, VideoFailure.of(null, gif = true, retries = 0))
        assertEquals(VideoFailure.Again, VideoFailure.of(null, gif = true, retries = VideoFailure.GIF_RETRIES - 1))
        assertEquals(VideoFailure.Failed, VideoFailure.of(null, gif = true, retries = VideoFailure.GIF_RETRIES))
    }

    @Test
    fun aVideoThatFailsSaysSoForItsRetry() {
        assertEquals(VideoFailure.Failed, VideoFailure.of(null, gif = false, retries = 0))
    }

    @Test
    fun theControlsReadMpvButKeepWhatThePlayerDecides() {
        val chosen = VideoState(url = "https://v/720.mp4", qualities = listOf(VideoQuality("https://v/720.mp4", "720p")), muted = true, subtitles = true, active = true, started = true)
        val mpv = MpvState(load = 1, entry = 4, started = true, ready = true, paused = false, positionMs = 1_500, durationMs = 9_000, cachedMs = 4_000, speed = 1.5, subtitleTrack = 2, muted = false)
        assertEquals(
            chosen.copy(playing = true, positionMs = 1_500, durationMs = 9_000, bufferedMs = 4_000, speed = 1.5, hasSubtitles = true),
            chosen.after(mpv),
        )
        val ended = chosen.after(mpv.copy(ended = true))
        assertEquals(true, ended.completed)
        assertEquals(false, ended.playing)
        assertEquals(true, ended.active, "wanted playing still, as Media3's playWhenReady past the end")
    }
}
