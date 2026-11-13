package com.aimdi.xtanative.services.speech

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/** What is being read aloud, if anything: the bar names [title] while [speaking]. */
data class SpeechPlayback(val title: String? = null, val speaking: Boolean = false) {
    companion object {
        val IDLE = SpeechPlayback()
    }
}

/**
 * Something that reads text aloud a chunk at a time: the platform's engine,
 * or a voice downloaded to the device. The reader only talks to this, so it
 * neither knows nor cares which one is speaking.
 */
interface SpeechEngine {
    /** The longest piece of text [say] is handed at once. */
    val maxChunkChars: Int

    /** Gets ready to read text in [textLanguage] (`de`, `en`, … or null when unknown); false when it cannot speak at all. */
    suspend fun prepare(choice: TtsChoice, textLanguage: String?): Boolean

    /** Speaks [chunk] and returns when it is over: finished, failed, or cut short by [stop]. Never waits forever. */
    suspend fun say(chunk: String): UtteranceOutcome

    /** Stops speaking; a [say] under way returns cancelled. */
    suspend fun stop()
}

/**
 * An engine that does better when told what comes next: a downloaded voice
 * synthesises audio before it can play it, so knowing the next chunk while
 * this one plays lets sentences follow without a pause.
 */
interface SpeechLookahead {
    fun upcoming(chunk: String)
}

/** The platform around a reading: the audio session it speaks in, and the stop control on the lock screen. */
interface SpeechSession {
    /** Readies the audio for speech without taking exclusive focus, which would mute a voice speaking from another process. */
    suspend fun prepareAudio() {}

    /** Shows [title] with a stop control that runs [stop]; playing is not claimed, as that takes exclusive focus too. */
    fun bind(title: String, stop: suspend () -> Unit) {}

    fun clear() {}
}

/**
 * Reading aloud, owned by the app rather than the screen that started it
 * (speech_store.dart): leave the article and it keeps reading until it ends
 * or is stopped. [engines] are asked best first; the platform's engine goes
 * last, as what is left when nothing better is ready.
 *
 * Long text is spoken in chunks, and every reading bumps a generation that
 * the chunk loop checks between chunks: a loop that belongs to an abandoned
 * reading stops instead of talking over its successor.
 */
class SpeechReader(
    private val engines: List<SpeechEngine>,
    private val session: SpeechSession = object : SpeechSession {},
    /** How long an engine may take to get ready before it counts as broken. */
    private val prepareTimeoutMillis: Long = 20_000,
) {
    private val playback = MutableStateFlow(SpeechPlayback.IDLE)
    val state: StateFlow<SpeechPlayback> = playback.asStateFlow()

    private val generation = AtomicInteger()

    /**
     * Reads [text] aloud, replacing whatever was being read. False when there
     * was nothing to say or no engine would speak, so the caller can point
     * the reader to the voice settings; returns when the reading ends.
     */
    suspend fun speak(title: String, text: String, choice: TtsChoice): Boolean {
        stop()
        if (text.isBlank()) return false
        val mine = generation.get()
        val language = detectTextLanguage(text)
        val engine = prepare(choice, language)
        if (mine != generation.get()) return true
        engine ?: return false
        session.prepareAudio()
        session.bind(title, ::stop)
        playback.value = SpeechPlayback(title, speaking = true)
        val read = readChunks(engine, chunkForSpeech(text, engine.maxChunkChars), mine) { engine.prepare(choice, language) }
        if (mine == generation.get()) finished()
        return read
    }

    suspend fun stop() {
        generation.incrementAndGet()
        session.clear()
        if (playback.value.speaking) playback.value = SpeechPlayback.IDLE
        engines.forEach { it.stop() }
    }

    private fun finished() {
        session.clear()
        if (playback.value.speaking) playback.value = SpeechPlayback.IDLE
    }

    /** The first engine ready to read; one that throws or takes too long counts as not ready. */
    private suspend fun prepare(choice: TtsChoice, language: String?): SpeechEngine? = engines.firstOrNull { engine ->
        try {
            withTimeoutOrNull(prepareTimeoutMillis) { engine.prepare(choice, language) } == true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Speaks [chunks] in order; false when the engine failed. A first chunk
     * that fails is tried once more after [retry]: engines sometimes refuse
     * the first utterance after binding. A reading stopped or replaced is
     * not a failure.
     */
    private suspend fun readChunks(engine: SpeechEngine, chunks: List<String>, mine: Int, retry: suspend () -> Boolean): Boolean {
        for ((index, chunk) in chunks.withIndex()) {
            if (mine != generation.get()) return true
            var outcome = engine.sayAhead(chunk, chunks.getOrNull(index + 1))
            if (outcome == UtteranceOutcome.FAILED && index == 0 && mine == generation.get()) {
                retry()
                outcome = engine.say(chunk)
            }
            if (outcome == UtteranceOutcome.CANCELLED) return true
            if (outcome == UtteranceOutcome.FAILED) return mine != generation.get()
        }
        return true
    }

    /** Says [chunk], telling a lookahead engine about [next] once this one is under way. */
    private suspend fun SpeechEngine.sayAhead(chunk: String, next: String?): UtteranceOutcome {
        if (this !is SpeechLookahead || next == null) return say(chunk)
        return coroutineScope {
            val saying = async(start = CoroutineStart.UNDISPATCHED) { say(chunk) }
            upcoming(next)
            saying.await()
        }
    }
}
