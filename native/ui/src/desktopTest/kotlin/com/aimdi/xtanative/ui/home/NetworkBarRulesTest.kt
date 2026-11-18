package com.aimdi.xtanative.ui.home

import com.aimdi.xtanative.ui.plugins.PluginClientBar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetworkBarRulesTest {
    @Test
    fun threeOrFourSectionsMakeTheBarFewerOrMoreMakeNone() {
        listOf("reddit", "bluesky", "mastodon", "threads", "rss", "hackernews", "pixiv", "substack", "stocks").forEach {
            assertTrue(NetworkBarRules.takesOver(it, 3), it)
            assertTrue(NetworkBarRules.takesOver(it, 4), it)
        }
        assertFalse(NetworkBarRules.takesOver("reddit", 2), "two sections are a feed row's tabs, not destinations")
        assertFalse(NetworkBarRules.takesOver("hackernews", 8), "Hacker News' lists run as its feed row, with no bottom bar")
        assertFalse(NetworkBarRules.takesOver("booru", 3), "a network without sections of its own keeps the app's bar")
        assertFalse(NetworkBarRules.takesOver(null, 3), "Following and X keep the app's bar")
    }

    @Test
    fun sectionsJoiningTheMenuLeaveTheBar() {
        val joins = { s: String -> s.startsWith("+") }
        assertEquals(Split(listOf("a", "b", "c", "d"), emptyList()), NetworkBarRules.split(listOf("a", "b", "c", "d"), joins))
        val pixiv = listOf("Home", "Ranking", "Favorites", "Search", "+More")
        assertEquals(Split(listOf("Home", "Ranking", "Favorites", "Search"), listOf("+More")), NetworkBarRules.split(pixiv, joins), "a section joining the menu leaves four on the bar")
    }

    @Test
    fun redditsOwnMenuIsTheBarsOnlyOne() {
        assertTrue("reddit" in NetworkBarRules.ONE_MENU)
        assertFalse("bluesky" in NetworkBarRules.ONE_MENU, "the others keep Home's menu of their options")
    }

    @Test
    fun eachPageKeepsItsOwnNetwork() {
        val takeover = NetworkBarTakeover()
        takeover.show(NetworkBarRules.FEED, "reddit", PluginClientBar())
        takeover.show(NetworkBarRules.owner(BarPage.Network("hackernews"))!!, "hackernews", PluginClientBar())
        assertNull(takeover.on(BarPage.Feed), "no sections handed yet: the app's bar stays")
        assertFalse(takeover.hidesBar(BarPage.Feed), "nor is it hidden before the plugin hands its sections")
        assertNull(takeover.on(BarPage.Saved))
        assertEquals("page:hackernews", NetworkBarRules.owner(BarPage.Network("hackernews")))
        takeover.show(NetworkBarRules.FEED, null, null)
        assertNull(takeover.on(BarPage.Feed))
    }
}
