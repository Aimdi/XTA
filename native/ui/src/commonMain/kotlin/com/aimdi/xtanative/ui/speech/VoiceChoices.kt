package com.aimdi.xtanative.ui.speech

import com.aimdi.xtanative.services.speech.Tts
import com.aimdi.xtanative.services.speech.TtsChoice
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.SpeechPrefs
import java.util.Locale

/** A speech engine installed on the device: its package, and what the system calls it. */
data class SystemEngine(val packageName: String, val label: String)

/** One voice an engine offers, with the locale the platform wants beside its name (`de-DE`). */
data class SystemVoice(val name: String, val locale: String)

/** What the platform says can speak: its engines, the system's default among them folded in, and the bound engine's voices. */
data class VoiceOptions(val engines: List<SystemEngine> = emptyList(), val voices: List<SystemVoice> = emptyList())

/** What the voice settings ask the platform: the engines, and the voices of [engine] (null: the system's default). */
fun interface SystemVoices {
    suspend fun options(engine: String?): VoiceOptions
}

/** One row of the engine choice, in XTA's order. */
sealed interface EngineRow {
    data object SystemDefault : EngineRow

    /** Sherpa's row; when it is not [installed], a button to get it follows. */
    data class Sherpa(val installed: Boolean) : EngineRow

    data class Engine(val engine: SystemEngine) : EngineRow
}

/**
 * The read-aloud settings (tts_settings.dart): the engine, the voice and the
 * speed as settings keep them, and what changing one does to the others.
 */
object VoiceChoices {
    const val MIN_RATE = 0.2
    const val MAX_RATE = 1.0

    /** The slider's stops between its ends: eight steps from 0.2 to 1.0. */
    const val RATE_STEPS = 7

    fun choiceOf(settings: Settings): TtsChoice =
        TtsChoice.of(settings[SpeechPrefs.engine], settings[SpeechPrefs.voiceName], settings[SpeechPrefs.voiceLocale], settings[SpeechPrefs.rate])

    /** [engine] picked (null: the system's default), and the voice cleared: a voice belongs to the engine that offered it. */
    fun withEngine(settings: Settings, engine: String?): Settings = settings
        .with(SpeechPrefs.engine, engine.orEmpty())
        .with(SpeechPrefs.voiceName, "")
        .with(SpeechPrefs.voiceLocale, "")

    /** Sherpa as the engine (`preferSherpaTts`). */
    fun preferSherpa(settings: Settings): Settings = withEngine(settings, Tts.SHERPA_ENGINE)

    /** [voice] picked; null is the engine's own default. */
    fun withVoice(settings: Settings, voice: SystemVoice?): Settings = settings
        .with(SpeechPrefs.voiceName, voice?.name.orEmpty())
        .with(SpeechPrefs.voiceLocale, voice?.locale.orEmpty())

    fun withRate(settings: Settings, rate: Double): Settings = settings.with(SpeechPrefs.rate, rate.coerceIn(MIN_RATE, MAX_RATE))

    /** The engine row that shows as chosen: Sherpa's, whatever package it was stored under. */
    fun chosenEngine(choice: TtsChoice): String? = if (Tts.isSherpa(choice.engine)) Tts.SHERPA_ENGINE else choice.engine

    /** The engine to bind when Sherpa's row is picked: the package installed, else Sherpa's own id. */
    fun sherpaPackage(engines: List<SystemEngine>): String = Tts.sherpaEngineId(engines.map { it.packageName })

    /** The system's default, Sherpa, then every other engine, so Sherpa is never listed twice. */
    fun engineRows(engines: List<SystemEngine>): List<EngineRow> {
        val packages = engines.map { it.packageName }
        return listOf(EngineRow.SystemDefault, EngineRow.Sherpa(Tts.sherpaInstalled(packages))) +
            engines.filterNot { Tts.isSherpa(it.packageName) }.map { EngineRow.Engine(it) }
    }

    /** Voices in the order the picker lists them: by locale, the platform's order within one. */
    fun sortedVoices(voices: List<SystemVoice>): List<SystemVoice> = voices.sortedBy { it.locale }

    /** The speed as the slider labels it. */
    fun rateText(rate: Double): String = String.format(Locale.ROOT, "%.2f", rate)
}
