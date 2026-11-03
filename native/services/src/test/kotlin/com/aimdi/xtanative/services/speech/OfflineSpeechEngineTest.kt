package com.aimdi.xtanative.services.speech

import java.io.File
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

class OfflineSpeechEngineTest {
    private val log: MutableList<String> = Collections.synchronizedList(ArrayList())
    private val loaded = ArrayList<InstalledVoice>()
    private val synthesizers = ArrayList<FakeSynthesizer>()
    private val discarded: MutableList<String> = Collections.synchronizedList(ArrayList())
    private val german = OfflineVoices.byId("de-thorsten-emotional")!!
    private val english = OfflineVoices.byId("en-libritts-r")!!
    private var installed = listOf(german)
    private var enabled = true
    private var loadError: Exception? = null
    private val player = FakeClipPlayer()

    private inner class FakeSynthesizer : VoiceSynthesizer {
        val speeds = ArrayList<Double>()
        var disposed = false

        override suspend fun synthesize(text: String, speed: Double): String {
            log += "synth:$text"
            speeds += speed
            yield()
            return "clip:$text"
        }

        override suspend fun dispose() {
            disposed = true
        }
    }

    private inner class FakeClipPlayer : ClipPlayer {
        var hold: CompletableDeferred<Unit>? = null
        private var playing: CompletableDeferred<UtteranceOutcome>? = null

        override suspend fun play(path: String): UtteranceOutcome {
            log += "play:$path"
            val done = CompletableDeferred<UtteranceOutcome>().also { playing = it }
            val held = hold
            if (held == null) {
                delay(1)
                if (done.complete(UtteranceOutcome.COMPLETED)) log += "done:$path"
            } else {
                held.invokeOnCompletion { if (done.complete(UtteranceOutcome.COMPLETED)) log += "done:$path" }
            }
            return done.await()
        }

        override suspend fun stop() {
            if (playing?.complete(UtteranceOutcome.CANCELLED) == true) log += "stop"
        }
    }

    /** The platform's engine, which reads when the downloaded voice does not. */
    private class SystemEngine : SpeechEngine {
        val spoken = ArrayList<String>()
        override val maxChunkChars = 3500
        override suspend fun prepare(choice: TtsChoice, textLanguage: String?) = true
        override suspend fun say(chunk: String): UtteranceOutcome = UtteranceOutcome.COMPLETED.also { spoken += chunk }
        override suspend fun stop() {}
    }

    private fun CoroutineScope.engine() = OfflineSpeechEngine(
        voiceFor = { language -> OfflineVoices.pick(installed, language)?.let { InstalledVoice(it, File("/voices/${it.id}")) } },
        enabled = { enabled },
        load = { voice ->
            loaded += voice
            loadError?.let { throw it }
            FakeSynthesizer().also { synthesizers += it }
        },
        player = player,
        discard = { discarded += it },
        scope = this,
        appLanguage = { "en" },
    )

    private val germanText = "Das ist der erste Satz, und er ist nicht sehr lang. " +
        "Der zweite Satz folgt sofort und sagt auch nicht viel. " +
        "Der dritte Satz ist das Ende, und wir sind froh darüber."

    private val englishText = "This is the first sentence and it is not very long. " +
        "The second one follows and it does not say much either. " +
        "The third sentence is the end and we are glad of that."

    @Test
    fun aGermanArticleIsReadByTheDownloadedGermanVoice() = runBlocking {
        val system = SystemEngine()
        assertTrue(SpeechReader(listOf(engine(), system)).speak("Post", germanText, TtsChoice()))
        assertEquals(german, loaded.single().voice)
        assertEquals(1, log.count { it.startsWith("play:") })
        assertTrue(system.spoken.isEmpty(), "the system engine stays quiet")
    }

