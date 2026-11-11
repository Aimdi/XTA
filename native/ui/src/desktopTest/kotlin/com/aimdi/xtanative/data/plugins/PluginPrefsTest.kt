package com.aimdi.xtanative.data.plugins

import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.store.StoreFile
import com.aimdi.xtanative.store.settings.BlueskyPrefs
import com.aimdi.xtanative.store.settings.DeepmarksPrefs
import com.aimdi.xtanative.store.settings.HomePrefs
import com.aimdi.xtanative.store.settings.KarakeepPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.SubstackPrefs
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class PluginPrefsTest {
    private fun file(): File = File(Files.createTempDirectory("plugin-prefs").toFile(), "plugins/prefs.json")

    @Test
    fun thePrivateSwitchIsKept(): Unit = runBlocking {
        val path = file()
        PluginPrefs(StoreFile(path)).update { it.copy(showPrivate = true) }
        assertTrue(PluginPrefs(StoreFile(path)).also { it.load() }.state.value.showPrivate)
    }

    @Test
    fun aFileThatIsNotPrefsReadsAsNone(): Unit = runBlocking {
        val path = file().apply { parentFile.mkdirs(); writeText("not json") }
        val prefs = PluginPrefs(StoreFile(path)).also { it.load() }
        assertEquals(PluginPrefsState(), prefs.state.value)
    }

    @Test
    fun aPluginGoesOnTheStripAfterTheOthersAndComesOff() {
        val pinned = Settings().with(HomePrefs.feedStripNetworks, listOf("reddit")).withPluginOnStrip("bluesky", pinned = true)
        assertEquals(listOf("reddit", "bluesky"), pinned[HomePrefs.feedStripNetworks])
        assertEquals(pinned, pinned.withPluginOnStrip("bluesky", pinned = true))
        assertEquals(listOf("reddit"), pinned.withPluginOnStrip("bluesky", pinned = false)[HomePrefs.feedStripNetworks])
    }

    @Test
    fun showAsATabPutsThePageOnTheBarAndOffItOnTheStrip() {
        val before = Settings().with(HomePrefs.seededNetworkTabs, listOf("bluesky", "reddit"))
        assertFalse(before.showsPluginAsTab("bluesky"), "not on until it is turned on")
        val shown = before.withPluginAsTab("bluesky", on = true)
        assertTrue(shown.showsPluginAsTab("bluesky"))
        assertTrue(shown[BlueskyPrefs.enabled])
        assertEquals(listOf("reddit"), shown[HomePrefs.seededNetworkTabs], "offered to the bar again")
        val hidden = shown.withPluginAsTab("bluesky", on = false)
        assertFalse(hidden.showsPluginAsTab("bluesky"))
        assertEquals(listOf("bluesky"), hidden[HomePrefs.feedStripNetworks], "it waits among Home's timelines")
    }

    @Test
    fun aPluginWithoutATabHasNoneToShow() {
        assertFalse(hasTabOf("karakeep"))
        assertTrue(hasTabOf("bluesky"))
        assertEquals(Settings(), Settings().withPluginAsTab("karakeep", on = true))
        assertFalse(Settings().withPluginAsTab("karakeep", on = true).showsPluginAsTab("karakeep"))
    }

    @Test
    fun uninstallingAPluginLeavesNothingOfItOnHome() {
        val settings = Settings()
            .with(HomePrefs.feedStripNetworks, listOf("bluesky", "reddit"))
            .with(HomePrefs.recentNetworks, listOf("reddit", "bluesky"))
            .with(HomePrefs.pages, listOf("feed", "bluesky", "saved"))
            .with(HomePrefs.topBarPins["bluesky"], listOf("search"))
            .with(HomePrefs.topBarPins["reddit"], listOf("settings"))
        val erased = settings.withoutPlugin("bluesky")
        assertEquals(listOf("reddit"), erased[HomePrefs.feedStripNetworks])
        assertEquals(listOf("reddit"), erased[HomePrefs.recentNetworks])
        assertEquals(listOf("feed", "saved"), erased[HomePrefs.pages])
        assertFalse(erased.isSet(HomePrefs.topBarPins["bluesky"]))
        assertEquals(listOf("settings"), erased[HomePrefs.topBarPins["reddit"]])
    }

    @Test
    fun uninstallingAPluginResetsItsOwnSettingsAndNoOtherNetworks() {
        val settings = Settings()
            .with(SubstackPrefs.readIds, """["a.substack.com/p/one"]""")
            .with(SubstackPrefs.markReadOnScroll, true)
            .with(BlueskyPrefs.instance, "https://example.social")
        val erased = settings.withoutPlugin("substack")
        assertFalse(erased.isSet(SubstackPrefs.readIds))
        assertFalse(erased.isSet(SubstackPrefs.markReadOnScroll))
        assertEquals("https://example.social", erased[BlueskyPrefs.instance])
        assertEquals(settings, settings.withoutPlugin("sub"), "a prefix of another id is not that plugin")
    }

    @Test
    fun uninstallingAPluginErasesTheKeysItKeptAmongTheSecrets() {
        val settings = Settings()
            .with(DeepmarksPrefs.apiKey, "dmk_live_x")
            .with(DeepmarksPrefs.secretKey, "nsec1example")
            .with(DeepmarksPrefs.apiBase, "http://localhost:4000")
            .with(KarakeepPrefs.apiKey, "kk")
        val erased = settings.withoutPlugin("deepmarks")
        assertFalse(listOf(DeepmarksPrefs.apiKey, DeepmarksPrefs.secretKey, DeepmarksPrefs.apiBase).any(erased::isSet))
        assertEquals("kk", erased[KarakeepPrefs.apiKey], "another plugin's key stays")
    }

    @Test
    fun settingsThatNeverNamedThePluginStayAsTheyWere() {
        val settings = Settings().with(HomePrefs.feedStripNetworks, listOf("reddit"))
        assertEquals(settings, settings.withoutPlugin("bluesky"))
        assertEquals(Settings(), Settings().withoutPlugin("bluesky"))
    }

    @Test
    fun uninstallingAPluginTakesItsAccountsOutOfTheGroups() {
        val session = Session(groups = listOf(FollowGroup("g1", "Friends", listOf("12", "bluesky:did:plc:maya", "reddit:spez", "blueskyfan"))))
        assertEquals(listOf("12", "reddit:spez", "blueskyfan"), session.withoutMembersOf("bluesky").groups.single().memberIds)
    }

    @Test
    fun uninstallingAPluginLetsAReinstallOfferItsTabAndStripPlaceAgain() {
        val settings = Settings()
            .with(HomePrefs.seededNetworkTabs, listOf("bluesky", "reddit"))
            .with(HomePrefs.seededStripNetworks, listOf("reddit", "bluesky"))
        val erased = settings.withoutPlugin("bluesky")
        assertEquals(listOf("reddit"), erased[HomePrefs.seededNetworkTabs], "XTA's uninstall forgets it was offered a tab")
        assertEquals(listOf("reddit"), erased[HomePrefs.seededStripNetworks])
    }
}
