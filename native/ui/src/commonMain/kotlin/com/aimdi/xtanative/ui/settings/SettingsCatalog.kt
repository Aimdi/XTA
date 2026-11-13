package com.aimdi.xtanative.ui.settings

import com.aimdi.xtanative.core.feed.FeedStrip
import com.aimdi.xtanative.store.settings.BlueskyPrefs
import com.aimdi.xtanative.store.settings.BooruPrefs
import com.aimdi.xtanative.store.settings.DeepmarksPrefs
import com.aimdi.xtanative.store.settings.HomePrefs
import com.aimdi.xtanative.store.settings.ImmichPrefs
import com.aimdi.xtanative.store.settings.KarakeepPrefs
import com.aimdi.xtanative.store.settings.MastodonPrefs
import com.aimdi.xtanative.store.settings.NetworkPrefs
import com.aimdi.xtanative.store.settings.Pref
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.ThreadsPrefs
import com.aimdi.xtanative.store.settings.XtaPrefs

/** The sections of XTA's settings, each a screen at `settings/<id>`. */
enum class SettingsSection(val id: String) {
    GENERAL("general"),
    POSTS("posts"),
    MEDIA("media"),
    HOME("home"),
    THEME("theme"),
    ACCESSIBILITY("accessibility"),
    SPEECH("speech"),
    ACCOUNT("account"),
    DATA("data"),
    SYNC("sync"),
    AI("ai"),
    DIAGNOSTICS("diagnostics"),
    ;

    companion object {
        fun of(id: String?): SettingsSection? = entries.firstOrNull { it.id == id }

        /** Settings' first group, in XTA's order. */
        val everyday = listOf(GENERAL, POSTS, MEDIA, HOME, THEME, ACCESSIBILITY, SPEECH)

        /** Under Advanced, folded away. */
        val advanced = listOf(AI, DIAGNOSTICS)
    }
}

/** What `settings/<id>` shows for a network, as the networks move into plugins (PLUGINS.md). */
enum class NetworkSettingsPlace {
    /** The section built from the network's preferences: a network the app still reads itself. */
    SECTION,

    /** The installed plugin's own settings. */
    PLUGIN,

    /** Nothing: a plugin that is not installed or has no settings, or no network at all. */
    NONE,
}

/**
 * What the plugin store knows, by plugin id: every plugin it lists (installed
 * or on offer), those installed, and the running ones that bring no settings.
 */
data class PluginIds(val listed: Set<String> = emptySet(), val installed: Set<String> = emptySet(), val withoutSettings: Set<String> = emptySet())

/** A network's own section, until its plugin brings one: built from [XtaPrefs.networks]. */
object NetworkSections {
    fun of(id: String?): NetworkPrefs? = XtaPrefs.networks.firstOrNull { it.id == id }

    /**
     * Where network [id]'s settings are. A network that is a plugin now
     * (its id one of [plugins]') has its plugin's own settings while that is
     * installed, and none while it is not, as XTA shows a network's settings
     * only while the network is on; one the app still reads keeps its section.
     */
    fun place(id: String, plugins: PluginIds): NetworkSettingsPlace = when {
        id in plugins.installed && id !in plugins.withoutSettings -> NetworkSettingsPlace.PLUGIN
        id in plugins.listed || id in plugins.installed || of(id) == null -> NetworkSettingsPlace.NONE
        else -> NetworkSettingsPlace.SECTION
    }

    /** What a network asks before it shows anything, as XTA's settings screens name it. */
    fun fields(network: NetworkPrefs): List<NetworkField> = when (network) {
        ThreadsPrefs -> listOf(NetworkField.Text(ThreadsPrefs.instance, NetworkFieldLabel.THREADS_INSTANCE))
        BlueskyPrefs -> listOf(NetworkField.Text(BlueskyPrefs.instance, NetworkFieldLabel.BLUESKY_INSTANCE))
        MastodonPrefs -> listOf(NetworkField.Text(MastodonPrefs.instance, NetworkFieldLabel.MASTODON_INSTANCE))
        BooruPrefs -> listOf(NetworkField.Text(BooruPrefs.host, NetworkFieldLabel.BOORU_HOST))
        KarakeepPrefs -> listOf(
            NetworkField.Text(KarakeepPrefs.serverUrl, NetworkFieldLabel.KARAKEEP_SERVER),
            NetworkField.Text(KarakeepPrefs.apiKey, NetworkFieldLabel.API_KEY, secret = true),
        )
        DeepmarksPrefs -> listOf(
            NetworkField.Text(DeepmarksPrefs.apiBase, NetworkFieldLabel.DEEPMARKS_SERVER),
            NetworkField.Text(DeepmarksPrefs.apiKey, NetworkFieldLabel.API_KEY, secret = true),
            NetworkField.Text(DeepmarksPrefs.secretKey, NetworkFieldLabel.DEEPMARKS_SIGNING_KEY, secret = true),
        )
        ImmichPrefs -> listOf(
            NetworkField.Text(ImmichPrefs.serverUrl, NetworkFieldLabel.IMMICH_SERVER),
            NetworkField.Text(ImmichPrefs.apiKey, NetworkFieldLabel.API_KEY, secret = true),
            NetworkField.Flag(ImmichPrefs.albumPerFolder, NetworkFieldLabel.IMMICH_ALBUM_PER_FOLDER),
            NetworkField.Flag(ImmichPrefs.includeVideos, NetworkFieldLabel.IMMICH_INCLUDE_VIDEOS),
        )
        else -> emptyList()
    }
}

