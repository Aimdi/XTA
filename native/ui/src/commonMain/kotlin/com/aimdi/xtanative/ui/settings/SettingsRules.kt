package com.aimdi.xtanative.ui.settings

import com.aimdi.xtanative.core.feed.LanguageFilter
import com.aimdi.xtanative.services.ai.AiConfig
import com.aimdi.xtanative.services.ai.AiProvider
import com.aimdi.xtanative.store.settings.AccessibilityPrefs
import com.aimdi.xtanative.store.settings.DesktopPrefs
import com.aimdi.xtanative.store.settings.GeneralPrefs
import com.aimdi.xtanative.store.settings.LanguageFilterAction
import com.aimdi.xtanative.store.settings.MediaPrefs
import com.aimdi.xtanative.store.settings.ThemePrefs
import com.aimdi.xtanative.store.settings.PostsPrefs
import com.aimdi.xtanative.store.settings.Settings
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.text.Collator
import java.util.Locale

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

/**
 * The language filter's picker: every language by its name, the ones kept
 * at the top, saved as the codes the filter reads (`en,fr,ja`). A code the
 * list has no language for (typed before there was a list) stays, by its code.
 */
object LanguagePicks {
    /** Every two-letter language code there is a name for. */
    val known: List<String> by lazy { Locale.getISOLanguages().toList() }

    /** The codes [saved] keeps, each once (an old code and its new one being one: `iw`, `he`). */
    fun chosen(saved: String): List<String> = LanguageFilter.parseLanguages(saved).distinctBy(LanguageFilter::canonical)

    /** What [chosen] is saved as. */
    fun saved(chosen: List<String>): String = chosen.joinToString(",")

    /** Language [code]'s name for a [reader]; the code itself where there is none. */
    fun name(code: String, reader: Locale): String =
        Locale.forLanguageTag(code).getDisplayLanguage(reader).takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) }
            ?.replaceFirstChar { it.titlecase(reader) } ?: code

    /** What finds [code] in the list's filter: its name for the [reader], in English and in itself, and the code. */
    fun terms(code: String, reader: Locale): List<String> {
        val locale = Locale.forLanguageTag(code)
        return listOf(name(code, reader), locale.getDisplayLanguage(Locale.ENGLISH), locale.getDisplayLanguage(locale), code).distinct()
    }

    /** The list's order: [chosen] first as picked, then every other of [known] by its name for the [reader]. */
    fun options(chosen: List<String>, reader: Locale, known: List<String> = this.known): List<String> {
        val byName = Collator.getInstance(reader)
        return chosen + known.filter { code -> chosen.none { LanguageFilter.canonical(it) == LanguageFilter.canonical(code) } }.sortedWith(compareBy(byName) { name(it, reader) })
    }
}

/**
 * Which settings this platform's pages and search show: none that do
 * nothing here. The desktop's window has no screenshot block, haptics or
 * status bar, and its video player has neither Android's direct hardware
 * decoding nor its audio focus to share with other apps; Android has none of
 * the desktop's notifications, tray, autostart, interface size or proxy.
 * The bottom bar's labels count only where a desktop window has that bar,
 * one too narrow for the frame's navigation beside its screens ([framed]).
 */
object PlatformSettings {
    val ANDROID_ONLY: Set<String> = setOf(
        GeneralPrefs.disableScreenshots.key,
        AccessibilityPrefs.haptics.key,
        ThemePrefs.hideStatusBar.key,
        MediaPrefs.directHardwareDecoding.key,
        MediaPrefs.backgroundPlaybackWithOtherApps.key,
    )

    val DESKTOP_ONLY: Set<String> = setOf(
        DesktopPrefs.notifications.key, DesktopPrefs.unreadOutside.key, DesktopPrefs.mediaDetails.key, DesktopPrefs.closeToTray.key, START_AT_LOGIN_KEY, DesktopPrefs.uiScale.key, DesktopPrefs.proxyMode.key,
    )

    /** The settings of the bottom bar alone, which a desktop window beside the frame's navigation does not have. */
    val BOTTOM_BAR_ONLY: Set<String> = setOf(ThemePrefs.showNavigationLabels.key)

    /** Whether the setting [key] is shown: on the [desktop] or Android, in a window [framed] by the navigation or not. */
    fun shown(key: String, desktop: Boolean, framed: Boolean): Boolean = when {
        desktop -> key !in ANDROID_ONLY && !(framed && key in BOTTOM_BAR_ONLY)
        else -> key !in DESKTOP_ONLY
    }
}
