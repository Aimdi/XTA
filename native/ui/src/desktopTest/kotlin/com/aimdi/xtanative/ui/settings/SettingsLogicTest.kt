package com.aimdi.xtanative.ui.settings

import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.services.ai.AiProvider
import com.aimdi.xtanative.store.settings.HomePrefs
import com.aimdi.xtanative.store.settings.LanguageFilterAction
import com.aimdi.xtanative.store.settings.MastodonPrefs
import com.aimdi.xtanative.store.settings.MediaPrefs
import com.aimdi.xtanative.store.settings.PostsPrefs
import com.aimdi.xtanative.store.settings.RedditPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.KarakeepPrefs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsSearchTest {
    private val controls = listOf(
        SettingsControl(SettingsSection.MEDIA, "media.mute", "Mute videos", "Whether videos should be muted by default"),
        SettingsControl(SettingsSection.MEDIA, "media.loop", "Loop videos", "If enabled, videos will loop after completing"),
        SettingsControl(SettingsSection.POSTS, "other.zen_mode", "Zen mode", "A calmer feed"),
    )

    @Test
    fun everyWordMustBeFoundAndATitleStartingWithTheFirstComesFirst() {
        assertEquals(listOf("media.loop", "media.mute"), SettingsSearch.controls(controls, "videos").map { it.key })
        assertEquals(listOf("media.mute"), SettingsSearch.controls(controls, "  MUTE ").map { it.key })
        val quality = SettingsControl(SettingsSection.MEDIA, "media.video_quality", "Video quality", "Lower quality streams")
        assertEquals(listOf("media.video_quality", "media.loop", "media.mute"), SettingsSearch.controls(controls + quality, "video").map { it.key })
        assertEquals(listOf("media.loop"), SettingsSearch.controls(controls, "loop after").map { it.key })
        assertTrue(SettingsSearch.controls(controls, "   ").isEmpty())
    }

    @Test
    fun sectionsMatchTheirTitleOrDescription() {
        val entries = listOf(SettingsEntry("general", "General", "Language, updates, and sharing"), SettingsEntry("media", "Media", "Image and video quality"))
        assertEquals(listOf("media"), SettingsSearch.entries(entries, "VIDEO").map { it.target })
        assertTrue(SettingsSearch.entries(entries, "").isEmpty())
    }
}

class HomePagesTest {
    @Test
    fun theSavedOrderComesFirstThenTheRestAndAnUnseededNetworkJoinsOnce() {
        val settings = Settings().with(HomePrefs.pages, listOf("saved", "feed", "gone")).with(RedditPrefs.enabled, true)
        val loaded = HomePages.load(settings, listOf("g1"))
        assertEquals(listOf("saved", "feed", "subscriptions", "trending", "reddit", "group-g1"), loaded.pages.map { it.id })
        assertEquals(listOf("saved", "feed", "reddit"), loaded.saved)
        assertEquals(listOf("reddit"), loaded.seeded)
        // Once seeded, taking it off sticks.
        val again = HomePages.load(settings.with(HomePrefs.pages, listOf("saved", "feed")).with(HomePrefs.seededNetworkTabs, loaded.seeded), emptyList())
        assertFalse(again.pages.first { it.id == "reddit" }.selected)
    }

    @Test
    fun aNetworkWithoutItsTabOrSwitchedOffIsNoPage() {
        val off = Settings().with(RedditPrefs.enabled, true).with(RedditPrefs.showTab, false)
        assertFalse("reddit" in HomePages.available(off, emptyList()))
        assertFalse("karakeep" in HomePages.available(Settings().with(KarakeepPrefs.enabled, true), emptyList()))
    }

    @Test
    fun aBarWithNothingTickedGetsHomesFourBack() {
        val loaded = HomePages.load(Settings().with(HomePrefs.pages, listOf("nothing")), emptyList())
        assertEquals(HomePages.DEFAULTS, loaded.saved)
    }

    @Test
    fun twoPagesStayOnTheBar() {
        val pages = listOf(HomePage("feed", true), HomePage("saved", true), HomePage("trending", false))
        assertNull(HomePages.toggled(pages, "feed", false))
        assertEquals(listOf("feed", "saved", "trending"), HomePages.selectedIds(HomePages.toggled(pages, "trending", true)!!))
        assertEquals(listOf("saved", "feed", "trending"), HomePages.moved(pages, 0, 1).map { it.id })
        assertEquals(pages, HomePages.moved(pages, 0, 5))
        assertEquals("g1", HomePages.groupOf("group-g1"))
        assertNull(HomePages.groupOf("feed"))
    }
}

class FeedSwitchesTest {
    @Test
    fun theSessionFollowsTheSettings() {
        val settings = Settings().with(PostsPrefs.includeReplies, false).with(MediaPrefs.autoplay, true)
        val mirrored = FeedSwitches.mirrored(Session(), settings)
        assertFalse(mirrored.includeReplies)
        assertTrue(mirrored.includeReposts)
        assertTrue(mirrored.autoplay)
        assertFalse(FeedSwitches.mirrored(Session(), Settings()).autoplay)
    }

    @Test
    fun aSwitchTurnedOffInTheSessionAloneIsCarriedOverOnce() {
        val adopted = FeedSwitches.adopted(Settings(), Session(includeReposts = false))
        assertFalse(adopted[PostsPrefs.includeReposts])
        assertFalse(adopted.isSet(PostsPrefs.includeReplies))
        val chosen = Settings().with(PostsPrefs.includeReposts, true)
        assertTrue(FeedSwitches.adopted(chosen, Session(includeReposts = false))[PostsPrefs.includeReposts])
    }
}

