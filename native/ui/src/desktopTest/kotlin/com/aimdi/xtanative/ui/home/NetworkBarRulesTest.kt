package com.aimdi.xtanative.ui.home

import com.aimdi.xtanative.ui.plugins.PluginClientBar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetworkBarRulesTest {
    @Test
    fun theNetworksWithSectionsTakeTheBottomBar() {
        listOf("reddit", "bluesky", "mastodon", "threads", "rss", "hackernews", "pixiv", "substack", "stocks").forEach {
            assertTrue(NetworkBarRules.takesOver(it, 2), it)
        }
        assertFalse(NetworkBarRules.takesOver("reddit", 1), "one section is no switch worth the bar")
        assertFalse(NetworkBarRules.takesOver("booru", 3), "a network without sections of its own keeps the app's bar")
        assertFalse(NetworkBarRules.takesOver(null, 3), "Following and X keep the app's bar")
    }

    @Test
    fun fourSectionsFitPastFourTheFirstThreeStay() {
        val joins = { s: String -> s.startsWith("+") }
        assertEquals(Split(listOf("a", "b", "c", "d"), emptyList()), NetworkBarRules.split(listOf("a", "b", "c", "d"), joins))
        val hn = listOf("Top", "New", "Best", "Ask", "Show", "Jobs", "Saved", "Following")
        assertEquals(Split(listOf("Top", "New", "Best"), listOf("Ask", "Show", "Jobs", "Saved", "Following")), NetworkBarRules.split(hn, joins))
        val pixiv = listOf("Home", "Ranking", "Favorites", "Search", "+More")
        assertEquals(Split(listOf("Home", "Ranking", "Favorites", "Search"), listOf("+More")), NetworkBarRules.split(pixiv, joins), "a section joining More leaves room for four")
    }

    @Test
    fun moreListsEveryPlaceButThePageShown() {
        val bar = listOf(BarPage.Feed, BarPage.Subscriptions, BarPage.Network("hackernews"), BarPage.Saved)
        val slots = bar.indices.map { BarSlot.Page(it) } + BarSlot.Networks
        assertEquals(listOf(BarSlot.Page(1), BarSlot.Page(2), BarSlot.Page(3), BarSlot.Networks), NetworkBarRules.moreSlots(slots, bar, BarPage.Feed))
        assertEquals(listOf(BarSlot.Page(0), BarSlot.Page(1), BarSlot.Page(3), BarSlot.Networks), NetworkBarRules.moreSlots(slots, bar, BarPage.Network("hackernews")))
    }

    @Test
    fun eachPageKeepsItsOwnNetwork() {
        val takeover = NetworkBarTakeover()
        takeover.show(NetworkBarRules.FEED, "reddit", PluginClientBar())
        takeover.show(NetworkBarRules.owner(BarPage.Network("hackernews"))!!, "hackernews", PluginClientBar())
        assertNull(takeover.on(BarPage.Feed), "no sections handed yet: the app's bar stays")
        assertNull(takeover.on(BarPage.Saved))
        assertEquals("page:hackernews", NetworkBarRules.owner(BarPage.Network("hackernews")))
        takeover.show(NetworkBarRules.FEED, null, null)
        assertNull(takeover.on(BarPage.Feed))
    }
}
