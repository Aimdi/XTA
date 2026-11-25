package com.aimdi.xtanative.store.settings

import com.aimdi.xtanative.core.feed.KeywordAction
import com.aimdi.xtanative.core.feed.MutedKeyword
import com.aimdi.xtanative.core.feed.MutedKeywords
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class SettingsTest {
    @Test
    fun aSetValueReadsBackTypedAndUnsetOnesAreDefaults() {
        val settings = Settings()
            .with(ThemePrefs.background, ThemeBackground.DIM)
            .with(MediaPrefs.gridColumns, 4)
            .with(AccessibilityPrefs.textScale, 1.2)
            .with(HomePrefs.pages, listOf("feed", "saved"))
        assertEquals(ThemeBackground.DIM, settings[ThemePrefs.background])
        assertEquals(4, settings[MediaPrefs.gridColumns])
        assertEquals(1.2, settings[AccessibilityPrefs.textScale])
        assertEquals(listOf("feed", "saved"), settings[HomePrefs.pages])
        assertEquals(PrefValue.Text("dim"), settings.values["theme.x_look.background"], "stored as XTA stores it")
        assertEquals(ThemeAccent.BLUE, settings[ThemePrefs.accent])
        assertTrue(settings.isSet(MediaPrefs.gridColumns))
        assertFalse(settings.without(MediaPrefs.gridColumns).isSet(MediaPrefs.gridColumns))
    }

    @Test
    fun aValueOfAnotherKindOrOutsideTheChoicesReadsAsTheDefault() {
        val settings = Settings(
            mapOf(
                "accessibility.disable_animations" to PrefValue.Text("true"),
                "theme.x_look.accent" to PrefValue.Text("teal"),
                "media.grid_columns" to PrefValue.Whole(7),
                "other.zen_mode_page_cap" to PrefValue.Real(10.0),
                "plugin.substack.podcast.speed" to PrefValue.Real(3.0),
            ),
        )
        assertFalse(settings[AccessibilityPrefs.disableAnimations])
        assertEquals(ThemeAccent.BLUE, settings[ThemePrefs.accent])
        assertEquals(3, settings[MediaPrefs.gridColumns])
        assertEquals(5, settings[PostsPrefs.zenPageCap])
        assertEquals(1.0, settings[SubstackPrefs.podcastSpeed])
    }

    @Test
    fun theInterfaceSizeIsOneOfItsPresetsElseTheDesktopsOwn() {
        assertEquals(1.0, Settings()[DesktopPrefs.uiScale])
        assertEquals(1.25, Settings().with(DesktopPrefs.uiScale, 1.25)[DesktopPrefs.uiScale])
        assertEquals(2.0, Settings(mapOf("desktop.ui_scale" to PrefValue.Whole(2)))[DesktopPrefs.uiScale], "a whole number is a preset too")
        assertEquals(1.0, Settings(mapOf("desktop.ui_scale" to PrefValue.Real(1.3)))[DesktopPrefs.uiScale], "between presets")
        assertEquals(1.0, Settings(mapOf("desktop.ui_scale" to PrefValue.Real(5.0)))[DesktopPrefs.uiScale], "past them")
        assertEquals(1.0, Settings(mapOf("desktop.ui_scale" to PrefValue.Real(Double.NaN)))[DesktopPrefs.uiScale])
        assertNull(DesktopPrefs.uiScale.seed, "nothing written until chosen")
        assertEquals(DesktopPrefs.UI_SCALES.sorted(), DesktopPrefs.UI_SCALES)
    }

    @Test
    fun aDoubleTakesAWholeNumberAndStaysInItsRange() {
        val settings = Settings(
            mapOf(
                "plugin.pixiv.detail_split" to PrefValue.Whole(1),
                "accessibility.text_scale_factor" to PrefValue.Real(2.0),
                "tts.rate" to PrefValue.Real(0.05),
            ),
        )
        assertEquals(0.9, settings[PixivPrefs.detailSplit], "XTA clamps the split, whole number or not")
        assertEquals(1.5, settings[AccessibilityPrefs.textScale])
        assertEquals(0.2, settings[SpeechPrefs.rate])
    }

    @Test
    fun theBooruTakesTheOlderNamesXtaReads() {
        val settings = Settings(mapOf("plugin.booru.max_rating" to PrefValue.Text("Explicit"), "plugin.booru.engine" to PrefValue.Text("gelbooru")))
        assertEquals(BooruRating.EXPLICIT, settings[BooruPrefs.maxRating])
        assertEquals(BooruEngine.GELBOORU_V2, settings[BooruPrefs.engine])
        assertEquals(PrefValue.Text("e"), settings.with(BooruPrefs.maxRating, BooruRating.EXPLICIT).values["plugin.booru.max_rating"])
    }

    @Test
    fun aListReadsFromEveryShapeXtaHasLeftOne() {
        fun pages(value: PrefValue) = Settings(mapOf("home.pages" to value))[HomePrefs.pages]
        assertEquals(listOf("reddit", "rss"), pages(PrefValue.Texts(listOf("reddit", "rss"))), "a real list")
        assertEquals(listOf("feed", "saved"), pages(PrefValue.Text("""["feed","saved"]""")), "a JSON array in a string")
        assertEquals(listOf("feed", "saved"), pages(PrefValue.Text("feed, saved")), "comma-separated")
        assertEquals(listOf("feed"), pages(PrefValue.Text("feed")), "one bare word")
        assertEquals(emptyList(), pages(PrefValue.Text("")))
        assertEquals(listOf("feed", "subscriptions", "trending", "saved"), pages(PrefValue.Text("5")), "JSON that is no list")
    }

    @Test
    fun theMediaQualityReadsEachStoredWordAndTheDefaultForAnyOther() {
        fun quality(raw: String) = Settings(mapOf("media.size" to PrefValue.Text(raw)))[MediaPrefs.imageQuality]
        MediaQuality.entries.forEach { assertEquals(it, quality(it.stored)) }
        listOf("meduim", "", "disabled").forEach { assertEquals(MediaQuality.MEDIUM, quality(it), it) }
        assertEquals(listOf("thumb", "small", "medium", "large"), MediaQuality.entries.map { it.stored })
    }

    @Test
    fun aJsonListIsWrittenAsTheJsonXtaWrites() {
        val settings = Settings().with(AccountPrefs.disabledAccountIds, listOf("a", "b"))
        assertEquals(PrefValue.Text("""["a","b"]"""), settings.values["home.feed_disabled_account_ids"])
        assertEquals(listOf("flutter"), Settings(mapOf("plugin.threads.search_history" to PrefValue.Text("""["flutter",3]""")))[ThreadsPrefs.searchHistory])
    }

    @Test
    fun preferencesAreWhatXtasWouldHold() {
        val settings = Settings(
            mapOf(
                "theme.x_look.background" to PrefValue.Text("dim"),
                "accessibility.text_scale_factor" to PrefValue.Text("big"),
                "plugin.someday.option" to PrefValue.Bool(true),
            ),
        )
        val preferences = settings.preferences()
        assertEquals(PrefValue.Text("dim"), preferences["theme.x_look.background"])
        assertEquals(PrefValue.Real(1.0), preferences["accessibility.text_scale_factor"], "XTA's launch replaces a value of the wrong kind")
        assertEquals(PrefValue.Bool(true), preferences["plugin.someday.option"], "a key this build does not know travels on")
        assertEquals(PrefValue.Bool(true), preferences["confirm_close"], "a default XTA seeds")
        assertNull(preferences["share_base_url"], "XTA seeds no share address")
    }

    @Test
    fun anExportOfSettingsKeepsThemAndStripsCredentials() {
        val settings = exportSample()
        val exported = settings.exported(includeSettings = true, includeSubscriptions = false)!!
        assertEquals(JsonPrimitive(true), exported["theme.true_black"])
        assertFalse("plugin.pixiv.access_token" in exported)
        assertFalse("ai.api_key" in exported)
        assertFalse("plugin.booru.site_credentials_api_key" in exported)
        assertEquals(JsonPrimitive("""["author"]"""), exported["plugin.hackernews.follows"])
    }

    @Test
    fun theProxySettingsStayOnThisDevice() {
        val settings = Settings()
            .with(DesktopPrefs.proxyMode, ProxyMode.MANUAL).with(DesktopPrefs.proxyHost, "proxy.lan").with(DesktopPrefs.proxyPort, 3128)
            .with(DesktopPrefs.proxyUser, "me").with(DesktopPrefs.proxyPassword, "pw").with(DesktopPrefs.proxyBypass, "nas.lan")
            .with(DesktopPrefs.closeToTray, true)
        val exported = settings.exported()!!
        assertTrue(DesktopPrefs.closeToTray.key in exported, "the desktop's other settings still travel")
        DesktopPrefs.proxy.forEach { assertFalse(it.key in exported, it.key) }
        val restored = Settings().with(DesktopPrefs.proxyHost, "mine").restoring(mapOf(DesktopPrefs.proxyHost.key to JsonPrimitive("theirs"), DesktopPrefs.proxyMode.key to JsonPrimitive("none")))
        assertEquals("mine", restored[DesktopPrefs.proxyHost])
        assertEquals(ProxyMode.SYSTEM, restored[DesktopPrefs.proxyMode])
    }

    @Test
    fun aSubscriptionsOnlyExportCarriesTheAccountsKeptInPreferences() {
        val selected = exportSample().exported(includeSettings = false, includeSubscriptions = true)!!
        assertEquals(setOf("plugin.hackernews.follows", "plugin.websources.follows", "plugin.pixiv.group_subscriptions"), selected.keys)
        assertEquals(JsonPrimitive("""[{"id":"123","name":"Artist","screen_name":"artist"}]"""), selected["plugin.pixiv.group_subscriptions"])
    }

    @Test
    fun anExportOfNeitherCarriesNoSettings() {
        assertNull(exportSample().exported(includeSettings = false, includeSubscriptions = false))
    }

    @Test
    fun theWordsMutedInEveryFeedTravelWithABackupAsTheyWereStored() {
        val words = listOf(MutedKeyword("spoilers", Instant.parse("2026-01-08T00:00:00Z"), KeywordAction.FOLD), MutedKeyword("black friday"))
        assertEquals("", Settings()[PostsPrefs.mutedWords])
        assertNull(Settings().preferences()["feed.muted_words"], "XTA has no such key, so none is seeded")
        val settings = Settings().with(PostsPrefs.mutedWords, MutedKeywords.encodeStored(words))
        assertEquals(words, MutedKeywords.parseStored(settings[PostsPrefs.mutedWords]))
        val exported = settings.exported(includeSettings = true, includeSubscriptions = false)!!
        val restored = Settings().restoring(exported)
        assertEquals(words, MutedKeywords.parseStored(restored[PostsPrefs.mutedWords]))
        assertFalse("feed.muted_words" in settings.exported(includeSettings = false, includeSubscriptions = true).orEmpty(), "a setting, not a subscription")
    }

    @Test
    fun aRestoreAppliesTheSettingsAsXtasImportDoes() {
        // What XTA's prefsForImport gave for these, dumped from XTA: {"home.pages":["feed","saved"],"theme.true_black":false,"gone":null}
        val incoming = mapOf<String, JsonElement>(
            "home.pages" to JsonArray(listOf(JsonPrimitive("feed"), JsonPrimitive(1), JsonPrimitive("saved"))),
            "plugin.pixiv.access_token" to JsonPrimitive("x"),
            "theme.true_black" to JsonPrimitive(false),
            "gone" to JsonNull,
            "shape" to JsonObject(emptyMap()),
        )
        val before = Settings(mapOf("gone" to PrefValue.Text("here"), "plugin.pixiv.access_token" to PrefValue.Text("mine")))
        val restored = before.restoring(incoming)
        assertEquals(listOf("feed", "saved"), restored[HomePrefs.pages])
        assertFalse(restored[ThemePrefs.trueBlack])
        assertFalse("gone" in restored.values, "null takes the key away")
        assertFalse("shape" in restored.values, "preferences cannot hold an object")
        assertEquals("mine", restored[PixivPrefs.accessToken], "another device's credential never pairs with this one's state")
    }

    @Test
    fun aRestoreNeverMovesAnAddressThisDevicesSecretIsSentTo() {
        val incoming = mapOf<String, JsonElement>(
            "ai.base_url" to JsonPrimitive("https://elsewhere.example"),
            "plugin.immich.server_url" to JsonPrimitive("https://elsewhere.example"),
            "plugin.booru.credentials_host" to JsonPrimitive("https://elsewhere.example"),
        )
        val keyed = Settings()
            .with(AiPrefs.baseUrl, "https://mine.example").with(AiPrefs.apiKey, "sk-mine")
            .with(BooruPrefs.credentialsHost, "https://danbooru.donmai.us").with(BooruPrefs.apiKey, "k")
        val restored = keyed.restoring(incoming)
        assertEquals("https://mine.example", restored[AiPrefs.baseUrl], "the reader's key stays with the reader's server")
        assertEquals("https://danbooru.donmai.us", restored[BooruPrefs.credentialsHost])
        assertEquals("https://elsewhere.example", restored[ImmichPrefs.serverUrl], "no Immich key here: the backup's address is taken")
        assertEquals("https://elsewhere.example", Settings().restoring(incoming)[AiPrefs.baseUrl], "nor on a device with no key")
    }

    @Test
    fun xtasOwnExportedSettingsReadTyped() {
        val section = (Json.parseToJsonElement(resource("/backup/xta_full.json")) as JsonObject)["settings"] as JsonObject
        val settings = Settings().restoring(section)
        assertEquals(ThemeBackground.DIM, settings[ThemePrefs.background])
        assertEquals(1.2, settings[AccessibilityPrefs.textScale])
        assertEquals(4, settings[MediaPrefs.gridColumns])
        assertEquals(listOf("feed", "saved", "reddit"), settings[HomePrefs.pages])
        assertEquals(0.45, settings[SpeechPrefs.rate])
        assertFalse(settings[GeneralPrefs.checkForUpdates])
        assertEquals("""{"x":["flutter"]}""", settings[SearchPrefs.recent])
        assertEquals(section, JsonObject(settings.values.mapValues { it.value.toJson() }), "and go back out as they came in")
    }

    private fun exportSample() = Settings(
        mapOf(
            "plugin.pixiv.group_subscriptions" to PrefValue.Text("""[{"id":"123","name":"Artist","screen_name":"artist"}]"""),
            "plugin.hackernews.follows" to PrefValue.Text("""["author"]"""),
            "plugin.pixiv.access_token" to PrefValue.Text("secret"),
            "ai.api_key" to PrefValue.Text("sk-secret"),
            "plugin.booru.site_credentials_api_key" to PrefValue.Text("{}"),
            "theme.true_black" to PrefValue.Bool(true),
        ),
    )

    private fun resource(path: String): String = checkNotNull(javaClass.getResource(path)) { path }.readText()
}
