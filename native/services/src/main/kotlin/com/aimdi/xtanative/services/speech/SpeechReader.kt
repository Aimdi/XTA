package com.aimdi.xtanative.services.speech

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What is being read aloud, if anything: the bar names [title] while
 * [speaking]. A reading [paused] is still the one at hand ([speaking] stays
 * true): it is silent, and goes on from where it was held.
 */
data class SpeechPlayback(val title: String? = null, val speaking: Boolean = false, val paused: Boolean = false) {
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

    /** The longest first piece, for an engine that is quicker to start on a short one; [maxChunkChars] for the others. */
    val firstChunkChars: Int get() = maxChunkChars

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
 *
 * A reading can be held ([pause]) and gone on with ([resume]): it starts
 * again at the sentence it was held in, as near as the time it had been
 * speaking tells ([SpeechPace]), since an engine does not say how far it got.
 */
class SpeechReader(
    private val engines: List<SpeechEngine>,
    private val session: SpeechSession = object : SpeechSession {},
    /** How long an engine may take to get ready before it counts as broken. */
    private val prepareTimeoutMillis: Long = 20_000,
    /** Milliseconds that only go forward, for how long an utterance has been speaking. */
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
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
        val read = readChunks(engine, chunkForSpeech(text, engine.maxChunkChars, engine.firstChunkChars), mine) { engine.prepare(choice, language) }
        if (mine == generation.get()) finished()
        return read
    }

    suspend fun stop() {
        generation.incrementAndGet()
        session.clear()
        if (playback.value.speaking) playback.value = SpeechPlayback.IDLE
        engines.forEach { it.stop() }
    }

    /** Counts the pauses, so the chunk loop knows an utterance was cut short by one even when the reading has gone on since. */
    private val pauses = AtomicInteger()

    /** Holds the reading at hand: it falls silent and keeps its place until [resume] or [stop]. */
    suspend fun pause() {
        val held = playback.changeIf({ it.speaking && !it.paused }) { it.copy(paused = true) }
        if (!held) return
        pauses.incrementAndGet()
        engines.forEach { it.stop() }
    }

    /** Goes on with a reading that was held. */
    fun resume() {
        playback.changeIf({ it.speaking && it.paused }) { it.copy(paused = false) }
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
     * not a failure. One held waits here, and then says what was left of the
     * chunk it was held in.
     */
    private suspend fun readChunks(engine: SpeechEngine, chunks: List<String>, mine: Int, retry: suspend () -> Boolean): Boolean {
        val pace = SpeechPace()
        var index = 0
        var rest: String? = null
        var fresh = true
        while (index < chunks.size) {
            playback.first { !it.paused }
            if (mine != generation.get()) return true
            val chunk = rest ?: chunks[index]
            val began = clock()
            val pausesBefore = pauses.get()
            var outcome = engine.sayAhead(chunk, ahead(chunks, index, fresh))
            if (outcome == UtteranceOutcome.FAILED && index == 0 && mine == generation.get()) {
                retry()
                outcome = engine.say(chunk)
            }
            val held = mine == generation.get() && pauses.get() != pausesBefore
            when {
                outcome == UtteranceOutcome.CANCELLED && held -> {
                    rest = restOfChunk(chunk, pace.saidIn(clock() - began))
                    fresh = true
                }
                outcome == UtteranceOutcome.CANCELLED -> return true
                outcome == UtteranceOutcome.FAILED -> return mine != generation.get()
                else -> {
                    pace.heard(chunk.length, clock() - began)
                    index++
                    rest = null
                    fresh = false
                }
            }
        }
        return true
    }

    /**
     * The chunks to announce while the one at [index] is said: each chunk is
     * announced once, [AHEAD] chunks before its turn, so a slow voice has
     * more than one chunk's playing time to get the next one ready. A reading
     * that starts, or starts again after being held ([fresh]), announces all
     * [AHEAD] of them: stopping an engine drops what it had ready.
     */
    private fun ahead(chunks: List<String>, index: Int, fresh: Boolean): List<String> =
        if (fresh) chunks.drop(index + 1).take(AHEAD) else listOfNotNull(chunks.getOrNull(index + AHEAD))

    /** Says [chunk], telling a lookahead engine about what comes [next] once this one is under way. */
    private suspend fun SpeechEngine.sayAhead(chunk: String, next: List<String>): UtteranceOutcome {
        if (this !is SpeechLookahead || next.isEmpty()) return say(chunk)
        return coroutineScope {
            val saying = async(start = CoroutineStart.UNDISPATCHED) { say(chunk) }
            next.forEach(::upcoming)
            saying.await()
        }
    }

    private companion object {
        /** How many chunks ahead of the one being said are being made ready. */
        const val AHEAD = 2
    }
}

/** Changes the value when [wanted] holds for it; whether it did. */
private inline fun <T> MutableStateFlow<T>.changeIf(wanted: (T) -> Boolean, change: (T) -> T): Boolean {
    while (true) {
        val before = value
        if (!wanted(before)) return false
        if (compareAndSet(before, change(before))) return true
    }
}

/**
 * How fast a reading speaks, learnt from the chunks it has finished, to tell
 * how far into a chunk a pause fell. It errs towards too little: a reading
 * that goes on repeats a few words rather than skips any.
 */
class SpeechPace {
    private var chars = 0L
    private var millis = 0L

    /** A chunk of [chars] characters took [millis] to say. */
    fun heard(chars: Int, millis: Long) {
        if (chars <= 0 || millis <= 0) return
        this.chars += chars
        this.millis += millis
    }

    /** How many characters were surely said in the first [millis] of an utterance. */
    fun saidIn(millis: Long): Int {
        val perSecond = if (this.millis >= LEARNT_MILLIS) chars * 1000.0 / this.millis * CAUTION else SLOWEST
        return (millis.coerceAtLeast(0) / 1000.0 * perSecond).toInt()
    }

    private companion object {
        /** Before this much speech is heard, the pace is a guess. */
        const val LEARNT_MILLIS = 2_000L

        /** Characters a second of the slowest reading: a slow rate, or a script with few characters to a word. */
        const val SLOWEST = 3.0
        const val CAUTION = 0.9
    }
}
