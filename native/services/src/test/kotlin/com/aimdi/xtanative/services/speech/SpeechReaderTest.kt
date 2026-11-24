package com.aimdi.xtanative.services.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

class SpeechReaderTest {
    /** Plays an engine: records what it is asked and answers as told. */
    private open class FakeEngine(
        var ready: Boolean = true,
        var outcome: UtteranceOutcome = UtteranceOutcome.COMPLETED,
        override val maxChunkChars: Int = 3500,
    ) : SpeechEngine {
        val spoken = ArrayList<String>()
        val languages = ArrayList<String?>()
        var stops = 0
        var hold: CompletableDeferred<Unit>? = null
        private var current: CompletableDeferred<UtteranceOutcome>? = null

        override suspend fun prepare(choice: TtsChoice, textLanguage: String?): Boolean {
            languages += textLanguage
            return ready
        }

        /** While set, an utterance lasts until it completes or the engine is stopped. */
        override suspend fun say(chunk: String): UtteranceOutcome {
            spoken += chunk
            val held = hold ?: return outcome
            val utterance = CompletableDeferred<UtteranceOutcome>().also { current = it }
            held.invokeOnCompletion { utterance.complete(outcome) }
            return utterance.await()
        }

        override suspend fun stop() {
            stops++
            current?.complete(UtteranceOutcome.CANCELLED)
        }
    }

    private val english = List(200) { "This is the story of a sentence that is long enough to count." }.joinToString(" ")

    @Test
    fun everyChunkIsSpokenInOrderAndThenTheBarClears() = runBlocking {
        val engine = FakeEngine(maxChunkChars = 300)
        val reader = SpeechReader(listOf(engine))
        assertTrue(reader.speak("Post", english, TtsChoice()))
        assertTrue(engine.spoken.size > 2)
        assertEquals(english, engine.spoken.joinToString(" "))
        engine.spoken.forEach { assertTrue(it.length <= 300) }
        assertEquals(SpeechPlayback.IDLE, reader.state.value)
    }

    @Test
    fun theBarShowsWhileReadingAndStoppingHalfWaySpeaksNoMore() = runBlocking {
        val engine = FakeEngine(maxChunkChars = 300)
        engine.hold = CompletableDeferred()
        val reader = SpeechReader(listOf(engine))
        val reading = async { reader.speak("Post", english, TtsChoice()) }
        while (engine.spoken.isEmpty()) yield()
        assertEquals(SpeechPlayback("Post", speaking = true), reader.state.value)
        reader.stop()
        assertTrue(reading.await(), "a stopped reading is no failure")
        assertEquals(1, engine.spoken.size)
        assertTrue(engine.stops >= 2)
        assertFalse(reader.state.value.speaking)
    }

    @Test
    fun aFailedFirstUtteranceIsTriedOnceMoreThenReported() = runBlocking {
        val engine = FakeEngine(outcome = UtteranceOutcome.FAILED, maxChunkChars = 300)
        val reader = SpeechReader(listOf(engine))
        assertFalse(reader.speak("Post", english, TtsChoice()))
        assertEquals(2, engine.spoken.size, "retried once, and the rest is not queued")
        assertEquals(2, engine.languages.size, "the engine was readied again before the retry")
        assertFalse(reader.state.value.speaking)
    }

    @Test
    fun withNoEngineReadyNothingIsSaid() = runBlocking {
        val engine = FakeEngine(ready = false)
        assertFalse(SpeechReader(listOf(engine)).speak("Post", "Short text to read.", TtsChoice()))
        assertTrue(engine.spoken.isEmpty())
    }

    @Test
    fun nothingToSayIsNoReading() = runBlocking {
        val engine = FakeEngine()
        assertFalse(SpeechReader(listOf(engine)).speak("Post", "   ", TtsChoice()))
        assertTrue(engine.spoken.isEmpty())
        assertTrue(engine.languages.isEmpty())
    }

