package com.aimdi.xtanative.ui.settings

import com.aimdi.xtanative.core.feed.LanguageFilter
import com.aimdi.xtanative.services.ai.AiConfig
import com.aimdi.xtanative.services.ai.AiProvider
import com.aimdi.xtanative.store.settings.LanguageFilterAction
import com.aimdi.xtanative.store.settings.PostsPrefs
import com.aimdi.xtanative.store.settings.Settings
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
