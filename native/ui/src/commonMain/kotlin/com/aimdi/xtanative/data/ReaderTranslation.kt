package com.aimdi.xtanative.data

import androidx.compose.runtime.staticCompositionLocalOf
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.services.ai.AiConfig
import com.aimdi.xtanative.services.translation.KeptTranslations
import com.aimdi.xtanative.services.translation.Translated
import com.aimdi.xtanative.services.translation.TranslationConfig
import com.aimdi.xtanative.services.translation.TranslationException
import com.aimdi.xtanative.services.translation.TranslationLanguage
import com.aimdi.xtanative.services.translation.Translator
import com.aimdi.xtanative.store.StoreDir
import com.aimdi.xtanative.store.settings.AiPrefs
import com.aimdi.xtanative.store.settings.ReadingPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.SettingsStore
import com.aimdi.xtanative.ui.post.TranslationMemory
import com.aimdi.xtanative.ui.post.TranslationRuns
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The reader's translation service for posts of every network (Settings ›
 * Translation), shared by X's posts and the plugins: what is set, as it
 * changes, and the translations it gives, kept on the device in
 * `translations.json`. Nothing is sent until a reader asks for one.
 */
class ReaderTranslation(private val settings: SettingsStore, http: XHttp, store: StoreDir) {
    private val file = store.file("translations.json")
    private val kept = KeptTranslations(load = { file.read() }, save = { file.write(it) })
    private val translator = Translator(http, kept)

    /** The setting now, and as it changes. */
    val config: Flow<TranslationConfig> = settings.settings.map(::configOf).distinctUntilChanged()

    fun current(): TranslationConfig = configOf(settings.state.value ?: Settings())

    /** The settings as read so far, kept current: cheap to follow from every post shown, unlike [config]. */
    val settingsNow: StateFlow<Settings?> get() = settings.state

    /** The service [now] sets ([settingsNow]'s value). */
    fun configFor(now: Settings?): TranslationConfig = configOf(now ?: Settings())

    /**
     * [text] translated with the service set, into its language or else
     * [appLanguage]; null while no service is set. Throws a
     * [TranslationException] when the service gives none.
     */
    suspend fun translate(text: String, appLanguage: String, config: TranslationConfig = current()): Translated? =
        if (!config.enabled) null else translator.translate(text, config, config.language(appLanguage))

    /** What [config]'s server translates, for Settings' Test. */
    suspend fun languages(config: TranslationConfig): List<TranslationLanguage> = translator.languages(config)

    /**
     * Forgets every translation: the service's kept on the device, and X's
     * and the service's kept for this run, the ones on screen too.
     */
    suspend fun clear() {
        kept.clear()
        TranslationRuns.shared.clear()
        TranslationMemory.shared.clear()
    }

    companion object {
        fun configOf(settings: Settings): TranslationConfig = TranslationConfig.read(
            settings[ReadingPrefs.translation],
            settings[ReadingPrefs.translationApiKey],
            AiConfig(settings[AiPrefs.baseUrl], settings[AiPrefs.apiKey], settings[AiPrefs.model]),
        )
    }
}

/** The reader's translation service, for the post views that offer one. */
val LocalTranslation = staticCompositionLocalOf<ReaderTranslation> { error("No translation provided") }
