package com.aimdi.xtanative.ui.speech

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.services.speech.OfflineVoice
import com.aimdi.xtanative.services.speech.TtsChoice
import com.aimdi.xtanative.services.speech.VoiceInstall
import com.aimdi.xtanative.store.settings.SpeechPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VoiceSettingsUi(
    val choice: TtsChoice = TtsChoice(),
    val offlineVoice: Boolean = true,
    /** What the platform offers; null while it is asked. */
    val options: VoiceOptions? = null,
    val installs: Map<String, VoiceInstall> = emptyMap(),
    val engine: VoiceInstall = VoiceInstall.Absent,
)

/**
 * The read-aloud settings (tts_settings.dart, offline_voices_section.dart):
 * the engine, its voices and the speed, and the voices that download. A
 * change of engine stops what is being read, as the sheet's OK does in XTA;
 * downloads run on when the screen is left.
 */
class VoiceSettingsViewModel(private val graph: AppGraph, private val parts: SpeechParts) : ViewModel() {
    private val options = MutableStateFlow<VoiceOptions?>(null)

    val ui: StateFlow<VoiceSettingsUi> = combine(graph.settings.settings, options, parts.voices.state, parts.voices.engine.state) { settings, options, installs, engine ->
        VoiceSettingsUi(VoiceChoices.choiceOf(settings), settings[SpeechPrefs.offlineVoice], options, installs, engine)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceSettingsUi())

    init {
        viewModelScope.launch { parts.voices.ensureLoaded() }
        viewModelScope.launch { load(VoiceChoices.choiceOf(graph.settings.load()).engine) }
    }

    /** What [engine] offers: the platform binds it to list its voices. */
    private suspend fun load(engine: String?) {
        options.value = runCatching { parts.systemVoices?.options(engine) }.getOrNull() ?: VoiceOptions()
    }

    fun setEngine(engine: String?) {
        viewModelScope.launch {
            graph.settings.update { VoiceChoices.withEngine(it, engine) }
            parts.speech.stop()
            load(engine)
        }
    }

    fun pickSherpa() = setEngine(VoiceChoices.sherpaPackage(options.value?.engines.orEmpty()))

    fun setVoice(voice: SystemVoice?) {
        viewModelScope.launch { graph.settings.update { VoiceChoices.withVoice(it, voice) } }
    }

    fun setRate(rate: Double) {
        viewModelScope.launch { graph.settings.update { VoiceChoices.withRate(it, rate) } }
    }

    fun setOfflineVoice(on: Boolean) {
        viewModelScope.launch { graph.settings.set(SpeechPrefs.offlineVoice, on) }
    }

    fun download(voice: OfflineVoice) = parts.keep { parts.voices.download(voice) }

    fun cancel(voice: OfflineVoice) = parts.voices.cancel(voice.id)

    fun delete(voice: OfflineVoice) = parts.keep { parts.voices.delete(voice) }

    fun downloadEngine() = parts.keep { parts.voices.engine.download() }

    fun cancelEngine() = parts.voices.engine.cancel()

    fun deleteEngine() = parts.keep { parts.voices.engine.delete() }
}