    @Test
    fun theFirstReadyEngineReadsAndOneThatHangsOrThrowsCountsAsNotReady() = runBlocking {
        val hangs = object : FakeEngine() {
            override suspend fun prepare(choice: TtsChoice, textLanguage: String?): Boolean = awaitCancellation()
        }
        val throws = object : FakeEngine() {
            override suspend fun prepare(choice: TtsChoice, textLanguage: String?): Boolean = error("model missing")
        }
        val offline = FakeEngine(ready = false)
        val system = FakeEngine()
        val reader = SpeechReader(listOf(hangs, throws, offline, system), prepareTimeoutMillis = 20)
        assertTrue(reader.speak("Post", "Short text to read.", TtsChoice()))
        assertEquals(listOf("Short text to read."), system.spoken)
        assertTrue(hangs.spoken.isEmpty() && throws.spoken.isEmpty() && offline.spoken.isEmpty())
    }

    @Test
    fun theTextsLanguageIsHandedToTheEngine() = runBlocking {
        val engine = FakeEngine()
        SpeechReader(listOf(engine)).speak("Post", List(5) { "Das ist ein Satz, der nicht auf Englisch ist und den wir vorlesen." }.joinToString(" "), TtsChoice())
        assertEquals(listOf<String?>("de"), engine.languages)
    }

    @Test
    fun aNewReadingReplacesTheOneUnderWay() = runBlocking {
        val engine = FakeEngine(maxChunkChars = 300)
        engine.hold = CompletableDeferred()
        val reader = SpeechReader(listOf(engine))
        val first = async { reader.speak("A", english, TtsChoice()) }
        while (engine.spoken.isEmpty()) yield()
        engine.hold = null
        assertTrue(reader.speak("B", "Another post to read.", TtsChoice()))
        assertTrue(first.await())
        assertEquals("Another post to read.", engine.spoken.last())
        assertEquals(2, engine.spoken.size, "the first reading said no more after it was replaced")
    }

    @Test
    fun anEngineThatReadsAheadHearsOfTheNextChunkWhileOneIsSaid() = runBlocking {
        val heard = ArrayList<String>()
        val engine = object : FakeEngine(maxChunkChars = 300), SpeechLookahead {
            override fun upcoming(chunk: String) {
                heard += "next:${chunk.length}"
            }

            override suspend fun say(chunk: String): UtteranceOutcome {
                heard += "say:${chunk.length}"
                return super.say(chunk)
            }
        }
        SpeechReader(listOf(engine)).speak("Post", english, TtsChoice())
        val chunks = chunkForSpeech(english, 300)
        assertEquals(chunks.size - 1, heard.count { it.startsWith("next:") })
        assertEquals("say:${chunks[0].length}", heard[0])
        assertEquals("next:${chunks[1].length}", heard[1], "the next chunk is announced once the first is under way")
        assertEquals("next:${chunks[2].length}", heard[2], "and the one after it, so a slow voice has time for both")
    }

    @Test
    fun theLockScreenNamesTheReadingAndItsStopStopsIt() = runBlocking {
        val engine = FakeEngine(maxChunkChars = 300)
        engine.hold = CompletableDeferred()
        var bound: Pair<String, suspend () -> Unit>? = null
        var cleared = 0
        val session = object : SpeechSession {
            override fun bind(title: String, stop: suspend () -> Unit) {
                bound = title to stop
            }

            override fun clear() {
                cleared++
            }
        }
        val reader = SpeechReader(listOf(engine), session)
        val reading = async { reader.speak("Post", english, TtsChoice()) }
        while (engine.spoken.isEmpty()) yield()
        assertEquals("Post", bound!!.first)
        bound!!.second()
        assertTrue(reading.await())
        assertFalse(reader.state.value.speaking)
        assertTrue(cleared >= 2)
    }