class SettingsRulesTest {
    @Test
    fun aPresetFromAnotherProviderClearsTheKeyAndTakesItsModel() {
        val typed = AiForm("https://api.openai.com/v1", "sk-1", "gpt-x")
        val grok = typed.withPreset(AiProvider.GROK)
        assertEquals(AiForm(AiProvider.GROK.baseUrl, "", AiProvider.GROK.model), grok)
        assertTrue(grok.isGrok)
        val same = typed.withPreset(AiProvider.OPENAI)
        assertEquals("sk-1", same.apiKey)
        assertEquals("gpt-x", same.model)
        assertEquals("openrouter/free", AiForm("https://openrouter.ai/api/v1").modelPlaceholder)
        assertEquals("k", AiForm(" https://a.example/v1 ", " k ", " m ").saved().apiKey)
    }

    @Test
    fun forYouKeepsOneAccount() {
        val accounts = listOf("a", "b")
        assertEquals(listOf("a"), ForYouAccounts.toggled(emptyList(), "a", on = false, accounts))
        assertNull(ForYouAccounts.toggled(listOf("a"), "b", on = false, accounts))
        assertEquals(emptyList(), ForYouAccounts.toggled(listOf("a"), "a", on = true, accounts))
        assertTrue(ForYouAccounts.isOn("b", listOf("a")))
    }

    @Test
    fun theLanguageFilterNeedsALanguageToHideOrFold() {
        val none = LanguageFilterInput.committed(Settings().with(PostsPrefs.languageAction, LanguageFilterAction.HIDE), "  ")
        assertEquals(LanguageFilterAction.OFF, none[PostsPrefs.languageAction])
        assertNull(LanguageFilterInput.withAction(none, LanguageFilterAction.FOLD))
        val typed = LanguageFilterInput.committed(Settings(), " en, fr ")
        assertEquals("en, fr", typed[PostsPrefs.languages])
        assertEquals(LanguageFilterAction.FOLD, LanguageFilterInput.withAction(typed, LanguageFilterAction.FOLD)!![PostsPrefs.languageAction])
    }

    @Test
    fun theFeedTabsAreFollowingForYouAndThePinnedNetworksThatAreOn() {
        val settings = Settings().with(HomePrefs.feedStripNetworks, listOf("reddit", "mastodon", "karakeep")).with(RedditPrefs.enabled, true).with(KarakeepPrefs.enabled, true)
        assertEquals(listOf("following", "x", "reddit"), SettingsChoices.feedTabs(settings))
        assertFalse(SettingsChoices.stripUsable(settings.with(MastodonPrefs.enabled, false), "mastodon"))
    }

    @Test
    fun languagesGoByTheNameEachCallsItself() {
        assertEquals("Deutsch", SettingsChoices.languageName("de"))
        assertEquals("pt-BR", SettingsChoices.languageTag("pt_BR"))
        assertEquals("pt" to "BR", SettingsChoices.localeOf("pt_BR"))
        assertEquals("zh" to "TW", SettingsChoices.localeOf("zh_Hant"))
        assertEquals("be" to "", SettingsChoices.localeOf("be_Latn"))
        assertEquals("de" to "", SettingsChoices.localeOf("de"))
        assertNull(SettingsChoices.localeOf("system"))
        assertNull(SettingsChoices.languageTag(SettingsChoices.SYSTEM_LANGUAGE))
        assertEquals(29, SettingsChoices.languages.size)
        assertEquals(SettingsChoices.languages.sortedBy { it.name.lowercase() }, SettingsChoices.languages)
    }

    @Test
    fun aNetworksSectionIsBuiltFromItsPreferences() {
        assertEquals("mastodon", NetworkSections.of("mastodon")?.id)
        assertNull(NetworkSections.of("general"))
        val immich = NetworkSections.fields(NetworkSections.of("immich")!!)
        assertEquals(4, immich.size)
        assertTrue((immich[1] as NetworkField.Text).secret)
        assertEquals(SettingsSection.SPEECH, SettingsSection.of("speech"))
    }

    @Test
    fun aNetworkThatIsAPluginHasItsPluginsSettingsOnlyWhileInstalled() {
        val none = PluginIds()
        assertEquals(NetworkSettingsPlace.SECTION, NetworkSections.place("bluesky", none))
        assertEquals(NetworkSettingsPlace.SECTION, NetworkSections.place("mastodon", none))
        val offered = PluginIds(listed = setOf("bluesky", "weather"))
        assertEquals(NetworkSettingsPlace.NONE, NetworkSections.place("bluesky", offered))
        assertEquals(NetworkSettingsPlace.SECTION, NetworkSections.place("mastodon", offered))
        val installed = PluginIds(listed = setOf("bluesky", "weather"), installed = setOf("bluesky", "weather"))
        assertEquals(NetworkSettingsPlace.PLUGIN, NetworkSections.place("bluesky", installed))
        // A plugin that was never a network here has its settings all the same.
        assertEquals(NetworkSettingsPlace.PLUGIN, NetworkSections.place("weather", installed))
        assertEquals(NetworkSettingsPlace.NONE, NetworkSections.place("bluesky", installed.copy(withoutSettings = setOf("bluesky"))))
        assertEquals(NetworkSettingsPlace.NONE, NetworkSections.place("general", none))
    }
}
