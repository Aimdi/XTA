package com.aimdi.xtanative.ui.speech

import com.aimdi.xtanative.services.speech.ClipPlayer
import com.aimdi.xtanative.services.speech.UtteranceOutcome
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.concurrent.thread
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Plays synthesised clips on a desktop without libmpv, which has no player
 * of its own in the app then (videos open in the system's): sherpa-onnx's
 * WAV files streamed to the default output with Java Sound, one at a time,
 * each on a thread of its own. A clip that has not started within
 * [startTimeoutMs] counts as failed, as on Android (clip_player.dart).
 */
class SoundClipPlayer(private val startTimeoutMs: Long = 10_000) : ClipPlayer {
    private val current = AtomicReference<Clip?>(null)

    override suspend fun play(path: String): UtteranceOutcome {
        stop()
        val clip = Clip(File(path)).also { current.set(it) }
        thread(name = "voice-clip", isDaemon = true) { clip.ended.complete(clip.run()) }
        if (withTimeoutOrNull(startTimeoutMs) { clip.started.await() } == null) {
            clip.stop()
            return UtteranceOutcome.FAILED
        }
        return clip.ended.await().also { current.compareAndSet(clip, null) }
    }

    override suspend fun stop() {
        current.getAndSet(null)?.stop()
    }

    private class Clip(private val file: File) {
        val started = CompletableDeferred<Unit>()
        val ended = CompletableDeferred<UtteranceOutcome>()

        @Volatile
        private var stopped = false

        @Volatile
        private var line: SourceDataLine? = null

        fun run(): UtteranceOutcome = try {
            AudioSystem.getAudioInputStream(file).use(::stream)
        } catch (_: Exception) {
            if (stopped) UtteranceOutcome.CANCELLED else UtteranceOutcome.FAILED
        } finally {
            started.complete(Unit)
        }

        private fun stream(input: AudioInputStream): UtteranceOutcome {
            val opened = AudioSystem.getSourceDataLine(input.format).also { line = it }
            try {
                opened.open(input.format)
                opened.start()
                started.complete(Unit)
                val frame = input.format.frameSize.coerceAtLeast(1)
                val buffer = ByteArray((opened.bufferSize / frame).coerceAtLeast(1) * frame)
                while (!stopped) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    opened.write(buffer, 0, read - read % frame)
                }
                if (!stopped) opened.drain()
                return if (stopped) UtteranceOutcome.CANCELLED else UtteranceOutcome.COMPLETED
            } finally {
                opened.close()
            }
        }

        /** Cuts the clip short: the line stops, which ends the write or drain under way. */
        fun stop() {
            stopped = true
            line?.let { opened ->
                runCatching { opened.stop() }
                runCatching { opened.flush() }
            }
            ended.complete(UtteranceOutcome.CANCELLED)
        }
    }
}
