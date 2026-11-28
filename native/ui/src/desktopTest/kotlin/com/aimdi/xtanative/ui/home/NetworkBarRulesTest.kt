package com.aimdi.xtanative.ui.home

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import com.aimdi.xtanative.ui.plugins.PluginBarContent
import com.aimdi.xtanative.ui.plugins.PluginClientBar
import com.aimdi.xtanative.ui.plugins.PluginHomeTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetworkBarRulesTest {
    @Test
    fun threeOrFourSectionsMakeTheBarFewerOrMoreMakeNone() {
        listOf("reddit", "bluesky", "mastodon", "threads", "rss", "hackernews", "pixiv", "substack", "stocks", "booru").forEach {
            assertTrue(NetworkBarRules.takesOver(it, 3), it)
            assertTrue(NetworkBarRules.takesOver(it, 4), it)
        }
        assertFalse(NetworkBarRules.takesOver("reddit", 2), "two sections are a feed row's tabs, not destinations")
        assertFalse(NetworkBarRules.takesOver("hackernews", 8), "Hacker News' lists run as its feed row, with no bottom bar")
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

    @Test
    fun aNetworkReadAsOneOfSeveralColumnsKeepsItsSectionsOffHomesBar() {
        val takeover = NetworkBarTakeover()
        takeover.show(NetworkBarRules.FEED, "pixiv", handed(4), inColumn = true)
        assertEquals("pixiv", takeover.on(BarPage.Feed)?.network, "the column draws them under itself")
        assertNull(takeover.onBar(BarPage.Feed), "not across every column of Home")
        takeover.show(NetworkBarRules.FEED, "pixiv", handed(4))
        assertEquals("pixiv", takeover.onBar(BarPage.Feed)?.network, "read alone, the sections are Home's bar")
    }

    private fun handed(sections: Int): PluginClientBar = PluginClientBar().apply {
        val tabs = List(sections) { PluginHomeTab(Icons.Filled.Home, "s$it", it == 0, {}) }
        val none: @Composable RowScope.() -> Unit = {}
        content = PluginBarContent(mutableStateOf(tabs), mutableStateOf<Color?>(null), mutableStateOf(none))
    }
}