    @Test
    fun aReadingHeldFallsSilentKeepsItsBarAndGoesOnWithTheChunkItWasHeldIn() = runBlocking {
        val engine = FakeEngine(maxChunkChars = 300)
        engine.hold = CompletableDeferred()
        val reader = SpeechReader(listOf(engine))
        val chunks = chunkForSpeech(english, 300)
        val reading = async { reader.speak("Post", english, TtsChoice()) }
        while (engine.spoken.isEmpty()) yield()
        reader.pause()
        assertEquals(SpeechPlayback("Post", speaking = true, paused = true), reader.state.value)
        repeat(20) { yield() }
        assertEquals(1, engine.spoken.size, "nothing is said while it is held")
        engine.hold = null
        reader.resume()
        assertTrue(reading.await())
        assertEquals(listOf(chunks[0]) + chunks, engine.spoken, "the chunk it was held in is said again, then the rest")
        assertEquals(SpeechPlayback.IDLE, reader.state.value)
    }

    @Test
    fun aReadingHeldLongIntoAChunkGoesOnFromTheSentenceItWasIn() = runBlocking {
        var now = 0L
        // Ten characters a second for two chunks; the third lasts until it is held.
        val engine = object : FakeEngine(maxChunkChars = 300) {
            override suspend fun say(chunk: String): UtteranceOutcome {
                if (spoken.size < 2) now += chunk.length * 100L
                hold = if (spoken.size == 2) CompletableDeferred() else null
                return super.say(chunk)
            }
        }
        val reader = SpeechReader(listOf(engine), clock = { now })
        val chunks = chunkForSpeech(english, 300)
        val sentence = "This is the story of a sentence that is long enough to count.".length + 1
        val reading = async { reader.speak("Post", english, TtsChoice()) }
        while (engine.spoken.size < 3) yield()
        // Two sentences and a half at the pace learnt, which is counted a tenth slower.
        now += (sentence * 2 + sentence / 2) * 1000L / 9
        reader.pause()
        reader.resume()
        assertTrue(reading.await())
        assertEquals(chunks[2].substring(sentence * 2), engine.spoken[3], "from the third sentence of the chunk, not its start")
        assertEquals(chunks.drop(3), engine.spoken.drop(4))
    }

    @Test
    fun stoppingAReadingThatIsHeldEndsIt() = runBlocking {
        val engine = FakeEngine(maxChunkChars = 300)
        engine.hold = CompletableDeferred()
        val reader = SpeechReader(listOf(engine))
        val reading = async { reader.speak("Post", english, TtsChoice()) }
        while (engine.spoken.isEmpty()) yield()
        reader.pause()
        reader.stop()
        assertTrue(reading.await())
        assertEquals(SpeechPlayback.IDLE, reader.state.value)
        assertEquals(1, engine.spoken.size)
        reader.resume()
        assertEquals(SpeechPlayback.IDLE, reader.state.value, "nothing is left to go on with")
    }

    @Test
    fun anEngineThatReadsAheadHearsOfTheNextChunksAgainAfterAPause() = runBlocking {
        val heard = ArrayList<String>()
        val engine = object : FakeEngine(maxChunkChars = 300), SpeechLookahead {
            override fun upcoming(chunk: String) {
                heard += chunk
            }
        }
        engine.hold = CompletableDeferred()
        val reader = SpeechReader(listOf(engine))
        val chunks = chunkForSpeech(english, 300)
        val reading = async { reader.speak("Post", english, TtsChoice()) }
        while (heard.size < 2) yield()
        reader.pause()
        engine.hold = null
        reader.resume()
        assertTrue(reading.await())
        assertEquals(listOf(chunks[1], chunks[2], chunks[1], chunks[2]), heard.take(4))
    }

    @Test
    fun thePaceIsLearntFromWhatWasSaidAndErrsTowardsTooLittle() {
        val pace = SpeechPace()
        assertEquals(30, pace.saidIn(10_000), "a guess at the slowest reading before anything is heard")
        pace.heard(1500, 100_000)
        assertEquals(135, pace.saidIn(10_000), "fifteen a second, less a tenth")
        assertEquals(0, pace.saidIn(-5))
    }
}
