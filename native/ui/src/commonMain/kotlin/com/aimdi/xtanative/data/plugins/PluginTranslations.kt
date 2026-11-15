package com.aimdi.xtanative.data.plugins

import com.aimdi.xtanative.services.translation.Translated
import com.aimdi.xtanative.services.translation.TranslationConfig
import com.aimdi.xtanative.services.translation.TranslationFailure
import com.aimdi.xtanative.ui.plugins.PluginTranslation
import com.aimdi.xtanative.ui.plugins.PluginTranslationProblem
import com.aimdi.xtanative.ui.plugins.PluginTranslator

/** The reader's translation service in the plugin API's terms ([com.aimdi.xtanative.ui.plugins.PluginHost.translator]). */
object PluginTranslations {
    /** [config] as a plugin sees it, the reader reading in [language]: null while no service is set. */
    fun translator(config: TranslationConfig, language: String): PluginTranslator? =
        if (!config.enabled) null else PluginTranslator(config.translatorName, tag(config.language(language)))

    /** One of XTA's language codes (`pt_BR`, `zh_Hant`) as a BCP 47 tag. */
    fun tag(code: String): String = code.trim().replace('_', '-')

    fun translation(translated: Translated): PluginTranslation =
        PluginTranslation(translated.text, translated.sourceLanguage, translated.translator)

    fun problem(failure: TranslationFailure): PluginTranslationProblem = when (failure) {
        TranslationFailure.NOT_CONFIGURED -> PluginTranslationProblem.NOT_SET_UP
        TranslationFailure.TOO_LONG -> PluginTranslationProblem.TOO_LONG
        TranslationFailure.UNREACHABLE -> PluginTranslationProblem.UNREACHABLE
        TranslationFailure.REFUSED -> PluginTranslationProblem.REFUSED
        TranslationFailure.RATE_LIMITED -> PluginTranslationProblem.RATE_LIMITED
        TranslationFailure.UNSUPPORTED_LANGUAGE -> PluginTranslationProblem.UNSUPPORTED_LANGUAGE
        TranslationFailure.BAD_ANSWER -> PluginTranslationProblem.FAILED
    }
}
