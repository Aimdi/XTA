package com.aimdi.xtanative.services.speech

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Turns text into a playable audio file with one loaded voice (sherpa-onnx on each platform). */
interface VoiceSynthesizer {
    /** Speaks [text] at [speed] (1 is the voice's own pace) into an audio file, and answers its path. */
    suspend fun synthesize(text: String, speed: Double): String

    suspend fun dispose()
}

/** Plays one synthesised clip at a time. */
interface ClipPlayer {
    /** Plays the file at [path]; returns when it ends, fails or is stopped. */
    suspend fun play(path: String): UtteranceOutcome

    suspend fun stop()
}

/**
 * Reads aloud with a voice downloaded to the device (offline_speech_engine.dart).
 * It offers to read only while offline voices are on and an installed voice
 * speaks the text's language (the app's when the text's is unknown);
 * otherwise, or when the voice fails to load, [prepare] says no and the
 * platform's engine reads instead.
 *
 * Synthesis runs ahead of playback: while one chunk plays, the next one
 * ([upcoming]) is being synthesised, so sentences follow without a gap.
 * Clips are handed to [discard] once played or dropped. [scope] runs the
 * synthesis ahead.
 *
 * A loaded voice holds tens of megabytes outside the heap, so it is let go
 * after [idleUnloadMillis] without reading (null keeps it), and at once
 * through [release] when offline voices are turned off.
 */
class OfflineSpeechEngine(
    private val voiceFor: suspend (language: String?) -> InstalledVoice?,
    private val enabled: () -> Boolean,
    private val load: suspend (InstalledVoice) -> VoiceSynthesizer,
    private val player: ClipPlayer,
    private val discard: suspend (path: String) -> Unit,
    private val scope: CoroutineScope,
    private val appLanguage: () -> String? = { null },
    private val idleUnloadMillis: Long? = IDLE_UNLOAD_MILLIS,
) : SpeechEngine, SpeechLookahead {
    private var voiceId: String? = null

    /** Loading and letting go of the voice, one at a time: the idle unload runs on [scope]. */
    private val loading = Mutex()
    private val resting = Any()
    @Volatile private var idle: Job? = null

    /** The loading voice. A failure is kept in the result, not thrown into [scope], which it would cancel. */
    private var synthesizer: Deferred<Result<VoiceSynthesizer>>? = null
    private var speed = 1.0

    /** Clips asked for but not played yet, in reading order. */
    private val queue = ArrayDeque<Pair<String, Deferred<String?>>>()

    /** Bumped by [stop], so a clip that finishes synthesising afterwards is not played. */
    private val epoch = AtomicInteger()

    /** A sentence or two: each is synthesised whole before it plays, so a long one is a long silence. */
    override val maxChunkChars: Int = 300

    /** One sentence to begin with, so the reading starts after a moment's synthesis, not twenty seconds' worth. */
    override val firstChunkChars: Int = 100

    override suspend fun prepare(choice: TtsChoice, textLanguage: String?): Boolean {
        if (!enabled()) {
            release()
            return false
        }
        val installed = voiceFor(textLanguage ?: appLanguage()) ?: return false
        speed = Tts.sherpaSpeed(choice.rate)
        stopResting()
        return try {
            loading.withLock { loadVoice(installed) }
            true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            loading.withLock { unload() }
            false
        } finally {
            restAfterwards()
        }
    }

    /** Lets go of the loaded voice now; the next reading loads it again. */
    suspend fun release() {
        stopResting()
        loading.withLock { unload() }
    }

    /** Starts the wait after which an unused voice is let go; any reading in between cancels it. */
    private fun restAfterwards() {
        val millis = idleUnloadMillis ?: return
        synchronized(resting) {
            stopResting()
            idle = scope.launch {
                delay(millis)
                loading.withLock { unload() }
            }
        }
    }

    private fun stopResting() = synchronized(resting) { idle?.cancel() }

    private suspend fun loadVoice(installed: InstalledVoice) {
        if (voiceId != installed.voice.id) {
            unload()
            voiceId = installed.voice.id
            synthesizer = scope.async { runCatching { load(installed) } }
        }
        synthesizer?.await()?.getOrThrow()
    }

    private suspend fun unload() {
        val loaded = synthesizer
        voiceId = null
        synthesizer = null
        withContext(NonCancellable) { loaded?.await()?.getOrNull()?.let { runCatching { it.dispose() } } }
    }

    override fun upcoming(chunk: String) {
        if (synthesizer == null) return
        synchronized(queue) { queue.addLast(chunk to synthesizeAhead(chunk)) }
    }

    override suspend fun say(chunk: String): UtteranceOutcome {
        if (synthesizer == null) return UtteranceOutcome.FAILED
        stopResting()
        try {
            val mine = epoch.get()
            val path = clipFor(chunk).await()
            if (mine != epoch.get()) {
                path?.let { discard(it) }
                return UtteranceOutcome.CANCELLED
            }
            path ?: return UtteranceOutcome.FAILED
            return try {
                player.play(path)
            } finally {
                scope.launch { runCatching { discard(path) } }
            }
        } finally {
            restAfterwards()
        }
    }

    /** The clip for [chunk]: the one on its way if it was announced, else a new one. Clips announced for something else are dropped. */
    private fun clipFor(chunk: String): Deferred<String?> {
        synchronized(queue) {
            while (queue.isNotEmpty()) {
                val (text, clip) = queue.removeFirst()
                if (text == chunk) return clip
                drop(clip)
            }
        }
        return synthesizeAhead(chunk)
    }

    private fun synthesizeAhead(chunk: String): Deferred<String?> = scope.async {
        try {
            synthesizer?.await()?.getOrNull()?.synthesize(chunk, speed)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    private fun drop(clip: Deferred<String?>) {
        scope.launch { runCatching { clip.await()?.let { discard(it) } } }
    }

    override suspend fun stop() {
        epoch.incrementAndGet()
        synchronized(queue) {
            while (queue.isNotEmpty()) drop(queue.removeFirst().second)
        }
        player.stop()
        restAfterwards()
    }

    companion object {
        /** Two minutes: long enough to pause between posts, short enough not to keep a voice loaded in the tray all day. */
        const val IDLE_UNLOAD_MILLIS = 120_000L
    }
}
