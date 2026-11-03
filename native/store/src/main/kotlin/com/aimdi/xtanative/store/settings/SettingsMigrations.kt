package com.aimdi.xtanative.store.settings

/**
 * What XTA does to its preferences at every launch, in its order. Each step
 * is done once (behind its own flag) or changes nothing the second time, so
 * running them all again is always safe.
 *
 * Left to the networks' own stores: Home's feed strip from before it was
 * hand-picked (it asks every network whether it is on and has a tab) and the
 * booru credentials' host (it needs the booru's host rules).
 */
object SettingsMigrations {
    private val steps: List<(Settings) -> Settings> = listOf(
        ::splitMediaQuality,
        ::collapseRepostsOff,
        ::downloadWithoutAsking,
        ::repointPublishedDocuments,
        ::themePresetToBackground,
        ::linksInAppOnce,
        ::updateCheckOffOnce,
    )

    /** [firstLaunch]: no settings were ever stored. */
    fun apply(settings: Settings, firstLaunch: Boolean = false): Settings =
        steps.fold(introSeen(settings, firstLaunch)) { current, step -> step(current) }

    /**
     * The first-launch cards are still to be seen on a first launch; an
     * install that ran before has seen them, even one from before they existed.
     */
    private fun introSeen(settings: Settings, firstLaunch: Boolean): Settings =
        if (settings.isSet(GeneralPrefs.introSeen)) settings else settings.with(GeneralPrefs.introSeen, !firstLaunch)

    /** The one quality became an image and a video quality; the old `disabled` meant load on tap, at the largest size. */
    private fun splitMediaQuality(settings: Settings): Settings {
        if (settings[MediaPrefs.qualitySplitMigrated]) return settings
        val previous = (settings.values[MediaPrefs.imageQuality.key] as? PrefValue.Text)?.value
        val disabled = previous == "disabled"
        val quality = PrefValue.Text(if (disabled) MediaQuality.LARGE.stored else previous ?: MediaQuality.MEDIUM.stored)
        return settings.with(MediaPrefs.loadManually, disabled)
            .with(MediaPrefs.imageQuality.key, quality)
            .with(MediaPrefs.videoQuality.key, quality)
            .with(MediaPrefs.qualitySplitMigrated, true)
    }

    /** Reposts fill the timeline as full posts, once, for installs that inherited the old default. */
    private fun collapseRepostsOff(settings: Settings): Settings = when {
        settings[PostsPrefs.collapseRepostsMigrated] -> settings
        else -> settings.with(PostsPrefs.collapseReposts, false).with(PostsPrefs.collapseRepostsMigrated, true)
    }

    /** Installs left on "ask" without a folder chosen save without asking, once. */
    private fun downloadWithoutAsking(settings: Settings): Settings {
        if (settings[MediaPrefs.downloadTypeMigrated]) return settings
        val folderChosen = listOf(MediaPrefs.downloadTreeUri, MediaPrefs.downloadPath).any { settings[it].isNotEmpty() }
        val moved = if (settings[MediaPrefs.downloadType] == DownloadType.ASK && !folderChosen) settings.with(MediaPrefs.downloadType, DownloadType.AUTO) else settings
        return moved.with(MediaPrefs.downloadTypeMigrated, true)
    }

    /** An address an earlier release saved as its default points at today's; the query ids read from the old one are forgotten. */
    private fun repointPublishedDocuments(settings: Settings): Settings {
        val endpoints = repoint(settings, DataPrefs.endpointRegistryUrl, PublishedDocuments.ENDPOINTS)
        val cleared = when {
            endpoints === settings -> endpoints
            else -> endpoints.with(DataPrefs.endpointRegistryCache, "").with(DataPrefs.endpointRegistryFetchedAt, "")
        }
        return repoint(cleared, DataPrefs.pluginCatalogueUrl, PublishedDocuments.PLUGINS)
    }

    private fun repoint(settings: Settings, pref: Pref<String>, current: String): Settings = when {
        settings.isSet(pref) && PublishedDocuments.isFormerDefault(settings[pref], current) -> settings.with(pref, current)
        else -> settings
    }

    /** A preset from before X Look becomes its background; the retired ones land on System. */
    private fun themePresetToBackground(settings: Settings): Settings {
        val stored = (settings.values[ThemePrefs.preset.key] as? PrefValue.Text)?.value ?: return settings
        if (stored == ThemePreset.NONE.stored) return settings
        val background = ThemePreset.entries.firstOrNull { it.stored == stored }?.background ?: ThemeBackground.SYSTEM
        return settings.with(ThemePrefs.background, background).with(ThemePrefs.preset, ThemePreset.NONE)
    }

    /** Links open in the app, set once: a stored value wins over a default, and every install had one stored. */
    private fun linksInAppOnce(settings: Settings): Settings = when {
        settings[GeneralPrefs.openLinksInAppReset] -> settings
        else -> settings.with(GeneralPrefs.openLinksInApp, true).with(GeneralPrefs.openLinksInAppReset, true)
    }

    /** The update check goes off once; turned back on afterwards, it stays on. */
    private fun updateCheckOffOnce(settings: Settings): Settings = when {
        settings[GeneralPrefs.updateCheckReset] -> settings
        else -> settings.with(GeneralPrefs.checkForUpdates, false).with(GeneralPrefs.updateCheckReset, true)
    }
}
