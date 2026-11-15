package com.aimdi.xtanative.ui.settings

import com.aimdi.xtanative.services.translation.TranslationConfig
import com.aimdi.xtanative.services.translation.TranslationFailure
import com.aimdi.xtanative.services.translation.TranslationService
import com.aimdi.xtanative.store.settings.ReadingPrefs
import com.aimdi.xtanative.store.settings.Settings

/**
 * Settings › Translation as typed (reader_translation_settings.dart): the
 * service, the language, the server's address and its key, until saved;
 * whether the key shows; and what the last Test found ([test]).
 */
data class TranslationForm(
    val service: TranslationService = TranslationService.OFF,
    val target: String = "",
    val endpoint: String = "",
    val apiKey: String = "",
    val keyHidden: Boolean = true,
    val test: TranslationTest? = null,
) {
    /** Whether the address and key fields show: DeepL and LibreTranslate have a server, the AI its own settings. */
    val asksServer: Boolean get() = service == TranslationService.DEEPL || service == TranslationService.LIBRE

    /** Whether Test is offered: the server's languages can be asked once there is an address to ask. */
    val canTest: Boolean get() = asksServer && test != TranslationTest.Testing && draft(TranslationConfig()).url("languages") != null

    /** What would be saved, with [saved]'s AI connection; the AI's choice keeps the key there was. */
    fun draft(saved: TranslationConfig): TranslationConfig = TranslationConfig(
        service = service,
        endpoint = if (asksServer) endpoint.trim() else "",
        apiKey = if (asksServer) apiKey.trim() else saved.apiKey,
        target = target,
        ai = saved.ai,
    )

    /** [settings] with [draft] saved, its key under its own secret preference; null when it cannot be used. */
    fun saved(settings: Settings, current: TranslationConfig): Settings? {
        val config = draft(current)
        if (!config.isValid) return null
        return settings.with(ReadingPrefs.translationApiKey, config.apiKey).with(ReadingPrefs.translation, config.toJson())
    }

    /** Another service chosen: what the last Test found no longer holds. */
    fun withService(service: TranslationService): TranslationForm = copy(service = service, test = null)

    /** An example server tapped: its address in the field. */
    fun withExample(address: String): TranslationForm = copy(endpoint = address, test = null)

    companion object {
        fun of(config: TranslationConfig) = TranslationForm(config.service, config.target, config.endpoint, config.apiKey)

        /**
         * Public LibreTranslate servers the reader may try, none chosen for
         * them: a tap fills the field, and what is translated then goes
         * there. libretranslate.com asks for a key.
         */
        val LIBRE_EXAMPLES = listOf("https://libretranslate.com", "https://translate.fedilab.app", "https://lt.vern.cc")

        /** The choices of the language: the app's (empty), then XTA's languages. */
        val targets: List<String> = listOf("") + SettingsChoices.languages.map { it.code }
    }
}

/** What Settings' Test found of a server: asking, its languages counted, or why it did not answer. */
sealed interface TranslationTest {
    data object Testing : TranslationTest

    data class Reached(val languages: Int) : TranslationTest

    data class Failed(val failure: TranslationFailure) : TranslationTest
}