/** One setting of a network's section; [label] names its text. */
sealed interface NetworkField {
    val label: NetworkFieldLabel

    data class Text(val pref: Pref<String>, override val label: NetworkFieldLabel, val secret: Boolean = false) : NetworkField

    data class Flag(val pref: Pref<Boolean>, override val label: NetworkFieldLabel) : NetworkField
}

enum class NetworkFieldLabel {
    THREADS_INSTANCE,
    BLUESKY_INSTANCE,
    MASTODON_INSTANCE,
    BOORU_HOST,
    KARAKEEP_SERVER,
    DEEPMARKS_SERVER,
    DEEPMARKS_SIGNING_KEY,
    IMMICH_SERVER,
    IMMICH_ALBUM_PER_FOLDER,
    IMMICH_INCLUDE_VIDEOS,
    API_KEY,
}

/** The choices XTA's settings offer, where they are not a preference's own. */
object SettingsChoices {
    /** Home's pages a Default tab can name (home_screen.dart's defaultHomePages). */
    val initialTabs = listOf("feed", "subscriptions", "trending", "saved")

    val zenPageCaps = listOf(3, 5, 10, 20)
    val gridColumns = listOf(1, 2, 3, 4, 5)
    val prefetchSeconds = listOf(0, 1, 5, 15, 30, 60)
    val downloadConcurrency = listOf(1, 2, 3, 4)

    /** The text size slider: 1.0 to 1.5 in ten steps. */
    const val TEXT_SCALE_MIN = 1.0f
    const val TEXT_SCALE_MAX = 1.5f
    const val TEXT_SCALE_STEPS = 9

    /**
     * The feeds the Default feed tab offers: Following, For you, then the
     * networks pinned on Home's strip that are on and can sit there.
     */
    fun feedTabs(settings: Settings): List<String> =
        FeedStrip.tabs(settings[HomePrefs.feedStripNetworks]) { id -> stripUsable(settings, id) }

    /** Whether network [id] is on and has a timeline Home can show. */
    fun stripUsable(settings: Settings, id: String): Boolean {
        val network = NetworkSections.of(id) ?: return false
        return network.showTab != null && settings[network.enabled]
    }

    /** The language picker's choices: the system's, then XTA's languages by the name each calls itself. */
    val languages: List<AppLanguage> = listOf(
        AppLanguage("ar", "العربية"), AppLanguage("be", "беларуская"), AppLanguage("be_Latn", "biełaruskaja (łacinka)"),
        AppLanguage("ca", "català"), AppLanguage("cs", "čeština"), AppLanguage("de", "Deutsch"), AppLanguage("en", "English"),
        AppLanguage("eo", "esperanto"), AppLanguage("es", "español"), AppLanguage("et", "eesti"), AppLanguage("eu", "euskara"),
        AppLanguage("fr", "français"), AppLanguage("hi", "हिंदी"), AppLanguage("id", "Bahasa Indonesia"), AppLanguage("it", "italiano"),
        AppLanguage("ja", "日本語"), AppLanguage("ko", "한국어"), AppLanguage("nb_NO", "norsk bokmål (Norge)"), AppLanguage("nl", "Nederlands"),
        AppLanguage("pl", "polski"), AppLanguage("pt", "português"), AppLanguage("pt_BR", "português (Brasil)"), AppLanguage("ro", "română"),
        AppLanguage("ru", "русский"), AppLanguage("tr", "Türkçe"), AppLanguage("uk", "українська"), AppLanguage("vi", "Tiếng Việt"),
        AppLanguage("zh_Hans", "中文 (简体中文)"), AppLanguage("zh_Hant", "中文 (繁體)"),
    ).sortedBy { it.name.lowercase() }

    const val SYSTEM_LANGUAGE = "system"

    /** The name [code] is shown under; null for the system's choice or a code this build has no language for. */
    fun languageName(code: String): String? = languages.firstOrNull { it.code == code }?.name

    /**
     * [code] as a BCP 47 tag the platforms know (`pt-BR`, `zh-Hans`), or null
     * for the system's: XTA keeps `pt_BR` and `zh_Hant`.
     */
    fun languageTag(code: String?): String? = code?.takeIf { it.isNotBlank() && it != SYSTEM_LANGUAGE }?.replace('_', '-')

    /**
     * The region Latin Belarusian is filed under. Compose's resources pick a
     * folder by language and region and cannot name a script
     * (values-b+be+Latn), so the app's and the plugins' Latin Belarusian is in
     * values-be-rXL, XL being one of ISO 3166's private codes (SCREENS.md).
     */
    const val LATIN_BELARUSIAN_REGION = "XL"

    /**
     * The language and region whose resources [code] reads, or null for the
     * system's: the scripts of Chinese are kept under China and Taiwan, Latin
     * Belarusian under [LATIN_BELARUSIAN_REGION].
     */
    fun localeOf(code: String?): Pair<String, String>? {
        val tag = code?.takeIf { it.isNotBlank() && it != SYSTEM_LANGUAGE } ?: return null
        return when (tag) {
            "zh_Hans" -> "zh" to "CN"
            "zh_Hant" -> "zh" to "TW"
            "be_Latn" -> "be" to LATIN_BELARUSIAN_REGION
            else -> tag.substringBefore('_') to tag.substringAfter('_', "")
        }
    }
}

/** One of XTA's languages: its stored [code] and the [name] it calls itself. */
data class AppLanguage(val code: String, val name: String)
