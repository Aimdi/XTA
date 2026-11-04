package com.aimdi.xtanative.ui.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PlayerTurnsTest {
    private class FakePlayer : PlayerCore {
        val opened = mutableListOf<String>()
        var stops = 0
        var released = false
        private val current = MutableStateFlow(VideoState())
        override val state: StateFlow<VideoState> = current

        override fun open(source: VideoSource) {
            opened += source.url
            current.value = VideoState(url = source.url, muted = source.muted)
        }

        override fun setActive(active: Boolean) {
            current.value = current.value.copy(active = active)
        }

        override fun stop() {
            stops++
            current.value = VideoState()
        }

        override fun release() {
            released = true
        }

        override fun setMuted(muted: Boolean) {
            current.value = current.value.copy(muted = muted)
        }

        override fun playOrPause() = Unit

        override fun seekTo(positionMs: Long) = Unit

        override fun setSpeed(speed: Double) = Unit

        override fun setQuality(url: String) = Unit

        override fun setSubtitles(shown: Boolean) = Unit

        override fun retry() = Unit

        override fun qualityFailureSeen() = Unit
    }

    private val made = mutableListOf<FakePlayer>()
    private val clip = VideoSource("clip.mp4", muted = false)
    private val loop = VideoSource.looping("loop.mp4")

    private fun turns(shared: Boolean, backgroundPlay: Boolean = true) = PlayerTurns({ FakePlayer().also { made += it } }, backgroundPlay, shared)

    @Test
    fun nothingIsMadeUntilATileWantsItAndThenTheHolderPlaysInIt() {
        val feed = turns(shared = true)
        assertNull(feed.playerOf("gif"))
        assertTrue(made.isEmpty())
        feed.want("gif", loop)
        feed.ask("video", clip)
        val player = made.single()
        assertSame(player, feed.playerOf("video"))
        assertNull(feed.playerOf("gif"))
        assertEquals(listOf("loop.mp4", "clip.mp4"), player.opened)
        assertTrue(player.state.value.active)
        feed.drop("video")
        assertSame(player, feed.playerOf("gif"))
        assertEquals("loop.mp4", player.opened.last())
    }

    @Test
    fun aFeedKeepsItsPlayerForTheNextTileAndATilesOwnLetsItGo() {
        val feed = turns(shared = true)
        feed.ask("video", clip)
        feed.drop("video")
        assertEquals(1, made.single().stops)
        assertFalse(made.single().released)
        feed.ask("next", clip)
        assertEquals(1, made.size)
        feed.release()
        assertTrue(made.single().released)

        val own = turns(shared = false)
        own.ask("video", clip)
        own.drop("video")
        assertTrue(made[1].released)
        own.ask("video", clip)
        assertEquals(3, made.size)
    }

    @Test
    fun fullScreenKeepsThePlayerThroughADropAndARelease() {
        val feed = turns(shared = true)
        feed.ask("video", clip)
        feed.pin("video")
        feed.ask("other", clip)
        feed.drop("video")
        feed.release()
        val player = made.single()
        assertSame(player, feed.playerOf("video"))
        assertFalse(player.released)
        feed.unpin("video")
        assertTrue(player.released)
        assertNull(feed.playerOf("video"))
    }

    @Test
    fun leavingTheFeedStopsWhatIsNotHeardAndReturningPlaysItAgain() {
        val feed = turns(shared = true)
        feed.want("gif", loop)
        val player = made.single()
        feed.pause()
        assertFalse(player.state.value.active)
        feed.resume()
        assertTrue(player.state.value.active)
        feed.ask("video", clip)
        feed.pause()
        // Heard, with Background play: it plays on.
        assertTrue(player.state.value.active)
        feed.resume()
        player.setMuted(true)
        feed.pause()
        assertFalse(player.state.value.active)
        feed.resume()
        assertTrue(player.state.value.active)
    }

    @Test
    fun withoutBackgroundPlayEvenAVideoHeardStopsAndFullScreenNeverDoes() {
        val feed = turns(shared = true, backgroundPlay = false)
        feed.ask("video", clip)
        val player = made.single()
        feed.pause()
        assertFalse(player.state.value.active)
        feed.ask("next", clip)
        // Opened while the feed is left: it waits.
        assertFalse(player.state.value.active)
        feed.resume()
        assertFalse(player.state.value.active)
        player.setActive(true)
        feed.pin("next")
        feed.pause()
        assertTrue(player.state.value.active)
    }
}
