package com.aimdi.xtanative.ui.speech

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.aimdi.xtanative.services.speech.SpeechEngine
import com.aimdi.xtanative.services.speech.Tts
import com.aimdi.xtanative.services.speech.TtsChoice
import com.aimdi.xtanative.services.speech.UtteranceOutcome
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Reads aloud with whatever text-to-speech engine Android has
 * (system_speech_engine.dart). Each utterance is followed through its start,
 * done, error and stop callbacks, and one that has not started within
 * [START_TIMEOUT_MS] counts as failed: an engine that refuses an utterance
 * or is not bound at all would otherwise leave the reading waiting in
 * silence. The engine is bound only when the choice changes it, since a new
 * binding starts the engine cold.
 */
internal class AndroidSpeechEngine(context: Context) : SpeechEngine, SystemVoices {
    private val context = context.applicationContext
    private val binding = Mutex()

    @Volatile
    private var tts: TextToSpeech? = null

    /** The package the engine was bound for; null for the system's default. */
    @Volatile
    private var bound: String? = null

    @Volatile
    private var ready = false
    private val ids = AtomicInteger()

    @Volatile
    private var pending: CompletableDeferred<UtteranceOutcome>? = null

    @Volatile
    private var pendingId: String? = null

    @Volatile
    private var started = false

    override val maxChunkChars: Int = 3500

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            if (utteranceId == pendingId) started = true
        }

        override fun onDone(utteranceId: String?) = settle(UtteranceOutcome.COMPLETED, utteranceId)

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = settle(UtteranceOutcome.FAILED, utteranceId)

        override fun onError(utteranceId: String?, errorCode: Int) = settle(UtteranceOutcome.FAILED, utteranceId)

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            if (started) settle(UtteranceOutcome.CANCELLED, utteranceId)
        }
    }

    override suspend fun prepare(choice: TtsChoice, textLanguage: String?): Boolean {
        val engine = bindFor(choice.engine) ?: return false
        return withContext(Dispatchers.Default) {
            val language = pickLanguage(engine, choice, textLanguage)
            if (language != null) runCatching { engine.setLanguage(Locale.forLanguageTag(language)) }
            val voiceName = choice.voiceName
            val voiceLocale = choice.voiceLocale
            if (voiceName != null && voiceLocale != null && Tts.voiceSuits(voiceLocale, language)) {
                runCatching { engine.voices?.firstOrNull { it.name == voiceName }?.let { engine.setVoice(it) } }
            }
            // The platform's scale has 1 as normal; the stored rate has 0.5 there.
            runCatching { engine.setSpeechRate((choice.rate * 2).toFloat()) }
            true
        }
    }

    override suspend fun say(chunk: String): UtteranceOutcome {
        val engine = tts?.takeIf { ready } ?: return UtteranceOutcome.FAILED
        settle(UtteranceOutcome.CANCELLED, null)
        val id = "xta-" + ids.incrementAndGet()
        val outcome = CompletableDeferred<UtteranceOutcome>()
        started = false
        pendingId = id
        pending = outcome
        val queued = runCatching {
            engine.speak(chunk, TextToSpeech.QUEUE_FLUSH, Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f) }, id)
        }.getOrDefault(TextToSpeech.ERROR)
        if (queued != TextToSpeech.SUCCESS) settle(UtteranceOutcome.FAILED, id)
        return coroutineScope {
            val watchdog = launch {
                delay(START_TIMEOUT_MS)
                if (!started && pendingId == id) {
                    settle(UtteranceOutcome.FAILED, id)
                    runCatching { engine.stop() }
                }
            }
            outcome.await().also { watchdog.cancel() }
        }
    }

    override suspend fun stop() {
        settle(UtteranceOutcome.CANCELLED, null)
        runCatching { tts?.stop() }
    }

    /** The engines, the system's default folded in, and the voices of [engine] once it is bound. */
    override suspend fun options(engine: String?): VoiceOptions {
        val bound = bindFor(engine) ?: bindFor(null) ?: return VoiceOptions()
        return withContext(Dispatchers.Default) {
            val engines = runCatching { bound.engines.map { SystemEngine(it.name, it.label.orEmpty()) } }.getOrDefault(emptyList())
            val default = runCatching { bound.defaultEngine }.getOrNull()
            val merged = Tts.mergeEngines(engines.map { it.packageName }, default).map { name -> engines.firstOrNull { it.packageName == name } ?: SystemEngine(name, name) }
            val voices = runCatching { bound.voices.orEmpty().map { SystemVoice(it.name, it.locale.toLanguageTag()) } }.getOrDefault(emptyList())
            VoiceOptions(merged, voices)
        }
    }

    /**
     * The engine bound for [requested] (null: the system's default), Sherpa's
     * installed package when Sherpa is asked for. Null when no engine would
     * start: an Android without one (common on de-Googled phones), or a
     * Sherpa that is not installed.
     */
    private suspend fun bindFor(requested: String?): TextToSpeech? = binding.withLock {
        val current = tts ?: bind(null) ?: return@withLock null
        val engines = runCatching { current.engines.map { it.name } }.getOrDefault(emptyList())
        val default = runCatching { current.defaultEngine }.getOrNull()
        if (engines.isEmpty() && default.isNullOrEmpty()) return@withLock null
        val target = Tts.boundEngine(requested, engines, default)
        if (target == null || target == (bound ?: default)) return@withLock current.takeIf { ready }
        bind(target) ?: if (Tts.isSherpa(requested)) null else bind(null)
    }

    /** A new binding to [engine], the last one shut down; null when it would not start in time. */
    private suspend fun bind(engine: String?): TextToSpeech? {
        settle(UtteranceOutcome.CANCELLED, null)
        withContext(Dispatchers.Main) { tts?.shutdown() }
        tts = null
        ready = false
        val init = CompletableDeferred<Boolean>()
        val made = withContext(Dispatchers.Main) {
            TextToSpeech(context, { status -> init.complete(status == TextToSpeech.SUCCESS) }, engine).apply {
                setOnUtteranceProgressListener(listener)
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            }
        }
        tts = made
        bound = engine
        ready = withTimeoutOrNull(START_TIMEOUT_MS) { init.await() } == true
        if (!ready) {
            withContext(Dispatchers.Main) { made.shutdown() }
            tts = null
            bound = null
            return null
        }
        return made
    }

    /** The first language the engine speaks: the text's own, then the voice's, the app's and English. */
    private suspend fun pickLanguage(engine: TextToSpeech, choice: TtsChoice, textLanguage: String?): String? {
        val installed = runCatching { engine.availableLanguages.orEmpty().map { it.toLanguageTag() } }.getOrDefault(emptyList())
        val app = Locale.getDefault().language.takeIf { it.isNotEmpty() }?.let(Tts::languageTag)
        val candidates = Tts.languageCandidates(textLanguage, choice.voiceLocale, app)
        return Tts.pickLanguage(candidates, installed) { tag ->
            Tts.languageAvailable(runCatching { engine.isLanguageAvailable(Locale.forLanguageTag(tag)) }.getOrNull())
        }
    }

    /** Ends the utterance waiting with [outcome]; with [id], only if it is still that one. */
    @Synchronized
    private fun settle(outcome: UtteranceOutcome, id: String?) {
        if (id != null && id != pendingId) return
        val waiting = pending
        pending = null
        pendingId = null
        waiting?.complete(outcome)
    }

    private companion object {
        /** Long enough for an engine that loads a voice model the first time. */
        const val START_TIMEOUT_MS = 20_000L
    }
}
