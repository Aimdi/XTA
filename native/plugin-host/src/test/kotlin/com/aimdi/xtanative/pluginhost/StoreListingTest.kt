package com.aimdi.xtanative.pluginhost

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The rules of XTA's plugin store (plugin_catalogue.dart, _plugin_store.dart and their tests), on a downloaded catalogue. */
class StoreListingTest {
    private val api = PluginApi(1)
    private val desktop = PluginPlatform.DESKTOP

    private fun entry(
        id: String,
        category: PluginCategory = PluginCategory.SOCIAL,
        version: String = "1.0.0",
        apiVersion: Int = 1,
        available: Boolean = true,
        isPrivate: Boolean = false,
        platforms: List<PluginPlatform> = PluginPlatform.entries,
    ) = CatalogueEntry(
        id, version, apiVersion, available, isPrivate, category,
        LocalizedText(mapOf("en" to "Catalogue name of $id")), LocalizedText.EMPTY,
        platforms.associateWith { PluginArtifact("https://plugins.example/$id.${it.extension}", "00".repeat(32), 10) },
    )

    private fun installed(id: String, version: String = "1.0.0", apiVersion: Int = 1, category: PluginCategory = PluginCategory.SOCIAL) =
        InstalledPlugin(id, version, apiVersion, "com.example.Plugin", "xta-plugin-$id-$version.jar", "00".repeat(32), 10, 0, category, LocalizedText(mapOf("en" to "Kept name of $id")), LocalizedText.EMPTY)

    private fun list(catalogue: PluginCatalogue?, vararg mine: InstalledPlugin, showPrivate: Boolean = false, platform: PluginPlatform = desktop) =
        storeListing(catalogue, mine.associateBy { it.id }, platform, api, showPrivate)

    private fun catalogue(vararg entries: CatalogueEntry) = PluginCatalogue(1, entries.toList())

    @Test
    fun installedPluginsComeFirstThenWhatIsOnOfferByCategoryInTheCataloguesOrder() {
        val catalogue = catalogue(
            entry("hackernews", PluginCategory.COMMUNITIES),
            entry("threads"),
            entry("pixiv", PluginCategory.ART),
            entry("reddit", PluginCategory.COMMUNITIES),
            entry("weather", PluginCategory.OTHER),
            entry("booru", PluginCategory.ART),
            entry("bluesky"),
        )
        val state = PluginStoreState(list(catalogue, installed("reddit", category = PluginCategory.COMMUNITIES), installed("pixiv", category = PluginCategory.ART)))

        assertEquals(listOf("pixiv", "reddit", "threads", "bluesky", "hackernews", "booru", "weather"), state.plugins.map { it.id })
        assertEquals(listOf("pixiv", "reddit"), state.installed.map { it.id })
        assertEquals(listOf(PluginCategory.SOCIAL, PluginCategory.COMMUNITIES, PluginCategory.ART, PluginCategory.OTHER), state.onOffer.keys.toList())
        assertEquals(listOf("booru"), state.onOffer[PluginCategory.ART]?.map { it.id })
        assertEquals(PluginStatus.Installed("1.0.0"), state["reddit"]?.status)
        assertEquals(PluginStatus.Available, state["threads"]?.status)
    }

    @Test
    fun aPluginHeldBackIsNoLongerOfferedButOneInstalledStaysInstalled() {
        val catalogue = catalogue(entry("reddit", available = false), entry("pixiv", available = false), entry("stocks"))
        val listed = list(catalogue, installed("reddit"))
        assertEquals(listOf("reddit", "stocks"), listed.map { it.id })
        assertEquals(PluginStatus.Installed("1.0.0"), listed.first().status)
    }

    @Test
    fun anInstalledPluginStaysWhenTheCatalogueDropsItOrCannotBeRead() {
        listOf(null, catalogue(), catalogue(entry("stocks"))).forEach { catalogue ->
            val reddit = list(catalogue, installed("reddit")).first()
            assertEquals(PluginStatus.Installed("1.0.0"), reddit.status)
            assertEquals("Kept name of reddit", reddit.name(Locale.ENGLISH), "the name it was installed with")
        }
        val named = list(catalogue(entry("reddit")), installed("reddit")).first()
        assertEquals("Catalogue name of reddit", named.name(Locale.ENGLISH), "the catalogue's name while it gives one")
    }

