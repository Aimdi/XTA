package com.aimdi.xtanative.store.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsMigrationsTest {
    @Test
    fun aFirstLaunchSetsTheOnceFlagsAndChangesNoDefault() {
        val launched = SettingsMigrations.apply(Settings(), firstLaunch = true)
        listOf(
            MediaPrefs.qualitySplitMigrated, PostsPrefs.collapseRepostsMigrated, MediaPrefs.downloadTypeMigrated,
            GeneralPrefs.openLinksInAppReset, GeneralPrefs.updateCheckReset,
        ).forEach { assertTrue(launched[it], it.key) }
        assertEquals(MediaQuality.MEDIUM, launched[MediaPrefs.imageQuality])
        assertEquals(DownloadType.AUTO, launched[MediaPrefs.downloadType])
        assertTrue(launched[GeneralPrefs.openLinksInApp])
        assertFalse(launched[GeneralPrefs.checkForUpdates])
        assertEquals(launched, SettingsMigrations.apply(launched), "a second launch changes nothing")
    }

    @Test
    fun theFirstLaunchCardsAreDueOnlyOnAFirstLaunch() {
        assertFalse(SettingsMigrations.apply(Settings(), firstLaunch = true)[GeneralPrefs.introSeen])
        assertTrue(SettingsMigrations.apply(Settings(mapOf("theme.true_black" to PrefValue.Bool(false))))[GeneralPrefs.introSeen], "an install from before the cards")
        val midway = SettingsMigrations.apply(Settings(), firstLaunch = true)
        assertFalse(SettingsMigrations.apply(midway)[GeneralPrefs.introSeen], "not seen yet stays not seen")
    }

    @Test
    fun theOldDisabledQualityMeansLoadOnTapAtTheLargestSize() {
        val launched = SettingsMigrations.apply(Settings(mapOf("media.size" to PrefValue.Text("disabled"))))
        assertTrue(launched[MediaPrefs.loadManually])
        assertEquals(MediaQuality.LARGE, launched[MediaPrefs.imageQuality])
        assertEquals(MediaQuality.LARGE, launched[MediaPrefs.videoQuality])
    }

    @Test
    fun theOneQualityBecomesTheVideoQualityToo() {
        val launched = SettingsMigrations.apply(Settings(mapOf("media.size" to PrefValue.Text("small"))))
        assertFalse(launched[MediaPrefs.loadManually])
        assertEquals(MediaQuality.SMALL, launched[MediaPrefs.videoQuality])
    }

    @Test
    fun theQualityIsSplitOnlyOnce() {
        val split = Settings(mapOf("media.size" to PrefValue.Text("small"), "media.video_quality" to PrefValue.Text("large"), "media.quality_split_migrated" to PrefValue.Bool(true)))
        assertEquals(MediaQuality.LARGE, SettingsMigrations.apply(split)[MediaPrefs.videoQuality])
    }

    @Test
    fun repostsStopCollapsingOnceThenTheReaderDecides() {
        val inherited = SettingsMigrations.apply(Settings(mapOf("feed.collapse_boosts" to PrefValue.Bool(true))))
        assertFalse(inherited[PostsPrefs.collapseReposts])
        val chosen = inherited.with(PostsPrefs.collapseReposts, true)
        assertTrue(SettingsMigrations.apply(chosen)[PostsPrefs.collapseReposts])
    }

    @Test
    fun askingWhereToSaveBecomesSavingWithoutAskingUnlessAFolderWasChosen() {
        val asking = Settings(mapOf("download.type" to PrefValue.Text("ask")))
        assertEquals(DownloadType.AUTO, SettingsMigrations.apply(asking)[MediaPrefs.downloadType])
        val withFolder = asking.with(MediaPrefs.downloadTreeUri, "content://tree/primary%3ADownload")
        assertEquals(DownloadType.ASK, SettingsMigrations.apply(withFolder)[MediaPrefs.downloadType])
    }

    @Test
    fun anEarlierReleasesDocumentAddressPointsAtTodaysAndForgetsWhatItRead() {
        val old = Settings()
            .with(DataPrefs.endpointRegistryUrl, "https://raw.githubusercontent.com/Aimdi/XTA/master/endpoints.json")
            .with(DataPrefs.endpointRegistryCache, "{\"ids\":{}}")
            .with(DataPrefs.endpointRegistryFetchedAt, "2026-01-01T00:00:00.000Z")
            .with(DataPrefs.pluginCatalogueUrl, "https://raw.githubusercontent.com/Aimdi/XTA/main/plugins.json")
        val launched = SettingsMigrations.apply(old)
        assertEquals(PublishedDocuments.ENDPOINTS, launched[DataPrefs.endpointRegistryUrl])
        assertEquals("", launched[DataPrefs.endpointRegistryCache])
        assertEquals("", launched[DataPrefs.endpointRegistryFetchedAt])
        assertEquals(PublishedDocuments.PLUGINS, launched[DataPrefs.pluginCatalogueUrl])
        val own = Settings().with(DataPrefs.endpointRegistryUrl, "https://example.org/endpoints.json").with(DataPrefs.endpointRegistryCache, "kept")
        assertEquals("kept", SettingsMigrations.apply(own)[DataPrefs.endpointRegistryCache], "an address set by hand stays")
    }

    @Test
    fun aPresetFromBeforeXLookBecomesItsBackground() {
        val dim = SettingsMigrations.apply(Settings(mapOf("theme.preset" to PrefValue.Text("x_look_dim"))))
        assertEquals(ThemeBackground.DIM, dim[ThemePrefs.background])
        assertEquals(ThemePreset.NONE, dim[ThemePrefs.preset])
        val retired = Settings(mapOf("theme.preset" to PrefValue.Text("fairy_forest"), "theme.x_look.background" to PrefValue.Text("light")))
        assertEquals(ThemeBackground.SYSTEM, SettingsMigrations.apply(retired)[ThemePrefs.background], "a retired preset lands on System")
        val chosen = Settings(mapOf("theme.preset" to PrefValue.Text("none"), "theme.x_look.background" to PrefValue.Text("light")))
        assertEquals(ThemeBackground.LIGHT, SettingsMigrations.apply(chosen)[ThemePrefs.background])
    }

    @Test
    fun linksMoveInsideTheAppAndTheUpdateCheckGoesOffOnlyOnce() {
        val old = Settings(mapOf("open_links_in_embedded_browser" to PrefValue.Bool(false), "should_check_for_updates" to PrefValue.Bool(true)))
        val launched = SettingsMigrations.apply(old)
        assertTrue(launched[GeneralPrefs.openLinksInApp])
        assertFalse(launched[GeneralPrefs.checkForUpdates])
        val chosenAfter = launched.with(GeneralPrefs.openLinksInApp, false).with(GeneralPrefs.checkForUpdates, true)
        val relaunched = SettingsMigrations.apply(chosenAfter)
        assertFalse(relaunched[GeneralPrefs.openLinksInApp], "the reader's later choice is never overridden")
        assertTrue(relaunched[GeneralPrefs.checkForUpdates])
    }
}
