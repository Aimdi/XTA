package com.aimdi.xtanative.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals

class PluginSwitcherTest {
    private val tabs = listOf(HomeTab.Following, HomeTab.Network("reddit"), HomeTab.Network("substack"), HomeTab.Network("bluesky"), HomeTab.Network("hackernews"))

    @Test
    fun theRecentlyUsedComeFirstThenHomesOrder() {
        assertEquals(listOf("bluesky", "reddit", "substack", "hackernews"), PluginSwitcher.networks(tabs, listOf("bluesky", "reddit")))
    }

    @Test
    fun onlyNetworksOnHomeAreOffered() {
        assertEquals(
            listOf("reddit", "substack", "bluesky", "hackernews"),
            PluginSwitcher.networks(tabs, listOf("mastodon")),
            "a network used once but taken off Home is not in the grid",
        )
        assertEquals(emptyList(), PluginSwitcher.networks(listOf(HomeTab.Following), listOf("reddit")))
    }

    @Test
    fun theWordsAreXtasOwnTimelinesAndNoNetworkOrGroup() {
        val tabs = listOf(HomeTab.Following, HomeTab.ForYou, HomeTab.Network("reddit"), HomeTab.Group("g", "G"))
        assertEquals(listOf(HomeTab.Following, HomeTab.ForYou), PluginSwitcher.timelines(tabs))
        assertEquals(listOf(HomeTab.Following), PluginSwitcher.timelines(listOf(HomeTab.Following, HomeTab.Network("reddit"))), "X left Home: no word for it")
    }
}