    @Test
    fun aPrivatePluginIsOfferedOnlyWhilePrivatePluginsAreShownButStaysOnceInstalled() {
        val catalogue = catalogue(entry("immich", PluginCategory.MEDIA, isPrivate = true), entry("karakeep", PluginCategory.BOOKMARKS, isPrivate = true))
        assertEquals(emptyList(), list(catalogue).map { it.id })
        assertEquals(listOf("karakeep", "immich"), list(catalogue, showPrivate = true).map { it.id })
        assertEquals(listOf("immich"), list(catalogue, installed("immich", category = PluginCategory.MEDIA)).map { it.id })
    }

    @Test
    fun aPluginForANewerApiNeedsANewerAppAndOneForAnApiThisAppNoLongerLoadsIsNotOffered() {
        val app = PluginApi(2, 3)
        val catalogue = catalogue(entry("newer", apiVersion = 4), entry("current", apiVersion = 3), entry("older", apiVersion = 1))
        val listed = storeListing(catalogue, emptyMap(), desktop, app, showPrivate = false)
        assertEquals(listOf("newer", "current"), listed.map { it.id })
        assertEquals(PluginStatus.NeedsNewerApp(4), listed.first().status)
        assertEquals(PluginStatus.Available, listed.last().status)
    }

    @Test
    fun aPluginWithoutAFileForThisPlatformIsNotOfferedOnIt() {
        val catalogue = catalogue(entry("tiktok", platforms = listOf(PluginPlatform.ANDROID)))
        assertEquals(emptyList(), list(catalogue).map { it.id })
        assertEquals(listOf("tiktok"), list(catalogue, platform = PluginPlatform.ANDROID).map { it.id })
    }

    @Test
    fun anUpdateIsOfferedOnlyForANewerVersionThisAppCanLoadAndInstall() {
        fun statusWith(entry: CatalogueEntry) = list(catalogue(entry), installed("reddit", version = "1.0.9")).single().status

        assertEquals(PluginStatus.UpdateAvailable("1.0.9", "1.0.10"), statusWith(entry("reddit", version = "1.0.10")))
        assertEquals(PluginStatus.Installed("1.0.9"), statusWith(entry("reddit", version = "1.0.9")))
        assertEquals(PluginStatus.Installed("1.0.9"), statusWith(entry("reddit", version = "1.0.8")), "never moved back")
        assertEquals(PluginStatus.Installed("1.0.9"), statusWith(entry("reddit", version = "2.0.0", available = false)))
        assertEquals(PluginStatus.Installed("1.0.9"), statusWith(entry("reddit", version = "2.0.0", apiVersion = 2)))
        assertEquals(PluginStatus.Installed("1.0.9"), statusWith(entry("reddit", version = "2.0.0", platforms = listOf(PluginPlatform.ANDROID))))
    }

    @Test
    fun anInstalledPluginBuiltForANewerApiThanThisAppsNeedsANewerApp() {
        val listed = list(catalogue(entry("reddit", version = "3.0.0", apiVersion = 2)), installed("reddit", version = "2.0.0", apiVersion = 2))
        assertEquals(PluginStatus.NeedsNewerApp(2, "2.0.0"), listed.single().status)
    }

    @Test
    fun anInstalledPluginForAnApiThisAppNoLongerLoadsSaysSoUntilItsUpdateComes() {
        val app = PluginApi(2)
        fun statusWith(entry: CatalogueEntry) =
            storeListing(catalogue(entry), mapOf("reddit" to installed("reddit", version = "1.0.0", apiVersion = 1)), desktop, app, false).single().status
        assertEquals(PluginStatus.Failed(PluginFailure.NeedsNewerPlugin(1), "1.0.0"), statusWith(entry("reddit", version = "1.0.0", apiVersion = 1)))
        assertEquals(PluginStatus.UpdateAvailable("1.0.0", "1.1.0"), statusWith(entry("reddit", version = "1.1.0", apiVersion = 2)))
    }

    @Test
    fun versionsCompareAsNumbersPartByPart() {
        assertTrue(PluginVersions.compare("1.0.10", "1.0.9") > 0)
        assertTrue(PluginVersions.compare("2", "1.9.9") > 0)
        assertTrue(PluginVersions.compare("1.1-beta", "1.1") < 0)
        assertTrue(PluginVersions.compare("1.1-beta", "1.1-alpha") > 0)
        assertEquals(0, PluginVersions.compare("1.0", "1.0.0"))
    }

    @Test
    fun anApiVersionIsSupportedNewerOrOlder() {
        val app = PluginApi(2, 3)
        assertEquals(null, app.refusal(3))
        assertEquals(PluginFailure.NeedsNewerApp(4), app.refusal(4))
        assertEquals(PluginFailure.NeedsNewerPlugin(1), app.refusal(1))
    }
}
