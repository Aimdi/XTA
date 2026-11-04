package com.aimdi.xtanative.ui.settings

import com.aimdi.xtanative.core.feed.LanguageFilter
import com.aimdi.xtanative.services.ai.AiConfig
import com.aimdi.xtanative.services.ai.AiProvider
import com.aimdi.xtanative.services.links.BrowserPicker
import com.aimdi.xtanative.services.links.InstalledBrowser
import com.aimdi.xtanative.services.links.LinkDestination
import com.aimdi.xtanative.services.links.LinkSettings
import com.aimdi.xtanative.services.speech.Tts
import com.aimdi.xtanative.store.settings.GeneralPrefs
import com.aimdi.xtanative.store.settings.LanguageFilterAction
import com.aimdi.xtanative.store.settings.PostsPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.SpeechPrefs
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Settings › AI as typed (_ai.dart): the three fields, until saved, and whether the key shows. */
data class AiForm(val baseUrl: String = "", val apiKey: String = "", val model: String = "", val keyHidden: Boolean = true) {
    private val host: String? get() = baseUrl.trim().toHttpUrlOrNull()?.host?.lowercase()

    val isGrok: Boolean get() = host == "api.x.ai"
    val isOpenRouter: Boolean get() = host == "openrouter.ai"

    /** The model the field suggests while empty. */
    val modelPlaceholder: String
        get() = when {
            isOpenRouter -> AiProvider.OPENROUTER.model
            isGrok -> AiProvider.GROK.model
            else -> AiProvider.OPENAI.model
        }

    /** [preset] filled in: another provider clears the key and takes its model; the same keeps both, unless no model was written. */
    fun withPreset(preset: AiProvider): AiForm {
        val config = AiConfig(baseUrl, apiKey, model).withPreset(preset)
        return copy(baseUrl = config.baseUrl, apiKey = config.apiKey, model = config.model)
    }

    /** What is saved: every field trimmed. */
    fun saved(): AiConfig = AiConfig(baseUrl.trim(), apiKey.trim(), model.trim())

    companion object {
        fun of(config: AiConfig) = AiForm(config.baseUrl, config.apiKey, config.model)
    }
}

/** Which signed-in accounts For you reads (home_account_filter.dart): every one, unless turned off here. */
object ForYouAccounts {
    fun isOn(accountId: String, hidden: Collection<String>): Boolean = accountId !in hidden

    /** False when turning [accountId] off would leave For you with no account at all. */
    fun canTurnOff(accountId: String, accountIds: List<String>, hidden: Collection<String>): Boolean =
        accountIds.any { it != accountId && it !in hidden }

    /** [hidden] once [accountId] is turned [on] or off; null when turning it off is refused. */
    fun toggled(hidden: List<String>, accountId: String, on: Boolean, accountIds: List<String>): List<String>? = when {
        on -> hidden - accountId
        !canTurnOff(accountId, accountIds, hidden) -> null
        else -> (hidden + accountId).distinct()
    }
}

/** Settings › Posts' language filter (_posts.dart). */
object LanguageFilterInput {
    fun isActive(typed: String): Boolean = LanguageFilter.parseLanguages(typed).isNotEmpty()

    /**
     * [settings] with the languages typed (trimmed) saved; with none left, the
     * filter goes back to showing everything.
     */
    fun committed(settings: Settings, typed: String): Settings {
        val trimmed = typed.trim()
        val saved = settings.with(PostsPrefs.languages, trimmed)
        return if (!isActive(trimmed)) saved.with(PostsPrefs.languageAction, LanguageFilterAction.OFF) else saved
    }

    /** [settings] with [action] chosen; hiding or folding needs a language to keep. */
    fun withAction(settings: Settings, action: LanguageFilterAction): Settings? =
        if (action != LanguageFilterAction.OFF && !isActive(settings[PostsPrefs.languages])) null else settings.with(PostsPrefs.languageAction, action)
}

/** Where links open, as the browser picker shows and stores it (_browser_picker.dart). */
object LinkChoice {
    fun settingsOf(settings: Settings) = LinkSettings(settings[GeneralPrefs.openLinksInApp], settings[GeneralPrefs.externalBrowser], settings[GeneralPrefs.cleanLinks])

    /** [settings] once [destination] is picked. */
    fun chosen(settings: Settings, destination: LinkDestination): Settings {
        val picked = BrowserPicker.choose(settingsOf(settings), destination)
        return settings.with(GeneralPrefs.openLinksInApp, picked.inApp ?: true).with(GeneralPrefs.externalBrowser, picked.browser.orEmpty())
    }

    fun selected(settings: Settings, browsers: List<InstalledBrowser>): LinkDestination =
        BrowserPicker.selected(settingsOf(settings), browsers)
}

/** A voice an engine offers. */
data class SpeechVoice(val name: String, val locale: String)

/** What the platform says can speak: its engines' packages and the voices of the one loaded. */
data class SpeechOptions(val engines: List<String> = emptyList(), val voices: List<SpeechVoice> = emptyList())

/** Settings › Read aloud (tts_settings.dart): one engine, voice and speed for everything read aloud. */
object SpeechChoices {
    /** The engine row that is chosen: null for the system's, Sherpa's id for any Sherpa build. */
    fun engineGroup(settings: Settings): String? {
        val engine = settings[SpeechPrefs.engine].ifEmpty { null }
        return if (Tts.isSherpa(engine)) Tts.SHERPA_ENGINE else engine
    }

    /** [settings] with [engine] chosen (null for the system's): a voice belongs to the engine that offered it, so it goes. */
    fun withEngine(settings: Settings, engine: String?): Settings =
        settings.with(SpeechPrefs.engine, engine.orEmpty()).with(SpeechPrefs.voiceName, "").with(SpeechPrefs.voiceLocale, "")

    /** [settings] with [voice] chosen, or the engine's default for null. */
    fun withVoice(settings: Settings, voice: SpeechVoice?): Settings =
        settings.with(SpeechPrefs.voiceName, voice?.name.orEmpty()).with(SpeechPrefs.voiceLocale, voice?.locale.orEmpty())

    /** The engines listed after the system's and Sherpa's rows. */
    fun otherEngines(options: SpeechOptions): List<String> = Tts.nonSherpa(options.engines)

    fun sherpaInstalled(options: SpeechOptions): Boolean = Tts.sherpaInstalled(options.engines)

    fun sherpaEngine(options: SpeechOptions): String = Tts.sherpaEngineId(options.engines)
}
