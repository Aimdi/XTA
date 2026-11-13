package com.aimdi.xtanative.ui.speech

import com.aimdi.xtanative.services.speech.UtteranceOutcome
import com.aimdi.xtanative.ui.media.ContinuityPlayer
import com.aimdi.xtanative.ui.media.PlaybackFrame
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** clip_player.dart's tests (offline_speech_engine_test.dart), with its fake player. */
class ContinuityClipPlayerTest {
    private class Player(start: PlaybackFrame = PlaybackFrame()) : ContinuityPlayer {
        @Volatile
        override var frame: PlaybackFrame = start
        override val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
        val commands: MutableList<String> = Collections.synchronizedList(ArrayList())
        var beforeOpen: suspend () -> Unit = {}

        fun emit(playing: Boolean? = null, completed: Boolean? = null, failed: Boolean? = null) {
            frame = frame.copy(playing = playing ?: frame.playing, completed = completed ?: frame.completed, failed = failed ?: frame.failed)
            changes.tryEmit(Unit)
        }

        override suspend fun open(url: String, startMs: Long) {
            commands += "open:$url"
            beforeOpen()
            emit(playing = false, completed = false, failed = false)
        }

        override suspend fun play() {
            commands += "play"
            emit(playing = true)
        }

        override suspend fun pause() {
            commands += "pause"
            emit(playing = false)
        }

        override suspend fun stop() {
            commands += "stop"
            emit(playing = false)
        }

        override suspend fun seek(positionMs: Long) = Unit

        override suspend fun setVolume(value: Double) = Unit

        override suspend fun setRate(value: Double) = Unit

        override suspend fun dispose() = Unit
    }

    private fun timed(block: suspend CoroutineScope.() -> Unit): Unit = runBlocking(Dispatchers.Default) { withTimeout(10_000) { block() } }

    private suspend fun until(condition: () -> Boolean) {
        while (!condition()) delay(2)
    }

    @Test
    fun completesWhenTheClipHasPlayedToTheEnd(): Unit = timed {
        val player = Player()
        val clips = ContinuityClipPlayer(create = { player })
        val playing = async(start = CoroutineStart.UNDISPATCHED) { clips.play("/tmp/a.wav") }
        until { "play" in player.commands }
        player.emit(completed = true, playing = false)

        assertEquals(UtteranceOutcome.COMPLETED, playing.await())
        assertEquals(listOf("open:/tmp/a.wav", "play"), player.commands)
    }

    @Test
    fun aStaleCompletedBeforePlaybackStartsIsIgnored(): Unit = timed {
        val player = Player(PlaybackFrame(completed = true))
        player.beforeOpen = { player.emit(completed = true) }
        val clips = ContinuityClipPlayer(create = { player })
        val playing = async(start = CoroutineStart.UNDISPATCHED) { clips.play("/tmp/b.wav") }
        until { "play" in player.commands }
        delay(50)
        assertFalse(playing.isCompleted)

        clips.stop()
        assertEquals(UtteranceOutcome.CANCELLED, playing.await())
        assertEquals("stop", player.commands.last())
    }

    @Test
    fun aPlayerErrorFailsTheClip(): Unit = timed {
        val player = Player()
        val clips = ContinuityClipPlayer(create = { player })
        val playing = async(start = CoroutineStart.UNDISPATCHED) { clips.play("/tmp/c.wav") }
        until { "play" in player.commands }
        player.emit(failed = true)
        assertEquals(UtteranceOutcome.FAILED, playing.await())
    }

    @Test
    fun aClipThatNeverStartsFailsInsteadOfHanging(): Unit = timed {
        val player = Player().apply { beforeOpen = { awaitCancellation() } }
        val clips = ContinuityClipPlayer(create = { player }, startTimeoutMs = 20)
        assertEquals(UtteranceOutcome.FAILED, clips.play("/tmp/d.wav"))
    }

    @Test
    fun theNextClipCutsThePreviousOneShortAndThePlayerIsMadeOnce(): Unit = timed {
        var made = 0
        val player = Player()
        val clips = ContinuityClipPlayer(create = { player.also { made++ } })
        val first = async(start = CoroutineStart.UNDISPATCHED) { clips.play("/tmp/e.wav") }
        until { "play" in player.commands }
        val second = async(start = CoroutineStart.UNDISPATCHED) { clips.play("/tmp/f.wav") }

        assertEquals(UtteranceOutcome.CANCELLED, first.await())
        until { "open:/tmp/f.wav" in player.commands && player.commands.count { it == "play" } == 2 }
        player.emit(completed = true, playing = false)
        assertEquals(UtteranceOutcome.COMPLETED, second.await())
        assertEquals(1, made)
    }
}
