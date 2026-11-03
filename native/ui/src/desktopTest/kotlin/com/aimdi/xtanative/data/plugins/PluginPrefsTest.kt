package com.aimdi.xtanative.data.plugins

import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.store.StoreFile
import com.aimdi.xtanative.store.settings.HomePrefs
import com.aimdi.xtanative.store.settings.Settings
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
}
