package com.aimdi.xtanative.plugins.ehviewer

import com.aimdi.xtanative.networks.ehviewer.EhCategory
import com.aimdi.xtanative.networks.ehviewer.EhSettings
import com.aimdi.xtanative.store.settings.EhReadingMode
import com.aimdi.xtanative.store.settings.EhViewerPrefs
import com.aimdi.xtanative.store.settings.Settings
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * What XTA's EH preferences say (eh_client.dart, eh_settings.dart), read
 * from the app's settings under XTA's keys ([EhViewerPrefs]); the pasted
 * cookies are the plugin's own, kept in its folder ([EhCookies]).
 */
internal object EhConfig {
    /**
     * What the client reads with. The site's content warning is always
     * answered, as XTA answers it in every request's `uconfig`: XTA shows
     * nothing before a gallery, and the plugin itself is offered only with
     * the store's private plugins.
     */
    fun client(settings: Settings, cookies: String): EhSettings = EhSettings(
        cookies = cookies,
        useExhentai = settings[EhViewerPrefs.useExhentai],
        categories = categories(settings),
        adultConsent = true,
    )

    /** The front page's categories; none stored is every one. */
    fun categories(settings: Settings): Set<EhCategory> = EhSettings.categoriesFrom(settings[EhViewerPrefs.categories])

    /** [category] chosen or not, as XTA's chips change it: the last one chosen stays, as the site reads none as all. */
    fun withCategory(settings: Settings, category: EhCategory, chosen: Boolean): Settings {
        val now = categories(settings)
        val next = when {
            chosen -> now + category
            now.size > 1 -> now - category
            else -> now
        }
        return settings.with(EhViewerPrefs.categories, categoriesText(next))
    }

    /** The categories as XTA stores them: a JSON list of its names for them, `["doujinshi","artistCg"]`. */
    fun categoriesText(categories: Set<EhCategory>): String =
        JsonArray(EhCategory.entries.filter { it in categories }.map { JsonPrimitive(storedName(it)) }).toString()

    /** XTA's name for [category] (its enum's), which [EhCategory.tryParse] reads back. */
    fun storedName(category: EhCategory): String = when (category) {
        EhCategory.MISC -> "misc"
        EhCategory.DOUJINSHI -> "doujinshi"
        EhCategory.MANGA -> "manga"
        EhCategory.ARTIST_CG -> "artistCg"
        EhCategory.GAME_CG -> "gameCg"
        EhCategory.IMAGE_SET -> "imageSet"
        EhCategory.COSPLAY -> "cosplay"
        EhCategory.ASIAN_PORN -> "asianPorn"
        EhCategory.NON_H -> "nonH"
        EhCategory.WESTERN -> "western"
    }

    fun readingMode(settings: Settings): EhReadingMode = settings[EhViewerPrefs.readingMode]

    fun preferJapanese(settings: Settings): Boolean = settings[EhViewerPrefs.preferJapanese]

    fun keepScreenOn(settings: Settings): Boolean = settings[EhViewerPrefs.keepScreenOn]
}