    @Test
    fun anEnglishArticleWithoutAnEnglishVoiceFallsBackToTheSystem() = runBlocking {
        val system = SystemEngine()
        SpeechReader(listOf(engine(), system)).speak("Post", englishText, TtsChoice())
        assertTrue(loaded.isEmpty() && log.isEmpty())
        assertTrue(system.spoken.isNotEmpty())
    }

    @Test
    fun switchedOffOrFailingToLoadTheSystemReads() = runBlocking {
        enabled = false
        val system = SystemEngine()
        SpeechReader(listOf(engine(), system)).speak("Post", germanText, TtsChoice())
        assertTrue(loaded.isEmpty())
        assertTrue(system.spoken.isNotEmpty())

        enabled = true
        loadError = IllegalStateException("model missing")
        val again = SystemEngine()
        SpeechReader(listOf(engine(), again)).speak("Post", germanText, TtsChoice())
        assertEquals(1, loaded.size)
        assertTrue(log.isEmpty())
        assertTrue(again.spoken.isNotEmpty())
    }

    @Test
    fun textOfUnknownLanguageUsesTheAppsLanguage() = runBlocking {
        installed = listOf(german, english)
        assertTrue(engine().prepare(TtsChoice(), null))
        assertEquals("en-libritts-r", loaded.single().voice.id)
    }

    @Test
    fun aLoadedVoiceIsKeptAndSwappedForAnotherLanguage() = runBlocking {
        installed = listOf(german, english)
        val offline = engine()
        offline.prepare(TtsChoice(), "de")
        offline.prepare(TtsChoice(), "de")
        assertEquals(1, loaded.size)
        offline.prepare(TtsChoice(), "en")
        assertEquals(2, loaded.size)
        assertTrue(synthesizers.first().disposed)
    }

    @Test
    fun aShortArticleIsASingleClip() = runBlocking {
        SpeechReader(listOf(engine())).speak("Post", germanText, TtsChoice())
        assertEquals(1, chunkForSpeech(germanText, 300).size)
        assertEquals(listOf("synth:$germanText", "play:clip:$germanText", "done:clip:$germanText"), log.toList())
    }

    @Test
    fun chunksPlayInOrderWithTheNextOneReadyBeforeThisOneEnds() = runBlocking {
        val text = List(4) { germanText }.joinToString(" ")
        val chunks = chunkForSpeech(text, 300)
        assertTrue(chunks.size > 2)
        SpeechReader(listOf(engine())).speak("Post", text, TtsChoice())
        assertEquals(chunks.map { "play:clip:$it" }, log.filter { it.startsWith("play:") })
        assertEquals(chunks.map { "synth:$it" }, log.filter { it.startsWith("synth:") })
        for (i in 0 until chunks.size - 1) {
            assertTrue(log.indexOf("synth:${chunks[i + 1]}") < log.indexOf("done:clip:${chunks[i]}"), "chunk ${i + 1} is ready before chunk $i ends")
        }
        while (discarded.size < chunks.size) yield()
        assertEquals(chunks.map { "clip:$it" }.toSet(), discarded.toSet())
    }

    @Test
    fun stoppingDropsWhatWasPreparedAndPlaysNothingMore() = runBlocking {
        val text = List(4) { germanText }.joinToString(" ")
        player.hold = CompletableDeferred()
        val reader = SpeechReader(listOf(engine()))
        val reading = async { reader.speak("Post", text, TtsChoice()) }
        while (log.none { it.startsWith("play:") }) yield()
        reader.stop()
        player.hold!!.complete(Unit)
        reading.await()
        repeat(20) { yield() }
        assertEquals(1, log.count { it.startsWith("play:") })
        assertTrue("stop" in log)
        assertEquals(log.count { it.startsWith("synth:") }, discarded.size, "no clip is left behind")
    }

    @Test
    fun theSpeechRateBecomesSherpaSpeed() = runBlocking {
        SpeechReader(listOf(engine())).speak("Post", germanText, TtsChoice(rate = 0.6))
        assertEquals(1.2, synthesizers.single().speeds.single(), 1e-9)
    }
}
