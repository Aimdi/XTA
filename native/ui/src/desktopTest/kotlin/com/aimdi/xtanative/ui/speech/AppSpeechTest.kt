package com.aimdi.xtanative.ui.speech

import com.aimdi.xtanative.services.speech.SpeechEngine
import com.aimdi.xtanative.services.speech.SpeechReader
import com.aimdi.xtanative.services.speech.Tts
import com.aimdi.xtanative.services.speech.TtsChoice
import com.aimdi.xtanative.services.speech.TtsFailureAction
import com.aimdi.xtanative.services.speech.UtteranceOutcome
import com.aimdi.xtanative.store.StoreDir
import com.aimdi.xtanative.store.settings.SettingsStore
import com.aimdi.xtanative.store.settings.SpeechPrefs
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class AppSpeechTest {
    private class Engine(var ready: Boolean = true) : SpeechEngine {
        val spoken = ArrayList<String>()
        val choices = ArrayList<TtsChoice>()
        var hold: CompletableDeferred<Unit>? = null
        private var current: CompletableDeferred<UtteranceOutcome>? = null
        override val maxChunkChars = 3500

        override suspend fun prepare(choice: TtsChoice, textLanguage: String?): Boolean {
            choices += choice
            return ready
        }

        override suspend fun say(chunk: String): UtteranceOutcome {
            spoken += chunk
            val held = hold ?: return UtteranceOutcome.COMPLETED
            val utterance = CompletableDeferred<UtteranceOutcome>().also { current = it }
            held.invokeOnCompletion { utterance.complete(UtteranceOutcome.COMPLETED) }
            return utterance.await()
        }

        override suspend fun stop() {
            current?.complete(UtteranceOutcome.CANCELLED)
        }
    }

    private fun settings() = SettingsStore(StoreDir(Files.createTempDirectory("speech-test").toFile()))

    private fun timed(block: suspend CoroutineScope.() -> Unit): Unit = runBlocking { withTimeout(30_000) { block() } }

    private suspend fun until(condition: () -> Boolean) {
        while (!condition()) delay(5)
    }

    @Test
    fun readsWithTheVoiceSettingsAndShowsTheTitleWhileReading(): Unit = timed {
        val engine = Engine().apply { hold = CompletableDeferred() }
        val store = settings()
        store.set(SpeechPrefs.rate, 0.7)
        store.set(SpeechPrefs.voiceName, "de-de-x-nfh")
        store.set(SpeechPrefs.voiceLocale, "de-DE")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val speech = AppSpeech(SpeechReader(listOf(engine)), store, scope, hasEngine = true)
        speech.speak("Maya", "Hallo, wie geht es dir heute?")
        until { speech.playback.value.speaking }
        assertTrue(speech.isReading("Maya"))
        assertFalse(speech.isReading("Someone else"))
        assertEquals(TtsChoice(voiceName = "de-de-x-nfh", voiceLocale = "de-DE", rate = 0.7), engine.choices.first())
        speech.toggle("Maya", "ignored")
        until { !speech.playback.value.speaking }
        assertNull(speech.notice.value)
        scope.cancel()
    }

    @Test
    fun noEngineThatReadsLeavesTheWayOutXtaOffers(): Unit = timed {
        val engine = Engine(ready = false)
        val store = settings()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val speech = AppSpeech(SpeechReader(listOf(engine)), store, scope, hasEngine = true)
        speech.speak("Article", "Some words to read.")
        until { speech.notice.value != null }
        assertEquals(SpeechNotice.Failed(TtsFailureAction.USE_SHERPA), speech.notice.value)
        engine.ready = true
        speech.useSherpa()
        until { engine.spoken.isNotEmpty() }
        assertEquals(Tts.SHERPA_ENGINE, store.get(SpeechPrefs.engine))
        assertEquals(Tts.SHERPA_ENGINE, engine.choices.last().engine)
        assertEquals(listOf("Some words to read."), engine.spoken)
        scope.cancel()
    }

    @Test
    fun sherpaThatWillNotReadOffersToGetIt(): Unit = timed {
        val store = settings()
        store.set(SpeechPrefs.engine, Tts.SHERPA_ENGINE)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val speech = AppSpeech(SpeechReader(listOf(Engine(ready = false))), store, scope, hasEngine = true)
        speech.speak("Article", "Words.")
        until { speech.notice.value != null }
        assertEquals(SpeechNotice.Failed(TtsFailureAction.INSTALL_SHERPA), speech.notice.value)
        speech.dismiss(SpeechNotice.NothingToRead)
        assertEquals(SpeechNotice.Failed(TtsFailureAction.INSTALL_SHERPA), speech.notice.value)
        speech.dismiss(speech.notice.value)
        assertNull(speech.notice.value)
        scope.cancel()
    }

    @Test
    fun theDesktopSaysItHasNoEngineAndEmptyTextSaysThereIsNothingToRead(): Unit = timed {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val none = AppSpeech(SpeechReader(emptyList()), settings(), scope, hasEngine = false)
        none.speak("Post", "Words.")
        assertEquals(SpeechNotice.NoEngine, none.notice.value)
        val engine = Engine()
        val speech = AppSpeech(SpeechReader(listOf(engine)), settings(), scope, hasEngine = true)
        speech.speak("Post", "   ")
        assertEquals(SpeechNotice.NothingToRead, speech.notice.value)
        assertTrue(engine.spoken.isEmpty())
        scope.cancel()
    }
}
