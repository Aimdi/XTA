package com.aimdi.xtanative.ui.plugins

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.store.saved.SavedContent
import com.aimdi.xtanative.store.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking

class PluginArchiveTest {
    @Test
    fun aLinkRecordIsXtasAndSavedReadsItAsThePluginsPost() {
        val archive = PluginPostArchive.link("pixiv", "https://www.pixiv.net/artworks/7", "Ann", "A study", listOf("https://i.pximg.net/a.jpg"))
        assertEquals("pixiv:https://www.pixiv.net/artworks/7", archive.id)
        assertEquals("Ann", archive.authorId, "XTA keeps the author when no one is named")
        val kept = SavedContent.of(archive.content)
        assertEquals(SavedContent.Kind.LINK, kept.kind)
        assertEquals("pixiv", kept.network)
        assertTrue(kept.matches("a study"))
        assertEquals(listOf("https://i.pximg.net/a.jpg"), kept.photos.map { it.url })
    }

    @Test
    fun aLinkRecordKeepsTheIdsItWasGiven() {
        val archive = PluginPostArchive.link("hackernews", "https://news.ycombinator.com/item?id=1", "pg", "Show HN", id = "hackernews:1", authorId = "pg")
        assertEquals("hackernews:1", archive.id)
        assertEquals("hackernews:1", SavedContent.of(archive.content).record["archiveId"].string)
    }

    @Test
    fun aLinkRecordReadsBackAsTheArchiveItWasKeptAs() {
        val kept = PluginPostArchive.link("booru", "https://danbooru.donmai.us/posts/1", "https://danbooru.donmai.us", "solo", listOf("https://cdn/a.jpg"))
        val read = PluginPostArchive.ofLink(kept.content, "booru")!!
        assertEquals(kept.id, read.id)
        assertEquals(kept.authorId, read.authorId)
        assertEquals(kept.content, read.content)

        val named = PluginPostArchive.link("hackernews", "https://news.ycombinator.com/item?id=1", "pg", "Show HN", id = "hackernews:1", authorId = "pg-id")
        assertEquals("hackernews:1", PluginPostArchive.ofLink(named.content, "hackernews")?.id, "the id it was given")
        assertEquals("pg-id", PluginPostArchive.ofLink(named.content, "hackernews")?.authorId)
    }

    @Test
    fun anotherPluginsOrKindsRecordIsNoLinkOfThisOne() {
        val pixiv = PluginPostArchive.link("pixiv", "https://www.pixiv.net/artworks/7", "Ann", "A study")
        assertEquals(null, PluginPostArchive.ofLink(pixiv.content, "booru"))
        assertEquals(null, PluginPostArchive.ofLink(PluginPostArchive.link("booru", "file:///etc/passwd", "x", "y").content, "booru"), "no web address")
        assertEquals(null, PluginPostArchive.ofLink(J.parse("""{"xtaPlugin":"reddit","post":{"id":"x"}}""").raw!!, "booru"))
    }

    @Test
    fun thePluginsHomeTimelineSwitchIsItsOwnAndOffUntilTurnedOn(): Unit = runBlocking {
        val app = MutableStateFlow<Settings?>(Settings())
        val reddit = PluginSettings("reddit", app) { change -> app.value = change(app.value!!) }
        assertFalse(reddit.inHomeFeed())
        reddit.setInHomeFeed(true)
        assertTrue(reddit.inHomeFeed())
        assertTrue(PluginSettings.inHomeFeed(app.value, "reddit"))
        assertFalse(PluginSettings.inHomeFeed(app.value, "bluesky"), "each plugin has its own")
        assertEquals("plugin.reddit.in_home_feed", PluginSettings.homeFeedKey("reddit"), "where XTA keeps it")
    }
}
