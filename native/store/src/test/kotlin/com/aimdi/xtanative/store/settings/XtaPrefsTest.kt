package com.aimdi.xtanative.store.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class XtaPrefsTest {
    /** Every key XTA declares, from its `constants.dart` and the files that keep their own (generated from XTA). */
    private val xtaKeys = resource("/settings/xta_keys.txt").lines().filter { it.isNotBlank() }

    /** What XTA writes at every launch (`setDefaultValues` in `main.dart`, a first launch's), as JSON. */
    private val xtaSeeds = Json.parseToJsonElement(resource("/settings/xta_seeds.json")) as JsonObject

    @Test
    fun everyPreferenceXtaHasIsDeclaredAndNoneIsMadeUp() {
        assertEquals(emptyList(), xtaKeys.filterNot(XtaPrefs::isKnown), "XTA keys without a declaration")
        assertEquals(emptyList(), XtaPrefs.all.map { it.key }.filterNot { it in xtaKeys }, "declared keys XTA does not have")
        assertEquals(XtaPrefs.all.size, XtaPrefs.all.map { it.key }.toSet().size, "no two declarations share a key")
    }

    @Test
    fun whatANetworkWritesOfAnothersIsThatNetworksOwn() {
        assertEquals(listOf("plugin.rss.read_ids"), WebSourcesPrefs.shared.map { it.key }, "the RSS reader marks a web source's post read among RSS's, as XTA does")
        XtaPrefs.networks.forEach { network ->
            network.shared.forEach { pref -> assertTrue(XtaPrefs.networks.any { it !== network && pref in it.prefs }, "${network.id} writes ${pref.key}, which another network declares") }
        }
    }

    @Test
    fun theDefaultsXtaWritesAtLaunchAreTheSeeds() {
        val seeded = XtaPrefs.all.filter { it.seed != null }.associate { it.key to it.seed }
        assertEquals(xtaSeeds.keys, seeded.keys, "the keys XTA seeds")
        xtaSeeds.forEach { (key, value) -> assertEquals(PrefValue.fromJson(value), seeded[key], key) }
    }

    @Test
    fun everySeedReadsAsItsDefault() {
        val settings = Settings()
        XtaPrefs.all.filter { it.seed != null }.forEach { pref -> assertEquals(pref.default, pref.read(pref.seed), pref.key) }
        assertEquals(ThemeBackground.SYSTEM, settings[ThemePrefs.background])
        assertEquals(listOf("feed", "subscriptions", "trending", "saved"), settings[HomePrefs.pages])
        assertEquals(0.45, settings[SpeechPrefs.rate])
        assertEquals(listOf("day", "week", "day_male", "day_female"), settings[PixivPrefs.novelRankingModes])
        assertEquals(emptyList(), settings[SavedPrefs.tabOrder], "XTA seeds an empty string, which is no order")
    }

    @Test
    fun theUnseededOnesReadAsXtasFallbacks() {
        val settings = Settings()
        assertFalse(settings[ThreadsPrefs.enabled], "a network is off until installed")
        assertTrue(settings[ThreadsPrefs.showTab], "its tab shows unless hidden")
        assertEquals("https://xy-threads.fly.dev", settings[ThreadsPrefs.apiBase])
        assertEquals("", settings[GeneralPrefs.shareBaseUrl], "empty shares x.com links")
        assertTrue(settings[HomePrefs.feedStripHandPicked], "XTA marks a first launch's strip as hand-picked")
        assertEquals(1.0, settings[SubstackPrefs.podcastSpeed])
        assertFalse(settings[PostsPrefs.catchUp["g1"]])
        assertEquals(emptyList(), settings[HomePrefs.topBarPins["reddit"]])
    }

    @Test
    fun theFamiliesNameWhatTheirKeysAreKeptFor() {
        assertEquals("feed.catch_up_mode.g1", PostsPrefs.catchUp["g1"].key)
        assertEquals("g1", PostsPrefs.catchUp.idOf("feed.catch_up_mode.g1"))
        assertNull(PostsPrefs.catchUp.idOf("feed.catch_up_mode."))
        assertEquals("reddit", HomePrefs.topBarPins.idOf("home.top_bar_pins.reddit"))
        assertTrue(XtaPrefs.isKnown("home.top_bar_pins.reddit"))
        assertFalse(XtaPrefs.isKnown("home.nothing"))
    }

    @Test
    fun theNetworksAreXtasPluginsInRegistryOrderWithTheirSwitches() {
        assertEquals(
            listOf(
                "threads", "bluesky", "mastodon", "tiktok", "instagram", "reddit", "hackernews", "substack", "rss",
                "websources", "pixiv", "booru", "ehviewer", "stocks", "karakeep", "deepmarks", "immich",
            ),
            XtaPrefs.networks.map { it.id },
        )
        XtaPrefs.networks.forEach { network ->
            assertEquals("plugin.${network.id}.enabled", network.enabled.key)
            network.showTab?.let { assertEquals("plugin.${network.id}.show_tab", it.key) }
            network.inHomeFeed?.let { assertEquals("plugin.${network.id}.in_home_feed", it.key) }
        }
        assertEquals(
            listOf("threads", "bluesky", "mastodon", "reddit", "rss", "websources", "booru"),
            XtaPrefs.networks.filter { it.inHomeFeed != null }.map { it.id },
        )
        assertEquals(listOf("karakeep", "deepmarks", "immich"), XtaPrefs.networks.filter { it.showTab == null }.map { it.id })
    }

    @Test
    fun credentialsAreNamedAndCaughtByTheirShape() {
        listOf("crash.github_token", "sync.webdav.password", "ai.api_key", "plugin.pixiv.user_id", "plugin.pixiv.download_index", "plugin.tiktok.cookies")
            .forEach { assertTrue(SecretPrefs.isSecret(it), it) }
        listOf("plugin.booru.site_credentials_api_key", "plugin.someday.api_key", "plugin.someday.secret_key", "x.refresh_token", "y.password")
            .forEach { assertTrue(SecretPrefs.isSecret(it), "$it by its shape") }
        listOf("theme.true_black", "plugin.booru.login", "plugin.hackernews.follows").forEach { assertFalse(SecretPrefs.isSecret(it), it) }
        assertNotNull(XtaPrefs.find("plugin.threads.direct.cookies_token")?.takeIf { SecretPrefs.isSecret(it.key) })
    }

    private fun resource(path: String): String = checkNotNull(javaClass.getResource(path)) { path }.readText()
}
