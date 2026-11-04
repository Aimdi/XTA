package com.aimdi.xtanative.plugins.hackernews

import com.aimdi.xtanative.networks.hackernews.HnStory
import com.aimdi.xtanative.store.settings.HackerNewsPrefs
import com.aimdi.xtanative.store.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The plugin's own rules: its routes and links, and what it keeps in the app's settings. */
class HackerNewsPluginRulesTest {
    private val story = HnStory(8863, "My YC app", url = "https://dropbox.com/", text = "", author = "dhouston", score = 111)

    @Test
    fun routesCarryWhatTheirScreensOpenWith() {
        assertEquals(HnRoute.Story(8863), HnRoutes.parse(HnRoutes.story(8863)))
        assertEquals(HnRoute.User("pg"), HnRoutes.parse(HnRoutes.user("pg")))
        assertNull(HnRoutes.parse("story?id=abc"))
        assertNull(HnRoutes.parse("elsewhere"))
    }

    @Test
    fun hackerNewsLinksOpenHere() {
        assertEquals(HnRoutes.story(8863), HnRoutes.forUrl("https://news.ycombinator.com/item?id=8863"))
        assertEquals(HnRoutes.user("pg"), HnRoutes.forUrl("https://news.ycombinator.com/user?id=pg"))
        assertNull(HnRoutes.forUrl("https://news.ycombinator.com/news"))
        assertNull(HnRoutes.forUrl("https://example.com/item?id=1"))
    }

    @Test
    fun heartsSavesAndFollowsAreKeptUnderXtasKeys() {
        val liked = HnKept.withLike(Settings(), story)
        assertEquals("""["8863"]""", liked[HackerNewsPrefs.likedPosts])
        val saved = HnKept.withSaved(liked, story)
        val followed = HnKept.withFollow(HnKept.withFollow(saved, "zed"), "Alice")
        val kept = HnKept.of(followed)
        assertTrue(kept.isLiked(story))
        assertTrue(kept.isSaved(story))
        assertEquals(listOf("Alice", "zed"), kept.follows)
        assertEquals("""["Alice","zed"]""", followed[HackerNewsPrefs.follows])
        assertTrue(kept.isFollowing("ALICE"))
        assertFalse(HnKept.of(HnKept.withLike(followed, story)).isLiked(story))
        assertEquals(HnKept(), HnKept.of(null))
    }
}
